"""Pinned current-source unit/Android-test compilation through the maintained Watchdog."""
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
HELPER = Path("/Users/speketi/Projects/HearthLedger/tools/run_offline_release.py")
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
    "scripts/run_transfer_exit_validation.py",
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
    parser.add_argument("--attempt", choices=("one", "two", "three", "four"), default="one")
    args = parser.parse_args()
    packet = REPO / ("docs/reviews/transfer-exit-20261006/compile-" + args.attempt)
    claim_path = Path("/private/tmp/UX-JUSTSHARE-COMPILE-20261006-" + args.attempt.upper() + ".json")
    if args.attempt != "one":
        previous = json.loads((REPO / "docs/reviews/transfer-exit-20261006/compile-one/result.json").read_text())
        raw = (REPO / "docs/reviews/transfer-exit-20261006/compile-one/gradle.log").read_text()
        assert previous["owned_cleanup"] and previous["failure"]
        assert "version: '8.10.1'" in raw and "could not resolve plugin artifact" in raw
    if args.attempt in ("three", "four"):
        previous = json.loads((REPO / "docs/reviews/transfer-exit-20261006/compile-two/result.json").read_text())
        assert previous["owned_cleanup"] and previous["failure"] == "AssertionError('Concurrent shared work appeared; stop own batch')"
    if args.attempt == "four":
        previous = json.loads((REPO / "docs/reviews/transfer-exit-20261006/compile-three/result.json").read_text())
        raw = (REPO / "docs/reviews/transfer-exit-20261006/compile-three/gradle.log").read_text()
        assert previous["owned_cleanup"] and previous["failure"]
        assert "Unresolved reference 'assertDoesNotExist'" in raw
    assert command("git", "rev-parse", "HEAD") == args.source
    subprocess.run(["rtk", "proxy", "git", "-c", "core.fsmonitor=false", "diff", "--quiet", "HEAD", "--", "app", "gradle", "build.gradle", "settings.gradle", "gradle.properties"], cwd=REPO, check=True, timeout=10)
    pins = {name: sha(REPO / name) for name in SOURCE_PATHS}
    assert sha(HELPER) == "b7667e7646e9e0a6d650fecdfdc8a4563f5a1153d068125c781744684283202e"
    assert not packet.exists() and not claim_path.exists(), "One-use execution already exists"
    sys.path.insert(0, str(HELPER.parent))
    spec = importlib.util.spec_from_file_location("unchanged_transfer_watchdog", HELPER)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    pressure = int(command("sysctl", "-n", "kern.memorystatus_vm_pressure_level"))
    assert pressure <= 2 and shutil.disk_usage(REPO).free >= 12 * 1024**3
    assert not foreign_work(module.processes()), "Shared Java/AVD/engine work is live"
    packet.mkdir(parents=True, mode=0o700)
    claim = {"actor": "root", "coordinator_grant": False, "source": args.source,
             "started_after_epoch": time.time(), "pressure": pressure,
             "scope": "Android-test Kotlin compilation only; twelve prepared cases" if args.attempt == "four" else "Current four JVM cases and Kotlin compilation of eleven Android cases only",
             "dependency_resolution": "offline" if args.attempt in ("one", "four") else "online after missing offline plugin",
             "helper_sha256": sha(HELPER), "source_pins": pins}
    with claim_path.open("x") as stream:
        json.dump(claim, stream, indent=2)
        stream.write("\n")
    (packet / "actor-claim.json").write_text(json.dumps(claim, indent=2) + "\n")
    tasks = [":app:compileDebugAndroidTestKotlin"] if args.attempt == "four" else [":app:testDebugUnitTest", "--tests", "*TransferProgressSessionTest", ":app:compileDebugAndroidTestKotlin"]
    argv = ["rtk", "proxy", str(REPO / "gradlew"), *tasks, "--no-daemon", "--max-workers=1",
            "-Dorg.gradle.jvmargs=-Xmx1536m", "-Pkotlin.compiler.execution.strategy=in-process"]
    if args.attempt in ("one", "four"):
        argv.insert(3, "--offline")
    env = dict(os.environ, JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home")
    watch = module.Watchdog(REPO, packet, "JUSTSHARE-EXIT-COMPILE-20261006-" + args.attempt.upper(), seconds=300)
    result = {"source": args.source, "source_pins": pins, "argv": argv,
              "apk_assembly": False, "android_cases_executed": 0,
              "coordinator_grant": False, "watchdog_completed": False,
              "foreign_work_observations": []}
    failure = None

    def shared_boundary():
        rows, owned, _ = watch.sample()
        foreign = foreign_work(rows, owned)
        if foreign:
            result["foreign_work_observations"].append([
                {"pid": pid, "pgid": rows[pid]["pgid"],
                 "identity_sha256": rows[pid]["identity"],
                 "command_sha256": rows[pid]["commandHash"]} for pid in foreign])
            raise AssertionError("Concurrent shared work appeared; stop own batch")

    try:
        watch.run(argv, env, "gradle.log", callback=shared_boundary)
        result["watchdog_completed"] = True
        assert command("git", "rev-parse", "HEAD") == args.source
        assert all(sha(REPO / name) == pin for name, pin in pins.items())
        if args.attempt == "four":
            proof = json.loads((REPO / "docs/reviews/transfer-exit-20261006/compile-three/jvm-test-proof.json").read_text())
            assert all(pins[name] == value for name, value in proof["source_pins"].items())
            result.update(jvm_tests_executed=0, prior_jvm_proof=proof["report_sha256"],
                          android_kotlin_compilation=True, inputs_unchanged=True)
        else:
            report = REPO / "app/build/test-results/testDebugUnitTest/TEST-com.blackandblue.justshare.domain.transfer.TransferProgressSessionTest.xml"
            assert report.stat().st_mtime >= claim["started_after_epoch"]
            suite = ET.fromstring(report.read_bytes())
            counts = {name: int(suite.attrib[name]) for name in ("tests", "failures", "errors", "skipped")}
            assert counts == {"tests": 4, "failures": 0, "errors": 0, "skipped": 0}
            result.update(tests=counts, report_sha256=sha(report), inputs_unchanged=True)
            (packet / report.name).write_bytes(report.read_bytes())
    except BaseException as error:
        failure = repr(error)
    finally:
        result.update(failure=failure, owned_cleanup=watch.cleanup(),
                      owned_identities=watch.owned, samples=watch.samples,
                      seconds=round(time.monotonic() - watch.start, 3))
        (packet / "result.json").write_text(json.dumps(result, indent=2) + "\n")
        print(json.dumps({key: value for key, value in result.items()
                          if key not in ("samples", "owned_identities", "source_pins")}))
    raise SystemExit(0 if failure is None and result["owned_cleanup"] else 1)


if __name__ == "__main__":
    main()
