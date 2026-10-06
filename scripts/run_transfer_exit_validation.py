"""One-use current-source unit/Android-test compilation through the maintained Watchdog."""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

REPO = Path(__file__).resolve().parents[1]
PACKET = REPO / "docs/reviews/transfer-exit-20261006/compile-one"
HELPER = Path("/Users/speketi/Projects/HearthLedger/tools/run_offline_release.py")
CLAIM = Path("/private/tmp/UX-JUSTSHARE-COMPILE-20261006-ONE.json")
SOURCE_PATHS = [
    "app/src/main/java/com/blackandblue/justshare/CommunicationService.kt",
    "app/src/main/java/com/blackandblue/justshare/domain/transfer/TransferProgressSession.kt",
    "app/src/main/java/com/blackandblue/justshare/presentation/TransferViewModel.kt",
    "app/src/main/java/com/blackandblue/justshare/ui/components/TransferExitGuard.kt",
    "app/src/main/java/com/blackandblue/justshare/ui/screens/TransferProgressScreen.kt",
    "app/src/main/java/com/blackandblue/justshare/ui/screens/RemoteTransferProgressScreen.kt",
    "app/src/test/java/com/blackandblue/justshare/domain/transfer/TransferProgressSessionTest.kt",
    "app/src/androidTest/java/com/blackandblue/justshare/ui/components/TransferExitGuardTest.kt",
    "app/src/androidTest/java/com/blackandblue/justshare/presentation/TransferRetryStateTest.kt",
    "app/src/androidTest/java/com/blackandblue/justshare/TransferConnectionOwnershipTest.kt",
    "build.gradle", "settings.gradle", "app/build.gradle", "gradle.properties",
    "gradle/wrapper/gradle-wrapper.properties",
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def command(*argv):
    return subprocess.check_output(["rtk", "proxy", *argv], cwd=REPO,
                                   text=True, timeout=10).strip()


def foreign_work(rows, allowed=()):
    return [pid for pid, row in rows.items() if pid not in allowed and any(
        marker in row["_command"] for marker in ("/bin/java ", "GradleDaemon", "qemu-system", "Godot"))]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True)
    args = parser.parse_args()
    assert command("git", "rev-parse", "HEAD") == args.source
    subprocess.run(["rtk", "proxy", "git", "-c", "core.fsmonitor=false", "diff", "--quiet", "HEAD", "--", "app", "gradle", "build.gradle", "settings.gradle", "gradle.properties"], cwd=REPO, check=True, timeout=10)
    pins = {name: sha(REPO / name) for name in SOURCE_PATHS}
    assert sha(HELPER) == "b7667e7646e9e0a6d650fecdfdc8a4563f5a1153d068125c781744684283202e"
    assert not PACKET.exists() and not CLAIM.exists(), "One-use execution already exists"
    sys.path.insert(0, str(HELPER.parent))
    spec = importlib.util.spec_from_file_location("unchanged_transfer_watchdog", HELPER)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    pressure = int(command("sysctl", "-n", "kern.memorystatus_vm_pressure_level"))
    assert pressure <= 2 and shutil.disk_usage(REPO).free >= 12 * 1024**3
    assert not foreign_work(module.processes()), "Shared Java/AVD/engine work is live"
    PACKET.mkdir(parents=True, mode=0o700)
    claim = {"actor": "root", "coordinator_grant": False, "source": args.source,
             "started_after_epoch": time.time(), "pressure": pressure,
             "scope": "Current four JVM cases and Kotlin compilation of eleven Android cases only",
             "helper_sha256": sha(HELPER), "source_pins": pins}
    with CLAIM.open("x") as stream:
        json.dump(claim, stream, indent=2)
        stream.write("\n")
    (PACKET / "actor-claim.json").write_text(json.dumps(claim, indent=2) + "\n")
    argv = ["rtk", "proxy", str(REPO / "gradlew"), "--offline",
            ":app:testDebugUnitTest", "--tests", "*TransferProgressSessionTest",
            ":app:compileDebugAndroidTestKotlin", "--no-daemon", "--max-workers=1",
            "-Dorg.gradle.jvmargs=-Xmx1536m", "-Pkotlin.compiler.execution.strategy=in-process"]
    env = dict(os.environ, JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home")
    watch = module.Watchdog(REPO, PACKET, "JUSTSHARE-EXIT-COMPILE-20261006-ONE", seconds=300)
    result = {"source": args.source, "source_pins": pins, "argv": argv,
              "apk_assembly": False, "android_cases_executed": 0,
              "coordinator_grant": False, "watchdog_completed": False}
    failure = None

    def shared_boundary():
        rows, owned, _ = watch.sample()
        assert not foreign_work(rows, owned), "Concurrent shared work appeared; stop own batch"

    try:
        watch.run(argv, env, "gradle.log", callback=shared_boundary)
        result["watchdog_completed"] = True
        assert command("git", "rev-parse", "HEAD") == args.source
        assert all(sha(REPO / name) == pin for name, pin in pins.items())
        report = REPO / "app/build/test-results/testDebugUnitTest/TEST-com.blackandblue.justshare.domain.transfer.TransferProgressSessionTest.xml"
        assert report.stat().st_mtime >= claim["started_after_epoch"]
        suite = ET.fromstring(report.read_bytes())
        counts = {name: int(suite.attrib[name]) for name in ("tests", "failures", "errors", "skipped")}
        assert counts == {"tests": 4, "failures": 0, "errors": 0, "skipped": 0}
        result.update(tests=counts, report_sha256=sha(report), inputs_unchanged=True)
        (PACKET / report.name).write_bytes(report.read_bytes())
    except BaseException as error:
        failure = repr(error)
    finally:
        result.update(failure=failure, owned_cleanup=watch.cleanup(),
                      owned_identities=watch.owned, samples=watch.samples,
                      seconds=round(time.monotonic() - watch.start, 3))
        (PACKET / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps({key: value for key, value in result.items()
                          if key not in ("samples", "owned_identities", "source_pins")}))
    raise SystemExit(0 if failure is None and result["owned_cleanup"] else 1)


if __name__ == "__main__":
    main()
