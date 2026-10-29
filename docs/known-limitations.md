# Known Limitations & Platform Constraints

In accordance with core engineering principles, this document honestly outlines technical boundaries, carrier restrictions, and platform limitations.

---

## 1. Android Input Method Boundaries

1. **Third-Party App Transport Autonomy**:
   - An Android keyboard is an `InputMethodService`, not a messaging client. It cannot dictate whether WhatsApp, Google Messages, or Signal sends an inserted image via RCS, MMS, cellular data, or proprietary cloud protocols.
   - Once content is handed off via `InputConnectionCompat.commitContent` or `Intent.ACTION_SEND`, the receiving messaging application owns the transmission pipeline.
2. **WebViews & Non-Standard Input Fields**:
   - Some applications (notably older hybrid apps or text-only terminals) do not declare supported image MIME types in `EditorInfo.contentMimeTypes`. In such fields, `commitContent` will fail or return false. Teleboard automatically cascades to Intent Handoff or Text-Art silhouette insertion.
3. **Password & Secure Input Fields**:
   - Android explicitly hides sticker surfaces and third-party media tools when typing in fields flagged with `TYPE_TEXT_VARIATION_PASSWORD` or `TYPE_NUMBER_VARIATION_PASSWORD`. This is an operating system security requirement and cannot be bypassed.

---

## 2. Cellular & SMS Transport Constraints

1. **Carrier Concatenation & SMS Costs**:
   - Multipart SMS transmission (`TELEBOARD_V1`) splits binary stickers into 120-byte chunks. A compressed 3 KB sticker requires ~25 SMS segments. On carrier plans without unlimited SMS, sending binary data over SMS can incur carrier charges.
   - For this reason, Teleboard does **not** send multipart SMS automatically in the background without explicit user confirmation.
2. **Google Play SMS Role Restrictions**:
   - Google Play strictly regulates `READ_SMS`, `RECEIVE_SMS`, and `SEND_SMS` permissions, restricting them only to apps designated as the user's default SMS client.
   - Teleboard intentionally uses `Intent.ACTION_SENDTO` (with `smsto:` URI) rather than claiming default SMS handler status, preserving a clean security posture and ensuring universal device compatibility.

---

## 3. Telegram Sticker Formats

1. **Animated TGS Stickers (Lottie Gzip)**:
   - Telegram's `.tgs` format is gzip-compressed Lottie vector JSON. While internal renderers can display the vector animation, messaging apps like WhatsApp or SMS composers only accept raster images (`image/webp`, `image/png`). For these targets, Teleboard extracts the initial raster keyframe.
2. **Video Stickers (WebM VP9)**:
   - Video stickers require hardware VP9 decoding. On older low-end devices, video sticker preview generation requires keyframe downsampling to conserve memory.

---

## 4. Cloudflare Infrastructure

1. **Free-Tier Invocations**:
   - Cloudflare Workers Free Tier provides 100,000 requests per day. Exceeding this limit returns HTTP 429 until the daily reset. Teleboard does not silently upgrade to paid tiers; the client gracefully cascades to Text-Art or local Intent Handoff.
2. **Ephemeral Retention**:
   - Stickers stored in Cloudflare R2 default to 24-hour expiration. Once purged, links permanently return an expired banner.
