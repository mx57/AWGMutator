package com.example.domain.model

/**
 * Representation of popular blocked or censored platforms (YouTube, Instagram, Telegram, Twitch, X, Discord, etc.)
 */
data class BlockedService(
    val id: String,
    val name: String,
    val iconEmoji: String,
    val testUrl: String,
    val fallbackHost: String,
    val fallbackPort: Int = 443,
    val category: ServiceCategory
)

enum class ServiceCategory {
    VIDEO_STREAMING,
    SOCIAL_NETWORK,
    MESSENGER,
    COMMUNITY_GAMING
}

data class ServiceProbeResult(
    val service: BlockedService,
    val isAccessible: Boolean,
    val latencyMs: Long? = null,
    val isDpiThrottled: Boolean = false,
    val error: String? = null
)

object BlockedServicesCatalog {
    val allServices = listOf(
        BlockedService(
            id = "youtube",
            name = "YouTube",
            iconEmoji = "▶️",
            testUrl = "https://www.youtube.com/generate_204",
            fallbackHost = "www.youtube.com",
            fallbackPort = 443,
            category = ServiceCategory.VIDEO_STREAMING
        ),
        BlockedService(
            id = "instagram",
            name = "Instagram",
            iconEmoji = "📸",
            testUrl = "https://www.instagram.com",
            fallbackHost = "instagram.com",
            fallbackPort = 443,
            category = ServiceCategory.SOCIAL_NETWORK
        ),
        BlockedService(
            id = "telegram",
            name = "Telegram",
            iconEmoji = "✈️",
            testUrl = "https://web.telegram.org",
            fallbackHost = "149.154.167.50",
            fallbackPort = 443,
            category = ServiceCategory.MESSENGER
        ),
        BlockedService(
            id = "twitch",
            name = "Twitch",
            iconEmoji = "🟣",
            testUrl = "https://www.twitch.tv",
            fallbackHost = "twitch.tv",
            fallbackPort = 443,
            category = ServiceCategory.VIDEO_STREAMING
        ),
        BlockedService(
            id = "x_twitter",
            name = "X (Twitter)",
            iconEmoji = "✖️",
            testUrl = "https://x.com",
            fallbackHost = "x.com",
            fallbackPort = 443,
            category = ServiceCategory.SOCIAL_NETWORK
        ),
        BlockedService(
            id = "discord",
            name = "Discord",
            iconEmoji = "🎮",
            testUrl = "https://discord.com/api/v9/gateway",
            fallbackHost = "discord.com",
            fallbackPort = 443,
            category = ServiceCategory.COMMUNITY_GAMING
        ),
        BlockedService(
            id = "tiktok",
            name = "TikTok",
            iconEmoji = "🎵",
            testUrl = "https://www.tiktok.com",
            fallbackHost = "tiktok.com",
            fallbackPort = 443,
            category = ServiceCategory.VIDEO_STREAMING
        ),
        BlockedService(
            id = "facebook",
            name = "Facebook",
            iconEmoji = "👥",
            testUrl = "https://www.facebook.com",
            fallbackHost = "facebook.com",
            fallbackPort = 443,
            category = ServiceCategory.SOCIAL_NETWORK
        )
    )
}
