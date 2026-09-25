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
- `GOOGLE_APPLICATION_CREDENTIALS` (local development only, when Application Default
  Credentials are otherwise unavailable; omit this in Cloud Run so production uses the attached
  `justshare-runtime` identity without a downloadable key)
- `ANDROID_PACKAGE_NAME` (com.blackandblue.justshare)
- `FIREBASE_APP_ID` (the exact Firebase Android app id allowed by App Check)
- `FREE_TIER_GB` (default 2)
- `PRO_TIER_GB` (default 5)
- `DATA_PACK_GB` (default 10)
- `MAX_RELAY_SESSION_BYTES` (default 5 GiB; must match the Worker session quota)
- `PUBSUB_PUSH_AUDIENCE` (the exact HTTPS push endpoint audience)
- `PUBSUB_PUSH_SERVICE_ACCOUNT` (the least-privilege push identity allowed to call RTDN)
- `PRO_PRODUCT_ID` (default "pro_unlock")
- `DATA_PACK_PRODUCT_ID` (default "data_pack_10gb")
- `SUPPORT_TIP_PRODUCT_ID` (legacy "student_developer_tip"; retained for purchase recovery). The three new entitlement-free consumables are `support_developer_1`, `support_developer_10`, and `support_developer_100`.
- `CF_RELAY_HMAC_SECRET` (64 hex characters; set to the same protected secret as the Cloudflare Worker)

All mobile API routes require a valid `X-Firebase-AppCheck` token issued for `FIREBASE_APP_ID` plus the pseudonymous `X-Device-Id` quota key. Configure the Firebase App Check Play Integrity provider before exposing the service. The RTDN route uses its separate authenticated Pub/Sub push identity.

Pull requests and `main` builds compile this backend and type-check the relay worker before any
Android testing-track deployment can run. This validation uses no cloud credentials and performs no
deployment.

The relay HMAC secret is server-only. Never place it in Android build properties, CI build arguments,
the APK, QR codes, logs, or source control. Android obtains five-minute session credentials from the
backend after the sender's selected bytes are atomically reserved. The reserved byte ceiling is
included in the signed credential and enforced by the Worker. Relay-size inputs must be non-negative
safe integers at both the route and quota-service boundaries, so an invalid request cannot mutate a
quota reservation before credential issuance fails.
