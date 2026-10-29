# Teleboard Setup & Installation Guide

This guide covers building, testing, installing, and enabling **Teleboard** on Android devices.

---

## 1. Prerequisites

### Mobile (Android & Flutter)
- **Flutter SDK**: `>= 3.20.0` (Dart `>= 3.3.0`)
- **Android SDK**: Build-Tools `34.0.0` or higher, Platform `android-34` or higher
- **JDK**: Java 17 (OpenJDK 17 recommended)
- **Android Gradle Plugin**: Supported AGP 8.x / 9.x with Gradle 8.x / 9.x

### Backend (Cloudflare Delivery)
- **Node.js**: `>= 20.0.0`
- **npm** or **bun** / **pnpm**
- **Wrangler**: `>= 3.100.0` (for Cloudflare Workers and R2 deployments)

---

## 2. Environment Configuration

### Backend Environment (`backend/.env.example`)
Create a `.env` in `backend/` if deploying or testing locally:
```env
ENVIRONMENT=production
DEFAULT_EXPIRATION_HOURS=24
MAX_EXPIRATION_HOURS=168
MAX_UPLOAD_SIZE_BYTES=5242880
CLOUDFLARE_ACCOUNT_ID=your_account_id_here
CLOUDFLARE_API_TOKEN=your_api_token_here
```

---

## 3. Building the Mobile Application

### Installing Dependencies
```bash
flutter pub get
```

### Running Automated Tests
```bash
# Run Flutter unit & widget tests
flutter test

# Run Android Kotlin unit tests
cd android && ./gradlew :app:testDebugUnitTest && cd ..
```

### Compiling Debug APK
```bash
flutter build apk --debug
```
Output artifact: `build/app/outputs/flutter-apk/app-debug.apk`

### Compiling Release APK
```bash
flutter build apk --release
```
Output artifact: `build/app/outputs/flutter-apk/app-release.apk`

---

## 4. Installing and Enabling the Keyboard

### Step 1: Install APK via ADB
```bash
adb install -r build/app/outputs/flutter-apk/app-debug.apk
```

### Step 2: Open Companion App
Launch the **Teleboard** app from your home screen.

### Step 3: Enable Keyboard
1. Navigate to the **Settings** tab (gear icon).
2. Tap **Enable Keyboard** to open Android System Input Method Settings.
3. Toggle **Teleboard Keyboard** ON. Android will display standard security warning for all custom input methods.

### Step 4: Select Active Keyboard
1. Return to Teleboard Settings and tap **Select Keyboard**.
2. Select **Teleboard Keyboard** from the input method picker.

### Step 5: Start Typing & Sending Stickers
Open any messaging application (WhatsApp, Google Messages, Telegram, Signal, Slack, Discord):
- Tap the text input field.
- Type using the minimalist QWERTY keyboard.
- Tap the **✦** sticker button to switch to the offline sticker library.
- Select any sticker and tap **`Send Sticker`**!
