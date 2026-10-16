#!/usr/bin/env python3
"""Publish Realm Markets only after unit, native and actual-client checks pass."""
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
project = repo / 'realm-markets'
properties = dict(line.split('=', 1) for line in (project / 'gradle.properties').read_text().splitlines() if '=' in line)
version = properties['mod_version']
assert re.fullmatch(r'\d+\.\d+\.\d+', version), 'Invalid release version'

reports = [ET.parse(p).getroot() for p in (project / 'build/test-results/test').glob('TEST-*.xml')]
unit = sum(int(r.get('tests', 0)) for r in reports)
assert unit > 0, 'No unit-test results'
assert not any(int(r.get(k, 0)) for r in reports for k in ('failures', 'errors', 'skipped')), 'Unsuccessful unit tests'
expected = sum(len(re.findall(r'@GameTest\s*\(', p.read_text()))
               for p in (project / 'src/gametest/java').rglob('*.java'))
assert expected > 0, 'No native tests configured'
native = {}
for filename in ['gametests.log', 'integration.log']:
    log = (project / filename).read_text(errors='replace')
    counts = re.findall(r'All (\d+) required tests passed', log, re.I)
    assert counts and int(counts[-1]) == expected, filename + ': native test total does not match configured tests'
    assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log, filename + ': unsuccessful Gradle run'
    native[filename] = int(counts[-1])

visual_dir = project / 'qa-results/markets-client'
visual = json.loads((visual_dir / 'result.json').read_text())
assert visual.get('success') is True, visual
images = ['realm-markets-stalls.png', 'realm-markets-owner.png', 'realm-markets-buying.png']
assert {p.name for p in visual_dir.glob('*.png')} == set(images), 'Unexpected or missing client screenshots'
for name in images:
    image = visual_dir / name
    assert image.is_file() and image.stat().st_size > 1024, name
    assert image.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n', name

companions = {
    'contracts-bounties-0.3.1.jar': '9746951762a6762b2cb466ca695b7d43f8669c9dc1d55c12cae1b4bd17953f0c',
    'realmcouncil-0.2.1.jar': '3e407c391f5e2f919e246d1d889323c57ea40b30e0387e9614ae32e0e9251a0a',
    'crown-ledger-0.1.1.jar': 'e82795f13e9f912f6729cebbad7a31dc242735b7a9e6396d6cbfb7eac02736aa',
    'realm-identity-0.1.0.jar': '9332d133320066d42bedb08fe129dee3da0de41f261f459140dea2a12ebf1606',
}
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
assert {p.name for p in (project / 'compat-libs').glob('*.jar')} == set(companions), 'Unexpected companion test setup'
for name, checksum in companions.items():
    assert digest(project / 'compat-libs' / name) == checksum, name + ': companion checksum mismatch'

jar_name = f'realm-markets-{version}.jar'
source = project / 'build/libs' / jar_name
with zipfile.ZipFile(source) as contents:
    assert contents.testzip() is None, 'Damaged production JAR'
    meta = tomllib.loads(contents.read('META-INF/neoforge.mods.toml').decode())
    assert next(m for m in meta['mods'] if m['modId'] == 'realmmarkets')['version'] == version
    names = set(contents.namelist())
    assert not any(p.startswith(('pt/realmmarkets/test/', 'pt/realmmarkets/visualtest/')) for p in names), 'Test classes in production JAR'
    assert not any(p.startswith('data/realmmarkets/structure/') for p in names), 'Test template in production JAR'
    assert 'pt/realmmarkets/RealmMarketsMod.class' in names, 'Production entrypoint missing'
    for stall in ['crimson_stall', 'sage_stall']:
        for path in [f'assets/realmmarkets/blockstates/{stall}.json',
                     f'assets/realmmarkets/models/item/{stall}.json',
                     f'data/realmmarkets/recipe/{stall}.json',
                     f'data/realmmarkets/loot_table/blocks/{stall}.json']:
            assert path in names, path
        for part in range(4):
            assert f'assets/realmmarkets/models/block/{stall}_part{part}.json' in names, f'{stall} part {part}'
    for language in ['en_us', 'pt_pt']:
        assert f'assets/realmmarkets/lang/{language}.json' in names, language

out = repo / 'mod-pronto'
out.mkdir(exist_ok=True)
jar = out / jar_name
shutil.copyfile(source, jar)
archive = out / f'realm-markets-{version}.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    z.write(jar, jar.name)
    for name in ['README.md', 'CHANGELOG.md', 'LICENSE']:
        z.write(project / name, name)

media = repo / 'media/realm-markets' / version
media.mkdir(parents=True, exist_ok=True)
for name in images:
    shutil.copyfile(visual_dir / name, media / name)
shutil.copyfile(visual_dir / 'result.json', media / 'validation.json')
(media / 'README.md').write_text('''# Realm Markets — Minecraft screenshots

These images were captured from the actual Minecraft 1.21.1 NeoForge client, using the packaged stall models, textures and production market screen. The interface images use demonstration listings and balances, rather than live player accounts.

- realm-markets-stalls.png: both original 3D market stalls rendered by Minecraft.
- realm-markets-owner.png: production owner screen with demonstration stock and earnings.
- realm-markets-buying.png: production buyer screen with demonstration offers and per-item prices.

All images may be used for the ROCKABITS Realm Markets project gallery.
''')
with zipfile.ZipFile(media / f'realm-markets-{version}-media.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for name in images + ['README.md']:
        z.write(media / name, name)

(out / f'realm-markets-{version}.sha256').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in [jar, archive]))
provenance = {
    'version': version,
    'source_commit': os.environ['GITHUB_SHA'],
    'tested_run': f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    'jar_sha256': digest(jar),
    'zip_sha256': digest(archive),
    'unit_tests': unit,
    'standalone_native_tests': native['gametests.log'],
    'companion_loaded_native_tests': native['integration.log'],
    'actual_client_validation': True,
    'chronicles_runtime_tested': False,
    'companion_jars': companions,
    'screenshots': {name: digest(media / name) for name in images},
}
(out / f'realm-markets-{version}.build.json').write_text(json.dumps(provenance, indent=2) + '\n')
print(f'::notice title=Tested Realm Markets::{jar.name}: {jar.stat().st_size} bytes; unit={unit}; native={native}; client passed; SHA256 {digest(jar)}')
