# Just-Share Backend

Backend service for Just-Share AlterSend Remote feature.

## Setup

1. Copy `.env.example` to `.env` and fill in the values.
2. Install dependencies: `npm install`
3. Build the project: `npm run build`
4. Start the server: `npm start`

## Environment Variables
- `PORT` (default 8080)
- `GOOGLE_CLOUD_PROJECT` (GCP project ID)
- `GOOGLE_APPLICATION_CREDENTIALS` (path to service account JSON)
- `ANDROID_PACKAGE_NAME` (com.blackandblue.justshare)
- `FREE_TIER_GB` (default 2)
- `PRO_TIER_GB` (default 5)
- `DATA_PACK_GB` (default 10)
- `MAX_RELAY_SESSION_BYTES` (default 5 GiB; must match the Worker session quota)
- `PUBSUB_SUBSCRIPTION_NAME` (Pub/Sub subscription name for RTDN)
- `PUBSUB_PUSH_AUDIENCE` (the exact HTTPS push endpoint audience)
- `PUBSUB_PUSH_SERVICE_ACCOUNT` (the least-privilege push identity allowed to call RTDN)
- `PRO_PRODUCT_ID` (default "pro_unlock")
- `CF_RELAY_HMAC_SECRET` (64 hex characters; set to the same protected secret as the Cloudflare Worker)

The relay HMAC secret is server-only. Never place it in Android build properties, CI build arguments,
the APK, QR codes, logs, or source control. Android obtains five-minute session credentials from the
backend after the sender's selected bytes are atomically reserved. The reserved byte ceiling is
included in the signed credential and enforced by the Worker.
- `DATA_PACK_PRODUCT_ID` (default "data_pack_10gb")
