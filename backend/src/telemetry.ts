import { AppReleaseConfig, Env, InstallEvent, TelemetryStats } from './types.js';

export interface ITelemetryStorage {
  recordInstall(event: InstallEvent): Promise<{ recorded: boolean; stats: TelemetryStats }>;
  getStats(): Promise<TelemetryStats>;
  reset(): Promise<void>;
}

function createEmptyStats(): TelemetryStats {
  return {
    totalInstalls: 0,
    todayInstalls: 0,
    last7DaysInstalls: 0,
    last30DaysInstalls: 0,
    dailyBreakdown: {},
    countryBreakdown: {},
    versionBreakdown: {},
    platformBreakdown: {},
    recentInstalls: [],
    lastUpdated: Date.now(),
  };
}

function toDateKey(timestamp: number): string {
  const d = new Date(timestamp);
  return d.toISOString().split('T')[0];
}

function updateAggregates(stats: TelemetryStats, event: InstallEvent): void {
  const dateKey = toDateKey(event.timestamp);
  const now = Date.now();
  const dayMs = 24 * 60 * 60 * 1000;

  stats.totalInstalls += 1;
  stats.dailyBreakdown[dateKey] = (stats.dailyBreakdown[dateKey] || 0) + 1;

  const country = (event.country || 'Unknown').toUpperCase();
  stats.countryBreakdown[country] = (stats.countryBreakdown[country] || 0) + 1;

  const version = event.appVersion || '1.0.0';
  stats.versionBreakdown[version] = (stats.versionBreakdown[version] || 0) + 1;

  const platform = event.platform || 'android';
  stats.platformBreakdown[platform] = (stats.platformBreakdown[platform] || 0) + 1;

  // Prepend recent install (keep max 50)
  stats.recentInstalls.unshift(event);
  if (stats.recentInstalls.length > 50) {
    stats.recentInstalls = stats.recentInstalls.slice(0, 50);
  }

  // Re-calculate rolling windows
  const todayKey = toDateKey(now);
  stats.todayInstalls = stats.dailyBreakdown[todayKey] || 0;

  let last7 = 0;
  let last30 = 0;
  for (const [dStr, count] of Object.entries(stats.dailyBreakdown)) {
    const diffDays = (now - new Date(dStr).getTime()) / dayMs;
    if (diffDays >= 0 && diffDays <= 7) {
      last7 += count;
    }
    if (diffDays >= 0 && diffDays <= 30) {
      last30 += count;
    }
  }
  stats.last7DaysInstalls = last7;
  stats.last30DaysInstalls = last30;
  stats.lastUpdated = now;
}

export class MemoryTelemetryStorage implements ITelemetryStorage {
  private installs = new Map<string, InstallEvent>();
  private stats: TelemetryStats = createEmptyStats();

  async recordInstall(event: InstallEvent): Promise<{ recorded: boolean; stats: TelemetryStats }> {
    if (this.installs.has(event.installId)) {
      return { recorded: false, stats: this.stats };
    }
    this.installs.set(event.installId, event);
    updateAggregates(this.stats, event);
    return { recorded: true, stats: this.stats };
  }

  async getStats(): Promise<TelemetryStats> {
    // Refresh rolling windows based on current time
    const now = Date.now();
    const dayMs = 24 * 60 * 60 * 1000;
    const todayKey = toDateKey(now);
    this.stats.todayInstalls = this.stats.dailyBreakdown[todayKey] || 0;

    let last7 = 0;
    let last30 = 0;
    for (const [dStr, count] of Object.entries(this.stats.dailyBreakdown)) {
      const diffDays = (now - new Date(dStr).getTime()) / dayMs;
      if (diffDays >= 0 && diffDays <= 7) {
        last7 += count;
      }
      if (diffDays >= 0 && diffDays <= 30) {
        last30 += count;
      }
    }
    this.stats.last7DaysInstalls = last7;
    this.stats.last30DaysInstalls = last30;
    return this.stats;
  }

  async reset(): Promise<void> {
    this.installs.clear();
    this.stats = createEmptyStats();
  }
}

export class CloudflareR2TelemetryStorage implements ITelemetryStorage {
  private static STATS_KEY = 'telemetry/stats.json';
  private static INSTALL_PREFIX = 'telemetry/installs/';

  constructor(private bucket: R2Bucket) {}

  async recordInstall(event: InstallEvent): Promise<{ recorded: boolean; stats: TelemetryStats }> {
    const installKey = `${CloudflareR2TelemetryStorage.INSTALL_PREFIX}${event.installId}.json`;
    
    // Check if already registered
    const existing = await this.bucket.get(installKey);
    if (existing) {
      const stats = await this.getStats();
      return { recorded: false, stats };
    }

    // Persist individual install event
    await this.bucket.put(installKey, JSON.stringify(event), {
      httpMetadata: { contentType: 'application/json' },
    });

    // Update aggregate stats
    const stats = await this.getStats();
    updateAggregates(stats, event);

    await this.bucket.put(CloudflareR2TelemetryStorage.STATS_KEY, JSON.stringify(stats), {
      httpMetadata: { contentType: 'application/json' },
    });

    return { recorded: true, stats };
  }

  async getStats(): Promise<TelemetryStats> {
    const obj = await this.bucket.get(CloudflareR2TelemetryStorage.STATS_KEY);
    if (!obj) {
      return createEmptyStats();
    }
    try {
      const text = await obj.text();
      const stats = JSON.parse(text) as TelemetryStats;
      return stats;
    } catch {
      return createEmptyStats();
    }
  }

  async reset(): Promise<void> {
    // Delete stats key
    await this.bucket.delete(CloudflareR2TelemetryStorage.STATS_KEY);
  }
}

export const memoryTelemetryInstance = new MemoryTelemetryStorage();

export function getTelemetryStorage(env: Env): ITelemetryStorage {
  if (env.STICKER_BUCKET) {
    return new CloudflareR2TelemetryStorage(env.STICKER_BUCKET);
  }
  return memoryTelemetryInstance;
}

const DEFAULT_RELEASE: AppReleaseConfig = {
  version: '1.0.1',
  versionCode: 2,
  downloadUrl: 'https://github.com/Samtes64/Teleflair/releases/latest/download/app-arm64-v8a-release.apk',
  releaseNotes: 'Optimized R8 release with architecture splitting and responsive bottom navigation.',
  minSupportedVersionCode: 1,
  updatedAt: Date.now(),
};

let memoryRelease: AppReleaseConfig = { ...DEFAULT_RELEASE };

export async function getAppRelease(env: Env): Promise<AppReleaseConfig> {
  if (env.STICKER_BUCKET) {
    const obj = await env.STICKER_BUCKET.get('telemetry/app_release.json');
    if (obj) {
      try {
        return JSON.parse(await obj.text()) as AppReleaseConfig;
      } catch (_) {}
    }
  }
  return memoryRelease;
}

export async function updateAppRelease(env: Env, config: AppReleaseConfig): Promise<void> {
  memoryRelease = { ...config, updatedAt: Date.now() };
  if (env.STICKER_BUCKET) {
    await env.STICKER_BUCKET.put('telemetry/app_release.json', JSON.stringify(memoryRelease), {
      httpMetadata: { contentType: 'application/json' },
    });
  }
}
