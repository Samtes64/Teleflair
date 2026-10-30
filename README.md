# Teleflair

> **A privacy-first Android keyboard with high-performance Telegram sticker importing, universal Smart Delivery, and edge serverless sync.**

[![Download APK](https://img.shields.io/badge/Download_APK-Telegram_Channel-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/thesam_ple_resources/8)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Flutter](https://img.shields.io/badge/Flutter-3.24+-02569B?logo=flutter&logoColor=white)](https://flutter.dev)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Cloudflare Workers](https://img.shields.io/badge/Cloudflare-Workers-F38020?logo=cloudflare&logoColor=white)](https://workers.cloudflare.com)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

---

### 📥 Direct APK Download

Looking to install and use Teleflair right away without compiling from source?  
👉 **[Download the Latest Release APK Directly](https://t.me/thesam_ple_resources/8)** (Available on Telegram)

---

## 💡 About Teleflair

Teleflair is an open-source, privacy-first Android keyboard and companion suite that bridges Telegram's rich sticker library into every Android messaging application—including WhatsApp, Instagram, TikTok, Discord, Slack, SMS, and web browsers.

Most third-party keyboards log keystrokes, sell typing data, or require network permissions for basic typing. I designed Teleflair around a strict philosophy: **zero keystroke tracking**, **zero invasive permissions**, and an **offline-first local database**.

---

## 🎨 Note on UI & Community Design

> **I prioritized architecture, privacy, and low-latency input over visual polish.**
> 
> The current interface is intentionally clean and functional. I didn't invest heavy effort into complex UI decorations so that designers and front-end contributors have a clean canvas to make it better also! 
> 
> If you have ideas for themes (e.g. OLED pure black, Material You dynamic color matching, retro mechanical styles, iOS glassmorphism), customizable keycap shapes, or visual improvements, I would love your pull requests! See [CONTRIBUTING.md](CONTRIBUTING.md) to get involved.

---

## 🏛️ System Architecture

Teleflair uses a decoupled, hybrid architecture to guarantee zero-latency input while providing rich cross-platform configuration and edge serverless sync.

```text
+-----------------------------------------------------------------------------------+
|                              ANDROID CLIENT ARCHITECTURE                          |
|                                                                                   |
|  +-------------------------------------+   +------------------------------------+ |
|  |     Flutter Companion Application   |   |        Native Kotlin IME Engine    | |
|  |                                     |   |                                    | |
|  |  * Stickers Screen (Grid/Packs)     |   |  * TeleboardInputMethodService     | |
|  |  * Telegram Importer Screen         |   |  * KeyboardLayoutView (QWERTY/Sym) | |
|  |  * Settings & Sensory Configuration |   |  * StickerPickerView (Vector Grid) | |
|  |  * SQLite Bridge (sqflite)          |   |  * GlideTypingDetector (Catmull)   | |
|  |                                     |   |  * SmartDeliveryCoordinator        | |
|  |                                     |   |  * ContextScannerService (A11y)    | |
|  +------------------+------------------+   +------------------+-----------------+ |
|                     |                                         |                   |
|                     +--------------------+--------------------+                   |
|                                          |                                        |
|                                          v                                        |
|                    +------------------------------------------+                   |
|                    |   Local Sandboxed Device Storage         |                   |
|                    |   * SQLite Database: teleboard.db        |                   |
|                    |   * Vector & WebP Files: files/stickers/ |                   |
|                    +------------------------------------------+                   |
+------------------------------------------+----------------------------------------+
                                           |
                                           | HTTPS (SSRF-Protected Cloud Proxy)
                                           v
+-----------------------------------------------------------------------------------+
|                            CLOUDFLARE EDGE BACKEND                                |
|                                                                                   |
|  * Serverless Hono Framework Worker (Node.js runtime)                             |
|  * Telegram API Hardened Proxy (/api/stickers/file, /api/stickers/pack)           |
|  * Cloudflare R2 Object Storage for Ephemeral Delivery & Fallback Viewer (/s/:id) |
|  * Multi-Tab Central Command Admin Console (/admin)                               |
|  * OTA Release Engine & Install Telemetry (/api/app/latest)                       |
+-----------------------------------------------------------------------------------+
```

### Architectural Breakdown

1. **Native Android IME Layer (`TeleboardInputMethodService`)**:
   - Implemented in Kotlin using Android's native `InputMethodService` windowing system.
   - Bypasses the Flutter engine during active typing to achieve **0ms rendering latency** and zero ANRs (Application Not Responding).
   - Manages touch dispatch, particle physics simulation, and SoundPool audio feedback.

2. **Smart Delivery Coordinator (`SmartDeliveryCoordinator`)**:
   - **Mode 1 (Direct Rich Content)**: Directly commits the WebP sticker into supported chat inputs (WhatsApp, Telegram, Google Messages) via `commitContent` using temporary, single-use `FileProvider` URI read permissions.
   - **Mode 2 (Targeted Intent Handoff)**: Dispatches images via targeted Android `ACTION_SEND` intents.
   - **Mode 3 (Universal SMS Fallback)**: Formats stickers as Universal SMS payloads to avoid carrier MMS compression failures.
   - **Mode 4 (Context Art for Comment Sections)**: Converts stickers into platform-calibrated Unicode Braille (2×4 dot matrix) or ASCII art for Instagram, TikTok, and LinkedIn comments where image pasting is disabled.

3. **Dual-Service Process Isolation**:
   - The **Context Scanner** (`ContextScannerService`) runs as a detached Android Accessibility service, analyzing input field types without ever sharing memory or intercepting keystrokes from the IME service.

4. **Edge Serverless Backend (Cloudflare Workers + R2)**:
   - TypeScript worker utilizing Hono for high-performance edge routing.
   - Proxies Telegram sticker packs and binary files so that client applications never need privileged Telegram Bot tokens embedded in client binaries.
   - Enforces strict path-traversal validation, disallowing traversal sequences (`..`) and absolute paths.

---

## ⚡ Core Capabilities

- **Native Kotlin Input Method**: Zero-latency, highly optimized typing surface.
- **Apple-Inspired Monochrome Aesthetics**: Vector icon glyphs, zero system emojis in keyboard interface, and uncluttered typography.
- **Continuous Glide Typing**: Catmull-Rom spline smoothing and DTW gesture path recognition.
- **Interactive Sensory Feedback**: Accelerometer-driven physical key rebound impulses and SoundPool mechanical switch profiles.
- **Telegram Pack Importer**: Import any public sticker set from a URL or pack name in seconds.
- **OTA Self-Updating**: Integrated update checker that queries the backend for signed releases.

---

## 🔒 Security & Privacy by Design

- **Zero Invasive Android Permissions**:
  - `READ_SMS` / `RECEIVE_SMS`: **NEVER REQUESTED**
  - `READ_CONTACTS`: **NEVER REQUESTED**
  - `ACCESS_FINE_LOCATION`: **NEVER REQUESTED**
- **No Keystroke Logging**: The input method runs in an isolated local sandbox. Keystrokes are never recorded or transmitted.
- **No Third-Party Analytics Trackers**: No Google Analytics, no Facebook SDK, no advertising IDs.
- For vulnerability reporting and full threat model disclosures, see [SECURITY.md](SECURITY.md).

---

## 🚀 Step-by-Step Setup Guide

### Prerequisites
- [Flutter SDK](https://flutter.dev/docs/get-started/install) (v3.24.0 or newer)
- [Android Studio](https://developer.android.com/studio) with Android SDK (API Level 34+)
- [Node.js](https://nodejs.org/) (v20+ recommended)
- Java Development Kit (JDK 17)

---

### Step 1: Clone the Repository
```bash
git clone https://github.com/Samtes64/Teleflair.git
cd Teleflair
```

---

### Step 2: Build & Run the Android Application

1. **Install Flutter dependencies**:
   ```bash
   flutter pub get
   ```

2. **Run verification test suites**:
   ```bash
   flutter test
   ```

3. **Configure Local Release Signing (Optional)**:
   For local release builds, copy the template and configure your keystore credentials:
   ```bash
   cp key.properties.example key.properties
   ```
   *(If `key.properties` is omitted, Gradle will automatically build using local debug signing).*

4. **Build the optimized ARM64 APK**:
   ```bash
   flutter build apk --release --split-per-abi
   ```
   Your architecture-optimized APK will be generated at:
   `build/app/outputs/flutter-apk/app-arm64-v8a-release.apk` (~23.9 MB)

5. **Install on connected device via ADB**:
   ```bash
   adb install -r build/app/outputs/flutter-apk/app-arm64-v8a-release.apk
   ```

---

### Step 3: Set Up the Cloudflare Worker Backend

1. **Navigate to the backend directory**:
   ```bash
   cd backend
   npm install
   ```

2. **Run backend automated tests**:
   ```bash
   npm test
   ```

3. **Deploy to Cloudflare Workers**:
   ```bash
   npx wrangler deploy
   ```

4. **Configure Production Secrets**:
   Store your private credentials securely in Cloudflare's encrypted environment:
   ```bash
   # 1. Set your secure admin console passphrase
   npx wrangler secret put ADMIN_SECRET

   # 2. Set your Telegram Bot API token
   npx wrangler secret put TELEGRAM_BOT_TOKEN
   ```

5. **Access the Admin Console**:
   Visit `https://<your-worker-name>.workers.dev/admin` and log in with your configured `ADMIN_SECRET`.

---

## 📁 Repository Structure

```text
Teleflair/
├── android/                 # Native Android layer (Kotlin InputMethodService)
│   ├── app/src/main/kotlin/com/teleboard/teleboard/
│   │   ├── MainActivity.kt
│   │   └── keyboard/
│   │       ├── TeleboardInputMethodService.kt  # Core Keyboard IME Service
│   │       ├── KeyboardLayoutView.kt           # QWERTY/Sym touch surface
│   │       ├── StickerPickerView.kt            # High-speed sticker selector
│   │       ├── SmartDeliveryCoordinator.kt     # Multi-mode dispatch logic
│   │       └── TeleboardDatabaseHelper.kt      # Sandboxed SQLite storage
│   └── app/proguard-rules.pro                  # R8 optimization rules
├── backend/                 # Cloudflare Worker & R2 backend (TypeScript + Hono)
│   ├── src/
│   │   ├── index.ts         # Edge routing & API endpoints
│   │   ├── storage.ts       # Cloudflare R2 & in-memory drivers
│   │   ├── telemetry.ts     # Privacy-first install telemetry
│   │   └── admin_html.ts    # Multi-tab admin command center
│   └── tests/api.test.ts    # Comprehensive API unit tests
├── docs/                    # Architectural documentation
│   ├── architecture.md
│   ├── delivery-system.md
│   ├── telegram-import.md
│   ├── privacy-and-security.md
│   └── testing.md
├── lib/                     # Companion Flutter application
│   ├── app/                 # Theme tokens & 3-tab navigation bar
│   ├── core/                # Platform bridge, updater & SQLite
│   └── features/            # Stickers, Import & Settings features
├── test/                    # Flutter unit & widget tests
├── CONTRIBUTING.md          # Open-source contribution guidelines
├── LICENSE                  # MIT License
└── SECURITY.md              # Security model & vulnerability policy
```

---

## 🤝 Contributing

I welcome issues, feature suggestions, and pull requests! Please check out [CONTRIBUTING.md](CONTRIBUTING.md) for local development workflows and code conventions.

---

## 📬 Contact & Support

For security inquiries or questions, contact:
- **Email**: [samtes64@gmail.com](mailto:samtes64@gmail.com)
- **GitHub**: [@Samtes64](https://github.com/Samtes64)

---

## 📜 License

Teleflair is licensed under the [MIT License](LICENSE).
