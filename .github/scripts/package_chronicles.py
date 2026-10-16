#!/usr/bin/env python3
"""Publish Chronicles only after real Origins runtime and RP-name client checks."""
from pathlib import Path
import importlib.util
import json
import os
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

repo = Path(__file__).resolve().parents[2]
project = repo / 'chronicles-rp'
spec = importlib.util.spec_from_file_location('chronicles_release_checks', project / 'tools/release_checks.py')
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)
source, snapshot, dependencies = checks.check_snapshot()
version = checks.properties()['mod_version']
assert re.fullmatch(r'\d+\.\d+\.\d+', version)
reports = [ET.parse(p).getroot() for p in (project / 'build/test-results/test').glob('TEST-*.xml')]
unit = sum(int(r.get('tests', 0)) for r in reports)
assert unit > 0 and not any(int(r.get(k, 0)) for r in reports for k in ('failures', 'errors', 'skipped'))
build = (project / 'build.log').read_text(errors='replace')
assert 'BUILD SUCCESSFUL' in build and 'BUILD FAILED' not in build
expected = sum(len(re.findall(r'@GameTest\s*\(', p.read_text()))
               for p in (project / 'src/gametest/java').rglob('*.java'))
assert expected > 17, 'Original native tests alone do not verify RP names'
native = {}
for name in ('gametests.log', 'gametests-without-classes.log', 'integration.log'):
    log = (project / name).read_text(errors='replace')
    counts = re.findall(r'All (\d+) required tests passed', log, re.I)
    assert counts and int(counts[-1]) == expected, f'{name}: required native tests missing or failing'
    assert 'CRP_NAMES_NATIVE_TESTS_PASSED' in log, name + ': real RP names assertions did not execute'
    assert 'CRP_NAMES_RUNTIME_DEPENDENCIES_PASSED' in log, name + ': actual runtime mode not verified'
    assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log, name
    native[name] = int(counts[-1])

clients = {}
for folder, logfile, marker in (
        ('client', 'visual-origins-client.log', 'CRP_ORIGINS_QA true'),
        ('client-without-classes', 'visual-origins-without-classes.log', 'CRP_ORIGINS_QA true'),
        ('rp-names-client', 'visual-client.log', 'CRP_NAMES_CLIENT_QA_PASSED')):
    report = json.loads((project / 'qa-results' / folder / 'result.json').read_text())
    assert report.get('success') is True, (folder, report)
    log = (project / logfile).read_text(errors='replace')
    assert marker in log and 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log, logfile
    clients[folder] = report

images = ['chronicles-rp-names-world.png', 'chronicles-rp-names-tab.png',
          'chronicles-rp-names-chat.png', 'chronicles-rp-names-profile.png']
visual = project / 'qa-results/rp-names-client'
for name in images:
    path = visual / name
    assert path.stat().st_size > 1024 and path.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n', name

out = repo / 'mod-pronto'
out.mkdir(exist_ok=True)
jar = out / source.name
shutil.copyfile(source, jar)
archive = out / f'cronicasrp-{version}.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    z.write(jar, jar.name)
    for name in ('README.md', 'CHANGELOG.md'):
        z.write(project / name, name)

media = repo / 'media/chronicles-rp' / version
media.mkdir(parents=True, exist_ok=True)
for name in images:
    shutil.copyfile(visual / name, media / name)
shutil.copyfile(visual / 'result.json', media / 'validation.json')
(media / 'README.md').write_text('''# Chronicles RP — RP names\n\nActual Minecraft 1.21.1 NeoForge screenshots, captured with real Origins, Jupiter and Origins: Classes plus the released roleplay companion mods. Demonstration characters are used for the gallery.\n\n- chronicles-rp-names-world.png: RP names above characters and the username fallback.\n- chronicles-rp-names-tab.png: RP names in the vanilla player list.\n- chronicles-rp-names-chat.png: normal player chat decorated with the RP name.\n- chronicles-rp-names-profile.png: character profile used in the demonstration.\n\nThe underlying Minecraft account names and UUIDs remain unchanged. Other mods can implement their own player-name interfaces.\n''')
with zipfile.ZipFile(media / f'chronicles-rp-{version}-media.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for name in images + ['README.md']:
        z.write(media / name, name)

(out / f'cronicasrp-{version}.sha256').write_text(''.join(
    f'{checks.digest(path)}  {path.name}\n' for path in (jar, archive)))
provenance = {
    'version': version, 'source_commit': snapshot['source_commit'],
    'tested_run': f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    'jar_sha256': checks.digest(jar), 'zip_sha256': checks.digest(archive),
    'unit_tests': unit, 'native_tests_with_classes': native['gametests.log'],
    'native_tests_without_classes': native['gametests-without-classes.log'],
    'native_tests_with_released_companions': native['integration.log'],
    'actual_client_validation': True, 'origins_client_with_and_without_classes': True,
    'chronicles_runtime_tested': True, 'network_protocol': '7',
    'client_chat_scope': 'Real integrated-server player chat transport; the development session has no authenticated signed-chat certificate.',
    'runtime_dependencies': dependencies,
    'dependency_checksum_scope': 'Observed SHA256 of the exact pinned CurseForge file IDs, fetched by Gradle over verified HTTPS; CurseMaven provides no publisher checksum files.',
    'companion_jars': {f'{name}.jar': digest for name, digest in checks.COMPANIONS.items()},
    'screenshots': {name: checks.digest(media / name) for name in images},
}
(out / f'cronicasrp-{version}.build.json').write_text(json.dumps(provenance, indent=2) + '\n')
print(f'::notice title=Tested Chronicles RP::{jar.name}: {jar.stat().st_size} bytes; unit={unit}; native={native}; real Origins and client passed; SHA256 {checks.digest(jar)}')
