package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PingTesterTest {

    private val pingTester = PingTester()

    @Test
    fun `isValidHost accepts valid IPv4 addresses`() {
        assertTrue(pingTester.isValidHost("127.0.0.1"))
        assertTrue(pingTester.isValidHost("8.8.8.8"))
        assertTrue(pingTester.isValidHost("192.168.1.1"))
        assertTrue(pingTester.isValidHost("188.114.97.1"))
    }

    @Test
    fun `isValidHost accepts valid IPv6 addresses`() {
        assertTrue(pingTester.isValidHost("2001:db8::1"))
        assertTrue(pingTester.isValidHost("::1"))
        assertTrue(pingTester.isValidHost("2606:4700:4700::1111"))
    }

    @Test
    fun `isValidHost accepts valid hostnames`() {
        assertTrue(pingTester.isValidHost("example.com"))
        assertTrue(pingTester.isValidHost("sub.domain.example.co.uk"))
        assertTrue(pingTester.isValidHost("localhost"))
        assertTrue(pingTester.isValidHost("cloudflare-dns.com"))
    }

    @Test
    fun `isValidHost rejects malicious argument injections and illegal inputs`() {
        assertFalse(pingTester.isValidHost("-c"))
        assertFalse(pingTester.isValidHost("-M"))
        assertFalse(pingTester.isValidHost("--help"))
        assertFalse(pingTester.isValidHost("-c 1 127.0.0.1"))
        assertFalse(pingTester.isValidHost("127.0.0.1; cat /etc/passwd"))
        assertFalse(pingTester.isValidHost("127.0.0.1 | id"))
        assertFalse(pingTester.isValidHost("$(whoami)"))
        assertFalse(pingTester.isValidHost("`id`"))
        assertFalse(pingTester.isValidHost("127.0.0.1\n-c 1"))
        assertFalse(pingTester.isValidHost("    "))
        assertFalse(pingTester.isValidHost(""))
    }

    @Test
    fun `parseEndpointHostAndPort extracts host and port correctly`() {
        val (host1, port1) = pingTester.parseEndpointHostAndPort("188.114.97.1:854")
        assertEquals("188.114.97.1", host1)
        assertEquals(854, port1)

        val (host2, port2) = pingTester.parseEndpointHostAndPort("[2606:4700:4700::1111]:2408")
        assertEquals("2606:4700:4700::1111", host2)
        assertEquals(2408, port2)

        val (host3, port3) = pingTester.parseEndpointHostAndPort("example.com")
        assertEquals("example.com", host3)
        assertEquals(854, port3)

        val (host4, port4) = pingTester.parseEndpointHostAndPort("2001:db8::1")
        assertEquals("2001:db8::1", host4)
        assertEquals(854, port4)
    }
}
