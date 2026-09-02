package com.example.domain.usecase

import com.example.domain.model.BottleneckSeverity
import com.example.domain.model.BottleneckType
import com.example.domain.model.ConnectionBottleneck
import com.example.domain.model.DiagnosticActionType
import com.example.domain.model.HandshakeStageStatus
import com.example.domain.model.LogDiagnosticReport
import com.example.domain.model.TunnelLogItem

class ParseDiagnosticLogsUseCase {

    private val cleanAnycastEndpoints = listOf(
        "162.159.130.1:1074",
        "172.64.100.1:4500",
        "104.16.132.229:500",
        "141.101.65.1:1074",
        "198.41.130.1:4500",
        "162.159.135.1:500",
        "172.64.104.1:1074",
        "104.19.18.1:4500"
    )

    operator fun invoke(
        logLines: List<String>,
        structuredLogs: List<TunnelLogItem> = emptyList(),
        currentConfigName: String? = null,
        currentEndpoint: String? = null,
        currentTx: Long = 0L,
        currentRx: Long = 0L
    ): LogDiagnosticReport {
        val allLogs = (logLines + structuredLogs.map { it.message }).distinct()

        var configName = currentConfigName
        var endpoint = currentEndpoint
        var txBytes = currentTx
        var rxBytes = currentRx
        var maxZeroRxCycles = 0
        var hasDnsProbeTimeout = false
        var hasEgressTimeout = false
        var hasEgressSuccess = false
        var isTunnelUp = false
        var hasIpv6InAddress = false
        var mtuValue = 1280
        var hasReservedToken = false
        var warpH1Value = 0L

        // Regex patterns for parsing log events
        val endpointRegex = Regex("Target Endpoint:\\s*([0-9a-zA-Z.:]+)|Endpoint\\s*=\\s*([0-9a-zA-Z.:]+)")
        val configNameRegex = Regex("connection to '([^']+)'")
        val trafficPollRegex = Regex("Tx=(\\d+)\\s*B,\\s*Rx=(\\d+)\\s*B(?:\\s*\\(ZeroRxCycles=(\\d+)\\))?")
        val h1Regex = Regex("H1\\s*=\\s*(\\d+)")
        val reservedRegex = Regex("Reserved\\s*=\\s*([0-9,\\s]+)")
        val mtuRegex = Regex("MTU\\s*=\\s*(\\d+)")

        for (line in allLogs) {
            configNameRegex.find(line)?.let {
                configName = it.groupValues[1]
            }
            endpointRegex.find(line)?.let {
                val ep = it.groupValues[1].ifEmpty { it.groupValues[2] }
                if (ep.isNotBlank()) endpoint = ep
            }
            trafficPollRegex.find(line)?.let {
                val tx = it.groupValues[1].toLongOrNull() ?: 0L
                val rx = it.groupValues[2].toLongOrNull() ?: 0L
                val zeroCycles = it.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
                if (tx > txBytes) txBytes = tx
                if (rx > rxBytes) rxBytes = rx
                if (zeroCycles > maxZeroRxCycles) maxZeroRxCycles = zeroCycles
            }
            h1Regex.find(line)?.let {
                warpH1Value = it.groupValues[1].toLongOrNull() ?: 0L
            }
            reservedRegex.find(line)?.let {
                hasReservedToken = true
            }
            mtuRegex.find(line)?.let {
                mtuValue = it.groupValues[1].toIntOrNull() ?: 1280
            }

            if (line.contains("Tunnel state changed to: UP") || line.contains("setState UP returned: UP")) {
                isTunnelUp = true
            }
            if (line.contains("DNS probe failed") || line.contains("DNS probe -> Poll timed out")) {
                hasDnsProbeTimeout = true
            }
            if (line.contains("Cloudflare Trace Probe failed") || line.contains("Trace Probe failed")) {
                hasEgressTimeout = true
            }
            if (line.contains("Trace Probe Success") || line.contains("WARP=on")) {
                hasEgressSuccess = true
            }
            if (line.contains("2606:4700") || line.contains("fd00:") || line.contains("::/128")) {
                hasIpv6InAddress = true
            }
        }

        val bottlenecks = mutableListOf<ConnectionBottleneck>()

        // 1. Bottleneck: Handshake Deadlock (Tx > 0, Rx == 0) - TSPU / ISP Subnet Block
        val isHandshakeDeadlock = txBytes > 0 && rxBytes == 0L
        if (isHandshakeDeadlock || maxZeroRxCycles >= 2) {
            bottlenecks.add(
                ConnectionBottleneck(
                    id = "btnk_handshake_drop",
                    type = BottleneckType.TSPU_HANDSHAKE_DROP,
                    severity = BottleneckSeverity.CRITICAL,
                    title = "Сброс UDP рукопожатия ТСПУ (Tx=$txBytes B, Rx=0 B)",
                    summary = "Пакеты отправляются, но ответ не доходит. Провайдер блокирует подсеть/порт.",
                    technicalDetails = "ТСПУ/РКН отбрасывает пакеты к узлу ${endpoint?.substringBefore(":") ?: "188.114.x.x"}. Требуется чистый Anycast узел.",
                    detectedInLog = "Tx=$txBytes B, Rx=0 B (Повторов без ответа: $maxZeroRxCycles)",
                    recommendedFix = "Переключиться на чистый узел 162.159.130.1:1074 / 172.64.100.1:4500",
                    actionType = DiagnosticActionType.APPLY_ENDPOINT,
                    actionPayload = cleanAnycastEndpoints.first()
                )
            )
        }

        // 2. Bottleneck: WARP Token Authentication / Reserved mismatch
        if (rxBytes in 1..400 && hasEgressTimeout && !hasEgressSuccess) {
            bottlenecks.add(
                ConnectionBottleneck(
                    id = "btnk_warp_auth",
                    type = BottleneckType.WARP_AUTH_REJECTED,
                    severity = BottleneckSeverity.CRITICAL,
                    title = "Сервер принял рукопожатие, но сбросил трафик",
                    summary = "Криптографическое рукопожатие принято ($rxBytes B), но выход в интернет заблокирован.",
                    technicalDetails = "Для WARP требуется валидный 3-байтовый client_id (Reserved). Без него Cloudflare сбрасывает трафик.",
                    detectedInLog = "Rx=$rxBytes B, Egress probe timeout",
                    recommendedFix = "Сгенерировать авторизованный профиль WARP",
                    actionType = DiagnosticActionType.REGENERATE_WARP_ACCOUNT,
                    actionPayload = null
                )
            )
        }

        // 3. Bottleneck: IPv6 Blackholing
        if (hasIpv6InAddress && (hasDnsProbeTimeout || hasEgressTimeout)) {
            bottlenecks.add(
                ConnectionBottleneck(
                    id = "btnk_ipv6_blackhole",
                    type = BottleneckType.IPV6_BLACKHOLE,
                    severity = BottleneckSeverity.WARNING,
                    title = "IPv6 Blackholing на сети оператора",
                    summary = "В профиле активирован IPv6, но оператор не маршрутизирует IPv6 пакеты.",
                    technicalDetails = "Запросы к IPv6 AAAA записям теряются в туннеле, вызывая зависания браузера.",
                    detectedInLog = "Address: 2606:4700:... -> DNS / Trace timeout",
                    recommendedFix = "Переключиться в режим IPv4-Only (удалить IPv6)",
                    actionType = DiagnosticActionType.SWITCH_IPV4_ONLY,
                    actionPayload = null
                )
            )
        }

        // 4. Bottleneck: MTU Clamping
        if (mtuValue > 1280) {
            bottlenecks.add(
                ConnectionBottleneck(
                    id = "btnk_mtu_size",
                    type = BottleneckType.MTU_FRAGMENTATION,
                    severity = BottleneckSeverity.INFO,
                    title = "Высокий MTU ($mtuValue)",
                    summary = "MTU > 1280 может фрагментироваться на сотовых сетях РФ.",
                    technicalDetails = "Заголовки AmneziaWG требуют безопасного размера пакета MTU 1280.",
                    detectedInLog = "MTU = $mtuValue",
                    recommendedFix = "Установить безопасный MTU 1280",
                    actionType = DiagnosticActionType.REPAIR_MTU_1280,
                    actionPayload = "1280"
                )
            )
        }

        val isHandshakeSucceeded = rxBytes > 0
        val isInternetFunctional = hasEgressSuccess && isHandshakeSucceeded

        val stages = listOf(
            HandshakeStageStatus(
                stageName = "1. Конфигурация & Интерфейс",
                description = "Парсинг ключей, MTU ($mtuValue) и параметров AWG",
                isSuccess = isTunnelUp,
                isCurrentOrFailed = !isTunnelUp,
                details = "Интерфейс VPN поднят, MTU $mtuValue"
            ),
            HandshakeStageStatus(
                stageName = "2. UDP Рукопожатие (Handshake)",
                description = "Отправка Initiation (Tx) и прием Response (Rx)",
                isSuccess = isHandshakeSucceeded,
                isCurrentOrFailed = isTunnelUp && !isHandshakeSucceeded,
                details = if (isHandshakeSucceeded) "Рукопожатие пройдено: Tx=$txBytes B, Rx=$rxBytes B" else "Рукопожатие не завершено: Tx=$txBytes B, Rx=0 B (Ответ сервера не получен)"
            ),
            HandshakeStageStatus(
                stageName = "3. Аутентификация ключа & Reserved",
                description = "Проверка авторизации PrivateKey и Client ID на шлюзе",
                isSuccess = isHandshakeSucceeded && (rxBytes > 400 || hasEgressSuccess),
                isCurrentOrFailed = isHandshakeSucceeded && rxBytes in 1..400 && !hasEgressSuccess,
                details = if (hasReservedToken || warpH1Value > 1L) "Reserved токен / H1 передан (H1=$warpH1Value)" else "Reserved токен не настроен"
            ),
            HandshakeStageStatus(
                stageName = "4. DNS Резолюция (UDP:53)",
                description = "Проверка разрешения доменов через туннель",
                isSuccess = !hasDnsProbeTimeout && isHandshakeSucceeded,
                isCurrentOrFailed = isHandshakeSucceeded && hasDnsProbeTimeout,
                details = if (hasDnsProbeTimeout) "DNS таймаут на порту 53" else "DNS резолверы отвечают штатно"
            ),
            HandshakeStageStatus(
                stageName = "5. Выход в интернет (HTTPS Egress)",
                description = "Проверка прохождения реального веб-трафика (CDN Trace)",
                isSuccess = isInternetFunctional,
                isCurrentOrFailed = isHandshakeSucceeded && !isInternetFunctional,
                details = if (isInternetFunctional) "Туннель полностью функционален, выход в сеть активен" else "Нет ответа от внешних хостов (Egress timeout)"
            )
        )

        val healthScore = when {
            isInternetFunctional -> 100
            isHandshakeSucceeded && !hasDnsProbeTimeout -> 65
            isHandshakeSucceeded -> 40
            isTunnelUp && txBytes > 0 -> 20
            else -> 0
        }

        val verdict = when {
            isInternetFunctional -> "Туннель работает: Рукопожатие успешно, интернет доступен."
            isHandshakeDeadlock -> "Блокировка ТСПУ: Сервер не отвечает на порт UDP (Tx=$txBytes B, Rx=0 B). Смените узел/порт."
            isHandshakeSucceeded && !isInternetFunctional -> "Рукопожатие принято, но интернет заблокирован из-за Reserved или IPv6."
            else -> "Соединение не установлено: выберите профиль и подключитесь."
        }

        return LogDiagnosticReport(
            activeConfigName = configName,
            targetEndpoint = endpoint,
            txBytes = txBytes,
            rxBytes = rxBytes,
            zeroRxCycles = maxZeroRxCycles,
            isHandshakeSucceeded = isHandshakeSucceeded,
            isInternetFunctional = isInternetFunctional,
            overallHealthScore = healthScore,
            summaryVerdict = verdict,
            stages = stages,
            bottlenecks = bottlenecks,
            recommendedEndpoints = cleanAnycastEndpoints
        )
    }
}
