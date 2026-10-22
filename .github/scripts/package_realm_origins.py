#!/usr/bin/env python3
"""Publish Realm Origins only after real Origins runtime and RP-name client checks."""
from pathlib import Path
import importlib.util
import json
import os
import re
import shutil
import struct
import zlib
import xml.etree.ElementTree as ET
import zipfile

repo = Path(__file__).resolve().parents[2]
project = repo / 'realm-origins'
spec = importlib.util.spec_from_file_location('realm_origins_release_checks', project / 'tools/release_checks.py')
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
    if folder != 'rp-names-client':
        assert report.get('journal_key') is True, 'P did not open the parchment journal'
        assert report.get('custom_occupation') is True, 'Custom occupation, validation and quick choices were not exercised'
        assert report.get('visual_rework') is True and report.get('step') == 23, 'Missing actual rework flow'
        assert report.get('with_classes') == (folder == 'client'), 'Wrong Origins Classes client mode'
    log = (project / logfile).read_text(errors='replace')
    assert marker in log and 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log, logfile
    clients[folder] = report

images = ['realm-origins-names-world.png', 'realm-origins-names-tab.png',
          'realm-origins-names-chat.png', 'realm-origins-names-profile.png']
visual = project / 'qa-results/rp-names-client'
for name in images:
    path = visual / name
    assert path.stat().st_size > 1024 and path.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n', name

out = repo / 'mod-pronto'
out.mkdir(exist_ok=True)
jar = out / source.name
shutil.copyfile(source, jar)
archive = out / f'realm-origins-{version}.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    z.write(jar, jar.name)
    for name in ('README.md', 'CHANGELOG.md'):
        z.write(project / name, name)

media = repo / 'media/realm-origins' / version
media.mkdir(parents=True, exist_ok=True)
for name in images:
    shutil.copyfile(visual / name, media / name)
shutil.copyfile(visual / 'result.json', media / 'validation.json')
def assert_rework_frame(path, journal=False):
    # The first pixel is independent of PNG row predictors. A transition can leave
    # the previous world frame in the framebuffer even though the screen changed.
    data = path.read_bytes()
    width, height, depth, color = struct.unpack('>IIBB', data[16:26])
    assert depth == 8 and color in (2, 6), 'Expected an actual RGB/RGBA screenshot'
    assert (width, height) in ((1600, 900), (854, 480)), (path.name, width, height)
    offset, compressed = 8, bytearray()
    while offset < len(data):
        length = struct.unpack('>I', data[offset:offset + 4])[0]
        if data[offset + 4:offset + 8] == b'IDAT':
            compressed.extend(data[offset + 8:offset + 8 + length])
        offset += length + 12
    first = zlib.decompressobj().decompress(bytes(compressed), 4)
    assert first[0] in range(5), 'Invalid screenshot row filter'
    if not journal:
        assert tuple(first[1:4]) == (6, 7, 13), 'Stale transition frame: ' + path.name


# Screenshots from the real creation/profile/exchange client, never rendered mockups.
rework_names = ['03-creation-en', '14-story-en', '05-signature-en', '06-oath-en',
                '13-welcome-en', '07-profile-en', '15-deeds-en', '16-profile-story-en',
                '17-wallet-en', '18-exchange-en', '11-profile-compact', '12-creation-compact',
                '19-exchange-compact', '20-edit-story-compact', '21-edit-signature-compact',
                '22-edit-review-compact', '23-welcome-compact', '24-occupation-en']
for name in rework_names:
    target = 'realm-origins-' + name + '.png'
    source_image = project / 'qa-results/client' / (name + '.png')
    assert source_image.stat().st_size > 1024 and source_image.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n', name
    assert_rework_frame(source_image, journal=name in ('07-profile-en', '15-deeds-en', '16-profile-story-en',
                                                     '17-wallet-en', '18-exchange-en', '11-profile-compact', '19-exchange-compact'))
    shutil.copyfile(source_image, media / target)
    images.append(target)
shutil.copyfile(project / 'src/main/resources/realm-origins.png', media / 'icon.png')
images.append('icon.png')
shutil.copyfile(project / 'qa-results/client/result.json', media / 'rework-validation.json')
(media / 'README.md').write_text(f'# Realm Origins {version}\n\nActual Minecraft 1.21.1 / NeoForge screenshots: cinematic creation and welcome, plus P opening the original parchment book for profile, deeds, story, wallet and exchange, with compact layouts. Origins powers remain in the external Origins native interface. The RP-name gallery also checks the world, TAB and normal chat with all twelve released companion mods. Test accounts use demonstration data.\n\nOriginal portal/book icon: icon.png. No screenshot is a generated mockup. Internal namespace remains cronicasrp; replace the old Chronicles RP JAR and keep the external Origins/Jupiter dependencies.\n')
with zipfile.ZipFile(media / f'realm-origins-{version}-media.zip', 'w', zipfile.ZIP_DEFLATED) as z:
    for name in images + ['README.md']:
        z.write(media / name, name)

(out / f'realm-origins-{version}.sha256').write_text(''.join(
    f'{checks.digest(path)}  {path.name}\n' for path in (jar, archive)))
provenance = {
    'version': version, 'display_name': 'Realm Origins', 'internal_mod_id': 'cronicasrp',
    'visual_rework_verified': True, 'parchment_journal_on_p_verified': True, 'personality_or_attributes_added': False, 'source_commit': snapshot['source_commit'],
    'tested_run': f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
    'jar_sha256': checks.digest(jar), 'zip_sha256': checks.digest(archive),
    'unit_tests': unit, 'native_tests_with_classes': native['gametests.log'],
    'native_tests_without_classes': native['gametests-without-classes.log'],
    'native_tests_with_released_companions': native['integration.log'],
    'actual_client_validation': True, 'origins_client_with_and_without_classes': True,
    'chronicles_runtime_tested': True, 'network_protocol': '8', 'custom_occupations_verified': True,
    'client_chat_scope': 'Real integrated-server player chat transport; the development session has no authenticated signed-chat certificate.',
    'runtime_dependencies': dependencies,
    'dependency_checksum_scope': 'Observed SHA256 of the exact pinned CurseForge file IDs, fetched by Gradle over verified HTTPS; CurseMaven provides no publisher checksum files.',
    'companion_jars': {f'{name}.jar': digest for name, digest in checks.COMPANIONS.items()},
    'screenshots': {name: checks.digest(media / name) for name in images},
}
(out / f'realm-origins-{version}.build.json').write_text(json.dumps(provenance, indent=2) + '\n')
print(f'::notice title=Tested Realm Origins::{jar.name}: {jar.stat().st_size} bytes; unit={unit}; native={native}; real Origins and client passed; SHA256 {checks.digest(jar)}')
