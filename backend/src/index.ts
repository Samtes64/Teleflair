import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { getCookie, setCookie, deleteCookie } from 'hono/cookie';
import { Env, InstallEvent, StickerMetadata, UploadResponse, AppReleaseConfig } from './types.js';
import { computeSha256, generateSecureToken, getStorage, renderViewerHtml } from './storage.js';
import { getTelemetryStorage, getAppRelease, updateAppRelease } from './telemetry.js';
import { renderAdminDashboardHtml, renderAdminLoginHtml } from './admin_html.js';

export const app = new Hono<{ Bindings: Env }>();

// Helper to authenticate admin console and API requests
function verifyAdminAuth(c: any): boolean {
  const adminSecret = c.env.ADMIN_SECRET || 'teleflair_admin_2026';

  // 1. Query parameter
  const queryKey = c.req.query('key') || c.req.query('secret') || c.req.query('token');
  if (queryKey === adminSecret) return true;

  // 2. HTTP Headers
  const authHeader = c.req.header('authorization') || '';
  if (authHeader.startsWith('Bearer ') && authHeader.slice(7) === adminSecret) return true;
  if (c.req.header('x-admin-key') === adminSecret) return true;

  // 3. Cookie
  const cookieVal = getCookie(c, 'teleflair_admin_auth');
  if (cookieVal === adminSecret) return true;

  return false;
}

// Enable CORS for mobile app requests
app.use('*', cors());

// Health check endpoint
app.get('/api/health', (c) => {
  return c.json({
    status: 'ok',
    service: 'teleboard-delivery-api',
    version: '1.0.0',
    environment: c.env.ENVIRONMENT || 'production',
    timestamp: new Date().toISOString(),
  });
});

// Proxy endpoint to query Telegram public sticker sets using backend bot token
app.get('/api/stickers/pack', async (c) => {
  const packName = c.req.query('name');
  if (!packName || !/^[a-zA-Z0-9_]{3,64}$/.test(packName)) {
    return c.json({ success: false, error: 'Invalid or missing pack name parameter' }, 400);
  }

  const token =
    c.req.query('botToken') ||
    c.req.header('x-telegram-bot-token') ||
    c.env.TELEGRAM_BOT_TOKEN;

  if (!token) {
    return c.json(
      {
        success: false,
        error:
          'TELEGRAM_BOT_TOKEN is not configured on this Cloudflare Worker. Add it using: npx wrangler secret put TELEGRAM_BOT_TOKEN or supply botToken parameter.',
      },
      503
    );
  }

  try {
    const tgRes = await fetch(
      `https://api.telegram.org/bot${token}/getStickerSet?name=${packName}`
    );
    if (!tgRes.ok) {
      if (tgRes.status === 429) {
        const retryAfter = tgRes.headers.get('retry-after');
        return c.json(
          {
            success: false,
            isRateLimit: true,
            error:
              'Telegram rate limit reached (HTTP 429). The shared community bot is experiencing high traffic.',
            retryAfterSeconds: retryAfter ? parseInt(retryAfter, 10) : 30,
          },
          429
        );
      }
      return c.json(
        { success: false, error: `Telegram API error: HTTP ${tgRes.status}` },
        tgRes.status as any
      );
    }

    const tgData = (await tgRes.json()) as any;
    if (!tgData.ok || !tgData.result) {
      return c.json(
        { success: false, error: tgData.description || 'Sticker set not found on Telegram' },
        404
      );
    }

    const result = tgData.result;
    const stickers = result.stickers || [];
    const requestUrl = new URL(c.req.url);
    const baseUrl = `${requestUrl.protocol}//${requestUrl.host}`;

    // Fetch file paths concurrently with limit
    const formattedStickers = (
      await Promise.all(
        stickers.map(async (s: any) => {
          try {
            const fileRes = await fetch(
              `https://api.telegram.org/bot${token}/getFile?file_id=${s.file_id}`
            );
            if (!fileRes.ok) return null;
            const fileData = (await fileRes.json()) as any;
            const filePath = fileData.result?.file_path;
            if (!filePath) return null;

            return {
              fileId: s.file_id,
              emoji: s.emoji || '⭐',
              isAnimated: !!s.is_animated,
              isVideo: !!s.is_video,
              width: s.width || 512,
              height: s.height || 512,
              filePath,
              // Securely route file downloads through Cloudflare proxy so the bot token is NEVER exposed
              downloadUrl: `${baseUrl}/api/stickers/file?path=${encodeURIComponent(filePath)}`,
            };
          } catch {
            return null;
          }
        })
      )
    ).filter(Boolean);

    return c.json(
      {
        success: true,
        name: result.name,
        title: result.title,
        totalStickers: formattedStickers.length,
        stickers: formattedStickers,
      },
      200,
      {
        'Cache-Control': 'public, max-age=86400, s-maxage=86400',
      }
    );
  } catch (err: any) {
    return c.json({ success: false, error: err.message || 'Failed to fetch sticker set' }, 500);
  }
});

// Proxy endpoint to stream Telegram sticker files securely without exposing bot token to clients
app.get('/api/stickers/file', async (c) => {
  const filePath = c.req.query('path');
  if (
    !filePath ||
    filePath.includes('..') ||
    filePath.startsWith('/') ||
    !/^[a-zA-Z0-9_\-\.\/]+$/.test(filePath)
  ) {
    return c.json({ success: false, error: 'Invalid or missing file path parameter' }, 400);
  }

  const token =
    c.req.query('botToken') ||
    c.req.header('x-telegram-bot-token') ||
    c.env.TELEGRAM_BOT_TOKEN;

  if (!token) {
    return c.json(
      {
        success: false,
        error: 'TELEGRAM_BOT_TOKEN is not configured on this Cloudflare Worker',
      },
      503
    );
  }

  try {
    const tgRes = await fetch(`https://api.telegram.org/file/bot${token}/${filePath}`);
    if (!tgRes.ok) {
      return c.json(
        { success: false, error: `Telegram error: HTTP ${tgRes.status}` },
        tgRes.status as any
      );
    }

    const contentType = tgRes.headers.get('content-type') || 'image/webp';
    return new Response(tgRes.body, {
      status: 200,
      headers: {
        'Content-Type': contentType,
        'Cache-Control': 'public, max-age=604800, s-maxage=604800', // Cache at Cloudflare edge for 7 days
      },
    });
  } catch (err: any) {
    return c.json({ success: false, error: err.message || 'Failed to proxy sticker file' }, 500);
  }
});

// Sticker upload endpoint
app.post('/api/stickers/upload', async (c) => {
  const maxBytes = parseInt(c.env.MAX_UPLOAD_SIZE_BYTES || '5242880', 10);
  const defaultHours = parseInt(c.env.DEFAULT_EXPIRATION_HOURS || '24', 10);
  const maxHours = parseInt(c.env.MAX_EXPIRATION_HOURS || '168', 10);

  const contentType = c.req.header('content-type') || '';
  let fileBuffer: ArrayBuffer | null = null;
  let mimeType = 'image/webp';

  if (contentType.includes('multipart/form-data')) {
    const formData = await c.req.formData();
    const file = formData.get('file');
    if (!file || typeof file === 'string' || !('arrayBuffer' in file)) {
      return c.json({ success: false, error: 'No file provided in form data' }, 400);
    }
    const uploadedFile = file as unknown as { arrayBuffer(): Promise<ArrayBuffer>; type?: string };
    fileBuffer = await uploadedFile.arrayBuffer();
    mimeType = uploadedFile.type || 'image/webp';
  } else if (
    contentType.startsWith('image/') ||
    contentType === 'application/octet-stream'
  ) {
    fileBuffer = await c.req.arrayBuffer();
    mimeType = contentType === 'application/octet-stream' ? 'image/webp' : contentType;
  } else {
    return c.json(
      {
        success: false,
        error: 'Invalid content-type. Expected multipart/form-data or image/*',
      },
      400
    );
  }

  if (!fileBuffer || fileBuffer.byteLength === 0) {
    return c.json({ success: false, error: 'Empty file payload' }, 400);
  }

  if (fileBuffer.byteLength > maxBytes) {
    return c.json(
      {
        success: false,
        error: `File size exceeds maximum permitted limit (${maxBytes} bytes)`,
      },
      413
    );
  }

  // Allowed MIME types
  const allowedTypes = ['image/webp', 'image/png', 'image/gif', 'image/jpeg'];
  if (!allowedTypes.includes(mimeType)) {
    mimeType = 'image/webp'; // Default fallback for raw Telegram sticker formats
  }

  const requestedHoursParam = c.req.query('expiresInHours');
  let expirationHours = requestedHoursParam ? parseInt(requestedHoursParam, 10) : defaultHours;
  if (isNaN(expirationHours) || expirationHours <= 0) expirationHours = defaultHours;
  if (expirationHours > maxHours) expirationHours = maxHours;

  const now = Date.now();
  const expiresAt = now + expirationHours * 60 * 60 * 1000;
  const token = generateSecureToken(20);
  const contentHash = await computeSha256(fileBuffer);

  const metadata: StickerMetadata = {
    token,
    key: `stickers/${token}`,
    mimeType,
    sizeBytes: fileBuffer.byteLength,
    contentHash,
    createdAt: now,
    expiresAt,
  };

  const storage = getStorage(c.env);
  await storage.put(metadata.key, fileBuffer, metadata);

  const requestUrl = new URL(c.req.url);
  const publicUrl = `${requestUrl.protocol}//${requestUrl.host}/s/${token}`;

  const responsePayload: UploadResponse = {
    success: true,
    token,
    url: publicUrl,
    mimeType,
    sizeBytes: fileBuffer.byteLength,
    expiresAt: new Date(expiresAt).toISOString(),
  };

  return c.json(responsePayload, 201);
});

// Sticker public viewer & direct stream route
app.get('/s/:token', async (c) => {
  const token = c.req.param('token');
  if (!token || !/^[a-zA-Z0-9_-]{16,64}$/.test(token)) {
    return c.html(renderViewerHtml({ token: '', rawUrl: '', expired: true }), 404);
  }

  const storage = getStorage(c.env);
  const item = await storage.get(`stickers/${token}`);

  if (!item) {
    return c.html(renderViewerHtml({ token, rawUrl: '', expired: true }), 404);
  }

  const isRaw = c.req.query('raw') === 'true';
  const acceptHeader = c.req.header('accept') || '';

  // Stream raw image if explicitly requested or client accepts image directly without html
  if (isRaw || (acceptHeader.includes('image/') && !acceptHeader.includes('text/html'))) {
    return new Response(item.data, {
      status: 200,
      headers: {
        'Content-Type': item.metadata.mimeType,
        'Content-Length': item.metadata.sizeBytes.toString(),
        'Cache-Control': 'public, max-age=3600',
        'Content-Disposition': 'inline',
        'ETag': `"${item.metadata.contentHash}"`,
      },
    });
  }

  // Otherwise, render polished responsive viewer webpage
  const requestUrl = new URL(c.req.url);
  const rawUrl = `${requestUrl.protocol}//${requestUrl.host}/s/${token}?raw=true`;

  return c.html(
    renderViewerHtml({
      token,
      rawUrl,
      expired: false,
      mimeType: item.metadata.mimeType,
      title: 'Teleboard Sticker',
    })
  );
});

// Metadata query endpoint
app.get('/api/stickers/:token', async (c) => {
  const token = c.req.param('token');
  const storage = getStorage(c.env);
  const item = await storage.get(`stickers/${token}`);

  if (!item) {
    return c.json({ error: 'Sticker not found or expired' }, 404);
  }

  return c.json({
    token: item.metadata.token,
    mimeType: item.metadata.mimeType,
    sizeBytes: item.metadata.sizeBytes,
    contentHash: item.metadata.contentHash,
    createdAt: new Date(item.metadata.createdAt).toISOString(),
    expiresAt: new Date(item.metadata.expiresAt).toISOString(),
  });
});

// Telemetry install endpoint: pinged once on first app open
app.post('/api/telemetry/install', async (c) => {
  let body: any = {};
  try {
    body = await c.req.json();
  } catch {
    return c.json({ success: false, error: 'Invalid JSON payload' }, 400);
  }

  const installId = body.installId;
  if (!installId || typeof installId !== 'string' || installId.length < 8 || installId.length > 128) {
    return c.json({ success: false, error: 'Invalid or missing installId (must be 8-128 chars)' }, 400);
  }

  const cfCountry = c.req.header('cf-ipcountry') || 'Unknown';
  const storage = getTelemetryStorage(c.env);

  const event: InstallEvent = {
    installId,
    platform: typeof body.platform === 'string' ? body.platform : 'android',
    osVersion: typeof body.osVersion === 'string' ? body.osVersion : undefined,
    appVersion: typeof body.appVersion === 'string' ? body.appVersion : '1.0.0',
    deviceModel: typeof body.deviceModel === 'string' ? body.deviceModel : undefined,
    country: cfCountry,
    timestamp: Date.now(),
  };

  const { recorded, stats } = await storage.recordInstall(event);

  return c.json(
    {
      success: true,
      recorded,
      totalInstalls: stats.totalInstalls,
    },
    201
  );
});

// Admin Web Console: dashboard or login screen
app.get('/admin', async (c) => {
  if (!verifyAdminAuth(c)) {
    return c.html(renderAdminLoginHtml());
  }

  // If verified via query param, set cookie so subsequent refreshes stay logged in
  const queryKey = c.req.query('key') || c.req.query('secret') || c.req.query('token');
  if (queryKey) {
    setCookie(c, 'teleflair_admin_auth', queryKey, {
      path: '/',
      httpOnly: true,
      sameSite: 'Lax',
      maxAge: 60 * 60 * 24 * 30, // 30 days
    });
  }

  const requestUrl = new URL(c.req.url);
  const baseUrl = `${requestUrl.protocol}//${requestUrl.host}`;
  const telemetryStorage = getTelemetryStorage(c.env);
  const stickerStorage = getStorage(c.env);

  const [stats, stickers, release] = await Promise.all([
    telemetryStorage.getStats(),
    stickerStorage.listStickers(50),
    getAppRelease(c.env),
  ]);

  return c.html(
    renderAdminDashboardHtml({
      stats,
      stickers,
      release,
      env: c.env,
      baseUrl,
    })
  );
});

// Admin Login form handler
app.post('/admin/login', async (c) => {
  const adminSecret = c.env.ADMIN_SECRET || 'teleflair_admin_2026';
  let secretInput = '';
  const contentType = c.req.header('content-type') || '';
  if (contentType.includes('application/json')) {
    const body = await c.req.json().catch(() => ({}));
    secretInput = body.secret || '';
  } else {
    const form = await c.req.formData().catch(() => null);
    secretInput = (form?.get('secret') as string) || '';
  }

  if (secretInput !== adminSecret) {
    return c.html(renderAdminLoginHtml('Invalid administrative secret key. Please try again.'), 401);
  }

  setCookie(c, 'teleflair_admin_auth', secretInput, {
    path: '/',
    httpOnly: true,
    sameSite: 'Lax',
    maxAge: 60 * 60 * 24 * 30, // 30 days
  });

  return c.redirect('/admin');
});

// Admin Logout handler
app.get('/admin/logout', (c) => {
  deleteCookie(c, 'teleflair_admin_auth', { path: '/' });
  return c.redirect('/admin');
});

// Admin Stats JSON API
app.get('/api/admin/stats', async (c) => {
  if (!verifyAdminAuth(c)) {
    return c.json({ success: false, error: 'Unauthorized: Invalid administrative credentials' }, 401);
  }
  const storage = getTelemetryStorage(c.env);
  const stats = await storage.getStats();
  return c.json({ success: true, stats });
});

// Admin OTA Release Management API
app.post('/api/admin/release', async (c) => {
  if (!verifyAdminAuth(c)) {
    return c.json({ success: false, error: 'Unauthorized' }, 401);
  }
  try {
    const body = await c.req.json();
    if (!body.version || !body.downloadUrl) {
      return c.json({ success: false, error: 'version and downloadUrl are required' }, 400);
    }
    const currentRelease = await getAppRelease(c.env);
    const newRelease: AppReleaseConfig = {
      version: String(body.version),
      versionCode: Number(body.versionCode) || currentRelease.versionCode + 1,
      downloadUrl: String(body.downloadUrl),
      releaseNotes: String(body.releaseNotes || 'Bug fixes and performance improvements'),
      minSupportedVersionCode: body.minSupportedVersionCode ? Number(body.minSupportedVersionCode) : currentRelease.minSupportedVersionCode,
      updatedAt: Date.now(),
    };
    await updateAppRelease(c.env, newRelease);
    return c.json({ success: true, release: newRelease });
  } catch (err: any) {
    return c.json({ success: false, error: err.message || 'Failed to update release' }, 500);
  }
});

// Admin Purge Expired Stickers API
app.post('/api/admin/purge-expired', async (c) => {
  if (!verifyAdminAuth(c)) {
    return c.json({ success: false, error: 'Unauthorized' }, 401);
  }
  try {
    const storage = getStorage(c.env);
    const purgedCount = await storage.purgeExpired();
    return c.json({ success: true, purgedCount });
  } catch (err: any) {
    return c.json({ success: false, error: err.message || 'Failed to purge expired stickers' }, 500);
  }
});

// Admin Delete Individual Sticker API
app.delete('/api/admin/stickers/:token', async (c) => {
  if (!verifyAdminAuth(c)) {
    return c.json({ success: false, error: 'Unauthorized' }, 401);
  }
  const token = c.req.param('token');
  if (!token) {
    return c.json({ success: false, error: 'Token is required' }, 400);
  }
  try {
    const storage = getStorage(c.env);
    await storage.delete(`stickers/${token}`);
    return c.json({ success: true, message: `Sticker ${token} deleted permanently` });
  } catch (err: any) {
    return c.json({ success: false, error: err.message || 'Failed to delete sticker' }, 500);
  }
});

// Admin Test Telegram Bot API
app.get('/api/admin/test-telegram', async (c) => {
  if (!verifyAdminAuth(c)) {
    return c.json({ success: false, error: 'Unauthorized' }, 401);
  }
  const token = c.env.TELEGRAM_BOT_TOKEN;
  if (!token) {
    return c.json({ success: false, error: 'TELEGRAM_BOT_TOKEN not configured on worker' }, 400);
  }
  try {
    const tgRes = await fetch(`https://api.telegram.org/bot${token}/getMe`);
    const data: any = await tgRes.json();
    return c.json({
      success: true,
      ok: data.ok,
      botUsername: data.result?.username,
      botName: data.result?.first_name,
      canJoinGroups: data.result?.can_join_groups,
      supportsInlineQueries: data.result?.supports_inline_queries,
    });
  } catch (err: any) {
    return c.json({ success: false, error: err.message || 'Failed to contact Telegram API' }, 500);
  }
});

// App update metadata endpoint (queried by mobile app update checker)
app.get('/api/app/latest', async (c) => {
  const release = await getAppRelease(c.env);
  return c.json({
    success: true,
    name: 'Teleflair',
    version: release.version,
    versionCode: release.versionCode,
    downloadUrl: release.downloadUrl,
    releaseNotes: release.releaseNotes,
    minSupportedVersionCode: release.minSupportedVersionCode ?? 1,
    publishedAt: new Date(release.updatedAt).toISOString(),
  });
});

export default app;
