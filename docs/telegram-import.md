# Telegram Sticker Pack Import System

Teleboard allows importing public Telegram sticker packs directly into Android local storage, making them available offline anywhere on the device.

---

## 1. Supported Link Formats

Teleboard accepts the following link and identifier formats:
1. `https://t.me/addstickers/<pack_name>`
2. `http://t.me/addstickers/<pack_name>`
3. `t.me/addstickers/<pack_name>`
4. `tg://addstickers?set=<pack_name>`
5. Raw pack identifier (e.g., `Animals_Stickers_2024`)

Input strings are sanitized and validated against Telegram's canonical identifier regex (`^[a-zA-Z0-9_]{3,64}$`). Arbitrary URLs and malformed slugs are strictly rejected.

---

## 2. Telegram API Integration Models

### Model A: Telegram Bot API (Direct & Primary)
- **Endpoint**: `https://api.telegram.org/bot<token>/getStickerSet?name=<pack_name>`
- **Asset resolution**: `https://api.telegram.org/bot<token>/getFile?file_id=<file_id>`
- **Asset download**: `https://api.telegram.org/file/bot<token>/<file_path>`
- **Authentication**: Official Telegram servers (`api.telegram.org`) strictly require an HTTP API Bot Token to fetch public sticker sets and download binary files.
- **Obtaining a Token**:
  1. Open Telegram and search for `@BotFather`.
  2. Send `/newbot` and follow the two prompts to name your bot.
  3. BotFather immediately provides an API token (e.g. `123456789:ABCdefGHIjklMNOpqrSTUvwxYZ`).
  4. Paste this token into Teleboard (via the **Configure Bot Token** prompt on the Import screen or in **Settings**).
- **Concurrency & Resilience**: Downloads execute with a 4-worker concurrent pool, preserving index order, computing SHA-256 integrity checksums, and handling static WebP stickers as well as animated/video stickers with static WebP thumbnail fallbacks.
- **Strict Real Functionality (Non-negotiable Principle 2.1)**: Teleboard never fabricates placeholder shapes for Telegram sticker imports. If a token is not yet configured, the user is guided with step-by-step instructions.

### Model B: Cloudflare Edge Proxy (Built-in High-Speed Default)
- Requests route to `GET /api/stickers/pack?name=<pack_name>` hosted on the Cloudflare Worker backend.
- The Worker queries Telegram's Bot API using the `TELEGRAM_BOT_TOKEN` configured in worker secrets (`wrangler secret put TELEGRAM_BOT_TOKEN`), or forwards an optional client token.
- Returns verified metadata and direct CDN asset download links to the client without exposing the server bot token.

---

## 3. Supported Sticker Formats & Conversion Pipeline

| Format | Native Extension | Primary Handling | Fallback Transport |
|---|---|---|---|
| **Static WebP** | `.webp` | Native Android `BitmapFactory` & Flutter `Image.file` | Direct insertion via `commitContent` |
| **Animated TGS** | `.tgs` | Gzip decompression to Lottie vector JSON | First-frame raster extraction for non-Lottie destinations |
| **Video WebM** | `.webm` | VP9 video decoder | Static keyframe extraction |

---

## 4. Local Storage and Data Integrity

1. **Filesystem Hierarchy**:
   Assets are stored in the application's private sandbox:
   `/data/user/0/com.teleboard.teleboard/files/stickers/<pack_id>/sticker_<index>.webp`
2. **Metadata Store**:
   Indexed in SQLite (`teleboard.db`) with SHA-256 content hashes, dimensions, file sizes, and emoji tags.
3. **Cascade Deletion**:
   Removing a pack safely deletes file assets from disk and cleans up foreign keys in `favorites` and `recents` within an atomic SQLite transaction.
4. **Offline Resilience**:
   Once imported, all sticker assets and database indices are 100% self-contained. The keyboard makes zero network calls during ordinary sticker browsing.
