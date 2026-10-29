# Security & Privacy Policy

## 1. Security Architecture & Threat Model

Teleflair is designed from the ground up as a **privacy-first Android keyboard**. Key security properties include:

### 1.1 Strict Least-Privilege Permissions
- Teleflair requests **zero invasive permissions**:
  - `READ_SMS` / `RECEIVE_SMS`: **NOT REQUESTED**.
  - `READ_CONTACTS`: **NOT REQUESTED**.
  - `ACCESS_FINE_LOCATION` / `COARSE_LOCATION`: **NOT REQUESTED**.
  - `CAMERA`: Used strictly for local real-time Animoji rendering via CameraX; raw image frames never leave the device.
  - `REQUEST_INSTALL_PACKAGES`: Required strictly for seamless in-app self-updating via the Android PackageInstaller.

### 1.2 Dual-Service Isolation
Android Accessibility services run in an isolated process lifecycle separate from the primary keyboard input method:
- The **Keyboard IME Service** (`TeleboardInputMethodService`) handles typing, touch impulses, and sticker dispatch.
- The **Context Scanner** (`ContextScannerService`) only inspects field types to determine whether the target input supports image pasting or requires fallback.

### 1.3 Local Sandboxed Storage
- Sticker images, cached packs, and usage logs are stored strictly inside the application's private sandbox (`/data/data/com.teleboard.teleboard/databases/teleboard.db`).
- No clipboard scraping or background keystroke logging is implemented.

### 1.4 Cloudflare Worker Edge Security
- **Path Traversal Protection**: Telegram file download proxy verifies strict path conventions, blocking traversal sequences (`..`).
- **Secrets Management**: Admin access credentials (`ADMIN_SECRET`) and Telegram Bot tokens (`TELEGRAM_BOT_TOKEN`) are stored strictly as encrypted Cloudflare environment secrets.
- **Payload Limits**: Upload payloads are limited to 5 MB with strict MIME-type whitelisting (`image/webp`, `image/png`, `image/jpeg`, `image/gif`).

---

## 2. Reporting a Vulnerability

If you discover a security vulnerability within Teleflair:
1. Please do **NOT** disclose the vulnerability publicly in open GitHub issues.
2. Send a report via email to **samtes64@gmail.com** or open a private security advisory on GitHub.
3. Include detailed steps to reproduce the issue and any relevant crash logs or network traces.
4. I aim to acknowledge reports within 48 hours and publish fixes promptly.

---

## 3. Supported Versions

| Version | Supported | Notes |
|---|---|---|
| 1.0.x | Yes | Current stable release with active patches |
| < 1.0.0 | No | Legacy prototype builds |
