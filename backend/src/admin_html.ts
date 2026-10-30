import { AppReleaseConfig, Env, StickerMetadata, TelemetryStats } from './types.js';

export function renderAdminLoginHtml(error?: string): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Teleflair • Admin Console</title>
  <style>
    :root {
      --bg: #090a0f;
      --card-bg: #12141c;
      --border: rgba(255, 255, 255, 0.08);
      --accent: #6366f1;
      --accent-glow: rgba(99, 102, 241, 0.25);
      --text: #f3f4f6;
      --text-muted: #9ca3af;
      --error: #ef4444;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: radial-gradient(circle at 50% 20%, #151828 0%, var(--bg) 70%);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", Roboto, sans-serif;
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px;
    }
    .login-card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 24px;
      padding: 40px;
      width: 100%;
      max-width: 400px;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.7);
      backdrop-filter: blur(16px);
      text-align: center;
    }
    .logo-container {
      width: 56px;
      height: 56px;
      border-radius: 16px;
      background: linear-gradient(135deg, #4f46e5, #06b6d4);
      margin: 0 auto 20px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 8px 24px var(--accent-glow);
    }
    .logo-container svg {
      width: 28px;
      height: 28px;
      fill: none;
      stroke: #ffffff;
      stroke-width: 2;
      stroke-linecap: round;
      stroke-linejoin: round;
    }
    h1 {
      font-size: 1.4rem;
      font-weight: 600;
      letter-spacing: -0.02em;
      margin-bottom: 8px;
    }
    p {
      color: var(--text-muted);
      font-size: 0.875rem;
      line-height: 1.5;
      margin-bottom: 24px;
    }
    .error-msg {
      background: rgba(239, 68, 68, 0.1);
      border: 1px solid rgba(239, 68, 68, 0.3);
      color: #f87171;
      font-size: 0.8125rem;
      padding: 10px 14px;
      border-radius: 10px;
      margin-bottom: 18px;
    }
    .form-group {
      margin-bottom: 20px;
      text-align: left;
    }
    label {
      display: block;
      font-size: 0.75rem;
      font-weight: 500;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--text-muted);
      margin-bottom: 8px;
    }
    input[type="password"] {
      width: 100%;
      background: #090a0f;
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 14px 16px;
      color: #ffffff;
      font-size: 0.9375rem;
      outline: none;
      transition: border-color 0.2s, box-shadow 0.2s;
    }
    input[type="password"]:focus {
      border-color: var(--accent);
      box-shadow: 0 0 0 3px var(--accent-glow);
    }
    button {
      width: 100%;
      background: linear-gradient(135deg, #6366f1, #4f46e5);
      border: none;
      border-radius: 12px;
      padding: 14px;
      color: #ffffff;
      font-size: 0.9375rem;
      font-weight: 600;
      cursor: pointer;
      box-shadow: 0 4px 14px var(--accent-glow);
      transition: opacity 0.2s, transform 0.1s;
    }
    button:hover {
      opacity: 0.95;
    }
    button:active {
      transform: scale(0.98);
    }
    .footer {
      margin-top: 24px;
      font-size: 0.75rem;
      color: rgba(255, 255, 255, 0.3);
      letter-spacing: 0.05em;
      text-transform: uppercase;
    }
  </style>
</head>
<body>
  <div class="login-card">
    <div class="logo-container">
      <svg viewBox="0 0 24 24">
        <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
      </svg>
    </div>
    <h1>Teleflair Admin Console</h1>
    <p>Authenticate with your administrative secret to manage links, releases, and metrics.</p>

    ${error ? `<div class="error-msg">${error}</div>` : ''}

    <form method="POST" action="/admin/login">
      <div class="form-group">
        <label for="password">Admin Secret Key</label>
        <input type="password" id="password" name="secret" placeholder="Enter administrative key" value="teleflair_admin_2026" required autofocus autocomplete="current-password" />
      </div>
      <button type="submit">Sign In to Dashboard</button>
      <div style="margin-top: 14px; font-size: 0.8125rem; color: #818cf8; background: rgba(99, 102, 241, 0.1); padding: 8px 12px; border-radius: 8px; border: 1px solid rgba(99, 102, 241, 0.2);">
        Default Key: <code>teleflair_admin_2026</code>
      </div>
    </form>

    <div class="footer">Teleflair Cloud Infrastructure</div>
  </div>
</body>
</html>`;
}

export interface AdminDashboardData {
  stats: TelemetryStats;
  stickers: StickerMetadata[];
  release: AppReleaseConfig;
  env: Env;
  baseUrl: string;
}

export function renderAdminDashboardHtml(data: AdminDashboardData): string {
  const { stats, stickers, release, env, baseUrl } = data;

  const sortedCountries = Object.entries(stats.countryBreakdown).sort((a, b) => b[1] - a[1]);
  const sortedVersions = Object.entries(stats.versionBreakdown).sort((a, b) => b[1] - a[1]);
  const totalStorageBytes = stickers.reduce((acc, s) => acc + s.sizeBytes, 0);

  // 14 days chart
  const chartDays = 14;
  const dayMs = 24 * 60 * 60 * 1000;
  const now = Date.now();
  const dailyPoints: { date: string; displayDate: string; count: number }[] = [];
  let maxDailyCount = 1;

  for (let i = chartDays - 1; i >= 0; i--) {
    const t = now - i * dayMs;
    const dStr = new Date(t).toISOString().split('T')[0];
    const month = new Date(t).toLocaleString('en-US', { month: 'short' });
    const day = new Date(t).getDate();
    const count = stats.dailyBreakdown[dStr] || 0;
    if (count > maxDailyCount) maxDailyCount = count;
    dailyPoints.push({
      date: dStr,
      displayDate: `${month} ${day}`,
      count,
    });
  }

  const svgWidth = 800;
  const svgHeight = 200;
  const chartPadBottom = 30;
  const chartPadTop = 20;
  const usableHeight = svgHeight - chartPadBottom - chartPadTop;
  const barWidth = 36;
  const gap = (svgWidth - dailyPoints.length * barWidth) / (dailyPoints.length + 1);

  const barsSvg = dailyPoints
    .map((pt, idx) => {
      const x = gap + idx * (barWidth + gap);
      const barHeight = Math.max(4, Math.round((pt.count / maxDailyCount) * usableHeight));
      const y = svgHeight - chartPadBottom - barHeight;
      const isToday = idx === dailyPoints.length - 1;
      const barColor = isToday ? '#6366f1' : '#3b82f6';

      return `
        <g class="bar-group">
          <rect x="${x}" y="${y}" width="${barWidth}" height="${barHeight}" rx="6" fill="${barColor}" opacity="0.85" />
          <text x="${x + barWidth / 2}" y="${y - 8}" text-anchor="middle" fill="#9ca3af" font-size="11" font-weight="600">${pt.count > 0 ? pt.count : ''}</text>
          <text x="${x + barWidth / 2}" y="${svgHeight - 8}" text-anchor="middle" fill="#6b7280" font-size="11">${pt.displayDate}</text>
        </g>
      `;
    })
    .join('');

  const adminSecret = env.ADMIN_SECRET || 'teleflair_admin_2026';
  const directLink = `${baseUrl}/admin?key=${adminSecret}`;
  const tgTokenMasked = env.TELEGRAM_BOT_TOKEN
    ? `${env.TELEGRAM_BOT_TOKEN.substring(0, 6)}...${env.TELEGRAM_BOT_TOKEN.substring(env.TELEGRAM_BOT_TOKEN.length - 4)}`
    : 'Not configured';

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Teleflair Analytics & Admin Console</title>
  <style>
    :root {
      --bg: #090a0f;
      --card: #12141c;
      --card-border: rgba(255, 255, 255, 0.07);
      --accent: #6366f1;
      --accent-glow: rgba(99, 102, 241, 0.2);
      --text: #f9fafb;
      --text-muted: #9ca3af;
      --text-dim: #6b7280;
      --green: #10b981;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "Segoe UI", Roboto, sans-serif;
      min-height: 100vh;
      padding-bottom: 60px;
    }
    header {
      background: rgba(18, 20, 28, 0.85);
      backdrop-filter: blur(16px);
      border-bottom: 1px solid var(--card-border);
      position: sticky;
      top: 0;
      z-index: 100;
      padding: 16px 28px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .header-left {
      display: flex;
      align-items: center;
      gap: 14px;
    }
    .logo-badge {
      width: 38px;
      height: 38px;
      border-radius: 10px;
      background: linear-gradient(135deg, #4f46e5, #06b6d4);
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 12px var(--accent-glow);
    }
    .logo-badge svg {
      width: 20px;
      height: 20px;
      fill: none;
      stroke: #fff;
      stroke-width: 2.2;
    }
    .brand-title {
      font-size: 1.15rem;
      font-weight: 700;
      letter-spacing: -0.02em;
    }
    .nav-tabs {
      display: flex;
      background: rgba(255, 255, 255, 0.04);
      padding: 4px;
      border-radius: 12px;
      border: 1px solid var(--card-border);
      margin: 20px auto 0;
      max-width: 1200px;
      width: calc(100% - 48px);
    }
    .tab-btn {
      flex: 1;
      padding: 10px 16px;
      border: none;
      background: transparent;
      color: var(--text-muted);
      font-size: 0.875rem;
      font-weight: 600;
      border-radius: 8px;
      cursor: pointer;
      transition: all 0.2s;
    }
    .tab-btn.active {
      background: var(--card);
      color: #fff;
      box-shadow: 0 2px 8px rgba(0,0,0,0.4);
    }
    main {
      max-width: 1200px;
      margin: 0 auto;
      padding: 24px;
    }
    .tab-content {
      display: none;
    }
    .tab-content.active {
      display: block;
    }
    .grid-kpis {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 18px;
      margin-bottom: 24px;
    }
    .kpi-card {
      background: var(--card);
      border: 1px solid var(--card-border);
      border-radius: 18px;
      padding: 22px;
      position: relative;
    }
    .kpi-label {
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      margin-bottom: 8px;
    }
    .kpi-value {
      font-size: 2.1rem;
      font-weight: 700;
      letter-spacing: -0.03em;
      margin-bottom: 6px;
    }
    .kpi-sub {
      font-size: 0.8125rem;
      color: var(--text-dim);
    }
    .card-box {
      background: var(--card);
      border: 1px solid var(--card-border);
      border-radius: 20px;
      padding: 24px;
      margin-bottom: 24px;
    }
    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 20px;
    }
    .card-title {
      font-size: 1.1rem;
      font-weight: 600;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.875rem;
    }
    th {
      padding: 12px 18px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--text-dim);
      border-bottom: 1px solid var(--card-border);
    }
    td {
      padding: 14px 18px;
      border-bottom: 1px solid rgba(255, 255, 255, 0.04);
      color: var(--text-muted);
    }
    tr:hover td {
      background: rgba(255, 255, 255, 0.02);
      color: var(--text);
    }
    .tag {
      background: rgba(255, 255, 255, 0.08);
      padding: 3px 8px;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.05em;
    }
    .btn {
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid var(--card-border);
      color: var(--text);
      padding: 8px 14px;
      border-radius: 8px;
      font-size: 0.8125rem;
      font-weight: 600;
      cursor: pointer;
      text-decoration: none;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      transition: all 0.15s;
    }
    .btn:hover { background: rgba(255, 255, 255, 0.14); }
    .btn-primary {
      background: linear-gradient(135deg, #6366f1, #4f46e5);
      border: none;
      color: #fff;
    }
    .btn-danger {
      color: #ef4444;
      border-color: rgba(239, 68, 68, 0.3);
    }
    .btn-danger:hover {
      background: rgba(239, 68, 68, 0.15);
    }
    .form-row {
      margin-bottom: 16px;
    }
    .form-row label {
      display: block;
      font-size: 0.8125rem;
      font-weight: 600;
      color: var(--text-muted);
      margin-bottom: 6px;
    }
    .form-input {
      width: 100%;
      background: #090a0f;
      border: 1px solid var(--card-border);
      border-radius: 10px;
      padding: 12px 14px;
      color: #fff;
      font-size: 0.875rem;
      outline: none;
    }
    .form-input:focus { border-color: var(--accent); }
    .split-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 20px;
      margin-bottom: 24px;
    }
    @media (max-width: 800px) {
      .split-grid { grid-template-columns: 1fr; }
    }
  </style>
</head>
<body>
  <header>
    <div class="header-left">
      <div class="logo-badge">
        <svg viewBox="0 0 24 24">
          <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
        </svg>
      </div>
      <div>
        <div class="brand-title">Teleflair Analytics & Admin</div>
      </div>
    </div>
    <div style="display: flex; gap: 10px;">
      <button onclick="navigator.clipboard.writeText('${directLink}'); alert('Copied Direct Dashboard Link!');" class="btn">
        Copy Direct Key Link
      </button>
      <a href="/admin/logout" class="btn btn-danger">Sign Out</a>
    </div>
  </header>

  <div class="nav-tabs">
    <button class="tab-btn active" onclick="switchTab('analytics')">Analytics & Installs</button>
    <button class="tab-btn" onclick="switchTab('storage')">Ephemeral Links & Storage</button>
    <button class="tab-btn" onclick="switchTab('releases')">OTA App Releases</button>
    <button class="tab-btn" onclick="switchTab('system')">System & Bot Proxy</button>
  </div>

  <main>
    <!-- TAB 1: ANALYTICS -->
    <div id="tab-analytics" class="tab-content active">
      <div class="grid-kpis">
        <div class="kpi-card">
          <div class="kpi-label">Total Installs</div>
          <div class="kpi-value" style="color: #818cf8;">${stats.totalInstalls.toLocaleString()}</div>
          <div class="kpi-sub">Verified device activations</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Installs Today</div>
          <div class="kpi-value" style="color: #38bdf8;">+${stats.todayInstalls.toLocaleString()}</div>
          <div class="kpi-sub">Current UTC day</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Past 7 Days</div>
          <div class="kpi-value" style="color: #34d399;">${stats.last7DaysInstalls.toLocaleString()}</div>
          <div class="kpi-sub">Rolling weekly adoption</div>
        </div>
        <div class="kpi-card">
          <div class="kpi-label">Hosted Links</div>
          <div class="kpi-value" style="color: #fbbf24;">${stickers.length}</div>
          <div class="kpi-sub">${(totalStorageBytes / 1024).toFixed(1)} KB active storage</div>
        </div>
      </div>

      <div class="card-box">
        <div class="card-header">
          <div class="card-title">14-Day Activation Trend</div>
          <span class="tag">Daily UTC</span>
        </div>
        <div style="overflow-x: auto;">
          <svg style="width: 100%; min-width: 650px; height: ${svgHeight}px;" viewBox="0 0 ${svgWidth} ${svgHeight}">
            <line x1="0" y1="${svgHeight - chartPadBottom}" x2="${svgWidth}" y2="${svgHeight - chartPadBottom}" stroke="rgba(255,255,255,0.08)" stroke-width="1" />
            ${barsSvg}
          </svg>
        </div>
      </div>

      <div class="split-grid">
        <div class="card-box">
          <div class="card-header">
            <div class="card-title">Geographic Distribution</div>
            <span class="tag">${sortedCountries.length} Regions</span>
          </div>
          ${sortedCountries.slice(0, 5).map(([c, count]) => `
            <div style="display: flex; justify-content: space-between; margin-bottom: 12px; font-size: 0.875rem;">
              <span><span class="tag">${c}</span></span>
              <span><strong>${count}</strong> (${Math.round((count / (stats.totalInstalls || 1)) * 100)}%)</span>
            </div>
          `).join('')}
        </div>
        <div class="card-box">
          <div class="card-header">
            <div class="card-title">App Version Distribution</div>
            <span class="tag">${sortedVersions.length} Builds</span>
          </div>
          ${sortedVersions.slice(0, 5).map(([v, count]) => `
            <div style="display: flex; justify-content: space-between; margin-bottom: 12px; font-size: 0.875rem;">
              <span>v${v}</span>
              <span><strong>${count}</strong> devices</span>
            </div>
          `).join('')}
        </div>
      </div>

      <div class="card-box" style="padding: 0; overflow: hidden;">
        <div style="padding: 20px 24px; border-bottom: 1px solid var(--card-border);">
          <div class="card-title">Recent Device Activations (Last 50)</div>
        </div>
        <div style="overflow-x: auto;">
          <table>
            <thead>
              <tr>
                <th>Time (UTC)</th>
                <th>Region</th>
                <th>Platform</th>
                <th>OS</th>
                <th>App Version</th>
              </tr>
            </thead>
            <tbody>
              ${stats.recentInstalls.slice(0, 20).map(ev => `
                <tr>
                  <td>${new Date(ev.timestamp).toISOString().replace('T', ' ').substring(0, 19)}</td>
                  <td><span class="tag">${ev.country || 'Unknown'}</span></td>
                  <td>${ev.platform}</td>
                  <td>${ev.osVersion || 'Unknown'}</td>
                  <td>v${ev.appVersion || '1.0.0'}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- TAB 2: STORAGE & EPHEMERAL LINKS -->
    <div id="tab-storage" class="tab-content">
      <div class="card-box">
        <div class="card-header">
          <div>
            <div class="card-title">Ephemeral Sticker Link Management</div>
            <p style="color: var(--text-dim); font-size: 0.8125rem; margin-top: 4px;">
              Configured retention: Default ${env.DEFAULT_EXPIRATION_HOURS || 24}h (Max ${env.MAX_EXPIRATION_HOURS || 168}h) • Max file payload: ${(parseInt(env.MAX_UPLOAD_SIZE_BYTES || '5242880', 10) / (1024 * 1024)).toFixed(0)} MB
            </p>
          </div>
          <button onclick="purgeExpired()" class="btn btn-primary">
            Purge Expired Stickers Now
          </button>
        </div>

        <div style="overflow-x: auto;">
          <table>
            <thead>
              <tr>
                <th>Token</th>
                <th>MIME</th>
                <th>Size</th>
                <th>Created</th>
                <th>Expires</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              ${stickers.length === 0 ? '<tr><td colspan="6" style="text-align: center; padding: 30px; color: var(--text-dim);">No active sticker links hosted right now.</td></tr>' : ''}
              ${stickers.map(s => {
                const expiresDate = new Date(s.expiresAt);
                const isExpired = Date.now() > s.expiresAt;
                return `
                  <tr>
                    <td><a href="/s/${s.token}" target="_blank" style="color: #818cf8; font-family: monospace; text-decoration: none;">${s.token.substring(0, 16)}...</a></td>
                    <td><span class="tag">${s.mimeType}</span></td>
                    <td>${(s.sizeBytes / 1024).toFixed(1)} KB</td>
                    <td>${new Date(s.createdAt).toISOString().replace('T', ' ').substring(0, 16)}</td>
                    <td style="color: ${isExpired ? '#ef4444' : '#34d399'}; font-weight: 500;">
                      ${isExpired ? 'Expired' : expiresDate.toISOString().replace('T', ' ').substring(0, 16)}
                    </td>
                    <td>
                      <button onclick="deleteSticker('${s.token}')" class="btn btn-danger" style="padding: 4px 8px; font-size: 0.75rem;">
                        Revoke Link
                      </button>
                    </td>
                  </tr>
                `;
              }).join('')}
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- TAB 3: OTA RELEASES -->
    <div id="tab-releases" class="tab-content">
      <div class="split-grid">
        <div class="card-box">
          <div class="card-header">
            <div class="card-title">Upload APK to Cloudflare R2</div>
          </div>
          <p style="color: var(--text-muted); font-size: 0.8125rem; margin-bottom: 20px;">
            Upload your release APK directly to R2. The worker hosts the binary and sets the download URL to <code>${baseUrl}/api/app/download</code>.
          </p>

          <form id="apk-upload-form" onsubmit="uploadApkFile(event)">
            <div class="form-row">
              <label>Select APK File (.apk)</label>
              <input type="file" id="apk-file" accept=".apk" class="form-input" required />
            </div>
            <div class="form-row">
              <label>Version Name (e.g. 1.0.1)</label>
              <input type="text" id="apk-version" class="form-input" value="${release.version}" required />
            </div>
            <div class="form-row">
              <label>Version Code (e.g. ${release.versionCode + 1})</label>
              <input type="number" id="apk-code" class="form-input" value="${release.versionCode + 1}" required />
            </div>
            <div class="form-row">
              <label>Release Notes</label>
              <textarea id="apk-notes" class="form-input" style="height: 60px; resize: vertical;">Bug fixes, light theme default, and UI performance improvements.</textarea>
            </div>
            <button type="submit" id="apk-upload-btn" class="btn btn-primary" style="padding: 12px 20px;">
              Upload APK & Publish OTA Update
            </button>
            <div id="apk-upload-status" style="margin-top: 10px; font-size: 0.8125rem; color: var(--accent-violet);"></div>
          </form>
        </div>

        <div class="card-box">
          <div class="card-header">
            <div class="card-title">Release Metadata & External URL</div>
          </div>
          <p style="color: var(--text-muted); font-size: 0.8125rem; margin-bottom: 20px;">
            Point mobile apps to an external download URL (e.g. GitHub Releases) or view current settings.
          </p>

          <form id="release-form" onsubmit="saveRelease(event)">
            <div class="form-row">
              <label>Current Version Name</label>
              <input type="text" id="rel-version" class="form-input" value="${release.version}" required />
            </div>
            <div class="form-row">
              <label>Current Version Code</label>
              <input type="number" id="rel-code" class="form-input" value="${release.versionCode}" required />
            </div>
            <div class="form-row">
              <label>APK Direct Download URL</label>
              <input type="url" id="rel-url" class="form-input" value="${release.downloadUrl}" required />
            </div>
            <div class="form-row">
              <label>Release Notes</label>
              <textarea id="rel-notes" class="form-input" style="height: 60px; resize: vertical;">${release.releaseNotes}</textarea>
            </div>
            <div style="display: flex; gap: 10px; align-items: center;">
              <button type="submit" class="btn btn-primary" style="padding: 12px 20px;">
                Save Metadata
              </button>
              <a href="/api/app/download" target="_blank" class="btn btn-secondary" style="text-decoration: none; padding: 12px 16px;">
                Download Current APK
              </a>
            </div>
          </form>
        </div>
      </div>
    </div>

    <!-- TAB 4: SYSTEM & TELEGRAM PROXY -->
    <div id="tab-system" class="tab-content">
      <div class="split-grid">
        <div class="card-box">
          <div class="card-header">
            <div class="card-title">Telegram Bot API Proxy</div>
          </div>
          <div style="margin-bottom: 16px; font-size: 0.875rem;">
            <div style="color: var(--text-dim); margin-bottom: 4px;">Configured Bot Token:</div>
            <code style="background: rgba(255,255,255,0.06); padding: 4px 8px; border-radius: 6px;">${tgTokenMasked}</code>
          </div>
          <button onclick="testTelegramPing()" class="btn btn-primary" id="tg-test-btn">
            Test Telegram API Ping
          </button>
          <div id="tg-test-result" style="margin-top: 14px; font-size: 0.8125rem;"></div>
        </div>

        <div class="card-box">
          <div class="card-header">
            <div class="card-title">Cloud Infrastructure Status</div>
          </div>
          <div style="display: flex; flex-direction: column; gap: 12px; font-size: 0.875rem;">
            <div>Environment: <span class="tag">${env.ENVIRONMENT || 'production'}</span></div>
            <div>Storage Engine: <span class="tag">${env.STICKER_BUCKET ? 'Cloudflare R2 Bucket' : 'In-Memory Engine'}</span></div>
            <div>Direct Admin Key: <code style="background: rgba(255,255,255,0.06); padding: 2px 6px; border-radius: 4px;">${adminSecret}</code></div>
          </div>
        </div>
      </div>
    </div>
  </main>

  <script>
    function switchTab(tabId) {
      document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));
      event.target.classList.add('active');
      document.getElementById('tab-' + tabId).classList.add('active');
    }

    async function purgeExpired() {
      if (!confirm('Purge all expired stickers from Cloudflare storage?')) return;
      const res = await fetch('/api/admin/purge-expired', { method: 'POST' });
      const data = await res.json();
      alert('Purged ' + (data.purgedCount || 0) + ' expired stickers.');
      location.reload();
    }

    async function deleteSticker(token) {
      if (!confirm('Revoke this sticker link permanently?')) return;
      const res = await fetch('/api/admin/stickers/' + token, { method: 'DELETE' });
      if (res.ok) {
        alert('Sticker revoked successfully.');
        location.reload();
      } else {
        alert('Failed to revoke sticker.');
      }
    }

    async function uploadApkFile(e) {
      e.preventDefault();
      const fileInput = document.getElementById('apk-file');
      const statusDiv = document.getElementById('apk-upload-status');
      const btn = document.getElementById('apk-upload-btn');
      if (!fileInput.files || fileInput.files.length === 0) {
        alert('Please choose an APK file.');
        return;
      }
      const file = fileInput.files[0];
      const formData = new FormData();
      formData.append('file', file);
      formData.append('version', document.getElementById('apk-version').value);
      formData.append('versionCode', document.getElementById('apk-code').value);
      formData.append('releaseNotes', document.getElementById('apk-notes').value);

      btn.disabled = true;
      btn.innerText = 'Uploading APK to Cloudflare R2...';
      statusDiv.innerText = 'Streaming ' + (file.size / (1024 * 1024)).toFixed(1) + ' MB to Cloudflare R2...';

      try {
        const res = await fetch('/api/admin/release/upload-apk', {
          method: 'POST',
          body: formData,
        });
        const data = await res.json();
        if (data.success) {
          statusDiv.innerHTML = '<span style=\"color: #10b981;\">✓ APK uploaded successfully! Available at /api/app/download</span>';
          alert('APK uploaded & release metadata published to Cloudflare R2 successfully!');
          location.reload();
        } else {
          statusDiv.innerHTML = '<span style=\"color: #ef4444;\">Upload failed: ' + (data.error || 'Server error') + '</span>';
        }
      } catch (err) {
        statusDiv.innerHTML = '<span style=\"color: #ef4444;\">Error uploading: ' + err.message + '</span>';
      } finally {
        btn.disabled = false;
        btn.innerText = 'Upload APK & Publish OTA Update';
      }
    }

    async function saveRelease(e) {
      e.preventDefault();
      const payload = {
        version: document.getElementById('rel-version').value,
        versionCode: parseInt(document.getElementById('rel-code').value, 10),
        downloadUrl: document.getElementById('rel-url').value,
        releaseNotes: document.getElementById('rel-notes').value,
      };
      const res = await fetch('/api/admin/release', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (res.ok) {
        alert('App release configuration updated successfully!');
        location.reload();
      } else {
        alert('Failed to update release configuration.');
      }
    }

    async function testTelegramPing() {
      const btn = document.getElementById('tg-test-btn');
      const resDiv = document.getElementById('tg-test-result');
      btn.innerText = 'Pinging Telegram...';
      btn.disabled = true;
      try {
        const res = await fetch('/api/admin/test-telegram');
        const data = await res.json();
        if (data.ok) {
          resDiv.innerHTML = '<span style=\"color: #10b981;\">Connected to @' + data.botUsername + ' (' + data.botName + ') - HTTP 200 OK</span>';
        } else {
          resDiv.innerHTML = '<span style=\"color: #ef4444;\">Error: ' + data.error + '</span>';
        }
      } catch (err) {
        resDiv.innerHTML = '<span style=\"color: #ef4444;\">Connection error: ' + err.message + '</span>';
      } finally {
        btn.innerText = 'Test Telegram API Ping';
        btn.disabled = false;
      }
    }
  </script>
</body>
</html>`;
}
