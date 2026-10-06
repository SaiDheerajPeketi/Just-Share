# Debug packaging outcome — 6 October 2026

The current Home fix is committed at `d66c25bf172fc821052298f34a6936bbf3b925a3`, app-src `5b78e7caa7244e6aa7795e118dbd835f2b34cae3`. It remains source-reviewed, without current compilation, installed UI or device-test acceptance.

The first execution-context check (exec53425) refused before a claim, Watchdog, Java or SDK work. Root had incorrectly checked the Projects/default-permissions context separately from the actual Just-Share/escalated context. The final PATH-only rebind preserves that refusal and every guard; all175 current source/build/configuration/JDK/helper/signing/SDK inputs then matched in the exact execution context.

At caller source `965e559de11b139a02f6cc0dc52250068e5094b0` and caller SHA256 `8b7b4e6b73553a612b138a0ff34f1171b3ab5f68f5660203cbb7454e9eac6f08`, exec61233 actually ran the two offline assemble requests under the unchanged300-second Watchdog. Gradle failed in2seconds resolving the configured AGP8.10.1 plugin marker; total caller time was3.633seconds. This is a failed packaging attempt, not a compilation or APK pass. No package task executed, no APK was verified, no SDK inspection ran, and no JVM or Android case was requested/executed. The earlier8 JVM pass remains bound to its earlier source.

The original one-use claim, raw log and failed result are retained in `permission-recovery-debug-packaging-20261006-one`. Result SHA256: `49ff83069fb5605e9980aef7365bf51210fb70fd79e888d7947c45868f82a5b9`. Raw log SHA256: `24bfcae5f076a93b5373660301d414750809a0fb35c05eddb49a1cccd6a0ba74`. Before/after inputs match; owned cleanup reports true.

Independent actual process inspection at1791274885.635351 verifies all three recorded identities and groups51240/51277 absent. Release SHA256: `ce55772f03d1feeeeed1900de67a6a30288f29b252ae38f9cb488ddf89795e62`. Pressure1/free24.4GiB and no foreign heavy worker are observations at that instant. No coordinator grant is claimed.

The next preparation uses normal dependency resolution for the same configured versions and the same two assemble tasks/six SDK inspections, with a distinct one-use packet. No source, toolchain, signing key, provider configuration, phone or release change is part of that successor. APK binding and independently reviewed disposable-device validation remain pending.
