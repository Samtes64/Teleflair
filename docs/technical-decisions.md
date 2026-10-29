# Technical Decisions

This document records the architectural and engineering decisions for **Teleboard**, the minimalist Android keyboard with Telegram stickers and universal smart delivery.

---

## 1. Hybrid Architecture: Native Kotlin IME + Flutter Companion Application

### Decision
Teleflair uses a **hybrid architecture**:
- **Native Android IME (`TeleboardInputMethodService`)**: Implemented in Kotlin using Android's native `InputMethodService` and native View hierarchies.
- **Flutter Companion Application**: Implemented in Flutter for Telegram pack management, sticker browsing, delivery diagnostics, text-art studio, and settings.
- **Shared Local Persistence**: Both layers share the exact same local SQLite database (`teleboard.db`) and asset directory (`context.filesDir/stickers/`), operating inside the same Linux user sandbox.

### Rationale
- **Zero Input Latency**: Embedding a full `FlutterEngine` inside an `InputMethodService` window introduces 400–800ms of startup latency on first keyboard display, consumes 80MB+ of heap, and causes window detachment crashes during rapid keyboard hide/show events.
- **Input Reliability**: An Android soft keyboard must respond instantaneously to key presses and never block the main UI thread. Native Kotlin views guarantee immediate tactile feedback, zero ANRs, and rock-solid IME lifecycle handling.
- **Rich Content Insertion (`commitContent`)**: Calling Android's `InputConnectionCompat.commitContent` and managing `FileProvider` URI temporary read permissions requires deep, direct access to Android's `InputConnection` and `EditorInfo`, which is most reliable when executed natively.
- **Rich UI for Companion App**: Flutter provides a world-class environment for pack importing, searching, visual configuration, and cross-platform fidelity for the management experience.

---

## 2. Shared Data Model & Offline-First Persistence

### Decision
A single SQLite database (`teleboard.db`) is the authoritative metadata store, accompanied by local filesystem storage for sticker assets (`/data/user/0/com.teleboard.teleboard/files/stickers/`).

### Database Schema
- `sticker_packs`: `id`, `name`, `title`, `source_type`, `total_stickers`, `installed_at`, `is_active`
- `stickers`: `id`, `pack_id`, `file_id`, `emoji`, `file_path`, `width`, `height`, `file_size`, `mime_type`, `is_animated`, `content_hash`, `added_at`
- `favorites`: `sticker_id`, `added_at`
- `recents`: `sticker_id`, `used_at`, `use_count`
- `delivery_logs`: `id`, `sticker_id`, `transport_used`, `status`, `target_package`, `timestamp`, `details`

### Rationale
- Zero duplication: The native IME directly queries SQLite using standard Android database helpers or ContentProvider, while the Flutter app queries SQLite using `sqflite`.
- Once imported, stickers are stored locally and accessible indefinitely without network connectivity.

---

## 3. Telegram Sticker Import & API Strategy

### Decision
Teleflair supports public Telegram sticker pack links in the form:
- `https://t.me/addstickers/<pack_name>`
- `t.me/addstickers/<pack_name>`
- Raw `<pack_name>` identifier

### Implementation Strategy
1. **Telegram Bot API**: Utilizes `getStickerSet` and `getFile` endpoints. In production, requests can be routed through the Cloudflare Worker backend or through a user-provided bot token in settings. Privileged tokens are never embedded into the client code.
2. **Bundled Starter Packs**: The app includes bundled starter sticker packs in local assets so the keyboard is immediately functional offline upon installation.
3. **Format Support**:
   - **Static WebP**: Fully supported natively across Android `BitmapFactory` and Flutter `Image.file`.
   - **TGS (Gzip Lottie)**: Gzip decompression to Lottie JSON vector format, with static frame rendering for messaging apps that only accept raster images.
   - **Transparency**: Fully preserved (alpha channel retained for sticker silhouettes).

---

## 4. Smart Delivery Engine Priority and Fallback Pipeline

### Decision
A single user action: **`Send Sticker`**. The delivery coordinator selects the best supported transport in strict priority order:

1. **Direct Rich-Content Insertion (`commitContent`)**:
   - Checks `EditorInfoCompat.getContentMimeTypes(editorInfo)`.
   - If target app (e.g., WhatsApp, Telegram, Signal, Google Messages) accepts `image/webp`, `image/png`, or `image/gif`, commits the sticker directly using `InputConnectionCompat.commitContent` with temporary `FLAG_GRANT_READ_URI_PERMISSION` from `FileProvider`.
2. **Messaging Intent / Android Share Sheet Handoff**:
   - If `commitContent` is unsupported or rejected by the input field, the coordinator hands off the sticker file via `Intent.ACTION_SEND` with `image/*` MIME type directly to the foreground messaging app or system picker.
3. **Cloudflare-Hosted Image Link (SMS Handoff)**:
   - When sending to an SMS conversation or non-rich app, the image is uploaded to Cloudflare R2, generating an unguessable expiring URL (`https://<domain>/s/<token>`), which is placed in the SMS composer via `Intent.ACTION_SENDTO`.
4. **SMS-Encoded Sticker Protocol (`TELEBOARD_V1`)**:
   - For compatible recipients, chunks compressed image data across multipart SMS with CRC32 checksums.
5. **Text-Art Fallback**:
   - When image delivery is rejected or unavailable, downsamples the sticker and generates a recognizable monochrome ASCII silhouette, inserting it directly into the text field.

---

## 5. Cloudflare Free-Tier Storage & Backend

### Decision
- **Cloudflare Workers**: Serverless execution using modern TypeScript.
- **Cloudflare R2**: S3-compatible zero-egress object storage for sticker images.
- **Cryptographic Tokens**: High-entropy 32-character URL-safe random tokens.
- **Link Expiration**: Default 24 hours (configurable up to 7 days), with automatic expiration enforcement.
- **Recipient Experience**: Clean, responsive HTML page with mobile dark/light mode and transparent checkerboard display, plus direct raw image streaming for embedded clients.

---

## 6. SMS Codec (`TELEBOARD_V1`) Protocol Design

### Decision
```text
Envelope: TB1:<transfer_id>:<part_index>:<total_parts>:<crc32>:<base64url_chunk>
```
- Base64url payload with CRC32 integrity verification.
- Chunk sizes optimized for standard SMS concatenation (130-byte chunks).
- Full rejection of duplicate, malformed, or corrupted parts.

---

## 8. High-Performance Canvas-Rendered Keyboard UI & Ergonomics

### Decision
- **Single-Pass Custom Views**: Replaced nested `FrameLayout` and `TextView` hierarchies with custom canvas-rendered views (`CompoundKeyView`, `SpecialKeyView`, `ActionKeyView`).
- **Ergonomics & Sizing**: Increased keyboard height to **350dp** (giving 62dp row heights and 10dp bottom clearance for gesture navigation bars), with prominent **22sp** primary letters and **11sp** secondary symbol superscripts.
- **Hold-to-Type Symbols**: Long-pressing keycaps (300ms threshold) immediately commits their secondary symbol (`q` -> `1`, `w` -> `2`, `a` -> `@`, `m` -> `?`, etc.) matching modern keyboard expectations.
- **Rapid Zero-Latency Typing**: Custom single-pass drawing eliminates layout measurement overhead, `RippleDrawable` fade persistence, and view creation lag, delivering instantaneous 120Hz response on `ACTION_DOWN` with mechanical haptic tick.

---

## 9. Universal Messaging App Delivery Engine (FileProvider Sandbox & Clipboard)

### Decision
- **FileProvider Sandbox Resolution**:
  - Flutter document directory resolves to `/data/user/0/<package>/app_flutter/`, which is outside `context.filesDir` (`/data/user/0/<package>/files/`).
  - Standard `FileProvider.getUriForFile` previously threw an `IllegalArgumentException` on sticker paths.
  - Resolved by providing `<root-path name="root" path="." />` and `<cache-path name="cache" path="." />` in `file_paths.xml`, while staging/transcoding outbound share assets in `context.cacheDir/stickers/`.
- **Universal Clipboard & Intent Synchronous Fallback**:
  - For messaging apps that do not advertise `EditorInfoCompat.getContentMimeTypes` (e.g. Instagram, SMS apps), the engine copies the content URI directly to the Android system clipboard with `ClipData.newUri` and dispatches `ACTION_SEND` targeting the foreground package with `FLAG_GRANT_READ_URI_PERMISSION`.
  - When the target app rejects raw WebP, `SmartDeliveryCoordinator` dynamically transcodes the bitmap to PNG in cache.
  - Automatically displays clear Toast guidance (`"Sticker sent!"`, `"Attaching sticker..."`, or `"Sticker copied to clipboard! Tap Paste in chat."`) and closes the picker so the user immediately returns to conversation view.

