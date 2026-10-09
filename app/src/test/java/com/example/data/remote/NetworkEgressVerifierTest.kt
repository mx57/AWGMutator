package com.example.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
class NetworkEgressVerifierTest {

    @Test
    fun testCheckDnsResolution_withLocalUdpServer_succeedsFast() = runBlocking {
        val serverSocket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val serverPort = serverSocket.localPort

        val verifier = NetworkEgressVerifier()

        // Start local mock DNS responder thread
        val responderThread = Thread {
            try {
                val buf = ByteArray(512)
                val pkt = DatagramPacket(buf, buf.size)
                serverSocket.soTimeout = 3000
                serverSocket.receive(pkt)
                // Craft valid DNS response (> 12 bytes)
                val resp = ByteArray(32)
                System.arraycopy(pkt.data, 0, resp, 0, 12) // copy header
                resp[2] = 0x81.toByte() // Response flag
                resp[3] = 0x80.toByte()
                val respPkt = DatagramPacket(resp, resp.size, pkt.address, pkt.port)
                serverSocket.send(respPkt)
            } catch (_: Exception) {
            } finally {
                serverSocket.close()
            }
        }
        responderThread.start()

        val timeMs = measureTimeMillis {
            val result = verifier.checkDnsResolution(listOf("127.0.0.1:$serverPort", "192.0.2.1", "192.0.2.2"))
            assertTrue("DNS probe should succeed when mock DNS server responds", result)
        }

        assertTrue("Probe should complete quickly ($timeMs ms) when first server responds", timeMs < 1000)
    }

    @Test
    fun testCheckDnsResolution_parallelSpeedup_whenFirstServerTimesOut() = runBlocking {
        val serverSocket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val serverPort = serverSocket.localPort

        val verifier = NetworkEgressVerifier()

        val responderThread = Thread {
            try {
                val buf = ByteArray(512)
                val pkt = DatagramPacket(buf, buf.size)
                serverSocket.soTimeout = 3000
                serverSocket.receive(pkt)
                val resp = ByteArray(32)
                System.arraycopy(pkt.data, 0, resp, 0, 12)
                resp[2] = 0x81.toByte()
                resp[3] = 0x80.toByte()
                val respPkt = DatagramPacket(resp, resp.size, pkt.address, pkt.port)
                serverSocket.send(respPkt)
            } catch (_: Exception) {
            } finally {
                serverSocket.close()
            }
        }
        responderThread.start()

        val timeMs = measureTimeMillis {
            // "192.0.2.1" comes FIRST in list.
            // In old sequential code, 192.0.2.1 blocks for 2000ms timeout before trying 127.0.0.1.
            // In new parallel code, 127.0.0.1 responds immediately (< 500ms) and cancels 192.0.2.1!
            val result = verifier.checkDnsResolution(listOf("192.0.2.1", "127.0.0.1:$serverPort", "192.0.2.2"))
            assertTrue("DNS probe should succeed", result)
        }

        assertTrue("Parallel DNS probe should complete in under 1000ms, actual: ${timeMs}ms (Sequential baseline would take ~2000ms)", timeMs < 1000)
    }

    @Test
    fun testCheckDnsResolution_whenAllFail_returnsFalse() = runBlocking {
        val verifier = NetworkEgressVerifier()
        // Unreachable IPs in TEST-NET-1 block
        val result = verifier.checkDnsResolution(listOf("192.0.2.1", "192.0.2.2"))
        assertFalse("DNS probe should return false when all servers fail", result)
    }
}
