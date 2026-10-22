#!/usr/bin/env python3
"""Release Realm Treasury only after fresh production, native and actual LAN-client checks."""
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

PROJECT = Path(__file__).resolve().parents[1]
ROOT = PROJECT.parent
STATE = PROJECT / 'build/treasury-release'
COMPANIONS = ['contracts-bounties-0.4.0', 'realmcouncil-0.2.2', 'realm-identity-0.1.2',
              'realm-markets-0.2.1', 'realm-notifications-0.1.1', 'realm-rewards-0.1.2',
              'realm-titles-0.1.0', 'realm-casino-0.3.0', 'laws-justice-0.4.0',
              'realm-economy-0.4.0', 'realm-marriage-0.1.0']
VERSION = dict(line.split('=', 1) for line in (PROJECT/'gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
NAME = f'realm-treasury-{VERSION}'
JAR = PROJECT/'build/libs'/f'{NAME}.jar'
SOURCE = os.environ.get('GITHUB_SHA')

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def snapshot():
    assert SOURCE and re.fullmatch('[0-9a-f]{40}', SOURCE), 'Explicit source commit is required'
    STATE.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(JAR) as z:
        meta = tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())['mods'][0]
        assert (meta['modId'], meta['displayName'], meta['version']) == ('kingdomtreasury', 'Realm Treasury', VERSION)
        assert not any('/test/' in n or '/visualtest/' in n or '/clienttest/' in n for n in z.namelist()), 'QA code in production JAR'
        assert 'pt/crownledger/api/CrownIncomeApi.class' in z.namelist()
        assert 'realm-treasury.mixins.json' in z.namelist()
    shutil.copyfile(JAR, STATE/JAR.name)
    (STATE/'snapshot.json').write_text(json.dumps({'source_commit': SOURCE, 'jar_sha256': digest(JAR)}, indent=2)+'\n')

def install():
    target = PROJECT/'compat-libs'
    target.mkdir(exist_ok=True)
    for old in target.glob('*.jar'):
        old.unlink()
    pins = {}
    for name in COMPANIONS:
        path = ROOT/'mod-pronto'/f'{name}.jar'
        provenance = json.loads(path.with_suffix('.build.json').read_text())
        assert digest(path) == provenance['jar_sha256'], f'Invalid companion: {name}'
        shutil.copyfile(path, target/path.name)
        pins[path.name] = digest(path)
    (STATE/'companions.json').write_text(json.dumps(pins, indent=2)+'\n')

def package():
    state = json.loads((STATE/'snapshot.json').read_text())
    assert SOURCE == state['source_commit'], 'Build and tests must use one source commit'
    assert digest(JAR) == digest(STATE/JAR.name) == state['jar_sha256'], 'Production JAR changed during QA'
    reports = [ET.parse(p).getroot() for p in (PROJECT/'build/test-results/test').glob('TEST-*.xml')]
    unit = sum(int(r.get('tests', 0)) for r in reports)
    expected_unit = sum(len(re.findall(r'@Test\b', p.read_text())) for p in (PROJECT/'src/test/java').rglob('*.java'))
    assert unit == expected_unit and unit >= 80
    assert not any(int(r.get(k, 0)) for r in reports for k in ['failures', 'errors', 'skipped'])
    expected_native = sum(len(re.findall(r'@GameTest\s*\(', p.read_text())) for p in (PROJECT/'src/gametest/java').rglob('*.java'))
    native = {}
    for name in ['gametests.log', 'integration.log']:
        log = (PROJECT/name).read_text(errors='replace')
        counts = re.findall(r'All (\d+) required tests passed', log, re.I)
        assert counts and int(counts[-1]) == expected_native, f'Missing successful native suite: {name}'
        assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log
        native[name] = expected_native
    pins = json.loads((STATE/'companions.json').read_text())
    assert {p.name for p in (PROJECT/'compat-libs').glob('*.jar')} == set(pins)
    assert all(digest(PROJECT/'compat-libs'/n) == sha for n, sha in pins.items())
    legacy = json.loads((PROJECT/'qa-results/crown-client/result.json').read_text())
    lan = json.loads((PROJECT/'qa-results/treasury-lan/result.json').read_text())
    assert legacy['success'] and lan['success'] and lan['stage'] == 23 and lan['actualMinecraftClients'] == 2 and lan['actualTcpLan']
    assert len(lan['checks']) >= 5 and len(lan['screenshots']) >= 8
    out = ROOT/'mod-pronto';out.mkdir(exist_ok=True)
    target = out/JAR.name
    if target.exists():
        assert digest(target) == state['jar_sha256'], 'Refusing to replace an existing release with different bytes'
    shutil.copyfile(STATE/JAR.name, target)
    archive = out/f'{NAME}.zip'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
        z.write(target, target.name)
        z.writestr('INSTALL.txt', 'Realm Treasury '+VERSION+'\nMinecraft 1.21.1 / NeoForge 21.1.256+ / Java 21\nReplace Crown Ledger in both clients and server; keep only one kingdomtreasury JAR.\nPreserves crown_ledger.dat, accounts, receipts and public contracts. Back up the world before migrating to format 8.\nRight-click the existing treasury chest to propose; F8 opens voting only.\n')
        z.write(PROJECT/'CHANGELOG.md', 'CHANGELOG.md')
    media = ROOT/'media/realm-treasury'/VERSION;media.mkdir(parents=True, exist_ok=True)
    images = []
    for directory, report in [('crown-client', legacy), ('treasury-lan', lan)]:
        for name in report['screenshots']:
            path = PROJECT/'qa-results'/directory/name
            assert path.is_file() and path.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
            shutil.copyfile(path, media/name);images.append(name)
    (media/'README.md').write_text('# Realm Treasury '+VERSION+'\n\nNew voting images show real menus and real ballots between two Minecraft processes connected by TCP LAN, using offline QA identities and controlled starting funds. The three legacy chest/account/policy images use demonstration balances in the production interface. No concept art is presented as gameplay.\n\n'+'\n\n'.join(f'![{n}]({n})' for n in images)+'\n')
    (media/'validation.json').write_text(json.dumps({'lan': lan, 'legacy': legacy}, indent=2)+'\n')
    (out/f'{NAME}.sha256').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in [target, archive]))
    (out/f'{NAME}.build.json').write_text(json.dumps(dict(state, version=VERSION, mod_id='kingdomtreasury', display_name='Realm Treasury', unit_tests=unit, native_tests=native, actual_client_validation=lan, legacy_client_validation=legacy, companions=pins, jar_bytes=target.stat().st_size, zip_sha256=digest(archive), github_run_id=os.environ.get('GITHUB_RUN_ID')), indent=2)+'\n')
    print(json.dumps({'jar': str(target), 'sha256': digest(target), 'unit_tests': unit, 'native_tests': native, 'images': images}, indent=2))

if __name__ == '__main__':
    {'snapshot': snapshot, 'install': install, 'package': package}[sys.argv[1]]()
