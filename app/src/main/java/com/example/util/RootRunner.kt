package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class RootCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val isSuccess: Boolean = exitCode == 0
)

/**
 * High-performance, robust Root (su) command execution engine.
 * Supports checking root status, kernel module verification, iptables manipulation,
 * and interface lifecycle management.
 */
object RootRunner {

    /**
     * Safely escapes a command-line argument for POSIX shell execution by single-quoting it
     * and escaping any single-quote characters contained inside.
     */
    fun escapeArg(arg: String): String {
        val sanitized = arg.replace("\u0000", "")
        return "'" + sanitized.replace("'", "'\\''") + "'"
    }

    /**
     * Checks if device has accessible SuperUser (su) binary and grants root permissions.
     */
    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = execute("id")
            result.isSuccess && (result.stdout.contains("uid=0(root)") || result.stdout.contains("root"))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes a batch of commands inside a single `su` shell session.
     * Drains stdout and stderr asynchronously via coroutines to prevent OS process pipe deadlocks
     * and minimize execution latency.
     */
    suspend fun execute(vararg commands: String): RootCommandResult =
        executeShell("su", *commands)

    /**
     * Executes commands using a specified shell binary (`su`, `sh`, etc.).
     */
    suspend fun executeShell(shellCommand: String = "su", vararg commands: String): RootCommandResult = coroutineScope {
        withContext(Dispatchers.IO) {
            var process: Process? = null
            try {
                val proc = ProcessBuilder(shellCommand).start()
                process = proc

                // Drain stdout and stderr concurrently to prevent process buffer deadlocks
                val stdoutDeferred = async(Dispatchers.IO) {
                    proc.inputStream.bufferedReader().use { it.readText() }
                }
                val stderrDeferred = async(Dispatchers.IO) {
                    proc.errorStream.bufferedReader().use { it.readText() }
                }

                // Write commands to stdin after stripping dangerous control characters/newlines
                proc.outputStream.bufferedWriter().use { writer ->
                    for (cmd in commands) {
                        val sanitizedCmd = cmd.replace("\u0000", "").replace("\r", "").replace("\n", " ")
                        writer.write(sanitizedCmd)
                        writer.newLine()
                    }
                    writer.write("exit")
                    writer.newLine()
                    writer.flush()
                }

                val exitCode = proc.waitFor()
                val stdout = stdoutDeferred.await().trim()
                val stderr = stderrDeferred.await().trim()

                RootCommandResult(
                    exitCode = exitCode,
                    stdout = stdout,
                    stderr = stderr
                )
            } catch (e: Exception) {
                RootCommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = e.localizedMessage ?: "Failed to execute root shell"
                )
            } finally {
                try {
                    process?.destroy()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Checks if kernel WireGuard or AmneziaWG module is loaded or supported.
     */
    suspend fun checkKernelWgSupport(): Boolean = withContext(Dispatchers.IO) {
        val res = execute("cat /proc/modules | grep -E 'wireguard|amneziawg'", "which wg || which awg")
        res.isSuccess && (res.stdout.contains("wireguard") || res.stdout.contains("amneziawg") || res.stdout.contains("/bin/"))
    }
}
