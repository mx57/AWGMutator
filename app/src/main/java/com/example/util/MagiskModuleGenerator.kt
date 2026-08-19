package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.domain.model.AwgConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object MagiskModuleGenerator {

    /**
     * Generates a fully compliant, flashable Magisk / KernelSU / APatch module ZIP file.
     * The module starts a kernel-level AmneziaWG/WireGuard tunnel at boot with policy routing.
     */
    suspend fun generateModuleZip(context: Context, config: AwgConfig): Result<File> = withContext(Dispatchers.IO) {
        try {
            val outputDir = File(context.cacheDir, "magisk_modules").apply { mkdirs() }
            val cleanName = config.name.replace(Regex("[^a-zA-Z0-9_]"), "_").lowercase()
            val zipFile = File(outputDir, "AWGMutator_${cleanName}_Magisk_Module.zip")
            if (zipFile.exists()) zipFile.delete()

            val primaryDns = config.dns.split(",").firstOrNull()?.trim() ?: "111.88.96.50"
            val cleanAddr = if (config.address.contains("/")) config.address else "${config.address}/32"

            val moduleProp = """
                id=awgmutator_tunnel
                name=AWGMutator Root Tunnel (${config.name})
                version=v2.0
                versionCode=200
                author=AWGMutator Engine
                description=Automated AmneziaWG/WireGuard kernel tunnel & Anti-DPI bypass for ${config.name}. Bypasses provider censorship without Android VpnService.
            """.trimIndent()

            val serviceSh = """
                #!/system/bin/sh
                # AWGMutator Magisk Boot Script
                MODDIR="${'$'}{0%/*}"
                
                # Wait until network is ready
                until [ "$(getprop sys.boot_completed)" = "1" ]; do
                    sleep 3
                done
                sleep 5
                
                IFNAME="awg0"
                CONF="${'$'}MODDIR/awg.conf"
                TABLE="51820"
                FWMARK="0x51820"
                
                # Clean old state
                ip link delete dev ${'$'}IFNAME 2>/dev/null || true
                ip rule del table ${'$'}TABLE 2>/dev/null || true
                
                # Create interface
                ip link add dev ${'$'}IFNAME type wireguard || ip link add dev ${'$'}IFNAME type amneziawg || true
                ip address add $cleanAddr dev ${'$'}IFNAME
                ip link set mtu ${config.mtu} dev ${'$'}IFNAME
                
                # Apply configuration
                wg setconf ${'$'}IFNAME ${'$'}CONF || awg setconf ${'$'}IFNAME ${'$'}CONF || true
                ip link set up dev ${'$'}IFNAME
                
                # Setup Routing & IPTables
                ip rule add not fwmark ${'$'}FWMARK table ${'$'}TABLE priority 1000
                ip route add default dev ${'$'}IFNAME table ${'$'}TABLE
                
                iptables -t mangle -A POSTROUTING -p tcp --tcp-flags SYN,RST SYN -o ${'$'}IFNAME -j TCPMSS --clamp-mss-to-pmtu
                iptables -t nat -A OUTPUT -p udp --dport 53 -j DNAT --to-destination $primaryDns:53
                iptables -t nat -A OUTPUT -p tcp --dport 53 -j DNAT --to-destination $primaryDns:53
            """.trimIndent()

            val postFsDataSh = """
                #!/system/bin/sh
                # MODDIR="${'$'}{0%/*}"
            """.trimIndent()

            val systemProp = """
                # AWGMutator system properties
                net.dns1=$primaryDns
            """.trimIndent()

            val confContent = config.toConfString()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                addZipFile(zos, "module.prop", moduleProp.toByteArray())
                addZipFile(zos, "service.sh", serviceSh.toByteArray())
                addZipFile(zos, "post-fs-data.sh", postFsDataSh.toByteArray())
                addZipFile(zos, "system.prop", systemProp.toByteArray())
                addZipFile(zos, "awg.conf", confContent.toByteArray())
            }

            Result.success(zipFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addZipFile(zos: ZipOutputStream, entryName: String, data: ByteArray) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    fun shareModuleZip(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Install Magisk Module via..."))
    }
}
