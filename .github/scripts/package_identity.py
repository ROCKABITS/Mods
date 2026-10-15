#!/usr/bin/env python3
"""Publish only a release that passed domain, standalone, companion compatibility and client checks."""
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
project = repo / 'realm-identity'
properties = dict(line.split('=', 1) for line in (project / 'gradle.properties').read_text().splitlines() if '=' in line)
version = properties['mod_version']
assert re.fullmatch(r'\d+\.\d+\.\d+', version)
reports = [ET.parse(p).getroot() for p in (project / 'build/test-results/test').glob('TEST-*.xml')]
unit = sum(int(r.get('tests', 0)) for r in reports)
assert unit > 0 and not any(int(r.get(k, 0)) for r in reports for k in ('failures', 'errors', 'skipped'))
expected = sum(p.read_text().count('@GameTest(') for p in (project / 'src/gametest/java').rglob('*.java'))
assert expected > 0
native = {}
for filename in ['gametests.log', 'integration.log']:
    log = (project / filename).read_text(errors='replace')
    counts = re.findall(r'All (\d+) required tests passed', log, re.I)
    assert counts and int(counts[-1]) >= expected, filename + ': native tests missing or failing'
    native[filename] = int(counts[-1])
visual_dir = project / 'qa-results/identity-client'
visual = json.loads((visual_dir / 'result.json').read_text())
assert visual.get('success') is True, visual
images = ['realm-identity-items.png', 'realm-identity-document.png', 'realm-identity-issuance.png']
for name in images:
    image = visual_dir / name
    assert image.is_file() and image.stat().st_size > 1024, name
    assert image.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n', name
jar_name = f'realm-identity-{version}.jar'
source = project / 'build/libs' / jar_name
with zipfile.ZipFile(source) as contents:
    assert contents.testzip() is None
    meta = tomllib.loads(contents.read('META-INF/neoforge.mods.toml').decode())
    assert next(m for m in meta['mods'] if m['modId'] == 'realmidentity')['version'] == version
    assert not any(p.startswith(('pt/realmidentity/test/', 'pt/realmidentity/visualtest/')) for p in contents.namelist())
    for p in ['assets/realmidentity/blockstates/writing_desk.json', 'assets/realmidentity/models/item/writing_desk.json', 'data/realmidentity/recipe/writing_desk.json', 'assets/realmidentity/models/item/identity_document.json']:
        assert p in contents.namelist(), p
out = repo / 'mod-pronto'
out.mkdir(exist_ok=True)
jar = out / jar_name
shutil.copyfile(source, jar)
archive = out / f'realm-identity-{version}.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    z.write(jar, jar.name)
    z.write(project / 'README.md', 'README.md')
digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
media = repo / 'media/realm-identity' / version
media.mkdir(parents=True, exist_ok=True)
for name in images:
    shutil.copyfile(visual_dir / name, media / name)
shutil.copyfile(visual_dir / 'result.json', media / 'validation.json')
(media / 'README.md').write_text('''# Realm Identity — Minecraft screenshots\n\nThese images were captured from the actual Minecraft 1.21.1 NeoForge client. The desk and document use the packaged models and textures. Menu screenshots use demonstration identities and a sample signature, rather than real player records.\n\n- realm-identity-items.png: original writing desk and physical document rendered by Minecraft.\n- realm-identity-document.png: production identity document with demonstration character data.\n- realm-identity-issuance.png: production writing-desk issuance screen with demonstration character data.\n\nAll images may be used for the ROCKABITS Realm Identity project gallery.\n''')
with zipfile.ZipFile(media / f'realm-identity-{version}-media.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for name in images + ['README.md']:
        z.write(media / name, name)
(out / f'realm-identity-{version}.sha256').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in [jar, archive]))
provenance = {
    'version': version, 'source_commit': os.environ['GITHUB_SHA'],
    'tested_run': f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    'jar_sha256': digest(jar), 'zip_sha256': digest(archive),
    'unit_tests': unit, 'standalone_native_tests': native['gametests.log'],
    'companion_loaded_native_tests': native['integration.log'],
    'actual_client_validation': True,
    'chronicles_runtime_tested': False,
    'companion_jars': {p.name: digest(p) for p in sorted((project / 'compat-libs').glob('*.jar'))},
    'screenshots': {name: digest(media / name) for name in images},
}
(out / f'realm-identity-{version}.build.json').write_text(json.dumps(provenance, indent=2) + '\n')
print(f'::notice title=Tested Realm Identity::{jar.name}: {jar.stat().st_size} bytes; unit={unit}; native={native}; client passed; SHA256 {digest(jar)}')
