# Transfer exit and retry

Android Back, the header arrow and the footer now share one exit action. During a
transfer they offer **Keep transferring** or **Stop transfer**. Completed or failed
transfers can leave directly. Manual exit disables the delayed completion navigation
so it cannot reset the next screen a second time.

An incomplete local transfer retains the selected files and their metadata. Its
start/progress flags are cleared so Discovery can start a retry. Each Wi-Fi
connection owns a progress generation; clearing invalidates replay and queued old
broadcasts. Navigation's explicit start flag does not erase a fast incoming
completion. Worker cleanup and socket/state installation use captured ownership
under the same service monitor as START. Network waits use worker-owned sockets
outside that monitor. An old worker can close only its own local sockets, and its
cleanup cannot stop a replacement's executor or foreground service. The sending
stream becomes available only after both peers complete their existing UTF headers,
so a send request cannot write file metadata into the handshake.

## Prepared validation

- Four `TransferProgressSessionTest` JVM cases cover cancelled/queued delivery,
  replacement progress, initial incoming progress and concurrent clear/publication.
- Seven `TransferExitGuardTest` Android cases cover Keep, Stop, actual Android Back,
  completed exit, completion while a prompt is open, recreation and the latest callback.
- Two `TransferRetryStateTest` Android cases exercise the actual ViewModel and ordered
  broadcasts while checking the selected original fixture bytes stay intact.
- Three `TransferConnectionOwnershipTest` Android cases invoke the actual service's
  stale cleanup/install guards against replacement sockets and executor. They do not
  start a service or transfer files. The handshake case uses a local loopback peer
  with bounded socket waits and fixture cleanup; no external device or provider is used.

`scripts/run_transfer_exit_validation.py` is a pinned caller of the unchanged
HearthLedger Watchdog. It pins source and helper hashes, admits no other Java/AVD/engine
worker, uses offline JDK 21/one worker/1536 MiB/in-process Kotlin, and retains the
300-second deadline and raw failure log. It runs the four JVM cases and compiles the
eleven initial Android cases (twelve after the handshake repair). Each attempt has
a distinct one-use packet. It does not assemble an APK or execute Android tests. The
actor claim explicitly records that it is not a coordinator grant.

Authored whitespace checks and independent bounded source review of progress
isolation, navigation timing and service ownership pass. Results belong in the
caller's separate packets. Android execution and visual review remain **unrun**.

Attempt one stopped before compilation because the host's offline cache lacked the
existing Android plugin 8.10.1. Its raw failure and released worker identities are
retained in `compile-one`; no tests ran. Attempt two permits ordinary dependency
resolution with the same unchanged 300-second Watchdog and resource bounds. It is
only admitted after checking that exact plugin failure and release. It stopped in
78.349 seconds when concurrent shared work appeared; its owned cleanup passed and
independent host inspection found all three recorded PIDs absent. No tests or
Android compilation received credit. Attempt three is a distinct one-use successor,
admitted only after that exact retained stop/release and a new idle-host check; it
also records any conflicting worker's hashed identity. Build files and dependency
versions are unchanged. Attempt three ran 209.680 seconds: main/unit compilation
completed and all four JVM cases actually passed with fresh XML
`8633832cea7da70ee8396ee4cf9206484dff8a817e03c6ec6caee7dc9f72b109`.
Android-test compilation then failed on an invalid top-level `assertDoesNotExist`
import; the method is available on the test interaction without that import. The
failure remains preserved and the overall batch failed. All nine owned identities
and two groups were independently verified absent afterward.

The invalid import is removed. Independent review also confirmed that publishing
the output stream before the UTF handshake could interleave a send payload with
the header. Stream publication now follows successful peer-header reading, inside
the existing generation guard. The wire format is unchanged. Attempt four is a
separate offline main/Android-test compilation only; it does not repeat the four
unchanged pure tests. Its exact source pins must match the earlier JVM proof for
that test/implementation pair. Attempt four succeeded offline in 28 seconds
(29.444-second caller), recompiling main and all twelve prepared Android cases.
No JVM cases were repeated and no Android case executed. Its exact source pins
stayed unchanged. Independent host inspection verified all three owned identities
and both groups absent. Native memory pressure remains 2; the maintained emulator
gate requires 1, so installed interactions/captures remain pending.

## Limits

Real Bluetooth/Wi-Fi Direct transfers and replacement connections require runtime
validation. The remote transfer flow keeps its existing reset behavior; selected
file preservation applies to incomplete local transfers. Existing delayed host
restart/stale group-info callbacks are outside this iteration. No protocol,
provider, manifest or dependency change is included. Original selected files are
not deleted by these changes.
