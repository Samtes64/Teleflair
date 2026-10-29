export interface Env {
  STICKER_BUCKET?: R2Bucket;
  STICKER_KV?: KVNamespace;
  TELEMETRY_KV?: KVNamespace;
  TELEGRAM_BOT_TOKEN?: string;
  ENVIRONMENT?: string;
  DEFAULT_EXPIRATION_HOURS?: string;
  MAX_EXPIRATION_HOURS?: string;
  MAX_UPLOAD_SIZE_BYTES?: string;
  ADMIN_SECRET?: string;
}

export interface StickerMetadata {
  token: string;
  key: string;
  mimeType: string;
  sizeBytes: number;
  contentHash: string;
  createdAt: number;
  expiresAt: number;
}

export interface UploadResponse {
  success: boolean;
  token: string;
  url: string;
  mimeType: string;
  sizeBytes: number;
  expiresAt: string;
}

export interface InstallEvent {
  installId: string;
  platform: string;
  osVersion?: string;
  appVersion?: string;
  country?: string;
  deviceModel?: string;
  timestamp: number;
}

export interface TelemetryStats {
  totalInstalls: number;
  todayInstalls: number;
  last7DaysInstalls: number;
  last30DaysInstalls: number;
  dailyBreakdown: Record<string, number>;
  countryBreakdown: Record<string, number>;
  versionBreakdown: Record<string, number>;
  platformBreakdown: Record<string, number>;
  recentInstalls: InstallEvent[];
  lastUpdated: number;
}

export interface AppReleaseConfig {
  version: string;
  versionCode: number;
  downloadUrl: string;
  releaseNotes: string;
  minSupportedVersionCode?: number;
  updatedAt: number;
}

export interface DeliverySettingsConfig {
  defaultExpirationHours: number;
  maxExpirationHours: number;
  maxUploadSizeBytes: number;
}
