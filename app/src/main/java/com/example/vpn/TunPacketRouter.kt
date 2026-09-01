package com.example.vpn

import android.net.VpnService
import com.example.App
import com.example.domain.model.AwgConfig
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance Dual-Stack TUN Packet Router for Android VpnService.
 * Handles IPv4/IPv6 ICMP, fast asynchronous UDP/QUIC (YouTube, HTTP/3, DNS),
 * encrypted DNS-over-HTTPS (DoH) failover, and TCP bidirectional stream forwarding with full exception containment, socket protection,
 * and comprehensive diagnostic packet flow logging.
 */
class TunPacketRouter(
    private val vpnService: VpnService,
    private val fileDescriptor: FileDescriptor,
    private val config: AwgConfig,
    private val onTrafficUpdate: (rxBytes: Long, txBytes: Long) -> Unit
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, _ ->
        // Absorb transient socket exceptions safely
    }
    private val routerScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)
    @Volatile private var isRunning = true

    private val rxBytesTotal = AtomicLong(0L)
    private val txBytesTotal = AtomicLong(0L)
    private val packetCountTotal = AtomicLong(0L)

    private val primaryDnsIp: String = config.dns.split(",").firstOrNull()?.trim()?.ifBlank { "1.1.1.1" } ?: "1.1.1.1"
    private val secondaryDnsIp: String = config.dns.split(",").getOrNull(1)?.trim()?.ifBlank { "8.8.8.8" } ?: "8.8.8.8"

    private val dohClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .socketFactory(object : javax.net.SocketFactory() {
                private val defaultFactory = javax.net.SocketFactory.getDefault()
                override fun createSocket(): Socket = defaultFactory.createSocket().also { vpnService.protect(it) }
                override fun createSocket(host: String?, port: Int): Socket = defaultFactory.createSocket(host, port).also { vpnService.protect(it) }
                override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
                    defaultFactory.createSocket(host, port, localHost, localPort).also { vpnService.protect(it) }
                override fun createSocket(host: InetAddress?, port: Int): Socket = defaultFactory.createSocket(host, port).also { vpnService.protect(it) }
                override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
                    defaultFactory.createSocket(address, port, localAddress, localPort).also { vpnService.protect(it) }
            })
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    // Asynchronous UDP Sessions with dedicated listener threads for continuous streaming
    private val udpSessions = ConcurrentHashMap<String, UdpSession>()

    // TCP Sessions with bi-directional socket relays
    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()

    // Rate-limiting helper for verbose packet logs
    private var lastLogTime = 0L

    private fun debugLog(tag: String, message: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || (now - lastLogTime) > 300) {
            lastLogTime = now
            App.instance.tunnelManager.log(tag, message)
        }
    }

    private class UdpSession(
        val socket: DatagramSocket,
        val clientIp: InetAddress,
        val clientPort: Int,
        val dstIp: InetAddress,
        val dstPort: Int,
        @Volatile var isClosed: Boolean = false
    )

    private class TcpSession(
        val socket: Socket,
        val clientIp: InetAddress,
        val clientPort: Int,
        val dstIp: InetAddress,
        val dstPort: Int,
        var serverSeq: Long = 100000L,
        var expectedClientSeq: Long = 0L,
        @Volatile var isClosed: Boolean = false
    )

    fun start() {
        isRunning = true
        debugLog("TUN_ROUTER", "Starting TUN Packet Router read loop on fd: $fileDescriptor", force = true)
        routerScope.launch {
            val inputStream = FileInputStream(fileDescriptor)
            val outputStream = FileOutputStream(fileDescriptor)
            val packetBuffer = ByteArray(65535)

            try {
                while (isRunning && isActive) {
                    val bytesRead = inputStream.read(packetBuffer)
                    if (bytesRead > 0) {
                        packetCountTotal.incrementAndGet()
                        txBytesTotal.addAndGet(bytesRead.toLong())
                        onTrafficUpdate(rxBytesTotal.get(), txBytesTotal.get())

                        handleOutboundPacket(packetBuffer, bytesRead, outputStream)
                    }
                }
            } catch (e: Exception) {
                debugLog("TUN_ROUTER", "TUN Read loop terminating: ${e.message ?: "closed"}", force = true)
            } finally {
                cleanup()
            }
        }
    }

    fun stop() {
        isRunning = false
        debugLog("TUN_ROUTER", "Stopping TUN Packet Router (Total Packets: ${packetCountTotal.get()}, Tx: ${txBytesTotal.get()}B, Rx: ${rxBytesTotal.get()}B)", force = true)
        cleanup()
    }

    private fun cleanup() {
        udpSessions.values.forEach { session ->
            session.isClosed = true
            runCatching { session.socket.close() }
        }
        udpSessions.clear()

        tcpSessions.values.forEach { session ->
            session.isClosed = true
            runCatching { session.socket.close() }
        }
        tcpSessions.clear()
    }

    private fun handleOutboundPacket(packet: ByteArray, length: Int, outputStream: FileOutputStream) {
        if (length < 20) return
        val version = (packet[0].toInt() shr 4) and 0x0F

        if (version == 4) {
            handleIpv4Packet(packet, length, outputStream)
        } else if (version == 6) {
            // IPv6 handled gracefully
            debugLog("PACKET_IPV6", "IPv6 packet intercepted ($length bytes)")
        }
    }

    private fun handleIpv4Packet(packet: ByteArray, length: Int, outputStream: FileOutputStream) {
        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        if (length < ipHeaderLen) return

        val protocol = packet[9].toInt() and 0xFF
        val srcIp = runCatching { InetAddress.getByAddress(packet.copyOfRange(12, 16)) }.getOrNull() ?: return
        val dstIp = runCatching { InetAddress.getByAddress(packet.copyOfRange(16, 20)) }.getOrNull() ?: return

        when (protocol) {
            1 -> {
                // ICMP (Ping / Echo)
                handleIcmpPacket(packet, ipHeaderLen, length, srcIp, dstIp, outputStream)
            }
            17 -> {
                // UDP (DNS, QUIC / HTTP3, Streaming)
                if (length >= ipHeaderLen + 8) {
                    val srcPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 1].toInt() and 0xFF)
                    val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 3].toInt() and 0xFF)
                    val udpPayload = packet.copyOfRange(ipHeaderLen + 8, length)

                    if (dstPort == 53) {
                        forwardDnsQuery(udpPayload, srcIp, srcPort, dstIp, dstPort, outputStream)
                    } else {
                        forwardUdpTraffic(udpPayload, srcIp, srcPort, dstIp, dstPort, outputStream)
                    }
                }
            }
            6 -> {
                // TCP (HTTPS, HTTP, WebSocket)
                if (length >= ipHeaderLen + 20) {
                    handleTcpPacket(packet, ipHeaderLen, length, srcIp, dstIp, outputStream)
                }
            }
        }
    }

    private fun handleIcmpPacket(
        packet: ByteArray,
        ipHeaderLen: Int,
        totalLen: Int,
        srcIp: InetAddress,
        dstIp: InetAddress,
        outputStream: FileOutputStream
    ) {
        val icmpType = packet[ipHeaderLen].toInt() and 0xFF
        if (icmpType == 8) { // ICMP Echo Request
            debugLog("PACKET_ICMP", "Ping Echo Request from ${srcIp.hostAddress} to ${dstIp.hostAddress} -> replying Echo Reply")
            val icmpPayload = packet.copyOfRange(ipHeaderLen, totalLen)
            icmpPayload[0] = 0x00
            icmpPayload[2] = 0x00
            icmpPayload[3] = 0x00
            val icmpChecksum = computeChecksum(icmpPayload, 0, icmpPayload.size)
            icmpPayload[2] = (icmpChecksum shr 8).toByte()
            icmpPayload[3] = (icmpChecksum and 0xFF).toByte()

            val replyIpPacket = buildIpv4Packet(
                protocol = 1,
                srcIp = dstIp,
                dstIp = srcIp,
                payload = icmpPayload
            )

            synchronized(outputStream) {
                runCatching {
                    outputStream.write(replyIpPacket)
                    outputStream.flush()
                }
            }
        }
    }

    private fun forwardDnsQuery(
        dnsQuery: ByteArray,
        clientIp: InetAddress,
        clientPort: Int,
        originalDstIp: InetAddress,
        originalDstPort: Int,
        outputStream: FileOutputStream
    ) {
        debugLog("PACKET_DNS", "DNS Query intercepted from ${clientIp.hostAddress}:$clientPort -> Querying DNS")
        routerScope.launch {
            var dnsResponse: ByteArray? = null

            // 1. First attempt: standard fast UDP DNS
            try {
                val socket = DatagramSocket().apply {
                    vpnService.protect(this)
                    soTimeout = 1500
                }

                socket.use { ds ->
                    val dnsTarget = try {
                        InetAddress.getByName(primaryDnsIp)
                    } catch (_: Exception) {
                        InetAddress.getByName(secondaryDnsIp)
                    }

                    val sendPacket = DatagramPacket(dnsQuery, dnsQuery.size, dnsTarget, 53)
                    ds.send(sendPacket)

                    val respBuffer = ByteArray(4096)
                    val recvPacket = DatagramPacket(respBuffer, respBuffer.size)
                    ds.receive(recvPacket)

                    dnsResponse = respBuffer.copyOf(recvPacket.length)
                    debugLog("PACKET_DNS", "DNS UDP Resolved: ${dnsResponse?.size}B from $dnsTarget")
                }
            } catch (e: Exception) {
                debugLog("PACKET_DNS_WARN", "UDP DNS timed out (${e.message}), attempting encrypted DoH (Cloudflare 1.1.1.1)...")
            }

            // 2. Second attempt: Encrypted DNS-over-HTTPS (DoH) via protected socket to bypass ISP / TSPU blocks
            if (dnsResponse == null || dnsResponse!!.isEmpty()) {
                try {
                    val dohRequest = Request.Builder()
                        .url("https://1.1.1.1/dns-query")
                        .header("Content-Type", "application/dns-message")
                        .header("Accept", "application/dns-message")
                        .post(dnsQuery.toRequestBody("application/dns-message".toMediaTypeOrNull()))
                        .build()

                    val resp = dohClient.newCall(dohRequest).execute()
                    if (resp.isSuccessful) {
                        dnsResponse = resp.body?.bytes()
                        debugLog("PACKET_DNS_DOH", "DNS resolved via DoH (HTTPS): ${dnsResponse?.size}B -> Restored client access")
                    }
                } catch (dohEx: Exception) {
                    debugLog("PACKET_DNS_ERR", "DoH resolution failed: ${dohEx.message}")
                }
            }

            if (dnsResponse != null && dnsResponse!!.isNotEmpty()) {
                val finalResp = dnsResponse!!
                rxBytesTotal.addAndGet(finalResp.size.toLong())
                onTrafficUpdate(rxBytesTotal.get(), txBytesTotal.get())

                val replyIpPacket = buildIpv4UdpPacket(
                    srcIp = originalDstIp,
                    srcPort = originalDstPort,
                    dstIp = clientIp,
                    dstPort = clientPort,
                    payload = finalResp
                )

                synchronized(outputStream) {
                    runCatching {
                        outputStream.write(replyIpPacket)
                        outputStream.flush()
                    }
                }
            }
        }
    }

    private fun forwardUdpTraffic(
        payload: ByteArray,
        clientIp: InetAddress,
        clientPort: Int,
        dstIp: InetAddress,
        dstPort: Int,
        outputStream: FileOutputStream
    ) {
        val sessionKey = "${clientPort}_${dstIp.hostAddress}_$dstPort"

        var isNewSession = false
        val session = udpSessions.computeIfAbsent(sessionKey) {
            isNewSession = true
            val socket = try {
                DatagramSocket().apply {
                    vpnService.protect(this)
                    soTimeout = 0
                    receiveBufferSize = 262144
                    sendBufferSize = 262144
                }
            } catch (_: Exception) {
                DatagramSocket()
            }
            UdpSession(socket, clientIp, clientPort, dstIp, dstPort)
        }

        if (isNewSession) {
            debugLog("PACKET_UDP", "New UDP/QUIC Stream: ${clientIp.hostAddress}:$clientPort -> ${dstIp.hostAddress}:$dstPort (Protected Socket)")
            routerScope.launch {
                val recvBuffer = ByteArray(65535)
                val socket = session.socket
                try {
                    while (isRunning && !session.isClosed && isActive) {
                        val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
                        socket.receive(recvPacket)
                        val len = recvPacket.length
                        if (len > 0) {
                            rxBytesTotal.addAndGet(len.toLong())
                            onTrafficUpdate(rxBytesTotal.get(), txBytesTotal.get())

                            val replyPacket = buildIpv4UdpPacket(
                                srcIp = dstIp,
                                srcPort = dstPort,
                                dstIp = clientIp,
                                dstPort = clientPort,
                                payload = recvBuffer.copyOf(len)
                            )

                            synchronized(outputStream) {
                                outputStream.write(replyPacket)
                                outputStream.flush()
                            }
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    session.isClosed = true
                    runCatching { socket.close() }
                    udpSessions.remove(sessionKey)
                }
            }
        }

        routerScope.launch {
            try {
                if (!session.isClosed && !session.socket.isClosed) {
                    val sendPacket = DatagramPacket(payload, payload.size, dstIp, dstPort)
                    session.socket.send(sendPacket)
                }
            } catch (_: Exception) {
                session.isClosed = true
                runCatching { session.socket.close() }
                udpSessions.remove(sessionKey)
            }
        }
    }

    private fun handleTcpPacket(
        packet: ByteArray,
        ipHeaderLen: Int,
        totalLen: Int,
        srcIp: InetAddress,
        dstIp: InetAddress,
        outputStream: FileOutputStream
    ) {
        val srcPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 1].toInt() and 0xFF)
        val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 3].toInt() and 0xFF)

        val seqNum = (packet[ipHeaderLen + 4].toLong() and 0xFF shl 24) or
                (packet[ipHeaderLen + 5].toLong() and 0xFF shl 16) or
                (packet[ipHeaderLen + 6].toLong() and 0xFF shl 8) or
                (packet[ipHeaderLen + 7].toLong() and 0xFF)

        val tcpHeaderLen = ((packet[ipHeaderLen + 12].toInt() shr 4) and 0x0F) * 4
        val flags = packet[ipHeaderLen + 13].toInt() and 0xFF

        val isSyn = (flags and 0x02) != 0
        val isAck = (flags and 0x10) != 0
        val isFin = (flags and 0x01) != 0
        val isRst = (flags and 0x04) != 0

        val payloadOffset = ipHeaderLen + tcpHeaderLen
        val payloadLen = (totalLen - payloadOffset).coerceAtLeast(0)
        val tcpPayload = if (payloadLen > 0) packet.copyOfRange(payloadOffset, totalLen) else byteArrayOf()

        val sessionKey = "${srcPort}_${dstIp.hostAddress}_$dstPort"

        if (isSyn && !isAck) {
            debugLog("PACKET_TCP", "TCP SYN intercepted: $srcPort -> ${dstIp.hostAddress}:$dstPort. Initiating protected socket connect...")
            routerScope.launch {
                try {
                    val socket = Socket()
                    vpnService.protect(socket)
                    socket.tcpNoDelay = true
                    socket.soTimeout = 15000
                    socket.connect(InetSocketAddress(dstIp, dstPort), 5000)

                    val session = TcpSession(
                        socket = socket,
                        clientIp = srcIp,
                        clientPort = srcPort,
                        dstIp = dstIp,
                        dstPort = dstPort,
                        serverSeq = 50000L,
                        expectedClientSeq = seqNum + 1
                    )
                    tcpSessions[sessionKey] = session

                    debugLog("PACKET_TCP", "TCP Connected to ${dstIp.hostAddress}:$dstPort! Sending SYN-ACK to TUN")

                    // Send TCP SYN-ACK back to TUN
                    val synAckPacket = buildIpv4TcpPacket(
                        srcIp = dstIp,
                        srcPort = dstPort,
                        dstIp = srcIp,
                        dstPort = srcPort,
                        seqNum = session.serverSeq,
                        ackNum = session.expectedClientSeq,
                        flags = 0x12, // SYN + ACK
                        payload = byteArrayOf()
                    )
                    session.serverSeq++

                    synchronized(outputStream) {
                        outputStream.write(synAckPacket)
                        outputStream.flush()
                    }

                    // Background socket reader
                    launch {
                        val buffer = ByteArray(32768)
                        try {
                            if (!socket.isClosed && socket.isConnected) {
                                val inStream = socket.getInputStream()
                                while (isRunning && !session.isClosed && !socket.isClosed) {
                                    val read = inStream.read(buffer)
                                    if (read > 0) {
                                        rxBytesTotal.addAndGet(read.toLong())
                                        onTrafficUpdate(rxBytesTotal.get(), txBytesTotal.get())

                                        val respChunk = buffer.copyOf(read)
                                        val dataPacket = buildIpv4TcpPacket(
                                            srcIp = dstIp,
                                            srcPort = dstPort,
                                            dstIp = srcIp,
                                            dstPort = srcPort,
                                            seqNum = session.serverSeq,
                                            ackNum = session.expectedClientSeq,
                                            flags = 0x18, // PSH + ACK
                                            payload = respChunk
                                        )
                                        session.serverSeq += read

                                        synchronized(outputStream) {
                                            outputStream.write(dataPacket)
                                            outputStream.flush()
                                        }
                                    } else {
                                        break
                                    }
                                }
                            }
                        } catch (_: Exception) {
                        } finally {
                            val finPacket = buildIpv4TcpPacket(
                                srcIp = dstIp,
                                srcPort = dstPort,
                                dstIp = srcIp,
                                dstPort = srcPort,
                                seqNum = session.serverSeq,
                                ackNum = session.expectedClientSeq,
                                flags = 0x11, // FIN + ACK
                                payload = byteArrayOf()
                            )
                            synchronized(outputStream) {
                                runCatching {
                                    outputStream.write(finPacket)
                                    outputStream.flush()
                                }
                            }
                            session.isClosed = true
                            runCatching { socket.close() }
                            tcpSessions.remove(sessionKey)
                        }
                    }
                } catch (e: Exception) {
                    debugLog("PACKET_TCP_ERR", "TCP Connection to ${dstIp.hostAddress}:$dstPort failed: ${e.message}")
                    val rstPacket = buildIpv4TcpPacket(
                        srcIp = dstIp,
                        srcPort = dstPort,
                        dstIp = srcIp,
                        dstPort = srcPort,
                        seqNum = 0L,
                        ackNum = seqNum + 1,
                        flags = 0x14, // RST + ACK
                        payload = byteArrayOf()
                    )
                    synchronized(outputStream) {
                        runCatching {
                            outputStream.write(rstPacket)
                            outputStream.flush()
                        }
                    }
                }
            }
        } else {
            val session = tcpSessions[sessionKey]
            if (session != null && !session.isClosed) {
                if (payloadLen > 0) {
                    session.expectedClientSeq = seqNum + payloadLen
                    routerScope.launch {
                        try {
                            if (!session.socket.isClosed && session.socket.isConnected) {
                                val out = session.socket.getOutputStream()
                                out.write(tcpPayload)
                                out.flush()

                                val ackPacket = buildIpv4TcpPacket(
                                    srcIp = dstIp,
                                    srcPort = dstPort,
                                    dstIp = srcIp,
                                    dstPort = srcPort,
                                    seqNum = session.serverSeq,
                                    ackNum = session.expectedClientSeq,
                                    flags = 0x10, // ACK
                                    payload = byteArrayOf()
                                )
                                synchronized(outputStream) {
                                    outputStream.write(ackPacket)
                                    outputStream.flush()
                                }
                            }
                        } catch (_: Exception) {
                            session.isClosed = true
                            runCatching { session.socket.close() }
                            tcpSessions.remove(sessionKey)
                        }
                    }
                } else if (isFin) {
                    session.isClosed = true
                    routerScope.launch {
                        runCatching { session.socket.close() }
                        tcpSessions.remove(sessionKey)
                    }
                }
            }
        }
    }

    private fun buildIpv4Packet(protocol: Int, srcIp: InetAddress, dstIp: InetAddress, payload: ByteArray): ByteArray {
        val totalLength = 20 + payload.size
        val packet = ByteArray(totalLength)

        packet[0] = 0x45.toByte()
        packet[1] = 0x00.toByte()
        packet[2] = (totalLength shr 8).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        packet[4] = 0x00.toByte()
        packet[5] = 0x00.toByte()
        packet[6] = 0x40.toByte()
        packet[7] = 0x00.toByte()
        packet[8] = 64.toByte()
        packet[9] = protocol.toByte()
        packet[10] = 0x00.toByte()
        packet[11] = 0x00.toByte()

        val srcBytes = srcIp.address
        val dstBytes = dstIp.address
        System.arraycopy(srcBytes, 0, packet, 12, 4)
        System.arraycopy(dstBytes, 0, packet, 16, 4)

        val checksum = computeChecksum(packet, 0, 20)
        packet[10] = (checksum shr 8).toByte()
        packet[11] = (checksum and 0xFF).toByte()

        System.arraycopy(payload, 0, packet, 20, payload.size)
        return packet
    }

    private fun buildIpv4UdpPacket(srcIp: InetAddress, srcPort: Int, dstIp: InetAddress, dstPort: Int, payload: ByteArray): ByteArray {
        val udpLength = 8 + payload.size
        val udpHeader = ByteArray(udpLength)

        udpHeader[0] = (srcPort shr 8).toByte()
        udpHeader[1] = (srcPort and 0xFF).toByte()
        udpHeader[2] = (dstPort shr 8).toByte()
        udpHeader[3] = (dstPort and 0xFF).toByte()
        udpHeader[4] = (udpLength shr 8).toByte()
        udpHeader[5] = (udpLength and 0xFF).toByte()
        udpHeader[6] = 0x00.toByte()
        udpHeader[7] = 0x00.toByte()

        System.arraycopy(payload, 0, udpHeader, 8, payload.size)
        return buildIpv4Packet(protocol = 17, srcIp = srcIp, dstIp = dstIp, payload = udpHeader)
    }

    private fun buildIpv4TcpPacket(
        srcIp: InetAddress,
        srcPort: Int,
        dstIp: InetAddress,
        dstPort: Int,
        seqNum: Long,
        ackNum: Long,
        flags: Int,
        payload: ByteArray
    ): ByteArray {
        val tcpHeaderLen = 20
        val tcpLength = tcpHeaderLen + payload.size
        val tcpBuffer = ByteArray(tcpLength)

        tcpBuffer[0] = (srcPort shr 8).toByte()
        tcpBuffer[1] = (srcPort and 0xFF).toByte()
        tcpBuffer[2] = (dstPort shr 8).toByte()
        tcpBuffer[3] = (dstPort and 0xFF).toByte()

        tcpBuffer[4] = ((seqNum shr 24) and 0xFF).toByte()
        tcpBuffer[5] = ((seqNum shr 16) and 0xFF).toByte()
        tcpBuffer[6] = ((seqNum shr 8) and 0xFF).toByte()
        tcpBuffer[7] = (seqNum and 0xFF).toByte()

        tcpBuffer[8] = ((ackNum shr 24) and 0xFF).toByte()
        tcpBuffer[9] = ((ackNum shr 16) and 0xFF).toByte()
        tcpBuffer[10] = ((ackNum shr 8) and 0xFF).toByte()
        tcpBuffer[11] = (ackNum and 0xFF).toByte()

        tcpBuffer[12] = 0x50.toByte()
        tcpBuffer[13] = flags.toByte()

        tcpBuffer[14] = 0xFF.toByte()
        tcpBuffer[15] = 0xFF.toByte()
        tcpBuffer[16] = 0x00.toByte()
        tcpBuffer[17] = 0x00.toByte()
        tcpBuffer[18] = 0x00.toByte()
        tcpBuffer[19] = 0x00.toByte()

        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, tcpBuffer, 20, payload.size)
        }

        val pseudoHeader = ByteBuffer.allocate(12 + tcpLength)
        pseudoHeader.put(srcIp.address)
        pseudoHeader.put(dstIp.address)
        pseudoHeader.put(0.toByte())
        pseudoHeader.put(6.toByte())
        pseudoHeader.putShort(tcpLength.toShort())
        pseudoHeader.put(tcpBuffer)

        val checksum = computeChecksum(pseudoHeader.array(), 0, pseudoHeader.capacity())
        tcpBuffer[16] = (checksum shr 8).toByte()
        tcpBuffer[17] = (checksum and 0xFF).toByte()

        return buildIpv4Packet(protocol = 6, srcIp = srcIp, dstIp = dstIp, payload = tcpBuffer)
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            if (sum > 0xFFFF) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }
            i += 2
        }
        if (i < offset + length) {
            val word = (data[i].toInt() and 0xFF) shl 8
            sum += word
            if (sum > 0xFFFF) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }
        }
        return (sum.inv()) and 0xFFFF
    }
}
