# Cloudflare Workers & R2 Deployment Guide

Teleboard leverages Cloudflare's serverless edge infrastructure and R2 object storage for ephemeral image-link delivery.

---

## 1. Cloudflare Free-Tier Quotas & Protections

Teleboard is designed to operate strictly within Cloudflare's free allowance:

| Service | Free Tier Allowance | Teleboard Usage Pattern |
|---|---|---|
| **Cloudflare Workers** | 100,000 requests / day | ~1 request per sticker link generation or retrieval |
| **Cloudflare R2 Storage** | 10 GB stored / month | Ephemeral retention (default 24h) keeps active storage under 250 MB |
| **R2 Class A Ops (Uploads)**| 1,000,000 requests / month | 1 Class A op per sticker upload |
| **R2 Class B Ops (Reads)** | 10,000,000 requests / month| 1 Class B op per recipient sticker view |
| **R2 Egress Bandwidth** | **100% Free (Zero egress fees)** | Zero bandwidth cost regardless of download volume |

### Quota Exhaustion Guardrails
- **Max Upload Size**: Hard limit of 5 MB enforced by Worker middleware.
- **Auto-Expiration**: Objects default to 24-hour expiration (maximum 7 days). Expired objects are lazily pruned or cleaned up via bucket lifecycle rules.
- **No Silent Paid Upgrades**: The backend does not auto-scale into paid tiers. If daily worker requests exceed 100k, the mobile client falls back automatically to Text-Art and Direct Intent Handoff.

---

## 2. Directory Structure

```text
backend/
├── package.json        # Dependencies (Hono, Zod, Wrangler)
├── tsconfig.json       # TypeScript configuration (ES2022, Bundler)
├── wrangler.toml       # Cloudflare Worker and R2 bucket bindings
├── src/
│   ├── index.ts        # Worker routes (/api/stickers/upload, /s/:token, /api/health)
│   ├── storage.ts      # R2 adapter, memory fallback, HTML viewer renderer
│   └── types.ts        # Type definitions and environment bindings
└── tests/
    └── api.test.ts     # Automated unit & integration tests
```

---

## 3. Local Development & Testing

The backend includes an in-memory storage fallback, allowing full test execution and local development without active Cloudflare credentials:

```bash
cd backend

# Install dependencies
npm install

# Run automated test suite
npm test

# Type-check TypeScript code
npm run build

# Start local Wrangler edge simulator
npx wrangler dev
```
The local development server runs at `http://127.0.0.1:8787`.

---

## 4. Production Cloudflare Deployment

### Step 1: Log in to Cloudflare
```bash
cd backend
npx wrangler login
```

### Step 2: Create the R2 Storage Bucket
```bash
# Create production bucket
npx wrangler r2 bucket create teleboard-stickers

# Create preview bucket for testing
npx wrangler r2 bucket create teleboard-stickers-preview
```

### Step 3: Configure 24-Hour Object Lifecycle Expiration
In the Cloudflare Dashboard:
1. Navigate to **R2 > teleboard-stickers > Settings > Lifecycle Rules**.
2. Add a rule: **Delete objects after 1 day**.
This automatically purges expired stickers on Cloudflare's backend without consuming worker invocations.

### Step 4: Deploy the Worker
```bash
npx wrangler deploy
```

Upon completion, Wrangler outputs your live edge URL:
```text
https://teleboard-delivery.<your-subdomain>.workers.dev
```

### Step 5: Configure Mobile Companion App
1. Open the Teleboard app on your Android device.
2. Navigate to **Settings > Cloudflare Smart Delivery**.
3. Paste your deployed Worker URL into **Cloudflare Worker URL**.
4. Tap **Save Configuration**.
