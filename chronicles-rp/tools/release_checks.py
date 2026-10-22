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
    'contracts-bounties-0.4.0': 'd1bd4726ac210e6d5904829bf9185efef76920aa8070ffb292d6e479689ee07e',
    'realmcouncil-0.2.2': '0a77af87a5c4e36f55ad12578662834886a7986864896a908a3dc4217e2c19cb',
    'crown-ledger-0.2.7': 'f377666807e0a31760fbd1bd209238a31a50db1903d50e5e1d7f1caf5ed44fce',
    'realm-identity-0.1.2': '4bb9ef1d4df009fe1061c5daeaa76e95fb9ce355c72f00a83b31dffe0b36f016',
    'realm-markets-0.2.1': 'bfb1e73186015ab94a86c841abba1df02bf1b5851a65771e622234624b806bb1',
    'realm-notifications-0.1.1': '2fb85c87eb296e49f3f68c0d7defd709ceca8913fa5a3f6ef9442d9b9548c99e',
    'realm-rewards-0.1.2': 'ab8efe3bd9a7b24a4f725bf6dfe1f07dec0b88ecfaac91533b57c45b11cb7bcb',
    'realm-titles-0.1.0': '65d854ffd92805f6e8cb0891454f80ba5a6f33394cc0375cf23f64098af2bd16',
    'realm-casino-0.3.0': '99c6c457d26be354f6cddddc4c865fe444f765b8d359004de12921aa0fad9117',
    'laws-justice-0.4.0': '78c32b0c3acc8930ffd508c9bb44cc98b794a4b87900438b19ab812c21d36f7b',
    'realm-economy-0.4.0': 'eb7b25c11b64e842297d5ed174c067749989b6d5440fbc858834bd0922f55ddf',
    'realm-marriage-0.1.0': 'c2d2471029a006843c542622a1e1e0543a20492ea22a5999de02dd423d2390ca'
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
        mod = next(m for m in meta['mods'] if m['modId'] == 'cronicasrp')
        assert mod['version'] == properties()['mod_version']
        assert mod['displayName'] == 'Realm Origins' and mod['logoFile'] == 'realm-origins.png'
        assert jar.read('realm-origins.png')[:8] == b'\x89PNG\r\n\x1a\n'
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
    jar = PROJECT / 'build/libs' / f"realm-origins-{properties()['mod_version']}.jar"
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
