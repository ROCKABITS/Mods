#!/usr/bin/env python3
"""Verify actual pinned runtime artifacts and keep the production JAR immutable.

CurseMaven deliberately provides no upstream checksum files. Exact CurseForge
file IDs are pinned in build.gradle; hashes below record the fetched bytes, not
invented publisher checksums. All downloads use normal verified HTTPS.
"""
from pathlib import Path
import hashlib
import json
import os
import shutil
import sys
import tomllib
import zipfile

PROJECT = Path(__file__).resolve().parents[1]
REPO = PROJECT.parent
STATE = PROJECT / 'build/release-validation'
PINS = {
    'origins-neoforge-1375372': ('8758523', 'origins', '0.3.2'),
    'jupiter-1072905': ('7738312', 'jupiter', '2.3.7'),
    'origins-classes-neoforge-1536425': ('8758587', 'origins_classes', '0.3'),
}
COMPANIONS = {
    'contracts-bounties-0.3.1': '9746951762a6762b2cb466ca695b7d43f8669c9dc1d55c12cae1b4bd17953f0c',
    'realmcouncil-0.2.1': '3e407c391f5e2f919e246d1d889323c57ea40b30e0387e9614ae32e0e9251a0a',
    'crown-ledger-0.2.0': '278826d3ed182e7ea33bcb57cbd8358cd9d39b5f5fb582b7029923f3fdd3ee77',
    'realm-identity-0.1.0': '9332d133320066d42bedb08fe129dee3da0de41f261f459140dea2a12ebf1606',
    'realm-markets-0.2.0': 'b57021c9efa3c7a6bd017d9b53d552963d10270da70bde5dfacad68a5f2e6866',
}


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def properties():
    return dict(line.split('=', 1) for line in (PROJECT / 'gradle.properties').read_text().splitlines()
                if '=' in line and not line.lstrip().startswith('#'))


def validate_jar(path):
    with zipfile.ZipFile(path) as jar:
        assert jar.testzip() is None, 'Corrupt release JAR'
        names = jar.namelist()
        meta = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
        assert next(m for m in meta['mods'] if m['modId'] == 'cronicasrp')['version'] == properties()['mod_version']
        for prefix in ('pt/cronicasrp/test/', 'data/rp_fixture/', 'META-INF/jarjar/',
                       'com/iafenvoy/', 'net/neoforged/neoforge/'):
            assert not any(n.startswith(prefix) for n in names), 'Fixture or dependency bundled: ' + prefix
        assert not any('ClientQA' in n or n.endswith('cash_empty.nbt') for n in names), 'Client/GameTest fixture in release'
        for notice in ('FONT_LICENSE.txt', 'licenses/Origins-NOTICE.txt', 'licenses/Origins-LGPL-3.0.txt'):
            assert notice in names, 'Missing bundled-asset notice: ' + notice
        assert 'pt/cronicasrp/ModAttachments.class' in names
        assert 'pt/cronicasrp/domain/CharacterProfile.class' in names
        assert 'pt/cronicasrp/mixins/PlayerDisplayNameMixin.class' in names
        assert {'config': 'cronicasrp.mixins.json'} in meta.get('mixins', []), 'Missing effective name-hover fix mixin declaration'
        config = json.loads(jar.read('cronicasrp.mixins.json'))
        assert config['package'] == 'pt.cronicasrp.mixins'
        assert 'PlayerDisplayNameMixin' in config['mixins']
        assert config.get('required') is True
        return meta


def snapshot():
    STATE.mkdir(parents=True, exist_ok=True)
    jar = PROJECT / 'build/libs' / f"cronicasrp-{properties()['mod_version']}.jar"
    validate_jar(jar)
    target = STATE / jar.name
    shutil.copyfile(jar, target)
    (STATE / 'production.json').write_text(json.dumps({
        'source_commit': os.environ['GITHUB_SHA'], 'filename': jar.name,
        'jar_sha256': digest(jar),
    }, indent=2) + '\n')


def verify_pins():
    STATE.mkdir(parents=True, exist_ok=True)
    resolved = json.loads((PROJECT / 'build/reports/pinned-runtime.json').read_text())
    assert len(resolved) == len(PINS), 'Expected actual Origins, Jupiter and Classes artifacts'
    manifest = {}
    for artifact in resolved:
        module = artifact['module']
        file_id, mod_id, version = PINS[module]
        assert artifact['file_id'] == file_id, module
        path = Path(artifact['path'])
        with zipfile.ZipFile(path) as jar:
            assert jar.testzip() is None
            meta = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
            actual = next(m for m in meta['mods'] if m['modId'] == mod_id)
            assert actual['version'] == version, (module, actual['version'], version)
        assert module not in manifest, module
        manifest[module] = {
            'file_id': file_id, 'mod_id': mod_id, 'version': version,
            'sha256': digest(path), 'bytes': path.stat().st_size,
            'source': f'https://cursemaven.com/curse/maven/{module}/{file_id}/{module}-{file_id}.jar',
        }
    assert set(manifest) == set(PINS)
    (STATE / 'dependencies.json').write_text(json.dumps(manifest, indent=2) + '\n')
    for module, item in manifest.items():
        print(f"::notice title=Verified real runtime dependency::{module} file {item['file_id']}; version {item['version']}; observed SHA256 {item['sha256']}")


def install_companions():
    target = PROJECT / 'compat-libs'
    target.mkdir(exist_ok=True)
    assert not list(target.glob('*.jar')), 'Unexpected pre-existing companion JAR'
    for name, expected in COMPANIONS.items():
        source = REPO / 'mod-pronto' / f'{name}.jar'
        assert digest(source) == expected, name
        meta = json.loads((REPO / 'mod-pronto' / f'{name}.build.json').read_text())
        assert meta['jar_sha256'] == expected, name
        shutil.copyfile(source, target / source.name)


def check_snapshot():
    saved = json.loads((STATE / 'production.json').read_text())
    assert saved['source_commit'] == os.environ['GITHUB_SHA'], 'Stale production snapshot'
    original = STATE / saved['filename']
    current = PROJECT / 'build/libs' / saved['filename']
    assert digest(original) == digest(current) == saved['jar_sha256'], 'Test configuration changed production JAR'
    validate_jar(original)
    deps = json.loads((STATE / 'dependencies.json').read_text())
    assert set(deps) == set(PINS)
    installed = {p.stem: digest(p) for p in (PROJECT / 'compat-libs').glob('*.jar')}
    assert installed == COMPANIONS, 'Integration companion artifacts changed'
    return original, saved, deps


if __name__ == '__main__':
    commands = {'snapshot': snapshot, 'dependencies': verify_pins, 'install': install_companions, 'check': check_snapshot}
    assert len(sys.argv) == 2 and sys.argv[1] in commands
    commands[sys.argv[1]]()
