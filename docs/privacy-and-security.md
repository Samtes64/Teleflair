# Privacy & Application Security Architecture

Keyboards are the most sensitive surface in any operating system because they process private messages, credentials, and search queries. Teleboard is engineered with a strict **Privacy-First** architecture.

---

## 1. Core Privacy Guarantees

### 1.1 Zero Keylogging Policy
- **No Keystroke Logging**: The native input method service (`TeleboardInputMethodService`) dispatches key events directly to `InputConnection.commitText()`. No text buffer is ever saved to disk or transmitted across network interfaces.
- **No Analytics or Trackers**: The application contains **zero** third-party telemetry, tracking SDKs, or advertising libraries.
- **Offline Typing**: Ordinary typing and sticker browsing do not require, use, or trigger network requests.

### 1.2 Sensitive Field Isolation
- When the active `EditorInfo` indicates password fields (`TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`, `TYPE_NUMBER_VARIATION_PASSWORD`), Teleboard automatically:
  1. Hides the sticker picker surface.
  2. Ensures the standard QWERTY keyboard is active.
  3. Disables predictive text and clipboard history triggers.

### 1.3 Minimalist Android Permissions
| Permission | Declared? | Justification |
|---|---|---|
| `INTERNET` | Yes | Required to download imported Telegram sticker packs and upload ephemeral image links to Cloudflare. |
| `ACCESS_NETWORK_STATE` | Yes | Checks network reachability before attempting pack imports or link uploads. |
| `READ_SMS` | **NO** | Restricted permission. Teleboard does **not** read private incoming SMS messages. |
| `RECEIVE_SMS` | **NO** | Teleboard does **not** register a background SMS broadcast receiver. |
| `SEND_SMS` | **NO** | Uses standard Android `Intent.ACTION_SENDTO` handoffs, requiring explicit user review in the default SMS app. |
| `READ_CONTACTS` | **NO** | No contact address book access required or requested. |

---

## 2. Cloudflare Ephemeral Storage Security

When stickers are delivered via Mode 3 (Cloudflare Ephemeral Link):
1. **Cryptographically Secure Tokens**:
   Public URLs use high-entropy random tokens generated using `crypto.getRandomValues(32)`. Sequential integer IDs or predictable slugs are never used.
2. **Strict Expiration**:
   Links expire automatically (default 24 hours, maximum 7 days). Expired requests return an explicit expiration card and will not reveal deleted image bytes.
3. **Payload Sanitization**:
   Uploads are validated for maximum payload size (5 MB) and MIME types (`image/webp`, `image/png`, `image/gif`, `image/jpeg`). Arbitrary binary or executable uploads are rejected with HTTP 400.
4. **No Identifiable Information in URLs**:
   Generated URLs do not contain phone numbers, Telegram handles, device IDs, or IP metadata.

---

## 3. Local Data Model Security

- **Storage Location**: Stored inside the application's private internal storage (`/data/user/0/com.teleboard.teleboard/`).
- **FileProvider Isolation**: Stickers shared with external messaging applications are scoped strictly through Android's `FileProvider` with temporary `FLAG_GRANT_READ_URI_PERMISSION`. Other applications cannot browse or read unshared assets.
