import { test, describe, before } from 'node:test';
import assert from 'node:assert/strict';
import app from '../src/index.js';
import { generateSecureToken, memoryStorageInstance } from '../src/storage.js';

describe('Teleboard Cloudflare Delivery Backend', () => {
  const env = {
    ENVIRONMENT: 'test',
    DEFAULT_EXPIRATION_HOURS: '24',
    MAX_EXPIRATION_HOURS: '168',
    MAX_UPLOAD_SIZE_BYTES: '1048576', // 1MB for test
  };

  test('GET /api/health returns 200 and healthy status', async () => {
    const res = await app.request('/api/health', {}, env);
    assert.equal(res.status, 200);
    const data = await res.json() as any;
    assert.equal(data.status, 'ok');
    assert.equal(data.service, 'teleboard-delivery-api');
    assert.equal(data.environment, 'test');
  });

  test('GET /api/stickers/pack validates pack name and handles missing token gracefully', async () => {
    // Missing name
    const resNoName = await app.request('/api/stickers/pack', {}, env);
    assert.equal(resNoName.status, 400);

    // With name but no token configured in test env
    const resNoToken = await app.request('/api/stickers/pack?name=MemeManMemesta', {}, env);
    assert.equal(resNoToken.status, 503);
    const data = await resNoToken.json() as any;
    assert.equal(data.success, false);
    assert.ok(data.error.includes('TELEGRAM_BOT_TOKEN'));
  });

  test('GET /api/stickers/file validates path and handles missing token gracefully', async () => {
    const resNoPath = await app.request('/api/stickers/file', {}, env);
    assert.equal(resNoPath.status, 400);

    // Rejects path traversal attempts
    const resTraversal = await app.request('/api/stickers/file?path=../../botToken/sendMessage', {}, env);
    assert.equal(resTraversal.status, 400);

    const resLeadingSlash = await app.request('/api/stickers/file?path=/etc/passwd', {}, env);
    assert.equal(resLeadingSlash.status, 400);

    const resNoToken = await app.request('/api/stickers/file?path=stickers/sample.webp', {}, env);
    assert.equal(resNoToken.status, 503);
  });

  test('POST /api/stickers/upload accepts valid binary image and returns token & URL', async () => {
    const fakeWebpBytes = new Uint8Array([82, 73, 70, 70, 0, 0, 0, 0, 87, 69, 66, 80]); // RIFF....WEBP
    const res = await app.request('/api/stickers/upload?expiresInHours=12', {
      method: 'POST',
      headers: {
        'Content-Type': 'image/webp',
      },
      body: fakeWebpBytes,
    }, env);

    assert.equal(res.status, 201);
    const data = await res.json() as any;
    assert.equal(data.success, true);
    assert.ok(data.token);
    assert.equal(data.mimeType, 'image/webp');
    assert.equal(data.sizeBytes, fakeWebpBytes.byteLength);
    assert.ok(data.url.includes(data.token));
  });

  test('POST /api/stickers/upload rejects oversized payloads', async () => {
    const oversized = new Uint8Array(1048576 + 10); // > 1MB limit in test env
    const res = await app.request('/api/stickers/upload', {
      method: 'POST',
      headers: {
        'Content-Type': 'image/webp',
      },
      body: oversized,
    }, env);

    assert.equal(res.status, 413);
    const data = await res.json() as any;
    assert.equal(data.success, false);
    assert.ok(data.error.includes('exceeds maximum'));
  });

  test('POST /api/stickers/upload rejects unsupported content-type', async () => {
    const res = await app.request('/api/stickers/upload', {
      method: 'POST',
      headers: {
        'Content-Type': 'text/plain',
      },
      body: 'invalid text',
    }, env);

    assert.equal(res.status, 400);
    const data = await res.json() as any;
    assert.equal(data.success, false);
  });

  test('GET /s/:token returns HTML viewer page for browsers', async () => {
    // First upload an image
    const sample = new Uint8Array([1, 2, 3, 4, 5]);
    const uploadRes = await app.request('/api/stickers/upload', {
      method: 'POST',
      headers: { 'Content-Type': 'image/webp' },
      body: sample,
    }, env);
    const { token } = await uploadRes.json() as any;

    // Request HTML page
    const viewerRes = await app.request(`/s/${token}`, {
      headers: { 'Accept': 'text/html,application/xhtml+xml' },
    }, env);

    assert.equal(viewerRes.status, 200);
    const html = await viewerRes.text();
    assert.ok(html.includes('<!DOCTYPE html>'));
    assert.ok(html.includes('Teleboard Sticker'));
    assert.ok(html.includes(`/s/${token}?raw=true`));
    assert.ok(html.includes('property="og:image"'));
    assert.ok(html.includes('name="twitter:card"'));
  });

  test('GET /s/:token?raw=true returns raw image stream with correct headers', async () => {
    const sample = new Uint8Array([10, 20, 30, 40, 50]);
    const uploadRes = await app.request('/api/stickers/upload', {
      method: 'POST',
      headers: { 'Content-Type': 'image/png' },
      body: sample,
    }, env);
    const { token } = await uploadRes.json() as any;

    const streamRes = await app.request(`/s/${token}?raw=true`, {}, env);
    assert.equal(streamRes.status, 200);
    assert.equal(streamRes.headers.get('Content-Type'), 'image/png');
    assert.equal(streamRes.headers.get('Cache-Control'), 'public, max-age=3600');
    assert.ok(streamRes.headers.get('ETag'));

    const bytes = new Uint8Array(await streamRes.arrayBuffer());
    assert.deepEqual(bytes, sample);
  });

  test('GET /s/:token returns expired page for invalid or expired tokens', async () => {
    const res = await app.request('/s/nonexistenttoken1234567890', {
      headers: { 'Accept': 'text/html' },
    }, env);

    assert.equal(res.status, 404);
    const html = await res.text();
    assert.ok(html.includes('Sticker Link Expired'));
  });

  test('GET /api/stickers/:token returns metadata for active sticker', async () => {
    const sample = new Uint8Array([7, 8, 9]);
    const uploadRes = await app.request('/api/stickers/upload', {
      method: 'POST',
      headers: { 'Content-Type': 'image/webp' },
      body: sample,
    }, env);
    const { token } = await uploadRes.json() as any;

    const metaRes = await app.request(`/api/stickers/${token}`, {}, env);
    assert.equal(metaRes.status, 200);
    const meta = await metaRes.json() as any;
    assert.equal(meta.token, token);
    assert.equal(meta.sizeBytes, 3);
    assert.ok(meta.expiresAt);
  });

  test('generateSecureToken generates high entropy random strings', () => {
    const t1 = generateSecureToken(16);
    const t2 = generateSecureToken(16);
    assert.equal(t1.length, 32); // 16 bytes = 32 hex chars
    assert.equal(t2.length, 32);
    assert.notEqual(t1, t2);
  });

  test('POST /api/telemetry/install records device installation and returns total installs', async () => {
    const installPayload = {
      installId: 'test_device_uuid_001',
      platform: 'android',
      osVersion: 'Android 14 (API 34)',
      appVersion: '1.0.0',
      deviceModel: 'Pixel 8',
    };

    const res = await app.request('/api/telemetry/install', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'cf-ipcountry': 'US',
      },
      body: JSON.stringify(installPayload),
    }, env);

    assert.equal(res.status, 201);
    const data = await res.json() as any;
    assert.equal(data.success, true);
    assert.equal(data.recorded, true);
    assert.ok(data.totalInstalls >= 1);
  });

  test('POST /api/telemetry/install deduplicates repeat calls with same installId', async () => {
    const installPayload = {
      installId: 'test_device_uuid_001', // Same ID as above
      platform: 'android',
      appVersion: '1.0.0',
    };

    const res = await app.request('/api/telemetry/install', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(installPayload),
    }, env);

    assert.equal(res.status, 201);
    const data = await res.json() as any;
    assert.equal(data.success, true);
    assert.equal(data.recorded, false); // Deduplicated!
  });

  test('POST /api/telemetry/install rejects missing or invalid installId', async () => {
    const res = await app.request('/api/telemetry/install', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ platform: 'android' }),
    }, env);

    assert.equal(res.status, 400);
    const data = await res.json() as any;
    assert.equal(data.success, false);
  });

  test('GET /api/admin/stats rejects unauthorized requests with 401', async () => {
    const res = await app.request('/api/admin/stats', {}, env);
    assert.equal(res.status, 401);
    const data = await res.json() as any;
    assert.equal(data.success, false);
  });

  test('GET /api/admin/stats allows authorized access via x-admin-key header', async () => {
    const res = await app.request('/api/admin/stats', {
      headers: {
        'x-admin-key': 'teleflair_admin_2026',
      },
    }, env);

    assert.equal(res.status, 200);
    const data = await res.json() as any;
    assert.equal(data.success, true);
    assert.ok(data.stats);
    assert.ok(data.stats.totalInstalls >= 1);
    assert.ok(data.stats.countryBreakdown['US'] >= 1);
  });

  test('GET /admin renders login card when unauthenticated', async () => {
    const res = await app.request('/admin', {}, env);
    assert.equal(res.status, 200);
    const html = await res.text();
    assert.ok(html.includes('Teleflair Admin Console'));
    assert.ok(html.includes('Admin Secret Key'));
    assert.ok(html.includes('/admin/login'));
  });

  test('GET /admin?key=... renders dashboard when secret provided', async () => {
    const res = await app.request('/admin?key=teleflair_admin_2026', {}, env);
    assert.equal(res.status, 200);
    const html = await res.text();
    assert.ok(html.includes('Teleflair Analytics'));
    assert.ok(html.includes('Total Installs'));
    assert.ok(html.includes('Recent Device Activations'));
  });

  test('POST /admin/login handles valid and invalid credentials', async () => {
    // Invalid
    const resInvalid = await app.request('/admin/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ secret: 'wrong_password' }),
    }, env);
    assert.equal(resInvalid.status, 401);

    // Valid
    const resValid = await app.request('/admin/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ secret: 'teleflair_admin_2026' }),
    }, env);
    assert.equal(resValid.status, 302); // Redirect to /admin
    const setCookie = resValid.headers.get('set-cookie');
    assert.ok(setCookie?.includes('teleflair_admin_auth'));
  });

  test('GET /admin/logout clears auth cookie and redirects', async () => {
    const res = await app.request('/admin/logout', {}, env);
    assert.equal(res.status, 302);
    const setCookie = res.headers.get('set-cookie');
    assert.ok(setCookie);
  });

  test('GET /api/app/latest returns update metadata and version code', async () => {
    const res = await app.request('/api/app/latest', {}, env);
    assert.equal(res.status, 200);
    const data = await res.json() as any;
    assert.equal(data.success, true);
    assert.equal(data.name, 'Teleflair');
    assert.ok(data.versionCode >= 1);
    assert.ok(data.downloadUrl);
  });

  test('POST /api/admin/release updates release configuration', async () => {
    const res = await app.request('/api/admin/release', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer teleflair_admin_2026',
      },
      body: JSON.stringify({
        version: '1.2.0',
        versionCode: 3,
        downloadUrl: 'https://example.com/teleflair-1.2.0.apk',
        releaseNotes: 'New sticker engine and speed boosts',
      }),
    }, env);

    assert.equal(res.status, 200);
    const data = await res.json() as any;
    assert.equal(data.success, true);
    assert.equal(data.release.version, '1.2.0');

    // Verify /api/app/latest reflects updated release
    const latestRes = await app.request('/api/app/latest', {}, env);
    const latestData = await latestRes.json() as any;
    assert.equal(latestData.version, '1.2.0');
    assert.equal(latestData.versionCode, 3);
  });

  test('POST /api/admin/purge-expired cleans up expired stickers', async () => {
    const res = await app.request('/api/admin/purge-expired', {
      method: 'POST',
      headers: { 'x-admin-key': 'teleflair_admin_2026' },
    }, env);
    assert.equal(res.status, 200);
    const data = await res.json() as any;
    assert.equal(data.success, true);
    assert.equal(typeof data.purgedCount, 'number');
  });

  test('DELETE /api/admin/stickers/:token deletes sticker', async () => {
    const res = await app.request('/api/admin/stickers/test_token_123', {
      method: 'DELETE',
      headers: { 'x-admin-key': 'teleflair_admin_2026' },
    }, env);
    assert.equal(res.status, 200);
    const data = await res.json() as any;
    assert.equal(data.success, true);
  });

  test('GET /api/app/download redirects or serves APK binary', async () => {
    const res = await app.request('/api/app/download', {}, env);
    // When configured with external URL, redirects with 302
    assert.ok(res.status === 302 || res.status === 200 || res.status === 404);
  });
});
