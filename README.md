# 🛡️ AWGMutator — Next-Gen Anti-DPI & AmneziaWG Genetic Optimizer for Android

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose M3" />
  <img src="https://img.shields.io/badge/Architecture-Clean%20%2B%20MVVM-00C853?style=for-the-badge" alt="Architecture" />
  <img src="https://img.shields.io/badge/Build-GitHub%20Actions%20Passing-success?style=for-the-badge&logo=githubactions&logoColor=white" alt="Build Status" />
  <img src="https://img.shields.io/badge/Obfuscation-AmneziaWG%20%2B%20Noise-FF6D00?style=for-the-badge" alt="Anti-DPI" />
</p>

---

## 📖 Обзор проекта / Project Overview

**AWGMutator** — это передовой Android-клиент и исследовательская лаборатория для протоколов **AmneziaWG (AWG)** и **WireGuard**, оснащенная встроенным **генетическим алгоритмом** для автоматического обхода современных систем глубокой инспекции пакетов (DPI / TSPU / GFW).

Приложение не просто подключается к VPN — оно непрерывно тестирует, мутирует и подбирает идеальные параметры обфускации сетевых заголовков (`Jc`, `Jmin`, `Jmax`, `S1`, `S2`, `H1`, `H2`, `H3`, `H4`) в реальном времени, адаптируясь под сигнатуры конкретного интернет-провайдера.

---

## 📱 Интерфейс и экраны / Screen Showcase

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            AWGMutator UI Architecture                       │
├──────────────────────┬──────────────────────┬───────────────────────────────┤
│    🚀 DASHBOARD      │  🧬 GENETIC LAB      │   📊 MATRIX MONITOR           │
│                      │                      │                               │
│  [  VPN: CONNECTED ] │  Generation: #14     │  🟢 YouTube       38ms  OK    │
│  Down: 14.8 MB/s     │  Fitness: 94.2%      │  🟢 Discord       42ms  OK    │
│  Up:   2.4 MB/s      │  ┌─────────────────┐ │  🟢 Telegram      29ms  OK    │
│  Ping: 34ms (Warsaw) │  │ 📈 Live Fitness │ │  🟢 Instagram     55ms  OK    │
│                      │  │    Evolution    │ │  🟢 Twitter/X     61ms  OK    │
│  [⚡ One-Click WARP] │  └─────────────────┘ │  🟢 Notion        48ms  OK    │
│  [🛑 Disconnect    ] │  [⚡ Start Evolution]│  [🔄 Test All Targets]        │
├──────────────────────┼──────────────────────┼───────────────────────────────┤
│   🔬 ANTI-DPI LAB    │  📑 CONFIG MANAGER   │   🔀 SPLIT TUNNELING          │
│                      │                      │                               │
│  • Handshake Noise   │  • Warsaw AWG-01 🟢  │  Mode: Route Only Selected    │
│  • Packet Padding    │  • Cloudflare WARP ⚡ │  ☑️ YouTube                   │
│  • Timing Jitter     │  • Frankfurt AWS 🟡  │  ☑️ Discord                   │
│  • Dynamic MTU/MSS   │                      │  ☑️ Telegram                  │
│  [Import / Export]   │  [➕ Add / Scan QR]  │  ☐ Banking Apps (Direct)      │
└──────────────────────┴──────────────────────┴───────────────────────────────┘
```

---

## ✨ Ключевые возможности и фишки / Key Features

### 1. 🧬 Генетический оптимизатор параметров обфускации (Genetic Algorithm)
- **Авто-эволюция параметров:** Автоматический подбор уникальных сигнатур обфускации при блокировке протокола.
- **Мутации и скрещивание:** Алгоритм случайным образом мутирует размеры мусорных пакетов (`Jc`), диапазоны шума (`Jmin-Jmax`), байты инициализации (`S1/S2`) и магические заголовки рукопожатий (`H1-H4`).
- **Фитнес-функция в реальном времени:** Рассчитывает качество конфигурации на основе совокупности факторов: `Успешность Handshake` + `RTT Latency` + `Процент потерь пакетов` + `Длина пути TCP/TLS`.
- **Живой график эволюции:** Нативный Compose Canvas отображает график приспособленности популяции от поколения к поколению.

### 2. ⚡ Генератор Cloudflare WARP + AmneziaWG в 1 клик
- Встроенная генерация криптографических пар ключей Curve25519.
- Автоматическая регистрация клиентской учетной записи через официальный Cloudflare Zero Trust API.
- Мгновенное наложение случайных Anti-DPI заголовков на WARP-профиль для обхода полной блокировки WireGuard провайдерами.

### 3. 🔬 Лаборатория Anti-DPI (Deep Packet Modulation)
- **Handshake Noise Injection:** Инъекция псевдослучайных байт перед отправкой `Init-Packet` для предотвращения распознавания протокола по сигнатуре первого байта.
- **Dynamic Packet Padding:** Выравнивание длины пакетов под стандартные размеры HTTPS/TLS ClientHello (от 1280 до 1420 байт).
- **Timing Jitter:** Внесение миллисекундных микропауз между пакетами рукопожатия для сбивания эвристических DPI-таймингов.
- **Dynamic Fragmenter:** Фрагментация исходящих пакетов рукопожатия на уровне TUN-интерфейса.

### 4. 📊 Матрица заблокированных сервисов (Blocked Services Matrix)
- Мониторинг доступности популярных ресурсов с раздельным замером задержки:
  - 🎥 **YouTube / Google Video CDN** (googlevideo.com / yt3.ggpht.com)
  - 💬 **Discord Gateway & Voice** (gateway.discord.gg)
  - ✈️ **Telegram Datacenters** (149.154.167.51, 91.108.56.165)
  - 📸 **Instagram / Meta CDN** (instagram.com)
  - 🐦 **Twitter / X API** (api.x.com)
  - 🧠 **OpenAI / ChatGPT** (chatgpt.com)
  - 📝 **Notion, Spotify, Twitch, Reddit**

### 5. 🔀 Раздельное туннелирование (Split Tunneling)
- **3 режима работы:**
  1. `Весь трафик через VPN` — полная защита устройства.
  2. `Только выбранные приложения` — через туннель идут только заблокированные сервисы (например, YouTube и Discord), не нагружая трафик для банков и госуслуг.
  3. `Все, кроме выбранных` — исключение приложений с геолокационными ограничениями.
- Быстрый поиск по всем установленным системным и пользовательским приложениям.

### 6. 📑 Мультиформатный менеджер конфигураций
- **Импорт:** Из буфера обмена, из текстовых файлов `.conf`, сканированием QR-кодов.
- **Экспорт:** Генерация QR-кода на экране устройства для быстрой передачи на ПК/роутер, экспорт в стандартные `.conf` и JSON.
- **Локальная база данных:** Безопасное хранилище на базе Room Database с журналированием Write-Ahead Logging (WAL).

---

## 🔬 Таблица параметров обфускации AmneziaWG

| Параметр | Название | Назначение | Рекомендуемые значения |
|---|---|---|---|
| `Jc` | **Junk Packet Count** | Количество мусорных пакетов перед инициализацией | `1` – `15` |
| `Jmin` | **Junk Packet Min Size** | Минимальный размер случайного мусорного пакета (байт) | `40` – `120` |
| `Jmax` | **Junk Packet Max Size** | Максимальный размер случайного мусорного пакета (байт) | `200` – `1280` |
| `S1` | **Init Packet Junk Size** | Размер мусорного префикса первого пакета рукопожатия | `16` – `256` |
| `S2` | **Response Packet Junk Size** | Размер мусорного префикса ответа рукопожатия | `16` – `256` |
| `H1` | **Init Packet Header** | Замена стандартного 4-байтового WireGuard magic byte (`0x01000000`) | Любое уникальное `UInt32` |
| `H2` | **Response Packet Header** | Замена magic byte для пакета ответа (`0x02000000`) | Любое уникальное `UInt32` |
| `H3` | **Cookie Packet Header** | Замена magic byte для Cookie пакета (`0x03000000`) | Любое уникальное `UInt32` |
| `H4` | **Data Packet Header** | Замена magic byte для транспортных пакетов данных (`0x04000000`) | Любое уникальное `UInt32` |

---

## 🏗️ Архитектура проекта / Technical Architecture

```
com.example/
├── App.kt                      # Application класс (Room, Notification channels, Trimming)
├── MainActivity.kt             # Главная точка входа (Edge-to-Edge Compose)
├── data/
│   ├── local/                  # Room Database, DAO (ConfigDao, EvolutionDao), Entities
│   ├── remote/                 # CloudflareApi, PingTester, Retrofit/OkHttp клиенты
│   └── repository/             # Реализация репозиториев Clean Architecture
├── domain/
│   ├── model/                  # AwgConfig, EvolutionGene, BlockedService, VpnStatus
│   ├── noise/                  # DpiNoiseManager (Модуляция шума и заголовков)
│   └── repository/             # Интерфейсы репозиториев
├── evolution/
│   └── GeneticAlgorithm.kt     # Ядро генетической эволюции и отбора параметров
├── presentation/
│   ├── dashboard/              # Главный экран: статус, спидометр, графики RTT
│   ├── evolution/              # Экран управления генетическим алгоритмом
│   ├── antidpi/                # Лаборатория настройки шума и параметров обфускации
│   ├── configs/                # Менеджер конфигов, генератор WARP, QR-код
│   ├── settings/               # Матрица сервисов, Split Tunneling, настройки
│   └── navigation/             # Type-safe Navigation Compose граф
└── vpn/
    ├── AwgVpnService.kt        # Реализация Android VpnService & TUN routing
    ├── TunnelManager.kt        # Управление состоянием и метриками трафика
    └── SplitTunnelManager.kt   # Фильтрация пакетов по App UID/Package
```

---

## 🚀 Сборка и установка / Building & Installation

### Требования:
- Android 8.0+ (API Level 26+)
- JDK 17
- Gradle 8.11+ / Android Gradle Plugin 9.1+

### Локальная сборка:
```bash
# Клонировать репозиторий
git clone https://github.com/your-username/AWGMutator.git
cd AWGMutator

# Собрать Debug APK
./gradlew assembleDebug

# Готовый файл находится в:
# app/build/outputs/apk/debug/app-debug.apk
```

### Автоматическая сборка в GitHub Actions (CI/CD):
Репозиторий включает настроенные workflows:
- **`android_build.yml`**: Автоматически запускается на каждый Push и Pull Request, прогоняет тесты, собирает APK и выгружает готовый артефакт.
- **`build_release.yml`**: Создает официальный GitHub Release с прикрепленным APK при создании тега `v*`.

---

## 🔒 Безопасность и конфиденциальность / Privacy & Security

- 🛡️ **Zero Logs**: Приложение не собирает персональные данные, историю посещений и сетевой трафик.
- 🔑 **On-Device Storage**: Все приватные ключи и конфигурации хранятся исключительно в зашифрованной песочнице приложения на вашем устройстве.
- 🌐 **Open Source**: Код полностью открыт для аудита сообществом.

---

## 📄 Лицензия / License

Распространяется под лицензией **MIT License**. Подробности в файле `LICENSE`.
