# Just Share — launch feature and monetization brief

This brief describes the Android launch candidate. It supersedes the earlier ad-supported concept.
Only behavior present in the signed candidate may appear in Google Play copy or screenshots.

## Launch product

### Nearby transfer

- Send and receive files over supported Bluetooth and local Wi-Fi Direct connections.
- Keep nearby transfer free, private, ad-free, account-free, and unlimited.
- Show the permission explanation before Android prompts.
- Preserve original file quality and show transfer progress, completion, retry, and history states.
- Keep baseline safety, confirmation, and transport protection available without payment.

### Remote Transfer

- Pair through a live QR code or manual session code.
- Route selected files through the protected relay only for delivery; do not position the relay as
  personal cloud storage.
- Give free users the backend-configured monthly Remote allowance.
- Give Pro users the backend-configured higher monthly Remote allowance.
- Let users add non-expiring relay quota with repeatable 10 GB data packs.
- Verify App Check, purchases, quota, reservations, and short-lived relay credentials on the backend.
- Never commit, reuse in evidence, or place a live session code in a listing asset.

### Settings and purchases

- Expose Just Share Pro as a one-time Play purchase that raises the monthly Remote allowance.
- Expose the 10 GB Remote data pack as a repeatable Play consumable with no entitlement.
- Expose “Support the student developer” as a repeatable Play consumable with no feature, quota,
  entitlement, charitable, or tax benefit.
- Display only the localized price returned by Google Play. If product details are unavailable, show
  a clear unavailable state rather than a hardcoded fallback price.
- Restore the durable Pro purchase automatically and scope progress, success, cancellation, and error
  feedback to the product the user selected.

## Launch monetization strategy

The launch app is ad-free. Local transfer is the acquisition and trust surface, so it remains free
and unlimited. Monetization applies to the hosted Remote relay, which has ongoing infrastructure
costs, and to optional voluntary support.

| Product | Play type | User benefit | RevenueCat mapping |
| --- | --- | --- | --- |
| `pro_unlock` | Non-consumable one-time product | Higher backend-configured monthly Remote allowance | `default` / `$rc_lifetime`, entitlement `pro` |
| `data_pack_10gb` | Consumable one-time product | Adds 10 GB of non-expiring Remote relay quota | Custom product, no entitlement |
| `student_developer_tip` | Consumable one-time product | Voluntary support only | `support` / `tip`, no entitlement |

Reference launch prices and regional strategy live in `PRICING.md`. Google Play is the source of
truth for every user-visible price. Do not advertise a subscription, free trial, discount, ads,
“remove ads” benefit, group sending, trusted devices, or another planned feature at launch.

## Future ideas, not launch claims

The following may be evaluated later but must not be shown as shipped, paid, or promised until they
exist in a verified release candidate:

- Multi-recipient group sending
- Expanded interruption recovery outside the implemented Remote protocol
- Trusted or blocked device management
- Search and filtering enhancements
- Cross-platform clients

Baseline security must never be paywalled. Any future paid feature must add capacity or convenience,
not remove essential protection from free transfers.

## Launch design prompt

```text
Design an Android UI set for “Just Share,” published by Black and Blue. It transfers photos, videos,
documents, audio, and other files through nearby Bluetooth/Wi-Fi Direct connections or the protected
Remote relay.

Use the existing red, black, and neutral visual system, rounded cards, clear hierarchy, generous
spacing, and accessible contrast. Keep all text inside safe areas and verify that no labels, badges,
buttons, system bars, or device-frame elements overlap at phone, 7-inch tablet, and 10-inch tablet
sizes.

Represent only these launch surfaces:
- Welcome and permission explanation
- Send or receive selection
- File selection
- Nearby device discovery and pairing
- Remote Transfer with live QR/manual code pairing
- Transfer progress, completion, retry, and history
- Settings
- Remote quota status and data-pack recovery state
- One-time Just Share Pro purchase with higher monthly Remote allowance
- Optional student-developer support tip with no benefit

Nearby transfer is free, unlimited, ad-free, and account-free. Remote Transfer includes a
backend-configured free monthly allowance. Pro raises that allowance with a one-time purchase, and
repeatable data packs add relay quota. Show prices only as localized values supplied by Google Play;
use a clear unavailable state in mockups where live product details are absent.

Do not add ad banners, interstitials, rewarded ads, subscriptions, trials, discount claims, cloud
storage claims, “remove ads” benefits, or unimplemented feature promises. Do not show live pairing
codes, personal files, real device names, email addresses, or other identifying data.
```
