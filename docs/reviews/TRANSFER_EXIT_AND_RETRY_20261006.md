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
cleanup cannot stop a replacement's executor or foreground service.

## Prepared validation

- Four `TransferProgressSessionTest` JVM cases cover cancelled/queued delivery,
  replacement progress, initial incoming progress and concurrent clear/publication.
- Seven `TransferExitGuardTest` Android cases cover Keep, Stop, actual Android Back,
  completed exit, completion while a prompt is open, recreation and the latest callback.
- Two `TransferRetryStateTest` Android cases exercise the actual ViewModel and ordered
  broadcasts while checking the selected original fixture bytes stay intact.
- Two `TransferConnectionOwnershipTest` Android cases invoke the actual service's
  stale cleanup/install guards against replacement sockets and executor. They do not
  start a service, transfer files or connect to a peer.

`scripts/run_transfer_exit_validation.py` is a pinned caller of the unchanged
HearthLedger Watchdog. It pins source and helper hashes, admits no other Java/AVD/engine
worker, uses offline JDK 21/one worker/1536 MiB/in-process Kotlin, and retains the
300-second deadline and raw failure log. It runs the four JVM cases and compiles the
eleven Android cases. Each attempt has a distinct one-use packet. It does not assemble an APK or execute Android tests. The
actor claim explicitly records that it is not a coordinator grant.

At source handoff, authored whitespace checks and independent bounded source review
of progress isolation, navigation timing and service ownership pass. All Kotlin compilation, JVM
execution, Android execution and visual review are still **unrun**. Results belong
in the caller's separate packets after execution.

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
versions are unchanged; attempt three is still unrun at this handoff.

## Limits

Real Bluetooth/Wi-Fi Direct transfers and replacement connections require runtime
validation. The remote transfer flow keeps its existing reset behavior; selected
file preservation applies to incomplete local transfers. Existing delayed host
restart/stale group-info callbacks are outside this iteration. No protocol,
provider, manifest or dependency change is included. Original selected files are
not deleted by these changes.
