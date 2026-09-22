# Just Share launch pricing

**Decision date:** 21 September 2026\
**Status:** implemented in candidate `428c1d3`; Play catalog and closed-test validation pending

## Strategy

Local Wi-Fi Direct and Bluetooth transfer is permanently free, private, and unlimited. Baseline
encryption and confirmation are never paywalled. Monetization applies to the hosted AlterSend Remote
relay, which creates an ongoing infrastructure cost, and to a one-time Pro convenience unlock.

## Catalog

| Product | United States | India | Store type |
| --- | ---: | ---: | --- |
| Just Share Pro | $9.99 | ₹399 | non-consumable one-time product |
| 10 GB Remote data pack | $1.99 | ₹99 | consumable one-time product |

- RevenueCat entitlement: `pro`
- RevenueCat offering: `default`
- Pro product: `pro_unlock`, package `$rc_lifetime`
- Data pack: `data_pack_10gb`; no entitlement, consumed only after backend verification
- No ads, weekly plan, recurring subscription, or trial at launch

Free includes local transfer and the backend-configured monthly Remote allowance. Pro grants the
backend-configured higher Remote allowance. Heavy use is funded by data packs. Do not advertise
planned group-send, resume, trusted-device, or history benefits until the exact release candidate
visibly implements them.

Remote quota is reserved from the sender using the selected file size when a relay session is
created. If Android cannot determine that size, the maximum five-gigabyte session amount is
reserved. The signed relay credential binds that exact ceiling so a modified client cannot request a
small reservation and relay a larger transfer.

RevenueCat runs in observer mode because the backend verifies purchases and owns quota. The relay
HMAC secret stays on the backend and Cloudflare Worker; the app receives only five-minute session
credentials.

## Optional student support

Offer one repeatable consumable `student_developer_tip` at a launch reference price of **$1.99 / ₹99**,
with Google Play supplying the localized price elsewhere. Map it to RevenueCat offering `support`
and custom package `tip`, with **no entitlement or quota benefit**. The existing backend verifies
and records the token before the client consumes it, so the tip can be purchased again safely.

## Closed-test measurements

Measure local and Remote transfer completion, relay fallback rate, quota exhaustion, product load,
verified purchase, consume/acknowledge success, restore/recovery, refunds, and support contacts.
Never send file names, paths, MIME content, peer identities, connection codes, IP addresses, or HMAC
tokens as analytics parameters.
