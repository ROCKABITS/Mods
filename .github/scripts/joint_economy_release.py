#!/usr/bin/env python3
"""Bind both economy releases to fresh production JARs from the same CI commit."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import sys
import tomllib
import xml.etree.ElementTree as ET
import zipfile


REPO = Path(__file__).resolve().parents[2]
PROJECTS = {'crown-ledger': 'kingdomtreasury', 'realm-markets': 'realmmarkets'}
STATE = REPO / 'crown-ledger/build/joint-release'
RELEASED = {
    'contracts-bounties-0.3.1.jar': '9746951762a6762b2cb466ca695b7d43f8669c9dc1d55c12cae1b4bd17953f0c',
    'realmcouncil-0.2.1.jar': '3e407c391f5e2f919e246d1d889323c57ea40b30e0387e9614ae32e0e9251a0a',
    'realm-identity-0.1.0.jar': '9332d133320066d42bedb08fe129dee3da0de41f261f459140dea2a12ebf1606',
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def release_version():
    versions = set()
    for project in PROJECTS:
        props = dict(line.split('=', 1) for line in (REPO / project / 'gradle.properties').read_text().splitlines() if '=' in line)
        versions.add(props['mod_version'])
    assert len(versions) == 1, 'Crown and Markets must be released together'
    version = versions.pop()
    assert re.fullmatch(r'\d+\.\d+\.\d+', version), 'Invalid release version'
    return version


def production_jar(project):
    return REPO / project / 'build/libs' / f'{project}-{release_version()}.jar'


def snapshot():
    STATE.mkdir(parents=True, exist_ok=True)
    manifest = {'source_commit': os.environ['GITHUB_SHA'], 'version': release_version(), 'production_jars': {}}
    for project, mod_id in PROJECTS.items():
        source = production_jar(project)
        with zipfile.ZipFile(source) as contents:
            assert contents.testzip() is None, project + ': damaged production JAR'
            meta = tomllib.loads(contents.read('META-INF/neoforge.mods.toml').decode())
            assert next(m for m in meta['mods'] if m['modId'] == mod_id)['version'] == release_version()
            assert not any('/test/' in n or '/visualtest/' in n or '/structure/' in n for n in contents.namelist()), project + ': production JAR includes fixtures'
        target = STATE / source.name
        shutil.copyfile(source, target)
        manifest['production_jars'][project] = {'name': target.name, 'sha256': digest(target)}
    (STATE / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')


def manifest():
    result = json.loads((STATE / 'manifest.json').read_text())
    assert result['source_commit'] == os.environ['GITHUB_SHA'], 'Counterpart is from a different commit'
    assert result['version'] == release_version(), 'Counterpart release version mismatch'
    assert set(result['production_jars']) == set(PROJECTS), 'Missing fresh production JAR'
    for project, jar in result['production_jars'].items():
        assert jar['name'] == f'{project}-{release_version()}.jar', 'Unexpected counterpart filename'
        assert digest(STATE / jar['name']) == jar['sha256'], 'Production snapshot changed'
        assert digest(production_jar(project)) == jar['sha256'], 'Production JAR changed after snapshot'
    return result


def install():
    state = manifest()
    for name, checksum in RELEASED.items():
        source = REPO / 'mod-pronto' / name
        provenance = json.loads(source.with_suffix('.build.json').read_text())
        assert digest(source) == checksum == provenance['jar_sha256'], name + ': released checksum mismatch'
    for project in PROJECTS:
        target = REPO / project / 'compat-libs'
        target.mkdir(exist_ok=True)
        for old in target.glob('*.jar'):
            old.unlink()
        counterpart = next(other for other in PROJECTS if other != project)
        jar = state['production_jars'][counterpart]
        shutil.copyfile(STATE / jar['name'], target / jar['name'])
        for name in RELEASED:
            shutil.copyfile(REPO / 'mod-pronto' / name, target / name)


def verify_companions(project):
    state = manifest()
    counterpart = next(other for other in PROJECTS if other != project)
    fresh = state['production_jars'][counterpart]
    expected = dict(RELEASED, **{fresh['name']: fresh['sha256']})
    target = REPO / project / 'compat-libs'
    assert {p.name for p in target.glob('*.jar')} == set(expected), project + ': unexpected companion setup'
    for name, checksum in expected.items():
        assert digest(target / name) == checksum, name + ': companion checksum mismatch'
    return expected, {'project': counterpart, 'source_commit': state['source_commit'],
                      'jar': fresh['name'], 'jar_sha256': fresh['sha256']}


def validate_checks(project):
    verify_companions(project)
    base = REPO / project
    reports = [ET.parse(path).getroot() for path in (base / 'build/test-results/test').glob('TEST-*.xml')]
    unit = sum(int(report.get('tests', 0)) for report in reports)
    assert unit > 0, project + ': missing unit tests'
    assert not any(int(r.get(key, 0)) for r in reports for key in ('failures', 'errors', 'skipped')), project + ': unsuccessful unit tests'
    build = (base / 'build.log').read_text(errors='replace')
    assert 'BUILD SUCCESSFUL' in build and 'BUILD FAILED' not in build, project + ': unsuccessful production build'
    expected = sum(len(re.findall(r'@GameTest\s*\(', path.read_text())) for path in (base / 'src/gametest/java').rglob('*.java'))
    assert expected > 0, project + ': no native tests configured'
    native = {}
    for filename in ('gametests.log', 'integration.log'):
        log = (base / filename).read_text(errors='replace')
        counts = re.findall(r'All (\d+) required tests passed', log, re.I)
        assert counts and int(counts[-1]) == expected, project + '/' + filename + ': required native tests missing or failing'
        assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log, project + '/' + filename + ': unsuccessful Gradle run'
        native[filename] = int(counts[-1])
        if filename == 'integration.log':
            marker = 'CROWN_MARKETS_TAX_INTEGRATION_PASSED' if project == 'crown-ledger' else 'MARKETS_CROWN_TAX_INTEGRATION_PASSED'
            assert marker in log, project + ': actual counterpart sales-tax assertions did not complete'
    visual_name = 'crown-client' if project == 'crown-ledger' else 'markets-client'
    visual = json.loads((base / 'qa-results' / visual_name / 'result.json').read_text())
    assert visual.get('success') is True, project + ': unsuccessful actual client validation'
    visual_log = (base / 'visual-client.log').read_text(errors='replace')
    assert 'BUILD SUCCESSFUL' in visual_log and 'BUILD FAILED' not in visual_log, project + ': unsuccessful visual Gradle run'
    return unit, native


if __name__ == '__main__':
    action = sys.argv[1]
    if action == 'snapshot':
        snapshot()
    elif action == 'install':
        install()
    elif action == 'check':
        for name in PROJECTS:
            unit, native = validate_checks(name)
            print(f'{name}: unit={unit}; native={native}; same-commit fresh counterpart verified; actual client passed')
    else:
        raise SystemExit('Use snapshot, install or check')
