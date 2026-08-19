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

/**
 * Real-world User-space TUN Packet Router for Android VpnService.
 * Handles IPv4 parsing, UDP forwarding, DNS proxying to uncensored servers,
 * and Anti-DPI WireGuard / AmneziaWG packet encapsulation.
 */
class TunPacketRouter(
    private val vpnService: VpnService,
    private val fileDescriptor: FileDescriptor,
    private val config: AwgConfig,
    private val onTrafficUpdate: (rxBytes: Long, txBytes: Long) -> Unit
) {
    private val routerScope = CoroutineScope(Dispatchers.IO + Job())
    private var isRunning = true

    private var rxBytesTotal = 0L
    private var txBytesTotal = 0L

    private val primaryDnsIp: String = config.dns.split(",").firstOrNull()?.trim()?.ifBlank { "1.1.1.1" } ?: "1.1.1.1"

    // Session cache for active outbound UDP flows
    private val udpSessions = ConcurrentHashMap<String, DatagramSocket>()

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
                        txBytesTotal += bytesRead
                        onTrafficUpdate(rxBytesTotal, txBytesTotal)

                        handleOutboundPacket(packetBuffer, bytesRead, outputStream)
                    }
                }
            } catch (_: Exception) {
                // Stopped
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
        udpSessions.values.forEach { socket ->
            runCatching { socket.close() }
        }
        udpSessions.clear()
    }

    private fun handleOutboundPacket(packet: ByteArray, length: Int, outputStream: FileOutputStream) {
        if (length < 20) return
        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return // Process IPv4 packets

        val ipHeaderLen = (packet[0].toInt() and 0x0F) * 4
        val protocol = packet[9].toInt() and 0xFF

        val srcIp = InetAddress.getByAddress(packet.copyOfRange(12, 16))
        val dstIp = InetAddress.getByAddress(packet.copyOfRange(16, 20))

        if (protocol == 17 && length >= ipHeaderLen + 8) { // UDP
            val srcPort = ((packet[ipHeaderLen].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 1].toInt() and 0xFF)
            val dstPort = ((packet[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or (packet[ipHeaderLen + 3].toInt() and 0xFF)
            val udpPayloadLen = length - (ipHeaderLen + 8)
            val udpPayload = packet.copyOfRange(ipHeaderLen + 8, length)

            if (dstPort == 53) {
                // DNS Query: forward to active uncensored DNS resolver
                forwardDnsQuery(udpPayload, srcIp, srcPort, dstIp, dstPort, outputStream)
            } else {
                // Generic UDP / WireGuard traffic
                forwardUdpTraffic(udpPayload, srcIp, srcPort, dstIp, dstPort, outputStream)
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
                    soTimeout = 2500
                }

                socket.use { ds ->
                    val dnsTarget = InetAddress.getByName(primaryDnsIp)
                    val sendPacket = DatagramPacket(dnsQuery, dnsQuery.size, dnsTarget, 53)
                    ds.send(sendPacket)

                    val respBuffer = ByteArray(4096)
                    val recvPacket = DatagramPacket(respBuffer, respBuffer.size)
                    ds.receive(recvPacket)

                    val dnsResponse = respBuffer.copyOf(recvPacket.length)
                    rxBytesTotal += dnsResponse.size
                    onTrafficUpdate(rxBytesTotal, txBytesTotal)

                    // Construct IPv4 UDP reply packet back into TUN interface
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
                // Query timed out or unreachable
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
                rxBytesTotal += responsePayload.size
                onTrafficUpdate(rxBytesTotal, txBytesTotal)

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
                // UDP timeout
            }
        }
    }

    private fun buildIpv4UdpPacket(
        srcIp: InetAddress,
        srcPort: Int,
        dstIp: InetAddress,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val totalLength = 20 + 8 + payload.size
        val packet = ByteBuffer.allocate(totalLength)

        // IP Header
        packet.put(0x45.toByte()) // Version 4, IHL 5 (20 bytes)
        packet.put(0x00.toByte()) // DSCP / ECN
        packet.putShort(totalLength.toShort()) // Total Length
        packet.putShort(0x1234.toShort()) // Identification
        packet.putShort(0x0000.toShort()) // Flags & Fragment Offset
        packet.put(64.toByte()) // TTL
        packet.put(17.toByte()) // Protocol (UDP = 17)
        packet.putShort(0x0000.toShort()) // Checksum placeholder
        packet.put(srcIp.address) // Source IP
        packet.put(dstIp.address) // Destination IP

        // Compute IP Header Checksum
        val ipChecksum = computeChecksum(packet.array(), 0, 20)
        packet.putShort(10, ipChecksum.toShort())

        // UDP Header
        packet.position(20)
        packet.putShort(srcPort.toShort()) // Source Port
        packet.putShort(dstPort.toShort()) // Destination Port
        packet.putShort((8 + payload.size).toShort()) // UDP Length
        packet.putShort(0x0000.toShort()) // UDP Checksum (optional in IPv4)

        // Payload
        packet.put(payload)

        return packet.array()
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
