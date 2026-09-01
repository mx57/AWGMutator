package com.example.domain.model

import java.util.UUID

/**
 * Severity level of tunnel diagnostic logs with visual styling hints.
 */
enum class LogSeverity {
    ERROR,
    WARN,
    SUCCESS,
    ANTI_DPI,
    ROUTING,
    DNS,
    STATS,
    MASQUE,
    INFO,
    DEBUG;

    companion object {
        fun fromTagAndMessage(tag: String, message: String): LogSeverity {
            val upperTag = tag.uppercase()
            val upperMsg = message.uppercase()

            return when {
                upperTag.contains("ERR") || upperTag.contains("FAIL") ||
                        upperMsg.contains("EXCEPTION") || upperMsg.contains("FAILED") || upperMsg.contains("ERROR") -> ERROR

                upperTag.contains("WARN") || upperMsg.contains("WARN") ||
                        upperMsg.contains("DROP") || upperMsg.contains("TIMEOUT") || upperMsg.contains("RETRY") -> WARN

                upperTag.contains("ANTI_DPI") || upperTag.contains("MUTAT") ||
                        upperTag.contains("NOISE") || upperTag.contains("GENETIC") -> ANTI_DPI

                upperTag.contains("DNS") || upperTag.contains("DOH") -> DNS

                upperTag.contains("MASQUE") || upperTag.contains("HTTP3") || upperTag.contains("QUIC") -> MASQUE

                upperTag.contains("STATS") || upperTag.contains("POLL") -> STATS

                upperTag.contains("ROUTING") || upperTag.contains("ROUTE") ||
                        upperTag.contains("SOCKET") || upperTag.contains("TUN") -> ROUTING

                upperMsg.contains("CONNECTED") || upperMsg.contains("SUCCESS") ||
                        upperMsg.contains("ESTABLISHED") || upperMsg.contains("HANDSHAKE ACTIVE") -> SUCCESS

                else -> INFO
            }
        }
    }
}

/**
 * Structured log item supporting automatic deduplication and repeat count aggregation.
 */
data class TunnelLogItem(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String,
    val tag: String,
    val message: String,
    val severity: LogSeverity = LogSeverity.fromTagAndMessage(tag, message),
    val count: Int = 1,
    val firstTimestamp: String = timestamp,
    val lastTimestamp: String = timestamp
) {
    val displayKey: String
        get() = "${tag.trim()}|${message.trim()}"

    val formattedDisplay: String
        get() = if (count > 1) {
            "[$lastTimestamp] [$tag] $message (repeated $count times)"
        } else {
            "[$timestamp] [$tag] $message"
        }
}
