import { Env, StickerMetadata } from './types.js';

export function generateSecureToken(byteLength = 24): string {
  const bytes = new Uint8Array(byteLength);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
}

export async function computeSha256(data: ArrayBuffer): Promise<string> {
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

export interface IStickerStorage {
  put(key: string, data: ArrayBuffer, metadata: StickerMetadata): Promise<void>;
  get(key: string): Promise<{ data: ArrayBuffer; metadata: StickerMetadata } | null>;
  delete(key: string): Promise<void>;
  listStickers(limit?: number): Promise<StickerMetadata[]>;
  purgeExpired(): Promise<number>;
}

export class MemoryStickerStorage implements IStickerStorage {
  private store = new Map<string, { data: ArrayBuffer; metadata: StickerMetadata }>();

  async put(key: string, data: ArrayBuffer, metadata: StickerMetadata): Promise<void> {
    this.store.set(key, { data, metadata });
  }

  async get(key: string): Promise<{ data: ArrayBuffer; metadata: StickerMetadata } | null> {
    const item = this.store.get(key);
    if (!item) return null;
    if (Date.now() > item.metadata.expiresAt) {
      this.store.delete(key);
      return null;
    }
    return item;
  }

  async delete(key: string): Promise<void> {
    this.store.delete(key);
  }

  async listStickers(limit = 50): Promise<StickerMetadata[]> {
    const list: StickerMetadata[] = [];
    const now = Date.now();
    for (const [, item] of this.store.entries()) {
      if (now <= item.metadata.expiresAt) {
        list.push(item.metadata);
      }
      if (list.length >= limit) break;
    }
    return list;
  }

  async purgeExpired(): Promise<number> {
    const now = Date.now();
    let count = 0;
    for (const [key, item] of this.store.entries()) {
      if (now > item.metadata.expiresAt) {
        this.store.delete(key);
        count++;
      }
    }
    return count;
  }
}

export class CloudflareR2Storage implements IStickerStorage {
  constructor(private bucket: R2Bucket) {}

  async put(key: string, data: ArrayBuffer, metadata: StickerMetadata): Promise<void> {
    await this.bucket.put(key, data, {
      httpMetadata: {
        contentType: metadata.mimeType,
      },
      customMetadata: {
        token: metadata.token,
        mimeType: metadata.mimeType,
        sizeBytes: metadata.sizeBytes.toString(),
        contentHash: metadata.contentHash,
        createdAt: metadata.createdAt.toString(),
        expiresAt: metadata.expiresAt.toString(),
      },
    });
  }

  async get(key: string): Promise<{ data: ArrayBuffer; metadata: StickerMetadata } | null> {
    const object = await this.bucket.get(key);
    if (!object) return null;

    const custom = object.customMetadata || {};
    const expiresAt = parseInt(custom.expiresAt || '0', 10);

    if (Date.now() > expiresAt) {
      // Lazy deletion of expired object
      await this.bucket.delete(key);
      return null;
    }

    const data = await object.arrayBuffer();
    const metadata: StickerMetadata = {
      token: custom.token || key,
      key,
      mimeType: custom.mimeType || 'image/webp',
      sizeBytes: parseInt(custom.sizeBytes || '0', 10),
      contentHash: custom.contentHash || '',
      createdAt: parseInt(custom.createdAt || '0', 10),
      expiresAt,
    };

    return { data, metadata };
  }

  async delete(key: string): Promise<void> {
    await this.bucket.delete(key);
  }

  async listStickers(limit = 50): Promise<StickerMetadata[]> {
    const objects = await this.bucket.list({ prefix: 'stickers/', limit });
    const list: StickerMetadata[] = [];
    const now = Date.now();
    for (const obj of objects.objects) {
      const custom = obj.customMetadata || {};
      const expiresAt = parseInt(custom.expiresAt || '0', 10);
      if (expiresAt === 0 || now <= expiresAt) {
        list.push({
          token: custom.token || obj.key.replace('stickers/', ''),
          key: obj.key,
          mimeType: custom.mimeType || 'image/webp',
          sizeBytes: parseInt(custom.sizeBytes || obj.size.toString(), 10),
          contentHash: custom.contentHash || '',
          createdAt: parseInt(custom.createdAt || '0', 10),
          expiresAt,
        });
      }
    }
    return list;
  }

  async purgeExpired(): Promise<number> {
    const objects = await this.bucket.list({ prefix: 'stickers/' });
    const now = Date.now();
    let count = 0;
    for (const obj of objects.objects) {
      const custom = obj.customMetadata || {};
      const expiresAt = parseInt(custom.expiresAt || '0', 10);
      if (expiresAt > 0 && now > expiresAt) {
        await this.bucket.delete(obj.key);
        count++;
      }
    }
    return count;
  }
}

export function getStorage(env: Env): IStickerStorage {
  if (env.STICKER_BUCKET) {
    return new CloudflareR2Storage(env.STICKER_BUCKET);
  }
  // Global memory storage fallback for development / testing
  return memoryStorageInstance;
}

export const memoryStorageInstance = new MemoryStickerStorage();

export function renderViewerHtml(options: {
  token: string;
  rawUrl: string;
  expired?: boolean;
  mimeType?: string;
  title?: string;
}): string {
  const {
    token,
    rawUrl,
    expired = false,
    mimeType = 'image/webp',
    title = 'Teleboard Sticker',
  } = options;

  if (expired) {
    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Sticker Expired • Teleboard</title>
  <style>
    :root {
      --bg: #09090b;
      --card-bg: #18181b;
      --border: #27272a;
      --text: #f4f4f5;
      --text-muted: #a1a1aa;
    }
    body {
      margin: 0;
      padding: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background: var(--bg);
      color: var(--text);
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
    }
    .card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 20px;
      padding: 32px;
      max-width: 360px;
      width: 85%;
      text-align: center;
      box-shadow: 0 10px 30px rgba(0,0,0,0.5);
    }
    .icon {
      font-size: 40px;
      margin-bottom: 16px;
    }
    h1 {
      font-size: 1.25rem;
      font-weight: 600;
      margin: 0 0 8px 0;
      letter-spacing: -0.01em;
    }
    p {
      color: var(--text-muted);
      font-size: 0.9rem;
      line-height: 1.4;
      margin: 0;
    }
    .brand {
      margin-top: 24px;
      font-size: 0.75rem;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--text-muted);
      opacity: 0.6;
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="icon">⏳</div>
    <h1>Sticker Link Expired</h1>
    <p>This sticker link was set to automatically expire and is no longer available.</p>
    <div class="brand">Teleboard Privacy Delivery</div>
  </div>
</body>
</html>`;
  }

  const pageUrl = rawUrl.replace('?raw=true', '');

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title}</title>

  <!-- Primary Metadata -->
  <meta name="title" content="${title}">
  <meta name="description" content="View sticker shared via Teleboard Smart Delivery">

  <!-- Open Graph / WhatsApp / Google Messages / iMessage / Telegram / Signal / Facebook -->
  <meta property="og:type" content="website">
  <meta property="og:url" content="${pageUrl}">
  <meta property="og:title" content="${title}">
  <meta property="og:description" content="View sticker shared via Teleboard">
  <meta property="og:image" content="${rawUrl}">
  <meta property="og:image:secure_url" content="${rawUrl}">
  <meta property="og:image:type" content="${mimeType}">
  <meta property="og:image:width" content="512">
  <meta property="og:image:height" content="512">
  <meta property="og:image:alt" content="${title}">

  <!-- Twitter / X Cards -->
  <meta name="twitter:card" content="summary_large_image">
  <meta name="twitter:url" content="${pageUrl}">
  <meta name="twitter:title" content="${title}">
  <meta name="twitter:description" content="View sticker shared via Teleboard">
  <meta name="twitter:image" content="${rawUrl}">
  <meta name="twitter:image:alt" content="${title}">

  <style>
    :root {
      --bg: #09090b;
      --card-bg: #18181b;
      --border: #27272a;
      --text: #f4f4f5;
      --text-muted: #a1a1aa;
    }
    @media (prefers-color-scheme: light) {
      :root {
        --bg: #f4f4f5;
        --card-bg: #ffffff;
        --border: #e4e4e7;
        --text: #09090b;
        --text-muted: #71717a;
      }
    }
    body {
      margin: 0;
      padding: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background: var(--bg);
      color: var(--text);
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
    }
    .card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 24px;
      padding: 32px 24px 24px 24px;
      max-width: 380px;
      width: 85%;
      text-align: center;
      box-shadow: 0 12px 36px rgba(0,0,0,0.25);
    }
    .sticker-container {
      width: 256px;
      height: 256px;
      margin: 0 auto 20px auto;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 16px;
      /* Subtle transparency checkerboard */
      background-image: linear-gradient(45deg, rgba(128,128,128,0.1) 25%, transparent 25%), 
                        linear-gradient(-45deg, rgba(128,128,128,0.1) 25%, transparent 25%), 
                        linear-gradient(45deg, transparent 75%, rgba(128,128,128,0.1) 75%), 
                        linear-gradient(-45deg, transparent 75%, rgba(128,128,128,0.1) 75%);
      background-size: 16px 16px;
      background-position: 0 0, 0 8px, 8px -8px, -8px 0px;
    }
    .sticker-img {
      max-width: 90%;
      max-height: 90%;
      object-fit: contain;
      filter: drop-shadow(0 4px 12px rgba(0,0,0,0.15));
    }
    .caption {
      font-size: 0.85rem;
      color: var(--text-muted);
      margin-bottom: 20px;
    }
    .btn {
      display: inline-block;
      padding: 10px 20px;
      border-radius: 9999px;
      background: var(--text);
      color: var(--bg);
      text-decoration: none;
      font-size: 0.85rem;
      font-weight: 500;
      transition: opacity 0.2s;
    }
    .btn:hover {
      opacity: 0.85;
    }
    .footer {
      margin-top: 24px;
      font-size: 0.72rem;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      color: var(--text-muted);
    }
  </style>
</head>
<body>
  <div class="card">
    <div class="sticker-container">
      <img src="${rawUrl}" alt="Sticker" class="sticker-img" />
    </div>
    <div class="caption">Shared via Teleboard Smart Delivery</div>
    <a href="${rawUrl}" download="sticker.webp" class="btn">Save Sticker</a>
    <div class="footer">Privacy-First Ephemeral Delivery</div>
  </div>
</body>
</html>`;
}
