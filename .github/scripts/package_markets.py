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
from joint_economy_release import validate_checks, verify_companions


repo = Path(__file__).resolve().parents[2]
project = repo / 'realm-markets'
properties = dict(line.split('=', 1) for line in (project / 'gradle.properties').read_text().splitlines() if '=' in line)
version = properties['mod_version']
assert re.fullmatch(r'\d+\.\d+\.\d+', version), 'Invalid release version'

joint_checks = {name: validate_checks(name) for name in ('crown-ledger', 'realm-markets')}
unit, native = joint_checks['realm-markets']
companions, counterpart = verify_companions('realm-markets')

visual_dir = project / 'qa-results/markets-client'
visual = json.loads((visual_dir / 'result.json').read_text())
assert visual.get('success') is True, visual
images = ['realm-markets-stalls.png', 'realm-markets-owner.png', 'realm-markets-buying.png']
assert {p.name for p in visual_dir.glob('*.png')} == set(images), 'Unexpected or missing client screenshots'
for name in images:
    image = visual_dir / name
    assert image.is_file() and image.stat().st_size > 1024, name
    assert image.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n', name

digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

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
    assert 'pt/realmmarkets/api/MarketsApi.class' in names, 'Sales-tax API missing'
    assert 'pt/realmmarkets/domain/SaleAmounts.class' in names, 'Sales-tax arithmetic missing'
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
- realm-markets-buying.png: production buyer screen with demonstration offers, purchase total, sales tax and seller net earnings.

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
    'fresh_counterpart': counterpart,
    'joint_release': 'Crown Ledger and Realm Markets, built and tested from the same source commit',
    'counterpart_unit_tests': joint_checks['crown-ledger'][0],
    'counterpart_native_tests': joint_checks['crown-ledger'][1],
    'screenshots': {name: digest(media / name) for name in images},
}
(out / f'realm-markets-{version}.build.json').write_text(json.dumps(provenance, indent=2) + '\n')
print(f'::notice title=Tested Realm Markets::{jar.name}: {jar.stat().st_size} bytes; unit={unit}; native={native}; client passed; SHA256 {digest(jar)}')
