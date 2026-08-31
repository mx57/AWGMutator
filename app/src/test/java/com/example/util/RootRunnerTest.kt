package com.example.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootRunnerTest {

    @Test
    fun testExecuteShellSuccess() = runBlocking {
        val result = RootRunner.executeShell("sh", "echo hello", "echo world")
        assertTrue("Execution should succeed", result.isSuccess)
        assertEquals(0, result.exitCode)
        assertEquals("hello\nworld", result.stdout)
        assertEquals("", result.stderr)
    }

    @Test
    fun testExecuteShellStderr() = runBlocking {
        val result = RootRunner.executeShell("sh", "echo error_out >&2")
        assertTrue(result.isSuccess)
        assertEquals("error_out", result.stderr)
    }

    @Test
    fun testExecuteShellNonExistentBinary() = runBlocking {
        val result = RootRunner.executeShell("non_existent_binary_xyz_123", "echo test")
        assertFalse(result.isSuccess)
        assertEquals(-1, result.exitCode)
        assertTrue(result.stderr.isNotEmpty())
    }

    @Test
    fun testEscapeArg() {
        assertEquals("'simple'", RootRunner.escapeArg("simple"))
        assertEquals("'hello world'", RootRunner.escapeArg("hello world"))
        assertEquals("'it'\\''s fine'", RootRunner.escapeArg("it's fine"))
        assertEquals("'; id;'", RootRunner.escapeArg("; id;"))
        assertEquals("'\$(whoami)'", RootRunner.escapeArg("$(whoami)"))
        assertEquals("'`id`'", RootRunner.escapeArg("`id`"))
    }

    @Test
    fun testNewlineSanitizationPreventsInjection() = runBlocking {
        // Attempting to inject extra commands via multiline string input
        val maliciousArg = "1.1.1.1\necho INJECTED"
        val escaped = RootRunner.escapeArg(maliciousArg)
        val result = RootRunner.executeShell("sh", "echo $escaped")

        assertTrue(result.isSuccess)
        // Verify newline was converted to space and command injection did not execute as a separate command
        assertEquals("1.1.1.1 echo INJECTED", result.stdout)
    }

    @Test
    fun testExecuteShellLargeOutputPerformance() = runBlocking {
        val startTime = System.currentTimeMillis()
        // Generate significant output to verify non-blocking stream handling
        val commands = Array(100) { i -> "echo 'Line $i'" }
        val result = RootRunner.executeShell("sh", *commands)
        val duration = System.currentTimeMillis() - startTime

        assertTrue(result.isSuccess)
        assertTrue("Output should contain all lines", result.stdout.contains("Line 99"))
        assertTrue("Execution of 100 commands should be under 2000ms, took ${duration}ms", duration < 2000)
    }
}
