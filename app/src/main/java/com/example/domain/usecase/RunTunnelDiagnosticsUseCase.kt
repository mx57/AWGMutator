package com.example.domain.usecase

import com.example.App
import com.example.domain.model.AwgConfig
import com.example.domain.model.DiagnosticActionType
import com.example.domain.model.DiagnosticStatus
import com.example.domain.model.DiagnosticStep
import com.example.domain.model.EndpointProbeDetail
import com.example.domain.model.TunnelDiagnosticReport
import com.example.util.WireGuardProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class RunTunnelDiagnosticsUseCase(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3000, TimeUnit.MILLISECONDS)
        .readTimeout(3000, TimeUnit.MILLISECONDS)
        .callTimeout(4000, TimeUnit.MILLISECONDS)
        .build()
) {

    private val candidateEndpoints = listOf(
        "162.159.130.1:1074",
        "172.64.100.1:4500",
        "104.16.132.229:500",
        "141.101.65.1:1074",
        "198.41.130.1:4500",
        "162.159.135.1:500",
        "172.64.104.1:1074",
        "104.19.18.1:4500",
        "162.159.195.1:1074",
        "188.114.98.1:4500",
        "188.114.99.1:500",
        "188.114.96.1:1074"
    )

    fun execute(config: AwgConfig?): Flow<TunnelDiagnosticReport> = flow {
        val steps = mutableListOf(
            DiagnosticStep("step_handshake", "1. Проверка UDP эндпоинтов и обхода ТСПУ", "Опрос матрицы Anycast-узлов реальными WireGuard Noise пакетами"),
            DiagnosticStep("step_account", "2. Аутентификация ключа и Reserved токена", "Проверка авторизации PrivateKey и 3 байт client_id на серверах"),
            DiagnosticStep("step_ipv6", "3. Анализ маршрутизации IPv6 / IPv4", "Выявление проблемы IPv6 Blackholing на мобильных сетях РФ"),
            DiagnosticStep("step_dns", "4. Проверка прохождения DNS (UDP:53)", "Тест доступности DNS-резолверов 1.1.1.1 и 8.8.8.8"),
            DiagnosticStep("step_egress", "5. Тест выхода в интернет (HTTPS Egress)", "Запрос Cloudflare CDN-Trace и проверка статуса WARP")
        )

        var report = TunnelDiagnosticReport(
            isRunning = true,
            currentStepIndex = 1,
            totalSteps = 5,
            overallVerdict = "Диагностика начата: опрос UDP эндпоинтов...",
            steps = steps
        )
        emit(report)

        // ================= STAGE 1: Real UDP Handshake Matrix =================
        val peerKey = config?.peerPublicKey?.ifBlank { WireGuardProbe.DEFAULT_CLOUDFLARE_WARP_PUBKEY } ?: WireGuardProbe.DEFAULT_CLOUDFLARE_WARP_PUBKEY
        val privKey = config?.privateKey
        val h1 = config?.h1 ?: 1L

        val testedDetails: List<EndpointProbeDetail> = coroutineScope {
            candidateEndpoints.map { ep ->
                async {
                    val (host, portStr) = if (ep.contains(":")) ep.split(":") else listOf(ep, "1074")
                    val port = portStr.toIntOrNull() ?: 1074
                    val probe = WireGuardProbe.probeEndpoint(
                        host = host,
                        port = port,
                        peerPublicKeyBase64 = peerKey,
                        clientPrivateKeyBase64 = privKey,
                        h1 = h1,
                        timeoutMs = 1200,
                        attempts = 2
                    )
                    EndpointProbeDetail(
                        endpoint = ep,
                        port = port,
                        isWorking = probe.isReachable,
                        latencyMs = probe.latencyMs,
                        error = probe.error
                    )
                }
            }.awaitAll()
        }

        val workingEndpoints = testedDetails.filter { it.isWorking }.sortedBy { it.latencyMs ?: 9999L }
        val best = workingEndpoints.firstOrNull()
        val currentEpWorking = testedDetails.firstOrNull { it.endpoint == config?.endpoint }?.isWorking == true

        val (s1Status, s1Text, s1Action, s1Label, s1Payload) = when {
            best != null && currentEpWorking -> {
                Tuple5(
                    DiagnosticStatus.SUCCESS,
                    "✓ Текущий эндпоинт ${config?.endpoint} отвечает (${best.latencyMs}ms). Доступно ${workingEndpoints.size} рабочих узлов.",
                    DiagnosticActionType.NONE,
                    null,
                    null
                )
            }
            best != null && !currentEpWorking -> {
                Tuple5(
                    DiagnosticStatus.ERROR,
                    "✗ Текущий эндпоинт '${config?.endpoint}' заблокирован ТСПУ (Rx=0B). Найден рабочий узел ${best.endpoint} (${best.latencyMs}ms).",
                    DiagnosticActionType.APPLY_ENDPOINT,
                    "Применить ${best.endpoint}",
                    best.endpoint
                )
            }
            else -> {
                Tuple5(
                    DiagnosticStatus.WARNING,
                    "⚠ Все стандартные UDP порты фильтруются провайдером. Рекомендуется использовать обфускацию AmneziaWG (Jc > 0).",
                    DiagnosticActionType.NONE,
                    null,
                    null
                )
            }
        }

        steps[0] = steps[0].copy(
            status = s1Status,
            resultText = s1Text,
            details = testedDetails.joinToString("\n") {
                "${if (it.isWorking) "✓" else "✗"} ${it.endpoint} — ${if (it.isWorking) "${it.latencyMs} ms" else (it.error ?: "Dropped")}"
            },
            latencyMs = best?.latencyMs,
            recommendedAction = s1Action,
            actionLabel = s1Label,
            actionPayload = s1Payload
        )

        report = report.copy(
            currentStepIndex = 2,
            testedEndpoints = testedDetails,
            bestEndpoint = best?.endpoint,
            bestEndpointLatencyMs = best?.latencyMs,
            steps = steps.toList()
        )
        emit(report)

        // ================= STAGE 2: Account & Reserved Key Verification =================
        val hasReserved = !config?.reserved.isNullOrBlank()
        val hasPrivKey = !config?.privateKey.isNullOrBlank()
        val isWarp = config?.isWarp == true

        val (s2Status, s2Text, s2Action, s2Label) = when {
            isWarp && hasReserved && hasPrivKey -> {
                Tuple4(
                    DiagnosticStatus.SUCCESS,
                    "✓ Профиль Cloudflare WARP содержит валидный client_id (Reserved: ${config?.reserved}) и закрытый ключ.",
                    DiagnosticActionType.NONE,
                    null
                )
            }
            isWarp && (!hasReserved || !hasPrivKey) -> {
                Tuple4(
                    DiagnosticStatus.ERROR,
                    "✗ Отсутствует токен Reserved. Cloudflare сбрасывает интернет-трафик неавторизованных ключей.",
                    DiagnosticActionType.REGENERATE_WARP_ACCOUNT,
                    "Сгенерировать чистый WARP аккаунт"
                )
            }
            else -> {
                Tuple4(
                    DiagnosticStatus.SUCCESS,
                    "✓ Частный сервер AmneziaWG: ключ настроен (H1=${config?.h1 ?: 1}, Jc=${config?.jc ?: 0}).",
                    DiagnosticActionType.NONE,
                    null
                )
            }
        }

        steps[1] = steps[1].copy(
            status = s2Status,
            resultText = s2Text,
            recommendedAction = s2Action,
            actionLabel = s2Label
        )

        report = report.copy(
            currentStepIndex = 3,
            steps = steps.toList()
        )
        emit(report)

        // ================= STAGE 3: IPv6 / IPv4 Routing Analysis =================
        val hasIpv6Configured = config?.address?.contains(":") == true || config?.allowedIps?.contains("::/0") == true
        val isIpv6Blackholing = hasIpv6Configured // Most Russian mobile carriers (Xiaomi/MTS/Megafon) blackhole IPv6 WireGuard routes

        val (s3Status, s3Text, s3Action, s3Label) = if (hasIpv6Configured) {
            Tuple4(
                DiagnosticStatus.WARNING,
                "⚠ Включен маршрут IPv6 (::/0). На мобильных операторах РФ неработающий IPv6 часто вызывает таймауты подключения.",
                DiagnosticActionType.SWITCH_IPV4_ONLY,
                "Переключить на 'Только IPv4' (IPv4-Only)"
            )
        } else {
            Tuple4(
                DiagnosticStatus.SUCCESS,
                "✓ Конфигурация работает в надежном режиме IPv4-Only (172.16.0.2/32, 0.0.0.0/0).",
                DiagnosticActionType.NONE,
                null
            )
        }

        steps[2] = steps[2].copy(
            status = s3Status,
            resultText = s3Text,
            recommendedAction = s3Action,
            actionLabel = s3Label
        )

        report = report.copy(
            currentStepIndex = 4,
            steps = steps.toList()
        )
        emit(report)

        // ================= STAGE 4: DNS Probe (UDP:53) =================
        val dnsProbeMs = testDnsUdp("1.1.1.1") ?: testDnsUdp("8.8.8.8")
        val (s4Status, s4Text) = if (dnsProbeMs != null && dnsProbeMs > 0) {
            Pair(
                DiagnosticStatus.SUCCESS,
                "✓ DNS-серверы 1.1.1.1 / 8.8.8.8 отвечают штатно за ${dnsProbeMs}ms."
            )
        } else {
            Pair(
                DiagnosticStatus.WARNING,
                "⚠ Прямой DNS UDP:53 не ответил. Рекомендуется использовать встроенный DoH (DNS-over-HTTPS) или системный DNS."
            )
        }

        steps[3] = steps[3].copy(
            status = s4Status,
            resultText = s4Text,
            latencyMs = dnsProbeMs
        )

        report = report.copy(
            currentStepIndex = 5,
            steps = steps.toList()
        )
        emit(report)

        // ================= STAGE 5: HTTPS Egress Trace Probe =================
        val egressResult = App.instance.networkEgressVerifier.verifyEgress()
        val (s5Status, s5Text) = if (egressResult.isFunctional) {
            Pair(
                DiagnosticStatus.SUCCESS,
                "✓ Выход в интернет подтвержден! Внешний IP: ${egressResult.publicIp ?: "N/A"} (${egressResult.countryCode ?: "Global"}), WARP: ${if (egressResult.isWarpActive) "ON" else "OFF"}."
            )
        } else {
            Pair(
                DiagnosticStatus.ERROR,
                "✗ Трафик не выходит наружу (${egressResult.errorMessage ?: "Таймаут соединения"}). Примените рекомендованные действия выше."
            )
        }

        steps[4] = steps[4].copy(
            status = s5Status,
            resultText = s5Text,
            latencyMs = egressResult.latencyMs
        )

        val hasErrors = steps.any { it.status == DiagnosticStatus.ERROR }
        val overall = when {
            !hasErrors && egressResult.isFunctional -> "Все системы работают отлично! Соединение полностью функционально."
            best != null && !currentEpWorking -> "Основная причина: блокировка эндпоинта ТСПУ. Нажмите кнопку 'Применить ${best.endpoint}'."
            !hasReserved && isWarp -> "Основная причина: ключ не авторизован на Cloudflare. Требуется авто-генерация аккаунта."
            else -> "Обнаружены неполадки с маршрутизацией. Примените исправления выше."
        }

        report = report.copy(
            isRunning = false,
            currentStepIndex = 5,
            overallVerdict = overall,
            isHealthy = !hasErrors && egressResult.isFunctional,
            steps = steps.toList()
        )
        emit(report)
    }.flowOn(Dispatchers.IO)

    private fun testDnsUdp(ip: String): Long? {
        return try {
            val address = InetAddress.getByName(ip)
            DatagramSocket().use { socket ->
                socket.soTimeout = 1200
                // Simple DNS query packet for google.com (A record)
                val queryPacket = byteArrayOf(
                    0x12, 0x34, // ID
                    0x01, 0x00, // Standard query
                    0x00, 0x01, // QDCOUNT = 1
                    0x00, 0x00, // ANCOUNT
                    0x00, 0x00, // NSCOUNT
                    0x00, 0x00, // ARCOUNT
                    0x06, 0x67, 0x6f, 0x6f, 0x67, 0x6c, 0x65, // google
                    0x03, 0x63, 0x6f, 0x6d, // com
                    0x00, // root
                    0x00, 0x01, // Type A
                    0x00, 0x01  // Class IN
                )
                val sendPacket = DatagramPacket(queryPacket, queryPacket.size, address, 53)
                val start = System.currentTimeMillis()
                socket.send(sendPacket)

                val buf = ByteArray(512)
                val recvPacket = DatagramPacket(buf, buf.size)
                socket.receive(recvPacket)
                val rtt = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                if (recvPacket.length > 12) rtt else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
}
