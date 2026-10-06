"""Source-only Just Share disposable-device proposal and strict evidence checks.

No SDK/process/resource query, subprocess, native import, profile creation or
device action is implemented. --execute always refuses before reading inputs.
Filling JSON fields or supplying a grant cannot enable native admission.
"""

import argparse
import hashlib
import json
import math
import re
from pathlib import Path


REPO = Path(__file__).resolve().parents[1]
PREPARATION = REPO / "docs/permission-recovery-device-validation-preparation-20261006.json"
PACKAGE = "com.blackandblue.justshare"
TEST_PACKAGE = PACKAGE + ".test"
RUNNER = TEST_PACKAGE + "/androidx.test.runner.AndroidJUnitRunner"
ACCEPTED_SOURCE = "965536c13b4678aa302f75f100ecab52e065f857"
APP_TREE = "9177d21b523f35e3aa53a8047c12c3f895e34afb"
APP_SRC_TREE = "658642c649f9f888804012e0aa3b6e713d24ccf2"
GIB = 1024 ** 3
LIMITS = {"total_seconds": 600, "work_cutoff_seconds": 480,
          "cleanup_cutoff_seconds": 570, "final_cutoff_seconds": 600,
          "preflight_free_bytes": 15 * GIB, "runtime_free_floor_bytes": 12 * GIB,
          "fresh_data_reserve_bytes": 3 * GIB, "memory_pressure_required": 1,
          "emulator_ram_mib": 1536, "emulator_cores": 2, "renewals": 0}
SUITES = {
    PACKAGE + ".ui.screens.PermissionsScreenTest": (
        "finalPermissionAndRecoveryActionsRemainAccessible",
        "returningFromSettingsRefreshesActualGrantsWithoutAutoNavigation",
        "unavailableSettingsKeepsRecoveryAndBrowseExitUsable"),
    PACKAGE + ".navigation.PermissionRecoveryNavigationTest": (
        "directShareDenialCanBrowseAndResumeDoesNotBounceNonlocalPages",
        "foregroundRevocationDisposesDiscoveryBeforeRecoveryAndCanResume",
        "otherTransportGrantCannotConstructBluetoothDiscovery",
        "receiverRoleSurvivesDirectEntryRecoveryWithoutInventingFiles",
        "selectedFilesAndSenderRoleSurviveDenialSettingsAndReturnToPicker"),
    PACKAGE + ".presentation.WifiPermissionRecoveryTest": (
        "deniedLateConnectionAndDiscoveryCallbacksCannotStartLocalService",
        "revocationStopsExistingServiceAndRejectsQueuedConnectionCallback",
        "settingsRecoveryTargetsOnlyThisAppsPermissionPage"),
    PACKAGE + ".presentation.TransferRetryStateTest": (
        "cancelledAndQueuedOldProgressCannotBlockSelectedFileRetry",
        "fastIncomingCompletionSurvivesTheNavigationStart",
        "permissionInterruptionInvalidatesOldUpdatesAndRetainsExactSelectionForRetry",
        "permissionLossAfterFastIncomingCompletionDoesNotReplaceTheFinishedResult"),
    PACKAGE + ".data.chat.BluetoothDiscoveryPermissionTest": (
        "liveGrantRevocationRejectsProtectedMetadataAndUnregistersFoundReceiver",
        "protectedGetterRaceIsCaughtAndQueuedCallbacksStayInactive",
        "stopWithoutScanGrantUnregistersAndRegrantRegistersOnceAgain"),
    PACKAGE + ".navigation.LocalTransportOwnershipTest": (
        "immediateHomeReentryReleasesOldLeaseWhileOutgoingDiscoveryStillFades",
        "receiverProgressReusesDiscoveryOwnerAndRevocationKeepsReceiveDirection",
        "senderProgressReusesDiscoveryOwnerAndRevocationStopsItWithoutLosingFiles"),
}
SELECTORS = tuple(cls + "#" + method for cls, methods in SUITES.items() for method in methods)
HEX = re.compile(r"[a-f0-9]{64}\Z")
BOOT_ID = re.compile(r"[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}\Z")


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()


def sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def native_admission():
    raise NotImplementedError(
        "SOURCE_ONLY: APK/certificate/source binding absent; supported offline "
        "SDK boundary and complete descendant containment unverified; no fresh "
        "root port/identity/capacity admission. No native action implemented.")


def instrumentation_results(raw, expected=SELECTORS):
    """Require a unique start/pass for every exact class#method and exact totals."""
    require(type(raw) is str and len(raw.encode()) <= 16 * 1024 ** 2,
            "instrumentation output missing or excessive")
    require(expected == SELECTORS and len(set(expected)) == len(expected) == 21,
            "only the exact selected 21 methods are admissible")
    wanted = set(expected)
    fields, starts, passes, pending = {}, [], [], None
    for line in raw.splitlines():
        status = re.fullmatch(r"INSTRUMENTATION_STATUS: ([A-Za-z_]+)=(.*)", line)
        require(not line.startswith("INSTRUMENTATION_STATUS:") or status,
                "malformed status field")
        if status:
            require(status[1] not in fields, "duplicate status field")
            fields[status[1]] = status[2]
        code = re.fullmatch(r"INSTRUMENTATION_STATUS_CODE: (-?\d+)", line)
        require(not line.startswith("INSTRUMENTATION_STATUS_CODE:") or code,
                "malformed status code")
        if code:
            selector = fields.get("class", "") + "#" + fields.get("test", "")
            require(selector in wanted and fields.get("numtests") == "21",
                    "unexpected class/method/count")
            require(code[1] in ("1", "0"), "failed, errored, ignored or assumption-skipped case")
            if code[1] == "1":
                require(pending is None and selector not in starts, "duplicate or overlapping start")
                starts.append(selector)
                pending = selector
            else:
                require(pending == selector and selector not in passes, "pass without its unique start")
                passes.append(selector)
                pending = None
            fields = {}
    require(not fields and pending is None and set(starts) == wanted and set(passes) == wanted
            and len(starts) == len(passes) == 21, "missing or duplicate outcome")
    require(re.findall(r"^INSTRUMENTATION_CODE: (-?\d+)\s*$", raw, re.M) == ["-1"],
            "missing unique successful completion")
    require(re.findall(r"^OK \((\d+) tests?\)\s*$", raw, re.M) == ["21"],
            "missing exact final test total")
    require(not re.search(r"INSTRUMENTATION_(?:FAILED|ABORTED)|FAILURES!!!|"
                          r"^INSTRUMENTATION_RESULT: (?:shortMsg|stack)=", raw, re.M),
            "aborted instrumentation")
    return {"case_count": 21, "failures": 0, "errors": 0, "skips": 0,
            "outcomes": [{"selector": selector, "status": "PASS"} for selector in expected],
            "raw_stdout_sha256": sha256(raw.encode())}


def parse_processes(process_text, executable_text):
    """Pure parser for future bounded ps observations; never observes the host."""
    executable_map = {}
    for line in executable_text.splitlines():
        match = re.fullmatch(r"\s*(\d+)\s+(/[^\n]+)", line)
        require(match and int(match[1]) > 0 and int(match[1]) not in executable_map,
                "partial or duplicate executable inventory")
        executable_map[int(match[1])] = match[2]
    pattern = re.compile(r"\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+"
                         r"(\w+\s+\w+\s+\d+\s+\d+:\d+:\d+\s+\d+)\s+(.*)")
    rows = {}
    for line in process_text.splitlines():
        match = pattern.fullmatch(line)
        require(match and int(match[1]) > 0 and int(match[1]) not in rows and match[6],
                "partial or duplicate process inventory")
        pid, ppid, pgid, uid = map(int, match.group(1, 2, 3, 4))
        require(pid in executable_map, "actual executable missing; do not classify argv substrings")
        start = " ".join(match[5].split())
        executable = executable_map[pid]
        rows[pid] = {"pid": pid, "ppid": ppid, "pgid": pgid, "uid": uid,
                     "lstart": start, "executable": executable,
                     "command_sha256": sha256(match[6].encode()),
                     "identity_sha256": sha256(canonical([pid, uid, start, executable]))}
    require(rows and set(rows) == set(executable_map), "empty or raced inventory")
    return rows


def foreign_heavy(rows, owned_identities, observer_ancestors):
    """A matching saved PID alone never grants ownership or excludes a process."""
    foreign = []
    for pid, row in rows.items():
        base = Path(row["executable"]).name.lower()
        heavy = base in ("java", "java.bin", "emulator", "emulator64-arm", "qemu", "avd")
        heavy = heavy or base.startswith(("qemu-system", "godot", "gradle"))
        if pid not in observer_ancestors and heavy and owned_identities.get(pid) != row["identity_sha256"]:
            foreign.append({key: row[key] for key in ("pid", "pgid", "identity_sha256", "command_sha256")})
    return foreign


def resource_admission(rows, owned_identities, observer_ancestors, pressure, free_bytes, preflight=True):
    require(type(pressure) is int and pressure == 1, "native memory pressure must equal 1")
    floor = LIMITS["preflight_free_bytes"] if preflight else LIMITS["runtime_free_floor_bytes"]
    require(type(free_bytes) is int and free_bytes >= floor, "insufficient disk floor/reserve")
    require(not foreign_heavy(rows, owned_identities, observer_ancestors), "foreign native workload")
    return True


def target_identity(observed, bound):
    keys = ("serial", "avd_name", "boot_id", "api", "abi", "qemu", "current_user",
            "adb_port", "console_port",
            "server_pid", "server_lstart", "server_identity_sha256", "server_binary_sha256",
            "server_listener", "emulator_pid", "emulator_lstart", "emulator_identity_sha256",
            "emulator_pgid", "emulator_executable")
    require(all(key in observed and observed[key] == bound.get(key)
                and type(observed[key]) is type(bound.get(key)) for key in keys), "target/server identity drift")
    require(type(bound["api"]) is int and bound["api"] == 35 and bound["abi"] == "arm64-v8a"
            and bound["qemu"] == "1" and bound["current_user"] == "0"
            and re.fullmatch(r"emulator-\d{4,5}", bound["serial"])
            and BOOT_ID.fullmatch(bound["boot_id"]), "not the newly bound API35 ARM64 emulator/user")
    require(type(bound["adb_port"]) is int and bound["adb_port"] == 5149
            and type(bound["console_port"]) is int and bound["console_port"] == 5582
            and bound["serial"] == "emulator-5582" and bound["server_listener"] == "127.0.0.1:5149"
            and re.fullmatch(r"JustSharePermissionRecovery20261006_[a-f0-9]{32}", bound["avd_name"]),
            "wrong proposed port or fresh profile scope")
    require(all(type(bound[key]) is int and bound[key] > 1
                for key in ("server_pid", "emulator_pid", "emulator_pgid")), "invalid owned process IDs")
    require(all(HEX.fullmatch(bound[key]) for key in
                ("server_identity_sha256", "server_binary_sha256", "emulator_identity_sha256")),
            "missing process/executable pins")
    return True


def apk_parity(expected, host, pulled):
    """Pure comparison; metadata must come from separately pinned packaging tools."""
    keys = ("path", "sha256", "bytes", "package", "certificate_sha256", "debuggable",
            "source_commit", "app_tree", "app_src_tree", "packaging_receipt_sha256", "test_target", "runner")
    require(set(expected) == set(host) == set(pulled) == set(keys), "exact APK metadata fields required")
    require(all(expected[key] is not None for key in keys if key not in ("test_target", "runner")),
            "APK pair is not yet bound")
    require(all(host[key] == expected[key] and type(host[key]) is type(expected[key])
                and pulled[key] == expected[key] and type(pulled[key]) is type(expected[key])
                for key in keys if key != "path"), "host/pulled APK, certificate, package or source drift")
    require(type(expected["path"]) is str and Path(expected["path"]).is_absolute()
            and host["path"] == expected["path"] and type(pulled["path"]) is str
            and Path(pulled["path"]).is_absolute(), "wrong host/pulled artifact paths")
    require(type(expected["bytes"]) is int and expected["bytes"] > 0 and expected["debuggable"] is True
            and HEX.fullmatch(expected["sha256"]) and HEX.fullmatch(expected["certificate_sha256"])
            and HEX.fullmatch(expected["packaging_receipt_sha256"])
            and re.fullmatch(r"[a-f0-9]{40}", expected["source_commit"])
            and expected["app_tree"] == APP_TREE and expected["app_src_tree"] == APP_SRC_TREE,
            "invalid artifact/source pins")
    if expected["package"] == TEST_PACKAGE:
        require(expected["test_target"] == PACKAGE and expected["runner"] == RUNNER,
                "wrong instrumentation target or runner")
    else:
        require(expected["package"] == PACKAGE and expected["test_target"] is None
                and expected["runner"] is None, "wrong app package/metadata")
    return True


def apk_pair_parity(expected, host, pulled):
    require(set(expected) == set(host) == set(pulled) == {"app", "test"}, "exact app/test pair required")
    for role in ("app", "test"):
        apk_parity(expected[role], host[role], pulled[role])
    require(expected["app"]["package"] == PACKAGE and expected["test"]["package"] == TEST_PACKAGE
            and all(expected["app"][key] == expected["test"][key] for key in
                    ("certificate_sha256", "source_commit", "app_tree", "app_src_tree", "packaging_receipt_sha256")),
            "APK pair signer/source/packaging mismatch")
    return True


def command_timeout(start, now, requested_seconds, phase="work"):
    """Future subprocess timeout, measured against a single nonrenewable start."""
    require(all(type(value) in (int, float) and math.isfinite(value) for value in
                (start, now, requested_seconds)) and now >= start and requested_seconds > 0,
            "invalid monotonic clock or requested timeout")
    require(phase in ("work", "cleanup", "final"), "unknown command phase")
    remaining = start + LIMITS[phase + "_cutoff_seconds"] - now
    require(remaining > 0, "fixed cumulative phase deadline reached")
    return min(requested_seconds, remaining)


def release_proof(rows, owned_identities, owned_groups, occupied_ports, before_files, after_files):
    require(owned_identities and owned_groups and before_files, "complete original ownership/file baseline required")
    require(not any(pid in rows and rows[pid]["identity_sha256"] == identity
                    for pid, identity in owned_identities.items()), "owned identity remains alive")
    require(not any(row["pgid"] in owned_groups for row in rows.values()), "owned group still has live members")
    require(not occupied_ports and before_files == after_files, "owned ports remain or immutable inputs changed")
    return {"owned_absence_verified": True, "owned_groups_absent": True,
            "owned_ports_free": True, "immutable_files_unchanged": True}


def validate_preparation(value):
    require(value["purpose"] == "justshare-permission-recovery-device-preparation-v1"
            and value["status"] == "SOURCE_ONLY_NATIVE_ADMISSION_DISABLED"
            and value["limits"] == LIMITS and value["selected_methods"] == list(SELECTORS), "proposal scope drift")
    require(value["native_admission_enabled"] is False and value["root_actor"] is False
            and value["coordinator_grant"] is False and value["runtime_authorized"] is False,
            "source preparation does not confer execution authority")
    require(value["expected_source"] == {"accepted_source": ACCEPTED_SOURCE,
            "app_tree": APP_TREE, "app_src_tree": APP_SRC_TREE}, "expected source changed")
    require(value["executed_cases"] == 0 and value["native_pass"] is None
            and value["screenshots"] == [] and value["after_release_proof"] is None,
            "source preparation cannot contain native evidence")
    for artifact in value["actual_apk_binding"].values():
        require(all(item is None for item in artifact.values()), "actual APK/cert/source pins must remain null")
    return True


def pure_mock_checks():
    checks = []
    def passed(label):
        checks.append(label)
    def refuses(label, call):
        try:
            call()
        except (ValueError, NotImplementedError):
            passed(label)
        else:
            raise AssertionError("failed to refuse: " + label)
    def output(selectors=SELECTORS, code="0", count="21"):
        blocks = []
        for selector in selectors:
            cls, method = selector.split("#")
            for current in ("1", code):
                blocks += ["INSTRUMENTATION_STATUS: class=" + cls,
                           "INSTRUMENTATION_STATUS: test=" + method,
                           "INSTRUMENTATION_STATUS: numtests=" + count,
                           "INSTRUMENTATION_STATUS_CODE: " + current]
        return "\n".join(blocks + ["OK (21 tests)", "INSTRUMENTATION_CODE: -1", ""])
    good = output()
    require(instrumentation_results(good)["case_count"] == 21, "complete mock output")
    passed("exact unique 21 class+method outcomes/count accepted")
    refuses("missing method", lambda: instrumentation_results(output(SELECTORS[:-1])))
    refuses("duplicate method", lambda: instrumentation_results(output(SELECTORS + SELECTORS[:1])))
    refuses("wrong class", lambda: instrumentation_results(good.replace(next(iter(SUITES)), PACKAGE + ".Wrong")))
    refuses("wrong numtests", lambda: instrumentation_results(output(count="20")))
    for code in ("-1", "-2", "-3", "-4"):
        refuses("failure/error/ignore/assumption code " + code, lambda code=code: instrumentation_results(output(code=code)))
    refuses("pass without start", lambda: instrumentation_results(good.replace("INSTRUMENTATION_STATUS_CODE: 1", "INSTRUMENTATION_STATUS_CODE: 0", 1)))
    refuses("overlapping start", lambda: instrumentation_results(good.replace("INSTRUMENTATION_STATUS_CODE: 0", "INSTRUMENTATION_STATUS_CODE: 1", 1)))
    refuses("duplicate field", lambda: instrumentation_results(good.replace("INSTRUMENTATION_STATUS: numtests=21", "INSTRUMENTATION_STATUS: numtests=21\nINSTRUMENTATION_STATUS: numtests=21", 1)))
    refuses("malformed extra status code", lambda: instrumentation_results(good + "INSTRUMENTATION_STATUS_CODE: -3 invalid\n"))
    refuses("missing final code", lambda: instrumentation_results(good.replace("INSTRUMENTATION_CODE: -1", "")))
    refuses("duplicate final code", lambda: instrumentation_results(good + "INSTRUMENTATION_CODE: -1\n"))
    refuses("wrong final total", lambda: instrumentation_results(good.replace("OK (21 tests)", "OK (20 tests)")))
    refuses("aborted output", lambda: instrumentation_results(good + "INSTRUMENTATION_FAILED: crash\n"))
    refuses("shortMsg output", lambda: instrumentation_results(good + "INSTRUMENTATION_RESULT: shortMsg=crash\n"))
    ps = " 17 1 17 501 Tue Oct 6 12:00:00 2026 /tool/java fake\n"
    rows = parse_processes(ps, "17 /tool/java\n")
    identity = rows[17]["identity_sha256"]
    require(foreign_heavy(rows, {}, set()), "foreign actual java must be detected")
    passed("actual executable and PID+lstart identity classify native work")
    require(not foreign_heavy(rows, {17: identity}, set()), "exact owned identity")
    passed("exact owned identity admitted for pure classification")
    require(foreign_heavy(rows, {17: "0" * 64}, set()), "reused PID is foreign")
    passed("PID reuse with changed start identity remains foreign")
    light = parse_processes(ps, "17 /bin/sh\n")
    require(not foreign_heavy(light, {}, set()), "argv text is not actual executable")
    passed("shell/RTK argv text alone is not classified as native work")
    for executable in ("java.bin", "emulator", "emulator64-arm", "qemu", "qemu-system-aarch64", "Godot_v4", "Gradle"):
        native = parse_processes(ps, "17 /tool/" + executable + "\n")
        require(foreign_heavy(native, {}, set()), "actual native executable must be detected")
        passed("foreign actual executable: " + executable)
    refuses("missing executable mapping", lambda: parse_processes(ps, ""))
    refuses("duplicate process PID", lambda: parse_processes(ps + ps, "17 /tool/java\n"))
    refuses("raced extra executable", lambda: parse_processes(ps, "17 /tool/java\n18 /bin/sh\n"))
    resource_admission(light, {}, set(), 1, 15 * GIB)
    passed("pressure1 and15GiB preflight admit pure observation")
    refuses("pressure2", lambda: resource_admission(light, {}, set(), 2, 15 * GIB))
    refuses("below15GiB", lambda: resource_admission(light, {}, set(), 1, 15 * GIB - 1))
    refuses("foreign heavy workload", lambda: resource_admission(rows, {}, set(), 1, 15 * GIB))
    refuses("below12GiB runtime floor", lambda: resource_admission(light, {}, set(), 1, 12 * GIB - 1, False))
    require(command_timeout(0, 470, 30) == 10, "remaining work bound")
    passed("every future command timeout capped to remaining480s work window")
    require(command_timeout(0, 565, 20, "cleanup") == 5, "remaining cleanup bound")
    passed("cleanup timeout capped to remaining570s cutoff")
    refuses("work after480s", lambda: command_timeout(0, 480, 1))
    refuses("cleanup after570s", lambda: command_timeout(0, 570, 1, "cleanup"))
    refuses("final after600s", lambda: command_timeout(0, 600, 1, "final"))
    refuses("nonfinite timeout", lambda: command_timeout(0, 0, float("inf")))
    target = {"serial": "emulator-5582", "avd_name": "JustSharePermissionRecovery20261006_" + "a" * 32,
              "boot_id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "api": 35, "abi": "arm64-v8a",
              "qemu": "1", "current_user": "0", "adb_port": 5149, "console_port": 5582,
              "server_pid": 100, "server_lstart": "Tue Oct 6 12:00:00 2026",
              "server_identity_sha256": "b" * 64, "server_binary_sha256": "c" * 64,
              "server_listener": "127.0.0.1:5149", "emulator_pid": 101,
              "emulator_lstart": "Tue Oct 6 12:00:01 2026", "emulator_identity_sha256": "d" * 64,
              "emulator_pgid": 99, "emulator_executable": "/pinned/qemu-system-aarch64"}
    target_identity(target, target)
    passed("complete exact mock target and dedicated server identity")
    for key, changed in (("boot_id", "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"), ("server_pid", 103),
                         ("server_lstart", "Tue Oct 6 12:00:02 2026"), ("server_listener", "127.0.0.1:5037"),
                         ("serial", "phone"), ("avd_name", "ExistingUserAVD"), ("api", 34),
                         ("abi", "x86_64"), ("qemu", "0"), ("current_user", "10"),
                         ("emulator_pgid", 77), ("emulator_executable", "/other/qemu")):
        refuses("target identity drift: " + key, lambda key=key, changed=changed:
                target_identity(dict(target, **{key: changed}), target))
    app = {"path": "/mock/app.apk", "sha256": "a" * 64, "bytes": 10, "package": PACKAGE,
           "certificate_sha256": "b" * 64, "debuggable": True, "source_commit": ACCEPTED_SOURCE,
           "app_tree": APP_TREE, "app_src_tree": APP_SRC_TREE, "packaging_receipt_sha256": "c" * 64,
           "test_target": None, "runner": None}
    test = dict(app, path="/mock/test.apk", package=TEST_PACKAGE, test_target=PACKAGE, runner=RUNNER)
    pair = {"app": app, "test": test}
    apk_pair_parity(pair, pair, pair)
    passed("exact mocked host/pulled APK/cert/source/package/test-target pair")
    for key, changed in (("sha256", "d" * 64), ("certificate_sha256", "e" * 64),
                         ("package", "com.other"), ("app_src_tree", "f" * 40), ("debuggable", False),
                         ("bytes", 11), ("test_target", "com.other"), ("runner", "com.other/Runner")):
        changed_pair = {"app": app, "test": dict(test, **{key: changed})}
        refuses("installed APK parity drift: " + key, lambda changed_pair=changed_pair:
                apk_pair_parity(pair, pair, changed_pair))
    refuses("unbound APK", lambda: apk_parity(dict(app, source_commit=None), app, app))
    changed_pair = {"app": app, "test": dict(test, certificate_sha256="f" * 64)}
    refuses("different pair signers", lambda: apk_pair_parity(changed_pair, changed_pair, changed_pair))
    baseline = {"/mock/config.ini": "a" * 64}
    release_proof(light, {100: "b" * 64}, {99}, [], baseline, baseline)
    passed("mocked owned absence/group/port/input release proof")
    refuses("live owned identity", lambda: release_proof(rows, {17: identity}, {99}, [], baseline, baseline))
    refuses("live owned group", lambda: release_proof(light, {100: "b" * 64}, {17}, [], baseline, baseline))
    refuses("occupied owned port", lambda: release_proof(light, {100: "b" * 64}, {99}, [5149], baseline, baseline))
    refuses("original bytes changed", lambda: release_proof(light, {100: "b" * 64}, {99}, [], baseline, {}))
    refuses("unconditional native refusal", native_admission)
    return {"kind": "PURE_MOCK_ONLY", "count": len(checks), "checks": checks,
            "native_executed": False, "device_cases_executed": 0}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument("--prepare", action="store_true")
    modes.add_argument("--self-check", action="store_true")
    modes.add_argument("--audit-output", type=Path)
    modes.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    if args.execute:
        native_admission()  # Before any input read or observation. No bypass flag.
    elif args.self_check:
        print(json.dumps(pure_mock_checks(), indent=2))
    elif args.audit_output:
        print(json.dumps(instrumentation_results(args.audit_output.read_text()), indent=2))
    else:
        value = json.loads(PREPARATION.read_bytes())
        validate_preparation(value)
        require(sha256(Path(__file__).read_bytes()) == value["caller_sha256"], "caller byte pin changed")
        print(json.dumps(value, indent=2))


if __name__ == "__main__":
    main()
