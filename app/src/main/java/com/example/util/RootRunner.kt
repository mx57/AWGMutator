package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

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
     */
    suspend fun execute(vararg commands: String): RootCommandResult = withContext(Dispatchers.IO) {
        var process: Process? = null
        var os: DataOutputStream? = null
        var reader: BufferedReader? = null
        var errorReader: BufferedReader? = null

        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            reader = BufferedReader(InputStreamReader(process.inputStream))
            errorReader = BufferedReader(InputStreamReader(process.errorStream))

            for (cmd in commands) {
                os.writeBytes("$cmd\n")
                os.flush()
            }

            os.writeBytes("exit\n")
            os.flush()

            val exitCode = process.waitFor()
            val stdout = reader.readText().trim()
            val stderr = errorReader.readText().trim()

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
            try { os?.close() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            try { errorReader?.close() } catch (_: Exception) {}
            try { process?.destroy() } catch (_: Exception) {}
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
