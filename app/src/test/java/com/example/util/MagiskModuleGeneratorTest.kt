package com.example.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.AwgConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MagiskModuleGeneratorTest {

    private lateinit var context: Context
    private lateinit var testConfig: AwgConfig

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testConfig = AwgConfig(
            name = "Test_Config_#1!",
            privateKey = "dGVzdF9wcml2YXRlX2tleQ==",
            address = "10.0.0.2/32",
            dns = "1.1.1.1, 8.8.8.8",
            mtu = 1420
        )
    }

    @Test
    fun testGenerateModuleZipSuccess() {
        runBlocking {
            val result = MagiskModuleGenerator.generateModuleZip(context, testConfig)

            assertTrue("Expected generateModuleZip to succeed", result.isSuccess)
            val zipFile = result.getOrNull()
            assertNotNull("Zip file should not be null", zipFile)
            assertTrue("Zip file should exist on disk", zipFile!!.exists())
            assertTrue("Zip file should not be empty", zipFile.length() > 0)

            // Read ZIP entries and verify contents
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries().toList().associateBy { it.name }

                assertTrue("Zip must contain module.prop", entries.containsKey("module.prop"))
                assertTrue("Zip must contain service.sh", entries.containsKey("service.sh"))
                assertTrue("Zip must contain post-fs-data.sh", entries.containsKey("post-fs-data.sh"))
                assertTrue("Zip must contain system.prop", entries.containsKey("system.prop"))
                assertTrue("Zip must contain awg.conf", entries.containsKey("awg.conf"))

                val modulePropText = zip.getInputStream(entries["module.prop"]).bufferedReader().readText()
                assertTrue(modulePropText.contains("id=awgmutator_tunnel"))
                assertTrue(modulePropText.contains("name=AWGMutator Root Tunnel (${testConfig.name})"))

                val serviceShText = zip.getInputStream(entries["service.sh"]).bufferedReader().readText()
                assertTrue("service.sh should contain IFNAME=\"awg0\"", serviceShText.contains("IFNAME=\"awg0\""))
                assertTrue("service.sh should contain ip address add", serviceShText.contains("ip address add 10.0.0.2/32 dev \$IFNAME"))
                assertTrue("service.sh should contain ip link set mtu", serviceShText.contains("ip link set mtu 1420 dev \$IFNAME"))
                assertTrue("service.sh should contain DNAT rule", serviceShText.contains("DNAT --to-destination 1.1.1.1:53"))

                val systemPropText = zip.getInputStream(entries["system.prop"]).bufferedReader().readText()
                assertTrue(systemPropText.contains("net.dns1=1.1.1.1"))

                val awgConfText = zip.getInputStream(entries["awg.conf"]).bufferedReader().readText()
                assertEquals(testConfig.toConfString(), awgConfText)
            }

            // Cleanup
            zipFile.delete()
        }
    }

    @Test
    fun testGenerateModuleZipErrorPathCacheDirException() {
        runBlocking {
            // Create context wrapper where getCacheDir throws an exception
            val failingContext = object : ContextWrapper(context) {
                override fun getCacheDir(): File {
                    throw RuntimeException("Simulated failure accessing cache directory")
                }
            }

            val result = MagiskModuleGenerator.generateModuleZip(failingContext, testConfig)

            assertTrue("Expected generateModuleZip to fail when cacheDir throws exception", result.isFailure)
            val exception = result.exceptionOrNull()
            assertNotNull("Exception should be captured in Result", exception)
            assertTrue(exception is RuntimeException)
            assertEquals("Simulated failure accessing cache directory", exception?.message)
        }
    }

    @Test
    fun testGenerateModuleZipErrorPathInvalidDirectory() {
        runBlocking {
            // Create a regular file in cacheDir named 'magisk_modules' so mkdirs() / File creation fails
            val cacheDir = context.cacheDir
            val blockingFile = File(cacheDir, "magisk_modules")
            blockingFile.createNewFile()

            try {
                val failingContext = object : ContextWrapper(context) {
                    override fun getCacheDir(): File {
                        return cacheDir
                    }
                }

                val result = MagiskModuleGenerator.generateModuleZip(failingContext, testConfig)

                assertTrue("Expected generateModuleZip to fail when output directory cannot be created", result.isFailure)
                assertNotNull("Exception should be present in Result.failure", result.exceptionOrNull())
            } finally {
                blockingFile.delete()
            }
        }
    }

    @Test
    fun testGenerateModuleZipNameSanitization() {
        runBlocking {
            val configWithSpecialChars = testConfig.copy(name = "My Test / Config @ 2025!!")
            val result = MagiskModuleGenerator.generateModuleZip(context, configWithSpecialChars)

            assertTrue(result.isSuccess)
            val zipFile = result.getOrNull()!!
            assertTrue(zipFile.name.contains("my_test___config___2025__"))
            assertTrue(zipFile.exists())

            zipFile.delete()
        }
    }

    @Test
    fun testShareModuleZipStartsChooserIntent() {
        val dummyZip = File(context.cacheDir, "test_share.zip").apply {
            writeText("dummy content")
            deleteOnExit()
        }

        MagiskModuleGenerator.shareModuleZip(context, dummyZip)

        val shadowApp = Shadows.shadowOf(context as Application)
        val startedIntent = shadowApp.nextStartedActivity

        assertNotNull("An activity should have been started to share file", startedIntent)
        assertEquals(Intent.ACTION_CHOOSER, startedIntent.action)
    }
}
