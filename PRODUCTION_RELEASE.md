# Just Share closed-test release contract

## Play review status — September 26, 2026

Google Play Publishing overview shows closed-testing Alpha release `1.0.5 (9)` and a default-listing phone-screenshot change under **Changes in review**. This is a submitted review, not approval or tester availability. Recheck the release overview after Play completes review; this status alone does not prove the relay or quota backend is live.

## Candidate architecture

- Package: `com.blackandblue.justshare`
- Target/compile SDK: 36
- Signing: a unique Just Share upload key; Google Play App Signing owns the distribution key
- Testing progression: internal → closed Alpha; no automatic production deployment
- Privacy site: `https://justshare.blackandblue.co.in`
- Firebase: app-scoped Analytics and Crashlytics with ad identifiers disabled
- RevenueCat: observer mode, `pro` entitlement; the backend remains purchase/quota authority
- Remote relay: `wss://relay.justshare.blackandblue.co.in`
- Quota API: `https://api.justshare.blackandblue.co.in`

## Protected inputs

Keep these only in their destination secret stores:

- unique upload keystore and passwords
- Google Play publishing service-account JSON
- RevenueCat Google public SDK key
- Firebase `google-services.json`
- backend Google service identity and Play verification credentials
- Cloudflare API token scoped to the Just Share Worker and DNS zone
- the same random 32-byte relay HMAC secret in backend and Worker secret stores
- RevenueCat webhook authorization secret if webhooks are enabled

The relay HMAC secret must never be a Gradle property, GitHub build argument, Android BuildConfig
value, QR field, log entry, screenshot, or repository file.

Purchase tokens are verified transiently and retained only as one-way SHA-256 hashes. Purchase
benefits and refunds are applied in idempotent Firestore transactions. Google Play RTDN push calls
must carry an OIDC token from the dedicated push service identity.

## Minimum cloud identities

- Backend runtime identity: Firestore read/write plus app-scoped Google Play purchase verification;
  no owner/editor role and no downloadable key.
- Pub/Sub push identity: permission only to invoke the RTDN endpoint; the backend checks its exact
  verified email and audience.
- Play CI uploader: app-scoped release permission for Just Share testing tracks only. Keep its JSON
  key solely in the protected GitHub environment and rotate it after any suspected exposure.
- Cloudflare deployment token: edit the Just Share Worker and `blackandblue.co.in` DNS only; no
  account-wide administrative grant.

## Dashboard contract

- Play products: `pro_unlock` (non-consumable) and `data_pack_10gb` (consumable)
- RevenueCat app package: `com.blackandblue.justshare`
- Entitlement/offering/package: `pro` / `default` / `$rc_lifetime`
- Data pack is not attached to `pro`; consume only after backend verification succeeds
- Restore behavior: transfer purchases to the current anonymous app user ID
- Firebase and RevenueCat identifiers are pseudonymous and must be covered by policy/deletion flow

## Evidence gates

- Verify Bluetooth, Wi-Fi Direct, direct Remote, relay fallback, QR scan, notification denial, low
  storage, interrupted transfer, resume/retry behavior, screen lock, reboot, and large files.
- Record foreground-service evidence for local and Remote transfer using the exact signed candidate.
- Exercise Pro purchase, pending, recovery, acknowledgment, data-pack verification/consumption,
  refund/revocation, and reinstall/restore on a licensed Play track.
- Confirm the final manifest, Data Safety form, permission declarations, target audience, content
  rating, privacy/deletion URLs, and support email match this build.
