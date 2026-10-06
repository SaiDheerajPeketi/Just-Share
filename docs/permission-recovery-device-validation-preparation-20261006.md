# Just Share disposable device validation preparation — 2026-10-06

**SOURCE ONLY. Native admission is disabled.** The proposed device work has not run. All actual APK, certificate, built-source, tool, target, port, process and admission pins remain null. The caller's `--execute` branch unconditionally refuses before reading inputs. Changing JSON flags or supplying a grant cannot enable it.

Owned preparation files:

- `docs/run-permission-recovery-device-validation-20261006.py`
- `docs/permission-recovery-device-validation-preparation-20261006.json`
- This document.

The caller SHA256 is `b767a3038cd8ee116d7bc1f79b7538c3efa38465bf95642a7a0e988296d731f6`. It provides pure evidence/admission predicates and a reviewable proposal, with no subprocess, SDK import, resource query, AVD creation or native executor. Python AST and **73 pure-mock checks pass**; those are source checks, not device outcomes.

## Source and artifact boundary

The historical accepted baseline is source `965536c13b4678aa302f75f100ecab52e065f857`, app tree `9177d21b523f35e3aa53a8047c12c3f895e34afb` and app/src tree `658642c649f9f888804012e0aa3b6e713d24ccf2`. Existing host proof at source `4e35e47fee047a6cfafd92079aacdc0c21df9efb`, committed in `81490059ada05cd983ef67d1a6db3350e1ab4699`, records 8 host passes and compilation of the selected 21 Android methods. It does not bind an APK pair or prove any Android case executed.

Root's Home successor is committed as `d66c25bf172fc821052298f34a6936bbf3b925a3`, app/src tree `5b78e7caa7244e6aa7795e118dbd835f2b34cae3`. Its Home source SHA256 is `ab979ef699d95e12e1f97c36682b4d7a368c0372c4eb51f25181b2bc9820b50e`; root reports source acceptance. This proposal preserves the historical baseline and records that successor separately. Current source, packaging receipt and native plan must be rebound together before admission. Historical hashes must not be silently reused for the successor.

Actual app/test paths, SHA256, size, package, signer certificate, debuggable status, built commit/tree, packaging receipt, test target and runner are null. Root must supply the separately reviewed packaging result, pinned CLI bytes/build tools and executable source/plan bytes. Installation must use only that exact pair. For each package, require one installed `base.apk`; pull it through the bound serial and prove host/pulled hash, size, certificate, package and source/receipt parity. Both APKs must have the same signer and source; test target must be `com.blackandblue.justshare`, with runner `com.blackandblue.justshare.test/androidx.test.runner.AndroidJUnitRunner`.

## Exact proposed scope

One new, owner-created disposable API35/ARM64 phone; no existing emulator, server or physical phone may be adopted. Selected cases:

| Class | Exact selected methods |
| --- | ---: |
| PermissionsScreenTest | 3 |
| PermissionRecoveryNavigationTest | 5 |
| WifiPermissionRecoveryTest | 3 |
| TransferRetryStateTest | 4 |
| BluetoothDiscoveryPermissionTest | 3 |
| LocalTransportOwnershipTest | 3 |
| Total | 21 |

The JSON and caller enumerate every full `class#method`, including `immediateHomeReentryReleasesOldLeaseWhileOutgoingDiscoveryStillFades`. A single bounded instrumentation invocation selects those exact methods. Actual process exit must be successful, with complete raw stdout/stderr and no ignored failure/daemon-restart messages. The pure parser requires exactly one start and one pass for every unique selector, each `numtests=21`, final `OK (21 tests)`, one successful completion code `-1` and zero failures, errors or skips. Missing, duplicate, unexpected, overlapping or malformed outcomes refuse acceptance.

These tests use Compose rules and permission/context/service/broadcast stand-ins or fictional callbacks. Even a real 21-case pass would cover that scope; it would not establish hardware discovery, a two-device transfer, full MainActivity behavior, another Android API or provider acceptance. Production captures have a separate receipt.

## Fresh profile and offline boundary

Proposed console/ADB pair is **5582/5583**, dedicated host ADB server **5149**, dead HTTP proxy **5151**. These are unreserved proposals until root's fresh actual port/identity/capacity gate. Default 5037 is excluded everywhere.

Use a new ignored repo root `build/permission-recovery-device-validation-20261006-<root-approved-32hex>`, with owner-only directories/files and fresh `profile/user-home`, `profile/emulator-home`, `profile/avd`, `data` and `tmp`. Redirect only `ANDROID_USER_HOME`, `ANDROID_EMULATOR_HOME`, `ANDROID_AVD_HOME` and `TMPDIR` into this root; preserve inherited host HOME and do not add CFFIXED_USER_HOME, following root's environment constraint. The AVD is `JustSharePermissionRecovery20261006_<same-32hex>`. Create its `.ini` and allowlisted config from the JSON, rather than copying an existing AVD. Use fresh 3GiB data, RAM1536MiB, two cores, 1080×2400 and density420. Existing private userdata, snapshots, accounts and credentials are never read or copied.

The installed immutable image is API35 `google_apis_playstore/arm64-v8a`, revision9, extension13. Eleven metadata/system/vendor/kernel/ramdisk/encryption files and only the named original **public** `Pixel_8_Android15_35_-_B.avd/config.ini` were statically hashed. Original public config SHA256 is `f4384c476ef8ccf5f38720f201d9d5755f2db618ab8e810490c65c112a488188`. These are preparation baselines; actual runtime before/after comparison is still required. No existing private AVD bytes were inspected.

The installed image lacks stock `userdata.img`. Root checked the [official emulator command-line documentation](https://developer.android.com/studio/run/emulator-commandline), which describes stock data initialization into `userdata-qemu.img`. A wholly new config-only `-wipe-data` boot remains uncertain for this installed image. If initialization or boot fails, stop before installation; do not fabricate readiness or fall back to existing private data.

Candidate emulator arguments include `-http-proxy http://127.0.0.1:5151` and `-dns-server 127.0.0.1`, intending to route outgoing TCP to an unavailable loopback proxy and prevent external DNS resolution. **Installed SDK syntax, semantics and offline isolation remain unverified.** A separately reviewed supported host boundary must cover other egress as well. The JSON describes a candidate network-deny sandbox allowing only owned loopback5149/5582/5583 and denying5151/DNS53; it does not assert that this sandbox is supported or effective. No global host firewall, DNS or radio changes are authorized by this preparation.

Normal SharingApp startup initializes configured Firebase/AppCheck, telemetry, conditional RevenueCat, purchase observation and BillingClient. Existing SDK initialization may attempt calls even under a blocked network boundary. Do not claim zero SDK calls or provider acceptance. No account login, purchase, provider dashboard configuration, external message or AppCheck token registration belongs to this scope. Do not capture logcat SDK tokens or private contents.

## Proposed ownership and bounded lifecycle

All shell/child commands must begin with RTK, including each independent subprocess. The JSON includes candidate vectors; installed ADB/emulator option ordering and support are pending root verification. A foreground dedicated server candidate is `rtk proxy <pinned-adb> -L tcp:127.0.0.1:5149 -P 5149 --one-device emulator-5582 nodaemon server`. Every client explicitly names `-H 127.0.0.1 -P 5149 -s emulator-5582`; no client may autostart another server.

A separate root-reviewed supervisor must own this foreground server and the emulator launched in recorded new sessions, tracking both launcher/RTK processes and actual ADB/emulator/QEMU descendants. Before every mutation, require exact live PID+lstart+uid+executable mapping+command+parent/group identity, dedicated server listener, emulator serial, unique AVD, boot ID, API35, ARM64, `ro.kernel.qemu=1`, boot completion and user0. The original PID alone is insufficient; reused or mismatched identities are foreign. Track every launched child with bounds and reaping; no helper imports or app-specific grants are inherited.

Root owns one independent **600-second native window** beginning before native preflight. It includes every query, launch, boot, install, test, UI action, teardown and receipt. Work ends by480s, cleanup by570s and final publication by600s, with no renewal, target replacement or interactive child continuing beyond the window. Each subprocess timeout is no greater than its remaining phase window. Suggested command caps are in JSON: query3s, boot total120s, install/pull30s each, instrumentation240s, UI action/capture10s, teardown/reap5s per bounded stage. Output is capped at16MiB per command. Startup and work must reserve cleanup time.

Fresh preflight requires actual memory pressure exactly1, at least15GiB free disk (3GiB data reserve plus12GiB floor), free proposed ports and no foreign actual Java/Gradle/emulator/QEMU/Godot process. Classification uses actual executable plus PID/start identity; shell/RTK argument text is not native work. Exclude only actual observer ancestors and exact owned identities. During work, pressure must remain1 and free disk at least12GiB; foreign work, resource failure, ownership drift or immutable-input change aborts owned work.

Cleanup signals only recorded owned sessions/descendants after rechecking identity and group membership. TERM then bounded KILL escalation is allowed only for those exact identities; reap all launched children. Never use killall, `adb kill-server`, an emulator-console global kill, foreign adoption, an existing server grant or an invented coordinator lease. After release, an independent observation must prove no matching owned identities, no live owned group members, freed owned ports, unchanged preexisting servers and unchanged SDK/public config bytes. Preserve raw command/test metadata and final resource proof. Native success requires complete outcomes and release proof; late or incomplete cleanup cannot become PASS.

## Production UI observation

If the same window has time and capacity after tests, clear only the owned app's synthetic test state and start **real MainActivity**. At normal and1.3 font scale, actually deny Android permission requests; capture recovery, final permission row and Allow/Open app settings/Not now actions; use Not now to browse Home, History and Settings without bouncing back to recovery. Inspect available Bluetooth/Wi-Fi contextual recovery and app-settings return without automatic navigation. Record and restore the original font scale. Keep UI hierarchy/PNG hashes and action records separate from instrumentation outcomes; missing captures stay UNRUN.

Historical Home requires both Wi-Fi and Bluetooth through a nondismissible hardware modal. Root's Home successor is recorded but the APK pair is unbound. An emulator hardware modal or unsupported route must be captured as a blocker, without fake hardware overrides, invented peers or discovery success. No screenshot score or native UI acceptance is supplied here.

## Remaining blockers

Actual APK/certificate/source pins and runtime admission are absent. This preparation does not implement a native supervisor; root separately reviews foreground/new-session ownership, descendant accounting, offline isolation, bounded cleanup and actual tool syntax. Fresh data initialization may fail because stock `userdata.img` is absent. The historical baseline must be rebound to the current source and actual pair before native review.

The five named helper files are pinned source references only. Hearth's app-specific disabled gate is reference evidence, **not a Just Share authority rule**; Pace's fixed serial and Dawn's separate lifecycle are not inherited. No SDK help, Java/Gradle, existing helper/import, process inventory, ADB, server, AVD, browser, remote/provider action or pixel capture was executed by this preparation. Exactly zero device cases executed; native pass, screenshots and after-release proof remain absent.
