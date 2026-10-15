#!/usr/bin/env python3
"""Package this run's release only after real server and client validation."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import tomllib
import xml.etree.ElementTree as ET
import zipfile

repo = Path(__file__).resolve().parents[2]
project = repo / 'contracts-bounties'
properties = dict(line.split('=', 1) for line in (project / 'gradle.properties').read_text().splitlines()
                  if '=' in line and not line.lstrip().startswith('#'))
version = properties['mod_version']
if not re.fullmatch(r'\d+\.\d+\.\d+', version):
    raise SystemExit('Invalid release version.')

reports = [ET.parse(path).getroot() for path in (project / 'build/test-results/test').glob('TEST-*.xml')]
unit_count = sum(int(report.get('tests', 0)) for report in reports)
if unit_count == 0 or any(int(report.get(key, 0)) for report in reports for key in ('failures', 'errors')):
    raise SystemExit('The current unit test results are missing or failing.')
native_log = (project / 'gametests.log').read_text(errors='replace')
matches = re.findall(r'All (\d+) required tests passed', native_log, re.IGNORECASE)
expected = sum(path.read_text().count('@GameTest(') for path in (project / 'src/gametest/java').rglob('*.java'))
if not matches or int(matches[-1]) < expected or expected == 0:
    raise SystemExit('Required native GameTests did not all execute and pass.')

visual_dir = project / 'qa-results/boards-client'
visual = json.loads((visual_dir / 'result.json').read_text())
if visual.get('success') is not True:
    raise SystemExit('Minecraft model/texture validation failed.')
screenshot = visual_dir / 'contracts-boards-minecraft.png'
if not screenshot.is_file() or screenshot.stat().st_size < 1024:
    raise SystemExit('The actual Minecraft screenshot is missing.')

jar_name = f'contracts-bounties-{version}.jar'
source = project / 'build/libs' / jar_name
with zipfile.ZipFile(source) as contents:
    if contents.testzip() is not None:
        raise SystemExit('Release JAR is corrupt.')
    metadata = tomllib.loads(contents.read('META-INF/neoforge.mods.toml').decode())
    mod = next(entry for entry in metadata['mods'] if entry['modId'] == 'contracts')
    if mod['version'] != version:
        raise SystemExit('Release metadata does not match the built version.')
    if any(path.startswith(('pt/contracts/test/', 'pt/contracts/visualtest/')) for path in contents.namelist()):
        raise SystemExit('Release JAR includes development-only fixtures.')
    for block in ['contract_board', 'tall_contract_board', 'wide_contract_board']:
        for path in [f'assets/contracts/blockstates/{block}.json',
                     f'assets/contracts/models/item/{block}.json',
                     f'data/contracts/recipe/{block}.json']:
            if path not in contents.namelist():
                raise SystemExit('Missing release resource: ' + path)

out = repo / 'mod-pronto'
out.mkdir(exist_ok=True)
jar = out / jar_name
shutil.copyfile(source, jar)
archive = out / f'contracts-bounties-{version}.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as package:
    package.write(jar, jar.name)
image = out / f'contracts-boards-{version}.png'
shutil.copyfile(screenshot, image)
shutil.copyfile(visual_dir / 'result.json', out / f'contracts-boards-{version}.json')
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
(out / f'contracts-bounties-{version}.sha256').write_text(''.join(
    f'{digest(path)}  {path.name}\n' for path in [jar, archive, image]))
(out / f'contracts-bounties-{version}.build.json').write_text(json.dumps({
    'version': version,
    'source_commit': os.environ['GITHUB_SHA'],
    'tested_run': f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    'jar_sha256': digest(jar),
    'zip_sha256': digest(archive),
    'screenshot_sha256': digest(image),
    'unit_tests': unit_count,
    'required_native_tests': int(matches[-1]),
    'actual_client_validation': True,
}, indent=2) + '\n')
print(f'::notice title=Tested release::{jar_name}: {jar.stat().st_size} bytes; SHA-256 {digest(jar)}; unit={unit_count}; native={matches[-1]}; actual client passed.')
