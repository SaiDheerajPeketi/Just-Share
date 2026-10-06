from pathlib import Path
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET

packet = Path(sys.argv[1])
result = json.loads((packet / 'result.json').read_text())
claim = json.loads((packet / 'actor-claim.json').read_text())
release = json.loads((packet / 'independent-release.json').read_text())
digest = hashlib.sha256((packet / 'result.json').read_bytes()).hexdigest()
assert digest == release['result_sha256'] and release['release_verified']
sources = {
    'com.blackandblue.justshare.LocalTransferPermissionsTest':
        'app/src/test/java/com/blackandblue/justshare/LocalTransferPermissionsTest.kt',
    'com.blackandblue.justshare.domain.transfer.TransferProgressSessionTest':
        'app/src/test/java/com/blackandblue/justshare/domain/transfer/TransferProgressSessionTest.kt',
}
assert set(claim['expected_suites']) == set(sources)
assert result['source_pins_before'] == result['source_pins_after']
assert result['inputs_unchanged'] and result['owned_cleanup'] and result['validation_passed']
assert not result['failures'] and not result['foreign_work_observations']
assert not any(result[key] for key in ('apk_assembly_requested', 'coordinator_grant', 'provider_actions'))
assert result['android_device_cases_executed'] == 0
assert sum(entry['count'] for entry in result['declared_android_methods_unrun'].values()) == 21
assert all(entry['executed'] == 0 for entry in result['declared_android_methods_unrun'].values())
out = {'source': result['source'], 'result_sha256': digest, 'release_verified': True,
       'suites': {}, 'totals': {key: 0 for key in ('tests', 'failures', 'errors', 'skipped')},
       'android_device_cases_executed': 0, 'android_declarations_unrun': 21}
for report in sorted(packet.glob('TEST-*.xml')):
    raw = report.read_bytes()
    suite = ET.fromstring(raw)
    name = suite.attrib['name']
    selected = claim['expected_suites'][name]
    source = packet.parents[1] / sources[name]
    assert hashlib.sha256(source.read_bytes()).hexdigest() == result['source_pins_before']['source_files'][sources[name]]
    declarations = re.findall(r'@Test\s+fun\s+(?:`([^`]+)`|(\w+))', source.read_text())
    names = {quoted or plain for quoted, plain in declarations}
    assert len(declarations) == selected['count'] == 4 and names == set(selected['methods'])
    cases = suite.findall('testcase')
    counts = {key: int(suite.attrib[key]) for key in out['totals']}
    assert suite.tag == 'testsuite' and counts == {'tests': 4, 'failures': 0, 'errors': 0, 'skipped': 0}
    assert len(cases) == 4 and all(case.attrib['classname'] == name for case in cases)
    assert len({case.attrib['name'] for case in cases}) == 4 and {case.attrib['name'] for case in cases} == names
    assert not any(case.find(tag) is not None for case in cases for tag in ('failure', 'error', 'skipped'))
    report_digest = hashlib.sha256(raw).hexdigest()
    assert report_digest == result['reports'][name]['fresh_raw_xml_sha256'] == result['tests'][name]['report_sha256']
    out['suites'][name] = {'counts': counts, 'report_sha256': report_digest}
    for key, value in counts.items():
        out['totals'][key] += value
assert set(out['suites']) == set(sources) and out['totals']['tests'] == 8
log = (packet / 'gradle.log').read_bytes()
assert hashlib.sha256(log).hexdigest() == result['raw_log_sha256'] and b'BUILD SUCCESSFUL' in log
for task in (':app:compileDebugKotlin', ':app:testDebugUnitTest', ':app:compileDebugAndroidTestKotlin'):
    assert re.findall(r'(?m)^> Task ' + re.escape(task) + r'(?: ([^\n]*))?$', log.decode()) == ['']
out.update(validation_passed=True, validator_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
target = packet / 'independent-report-checks.json'
with target.open('x') as stream:
    stream.write(json.dumps(out, indent=2) + '\n')
print(json.dumps({'source': out['source'], 'totals': out['totals'], 'validation_passed': True}))
