#!/usr/bin/env python3
"""Validate and package Realm Council; expose bounded diagnostics publicly."""
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

repo = Path(__file__).resolve().parents[2]
project = repo / 'realm-council'


def annotate(kind, title, message):
    safe = str(message).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::{kind} title={title}::{safe}')


if '--summary' in sys.argv:
    parts = ['# Realm Council validation\n']
    for path in sorted((project / 'build/test-results/test').glob('TEST-*.xml')):
        report = ET.parse(path).getroot()
        line = f"{report.get('name')}: tests={report.get('tests')}, failures={report.get('failures')}, errors={report.get('errors')}, skipped={report.get('skipped')}"
        annotate('notice', 'Realm Council unit tests', line)
        parts.append(line + '\n')
    for name in ['build.log', 'gametests.log']:
        path = project / name
        if not path.is_file():
            continue
        log = path.read_text(errors='replace')
        tail = '\n'.join(log.splitlines()[-160:])[-16000:]
        parts.append(f"\n## {name}\n\n```text\n{tail.replace('```', '~~~')}\n```\n")
        if name == 'gametests.log':
            results = [line for line in log.splitlines() if re.search(r'All \d+ required tests passed|GAME TESTS COMPLETE|tests failed', line, re.IGNORECASE)]
            if results:
                annotate('notice', 'Realm Council native tests', '\n'.join(results))
        if 'BUILD FAILED' in log or 'FAILURE: Build failed' in log:
            for index, offset in enumerate(range(0, len(tail), 1800), start=1):
                annotate('error', f'{name} diagnostics {index}', tail[offset:offset + 1800])
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        Path(summary).write_text('\n'.join(parts), encoding='utf-8')
    raise SystemExit(0)

properties = dict(line.split('=', 1) for line in (project / 'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.lstrip().startswith('#'))
version = properties['mod_version']
if not re.fullmatch(r'\d+\.\d+\.\d+', version):
    raise SystemExit('Invalid release version.')
reports = [ET.parse(path).getroot() for path in (project / 'build/test-results/test').glob('TEST-*.xml')]
unit_count = sum(int(report.get('tests', 0)) for report in reports)
if not unit_count or any(int(report.get(key, 0)) for report in reports for key in ('failures', 'errors', 'skipped')):
    raise SystemExit('Current unit tests are missing, failed or skipped.')
log = (project / 'gametests.log').read_text(errors='replace')
matches = re.findall(r'All (\d+) required tests passed', log, re.IGNORECASE)
expected = sum(path.read_text().count('@GameTest(') for path in (project / 'src/gametest/java').rglob('*.java'))
if not matches or not expected or int(matches[-1]) < expected:
    raise SystemExit('Not all required native tests executed and passed.')

name = f'realmcouncil-{version}.jar'
source = project / 'build/libs' / name
with zipfile.ZipFile(source) as jar:
    if jar.testzip() is not None:
        raise SystemExit('Corrupt release JAR.')
    metadata = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
    mod = next(entry for entry in metadata['mods'] if entry['modId'] == 'realmcouncil')
    if mod['version'] != version:
        raise SystemExit('JAR version does not match the source version.')
    if any(path.startswith(('pt/realmcouncil/test/', 'pt/realmcouncil/testclient/')) for path in jar.namelist()):
        raise SystemExit('Development fixtures found in release JAR.')
    for block in ['notice_board', 'ballot_box', 'oath_desk']:
        for path in [f'assets/realmcouncil/blockstates/{block}.json',
                     f'data/realmcouncil/recipe/{block}.json']:
            if path not in jar.namelist():
                raise SystemExit('Missing block resource: ' + path)

out = repo / 'mod-pronto'
out.mkdir(exist_ok=True)
jar = out / name
shutil.copyfile(source, jar)
archive = out / f'realmcouncil-{version}.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as package:
    package.write(jar, jar.name)
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
(out / f'realmcouncil-{version}.sha256').write_text(''.join(
    f'{digest(path)}  {path.name}\n' for path in [jar, archive]))
(out / f'realmcouncil-{version}.build.json').write_text(json.dumps({
    'version': version,
    'source_commit': os.environ['GITHUB_SHA'],
    'tested_run': f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    'jar_sha256': digest(jar),
    'zip_sha256': digest(archive),
    'unit_tests': unit_count,
    'required_native_tests': int(matches[-1]),
    'public_access': 'physical_stations_only',
}, indent=2) + '\n')
annotate('notice', 'Tested Realm Council', f'{name}: {jar.stat().st_size} bytes; SHA-256 {digest(jar)}; unit={unit_count}; native={matches[-1]}.')
