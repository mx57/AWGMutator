package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.os.Build
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.remote.CloudflareApi
import com.example.data.remote.PingTester
import com.example.data.repository.ConfigRepositoryImpl
import com.example.data.repository.EvolutionRepositoryImpl
import com.example.domain.noise.DpiNoiseManager
import com.example.domain.repository.ConfigRepository
import com.example.domain.repository.EvolutionRepository
import com.example.evolution.GeneticAlgorithm
import com.example.vpn.SplitTunnelManager
import com.example.vpn.TunnelManager

/**
 * Main Application class for AWGMutator.
 * Initializes notification channels, Room DB, repositories, noise manager, and core services.
 * Implements memory trimming callbacks to adhere to Android Q+ ashmem guidelines.
 */
class App : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var configRepository: ConfigRepository
        private set

    lateinit var evolutionRepository: EvolutionRepository
        private set

    lateinit var cloudflareApi: CloudflareApi
        private set

    lateinit var pingTester: PingTester
        private set

    lateinit var networkEgressVerifier: com.example.data.remote.NetworkEgressVerifier
        private set

    lateinit var dpiNoiseManager: DpiNoiseManager
        private set

    lateinit var tunnelManager: TunnelManager
        private set

    lateinit var rootTunnelManager: com.example.vpn.RootTunnelManager
        private set

    lateinit var splitTunnelManager: SplitTunnelManager
        private set

    lateinit var appTrafficTracker: com.example.vpn.AppTrafficTracker
        private set

    lateinit var geneticAlgorithm: GeneticAlgorithm
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannels()

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "awg_mutator.db"
        ).setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
         .fallbackToDestructiveMigration(true).build()

        configRepository = ConfigRepositoryImpl(database.configDao())
        evolutionRepository = EvolutionRepositoryImpl(database.evolutionDao())

        cloudflareApi = CloudflareApi()
        pingTester = PingTester()
        networkEgressVerifier = com.example.data.remote.NetworkEgressVerifier()
        dpiNoiseManager = DpiNoiseManager()
        splitTunnelManager = SplitTunnelManager(applicationContext)
        appTrafficTracker = com.example.vpn.AppTrafficTracker(applicationContext, splitTunnelManager)
        tunnelManager = TunnelManager(applicationContext)
        rootTunnelManager = com.example.vpn.RootTunnelManager(applicationContext)
        geneticAlgorithm = GeneticAlgorithm(
            pingTester = pingTester,
            evolutionRepository = evolutionRepository
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vpnChannel = NotificationChannel(
                CHANNEL_VPN_STATUS,
                "VPN Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active VPN connection status"
                setShowBadge(false)
            }

            val evoChannel = NotificationChannel(
                CHANNEL_EVOLUTION,
                "Evolution Progress",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows genetic mutation & testing progress"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(vpnChannel)
            manager?.createNotificationChannel(evoChannel)
        }
    }

    companion object {
        const val CHANNEL_VPN_STATUS = "awg_vpn_status_channel"
        const val CHANNEL_EVOLUTION = "awg_evolution_channel"

        lateinit var instance: App
            private set
    }
}
