#!/usr/bin/env python3
"""Publish only a release that passed domain, standalone, real integration and client checks."""
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
project = repo / 'crown-ledger'
properties = dict(line.split('=', 1) for line in (project / 'gradle.properties').read_text().splitlines() if '=' in line)
version = properties['mod_version']
assert re.fullmatch(r'\d+\.\d+\.\d+', version)
joint_checks = {name: validate_checks(name) for name in ('crown-ledger', 'realm-markets')}
unit, native = joint_checks['crown-ledger']
companions, counterpart = verify_companions('crown-ledger')
visual_dir = project / 'qa-results/crown-client'
visual = json.loads((visual_dir / 'result.json').read_text())
assert visual.get('success') is True, visual
images = ['crown-chest-minecraft.png', 'crown-ledger-overview.png', 'crown-ledger-policy.png']
for name in images:
    image = visual_dir / name
    assert image.is_file() and image.stat().st_size > 1024, name
    assert image.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n', name
jar_name = f'crown-ledger-{version}.jar'
source = project / 'build/libs' / jar_name
with zipfile.ZipFile(source) as contents:
    assert contents.testzip() is None
    meta = tomllib.loads(contents.read('META-INF/neoforge.mods.toml').decode())
    assert next(m for m in meta['mods'] if m['modId'] == 'kingdomtreasury')['version'] == version
    assert not any(p.startswith(('pt/crownledger/test/', 'pt/crownledger/visualtest/')) for p in contents.namelist())
    assert not any(p.startswith('data/kingdomtreasury/structure/') for p in contents.namelist())
    for p in ['assets/kingdomtreasury/blockstates/crown_chest.json', 'assets/kingdomtreasury/models/item/crown_chest.json', 'data/kingdomtreasury/recipe/crown_chest.json']:
        assert p in contents.namelist(), p
out = repo / 'mod-pronto'
out.mkdir(exist_ok=True)
jar = out / jar_name
shutil.copyfile(source, jar)
archive = out / f'crown-ledger-{version}.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    z.write(jar, jar.name)
    for name in ['README.md', 'CHANGELOG.md']:
        z.write(project / name, name)
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
media = repo / 'media/crown-ledger' / version
media.mkdir(parents=True, exist_ok=True)
for name in images:
    shutil.copyfile(visual_dir / name, media / name)
shutil.copyfile(visual_dir / 'result.json', media / 'validation.json')
(media / 'README.md').write_text('''# Crown Ledger — Minecraft screenshots\n\nThese images were captured from the actual Minecraft 1.21.1 NeoForge client. The chest uses the packaged model and textures. Menu screenshots use demonstration balances and policy values, rather than a live player economy.\n\n- crown-chest-minecraft.png: original crown chest model rendered by Minecraft.\n- crown-ledger-overview.png: public treasury overview with demonstration values.\n- crown-ledger-policy.png: governor policy screen, including the independent market sales-tax rate.\n\nAll images may be used for the ROCKABITS Crown Ledger project gallery.\n''')
with zipfile.ZipFile(media / f'crown-ledger-{version}-media.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for name in images + ['README.md']:
        z.write(media / name, name)
(out / f'crown-ledger-{version}.sha256').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in [jar, archive]))
provenance = {
    'version': version, 'source_commit': os.environ['GITHUB_SHA'],
    'tested_run': f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    'jar_sha256': digest(jar), 'zip_sha256': digest(archive),
    'unit_tests': unit, 'standalone_native_tests': native['gametests.log'],
    'real_companion_native_tests': native['integration.log'],
    'actual_client_validation': True,
    'chronicles_runtime_tested': False,
    'companion_jars': companions,
    'fresh_counterpart': counterpart,
    'joint_release': 'Crown Ledger and Realm Markets, built and tested from the same source commit',
    'counterpart_unit_tests': joint_checks['realm-markets'][0],
    'counterpart_native_tests': joint_checks['realm-markets'][1],
    'screenshots': {name: digest(media / name) for name in images},
}
(out / f'crown-ledger-{version}.build.json').write_text(json.dumps(provenance, indent=2) + '\n')
print(f'::notice title=Tested Crown Ledger::{jar.name}: {jar.stat().st_size} bytes; unit={unit}; native={native}; client passed; SHA256 {digest(jar)}')
