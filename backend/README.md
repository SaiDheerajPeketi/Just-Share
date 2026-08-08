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
- `ANDROID_PACKAGE_NAME` (com.invincible.jedishare)
- `FREE_TIER_GB` (default 2)
- `PRO_TIER_GB` (default 5)
- `DATA_PACK_GB` (default 10)
- `PUBSUB_SUBSCRIPTION_NAME` (Pub/Sub subscription name for RTDN)
- `PRO_PRODUCT_ID` (default "pro_unlock")
- `DATA_PACK_PRODUCT_ID` (default "data_pack_10gb")
