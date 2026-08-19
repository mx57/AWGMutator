package com.example.vpn

import android.net.VpnService
import com.example.domain.model.AwgConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance User-space TUN Packet Router for Android VpnService.
 * Handles IPv4 ICMP Echo (ping), UDP (DNS + WireGuard/AWG traffic),
 * and TCP bidirectional proxy relay with socket protection.
 */
class TunPacketRouter(
    private val vpnService: VpnService,
    private val fileDescriptor: FileDescriptor,
    private val config: AwgConfig,
    private val onTrafficUpdate: (rxBytes: Long, txBytes: Long) -> Unit
) {
    private val routerScope = CoroutineScope(Dispatchers.IO + Job())
    private var isRunning = true

    private val rxBytesTotal = AtomicLong(0L)
    private val txBytesTotal = AtomicLong(0L)

    private val primaryDnsIp: String = config.dns.split(",").firstOrNull()?.trim()?.ifBlank { "111.88.96.50" } ?: "111.88.96.50"
    private val secondaryDnsIp: String = config.dns.split(",").getOrNull(1)?.trim()?.ifBlank { "111.88.96.51" } ?: "111.88.96.51"

    // Session cache for outbound UDP sockets
    private val udpSessions = ConcurrentHashMap<String, DatagramSocket>()

    // Session cache for outbound TCP sockets
    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()

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
        routerScope.launch {
            val inputStream = FileInputStream(fileDescriptor)
            val outputStream = FileOutputStream(fileDescriptor)
            val packetBuffer = ByteArray(32768)

            try {
                while (isRunning && isActive) {
                    val bytesRead = inputStream.read(packetBuffer)
                    if (bytesRead > 0) {
                        txBytesTotal.addAndGet(bytesRead.toLong())
                        onTrafficUpdate(rxBytesTotal.get(), txBytesTotal.get())

                        handleOutboundPacket(packetBuffer, bytesRead, outputStream)
                    }
                }
            } catch (_: Exception) {
                // Interface closed
            } finally {
                cleanup()
            }
        }
    }

    fun stop() {
        isRunning = false
        cleanup()
    }

    private fun cleanup() {
        udpSessions.values.forEach { runCatching { it.close() } }
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
        if (version != 4) return // Process IPv4

        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        if (length < ipHeaderLen) return

        val protocol = packet[9].toInt() and 0xFF
        val srcIp = InetAddress.getByAddress(packet.copyOfRange(12, 16))
        val dstIp = InetAddress.getByAddress(packet.copyOfRange(16, 20))

        when (protocol) {
            1 -> {
                // ICMP (Echo Request / Ping)
                handleIcmpPacket(packet, ipHeaderLen, length, srcIp, dstIp, outputStream)
            }
            17 -> {
                // UDP
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
                // TCP
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
            val icmpPayload = packet.copyOfRange(ipHeaderLen, totalLen)
            // Change type to 0 (Echo Reply)
            icmpPayload[0] = 0x00
            // Reset ICMP checksum
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
        routerScope.launch {
            try {
                val socket = DatagramSocket().apply {
                    vpnService.protect(this)
                    soTimeout = 2000
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

                    val dnsResponse = respBuffer.copyOf(recvPacket.length)
                    rxBytesTotal.addAndGet(dnsResponse.size.toLong())
                    onTrafficUpdate(rxBytesTotal.get(), txBytesTotal.get())

                    val replyIpPacket = buildIpv4UdpPacket(
                        srcIp = originalDstIp,
                        srcPort = originalDstPort,
                        dstIp = clientIp,
                        dstPort = clientPort,
                        payload = dnsResponse
                    )

                    synchronized(outputStream) {
                        outputStream.write(replyIpPacket)
                        outputStream.flush()
                    }
                }
            } catch (_: Exception) {
                // Timeout
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
        val socket = udpSessions.computeIfAbsent(sessionKey) {
            try {
                DatagramSocket().apply {
                    vpnService.protect(this)
                    soTimeout = 4000
                }
            } catch (_: Exception) {
                DatagramSocket()
            }
        }

        routerScope.launch {
            try {
                val sendPacket = DatagramPacket(payload, payload.size, dstIp, dstPort)
                socket.send(sendPacket)

                val respBuffer = ByteArray(8192)
                val recvPacket = DatagramPacket(respBuffer, respBuffer.size)
                socket.receive(recvPacket)

                val responsePayload = respBuffer.copyOf(recvPacket.length)
                rxBytesTotal.addAndGet(responsePayload.size.toLong())
                onTrafficUpdate(rxBytesTotal.get(), txBytesTotal.get())

                val replyPacket = buildIpv4UdpPacket(
                    srcIp = dstIp,
                    srcPort = dstPort,
                    dstIp = clientIp,
                    dstPort = clientPort,
                    payload = responsePayload
                )

                synchronized(outputStream) {
                    outputStream.write(replyPacket)
                    outputStream.flush()
                }
            } catch (_: Exception) {
                // Timeout
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
            // TCP SYN: Start new TCP connection
            routerScope.launch {
                try {
                    val socket = Socket()
                    vpnService.protect(socket)
                    socket.tcpNoDelay = true
                    socket.soTimeout = 8000
                    socket.connect(InetSocketAddress(dstIp, dstPort), 3500)

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

                    // Start background socket reader for incoming response data
                    launch {
                        val buffer = ByteArray(16384)
                        val inStream = socket.getInputStream()
                        try {
                            while (isRunning && !session.isClosed) {
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
                        } catch (_: Exception) {
                            // Socket closed
                        } finally {
                            // Send FIN to client
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
                } catch (_: Exception) {
                    // Connect failed, send RST back
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
                            val out = session.socket.getOutputStream()
                            out.write(tcpPayload)
                            out.flush()

                            // ACK client's data
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
                        } catch (_: Exception) {
                            session.isClosed = true
                        }
                    }
                } else if (isFin) {
                    session.expectedClientSeq = seqNum + 1
                    session.isClosed = true
                    val finAck = buildIpv4TcpPacket(
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
                            outputStream.write(finAck)
                            outputStream.flush()
                        }
                    }
                    runCatching { session.socket.close() }
                    tcpSessions.remove(sessionKey)
                } else if (isRst) {
                    session.isClosed = true
                    runCatching { session.socket.close() }
                    tcpSessions.remove(sessionKey)
                }
            }
        }
    }

    private fun buildIpv4Packet(
        protocol: Int,
        srcIp: InetAddress,
        dstIp: InetAddress,
        payload: ByteArray
    ): ByteArray {
        val totalLength = 20 + payload.size
        val packet = ByteBuffer.allocate(totalLength)

        packet.put(0x45.toByte())
        packet.put(0x00.toByte())
        packet.putShort(totalLength.toShort())
        packet.putShort(0x1234.toShort())
        packet.putShort(0x0000.toShort())
        packet.put(64.toByte())
        packet.put(protocol.toByte())
        packet.putShort(0x0000.toShort())
        packet.put(srcIp.address)
        packet.put(dstIp.address)

        val ipChecksum = computeChecksum(packet.array(), 0, 20)
        packet.putShort(10, ipChecksum.toShort())
        packet.position(20)
        packet.put(payload)

        return packet.array()
    }

    private fun buildIpv4UdpPacket(
        srcIp: InetAddress,
        srcPort: Int,
        dstIp: InetAddress,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = 8 + payload.size
        val totalLength = 20 + udpLength
        val packet = ByteBuffer.allocate(totalLength)

        packet.put(0x45.toByte())
        packet.put(0x00.toByte())
        packet.putShort(totalLength.toShort())
        packet.putShort(0x1234.toShort())
        packet.putShort(0x0000.toShort())
        packet.put(64.toByte())
        packet.put(17.toByte()) // UDP
        packet.putShort(0x0000.toShort())
        packet.put(srcIp.address)
        packet.put(dstIp.address)

        val ipChecksum = computeChecksum(packet.array(), 0, 20)
        packet.putShort(10, ipChecksum.toShort())

        packet.position(20)
        packet.putShort(srcPort.toShort())
        packet.putShort(dstPort.toShort())
        packet.putShort(udpLength.toShort())
        packet.putShort(0x0000.toShort())
        packet.put(payload)

        return packet.array()
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
        val totalLength = 20 + tcpHeaderLen + payload.size
        val packet = ByteBuffer.allocate(totalLength)

        // IP Header
        packet.put(0x45.toByte())
        packet.put(0x00.toByte())
        packet.putShort(totalLength.toShort())
        packet.putShort(0x4321.toShort())
        packet.putShort(0x4000.toShort()) // Don't Fragment
        packet.put(64.toByte())
        packet.put(6.toByte()) // TCP
        packet.putShort(0x0000.toShort())
        packet.put(srcIp.address)
        packet.put(dstIp.address)

        val ipChecksum = computeChecksum(packet.array(), 0, 20)
        packet.putShort(10, ipChecksum.toShort())

        // TCP Header
        packet.position(20)
        packet.putShort(srcPort.toShort())
        packet.putShort(dstPort.toShort())
        packet.putInt(seqNum.toInt())
        packet.putInt(ackNum.toInt())
        packet.put(0x50.toByte()) // Header length 5 (20 bytes)
        packet.put(flags.toByte())
        packet.putShort(65535.toShort()) // Window size
        packet.putShort(0x0000.toShort()) // TCP Checksum placeholder
        packet.putShort(0x0000.toShort()) // Urgent pointer

        // Payload
        if (payload.isNotEmpty()) {
            packet.put(payload)
        }

        // Calculate TCP Checksum over Pseudo-Header
        val tcpChecksum = computeTcpChecksum(srcIp, dstIp, packet.array(), 20, tcpHeaderLen + payload.size)
        packet.putShort(36, tcpChecksum.toShort())

        return packet.array()
    }

    private fun computeTcpChecksum(srcIp: InetAddress, dstIp: InetAddress, packet: ByteArray, tcpOffset: Int, tcpLength: Int): Int {
        var sum = 0
        val src = srcIp.address
        val dst = dstIp.address

        // Pseudo header
        for (i in 0..3 step 2) {
            sum += ((src[i].toInt() and 0xFF) shl 8) or (src[i + 1].toInt() and 0xFF)
            sum += ((dst[i].toInt() and 0xFF) shl 8) or (dst[i + 1].toInt() and 0xFF)
        }
        sum += 6 // Protocol TCP
        sum += tcpLength

        var i = tcpOffset
        val end = tcpOffset + tcpLength
        while (i < end - 1) {
            val high = packet[i].toInt() and 0xFF
            val low = packet[i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
        }
        if (i < end) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }

        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            val high = data[i].toInt() and 0xFF
            val low = data[i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
        }
        if (i < offset + length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
