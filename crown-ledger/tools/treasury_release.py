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
              'realm-economy-0.4.1', 'realm-marriage-0.1.0']
ORIGINS_SHA='0b90c49b9e3c49d2f0343301ac3662b21d6ce5551ea343fb7fc8ebdab9e09684'
WALLET_RUNTIME={
    'origins-neoforge-1375372-8758523.jar':'93383233a047a9838af067b64452964183cc4fe9f2d9c269ffb253141136caf6',
    'jupiter-1072905-7738312.jar':'95af332af250f97fe2d9d288fe6b2057c266673a388348bf33756b0b693ace15',
    'origins-classes-neoforge-1536425-8758587.jar':'14fcc794ae3fc66f752961f7df480356bb5b7cb01d78a2670e9f1e9d18bafb67',
}
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
        assert not any('/test/' in n or '/visualtest/' in n or '/clienttest/' in n or '/loanclienttest/' in n or '/structure/' in n for n in z.namelist()), 'QA code/resources in production JAR'
        assert 'pt/crownledger/api/CrownIncomeApi.class' in z.namelist()
        assert 'realm-treasury.mixins.json' in z.namelist()
        assert 'pt/crownledger/service/LoanService.class' in z.namelist() and 'pt/crownledger/client/LoanScreen.class' in z.namelist()
        model=json.loads(z.read('assets/kingdomtreasury/models/block/loan_desk.json'))
        assert len(model['elements'])>=60
        assert 'data/kingdomtreasury/recipe/loan_desk.json' in z.namelist()
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
    origins=ROOT/'mod-pronto/realm-origins-0.5.7.jar'
    assert digest(origins)==ORIGINS_SHA==json.loads(origins.with_suffix('.build.json').read_text())['jar_sha256']

def package(validate_only=False):
    state = json.loads((STATE/'snapshot.json').read_text())
    assert SOURCE == state['source_commit'], 'Build and tests must use one source commit'
    assert digest(JAR) == digest(STATE/JAR.name) == state['jar_sha256'], 'Production JAR changed during QA'
    reports = [ET.parse(p).getroot() for p in (PROJECT/'build/test-results/test').glob('TEST-*.xml')]
    unit = sum(int(r.get('tests', 0)) for r in reports)
    expected_unit = sum(len(re.findall(r'@Test\b', p.read_text())) for p in (PROJECT/'src/test/java').rglob('*.java'))
    assert unit == expected_unit and unit >= 102
    assert not any(int(r.get(k, 0)) for r in reports for k in ['failures', 'errors', 'skipped'])
    expected_native = sum(len(re.findall(r'@GameTest\s*\(', p.read_text())) for p in (PROJECT/'src/gametest/java').rglob('*.java'))
    native = {}
    for name in ['gametests.log', 'integration.log']:
        log = (PROJECT/name).read_text(errors='replace')
        counts = re.findall(r'All (\d+) required tests passed', log, re.I)
        assert counts and int(counts[-1]) == expected_native, f'Missing successful native suite: {name}'
        assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log
        native[name] = expected_native
    expected_loans=sum(len(re.findall(r'@GameTest\s*\(',p.read_text())) for p in (PROJECT/'src/loantest/java').rglob('*.java'))
    for name in ['loans-native.log','loans-integration.log']:
        log=(PROJECT/name).read_text(errors='replace');counts=re.findall(r'All (\d+) required tests passed',log,re.I)
        assert counts and int(counts[-1])==expected_loans and expected_loans>=14,name
        assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log
        assert 'TREASURY_LOAN_WALLET_PASSED' in log and 'TREASURY_LOAN_RECOVERY_PASSED' in log
        native[name]=expected_loans
    pins = json.loads((STATE/'companions.json').read_text())
    assert {p.name for p in (PROJECT/'compat-libs').glob('*.jar')} == set(pins)
    assert all(digest(PROJECT/'compat-libs'/n) == sha for n, sha in pins.items())
    legacy = json.loads((PROJECT/'qa-results/crown-client/result.json').read_text())
    lan = json.loads((PROJECT/'qa-results/treasury-lan/result.json').read_text())
    assert legacy['success'] and lan['success'] and lan['stage'] == 23 and lan['actualMinecraftClients'] == 2 and lan['actualTcpLan']
    assert len(lan['checks']) >= 5 and len(lan['screenshots']) >= 8
    credit=json.loads((PROJECT/'qa-results/loans-client/result.json').read_text())
    assert credit['success'] and credit['stage']==28 and credit['actualMinecraftClients']==2 and credit['actualTcpLan']
    assert credit['actualChroniclesWallet'] and credit['actualCouncilPresident'] and len(credit['checks'])>=4 and len(credit['screenshots'])>=13
    for logfile in ['visual-client.log','lan-client.log','loans-client.log']:
        log=(PROJECT/logfile).read_text(errors='replace');assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log
    cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/curse.maven'
    for name,sha in WALLET_RUNTIME.items():
        candidates=list(cache.rglob(name));assert candidates and all(digest(p)==sha for p in candidates),name
    assert digest(ROOT/'mod-pronto/realm-origins-0.5.7.jar')==ORIGINS_SHA
    if validate_only:
        print(json.dumps({'source_commit':SOURCE,'jar_sha256':state['jar_sha256'],'unit_tests':unit,'native_tests':native,'client_scenarios':3,'loan_screenshots':len(credit['screenshots'])},indent=2))
        return
    out = ROOT/'mod-pronto';out.mkdir(exist_ok=True)
    target = out/JAR.name
    if target.exists():
        assert digest(target) == state['jar_sha256'], 'Refusing to replace an existing release with different bytes'
    shutil.copyfile(STATE/JAR.name, target)
    archive = out/f'{NAME}.zip'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
        z.write(target, target.name)
        z.writestr('INSTALL.txt', 'Realm Treasury '+VERSION+'\nMinecraft 1.21.1 / NeoForge 21.1.256+ / Java 21\nReplace the old treasury JAR in clients and server; keep one kingdomtreasury JAR.\nPreserves crown_ledger.dat, accounts, receipts and contracts. Back up the complete world and profiles before migrating to format 9. Old JARs cannot read the new format.\nCraft the Royal Credit Counter for loans. Use the treasury chest for public finances and votes; F8 opens public voting only.\n')
        z.write(PROJECT/'CHANGELOG.md', 'CHANGELOG.md')
    media = ROOT/'media/realm-treasury'/VERSION;media.mkdir(parents=True, exist_ok=True)
    images = []
    for directory, report in [('crown-client', legacy), ('treasury-lan', lan),('loans-client',credit)]:
        for name in report['screenshots']:
            path = PROJECT/'qa-results'/directory/name
            assert path.is_file() and path.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
            shutil.copyfile(path, media/name);images.append(name)
    assert len(images)==len(set(images))
    (media/'README.md').write_text('# Realm Treasury '+VERSION+'\n\nActual Minecraft captures. Credit images show the original 66-element counter and real loan menus, native packets, Realm Origins wallets and Realm Council mandates between two Minecraft processes over TCP LAN. Starting funds, characters and applications are controlled QA fixtures. The world was closed and reopened with an active debt. Voting images use two native LAN clients; the three legacy chest/account/policy images use demonstration balances. No concept art is presented as gameplay.\n\n'+'\n\n'.join(f'![{n}]({n})' for n in images)+'\n')
    (media/'validation.json').write_text(json.dumps({'loans':credit,'lan': lan, 'legacy': legacy}, indent=2)+'\n')
    (out/f'{NAME}.sha256').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in [target, archive]))
    (out/f'{NAME}.build.json').write_text(json.dumps(dict(state, version=VERSION, mod_id='kingdomtreasury', display_name='Realm Treasury', unit_tests=unit, native_tests=native, actual_loan_validation=credit, actual_client_validation=lan, legacy_client_validation=legacy, companions=pins, wallet_mod={'realm-origins-0.5.7.jar':ORIGINS_SHA}, wallet_dependencies=WALLET_RUNTIME, jar_bytes=target.stat().st_size, zip_sha256=digest(archive), github_run_id=os.environ.get('GITHUB_RUN_ID')), indent=2)+'\n')
    print(json.dumps({'jar': str(target), 'sha256': digest(target), 'unit_tests': unit, 'native_tests': native, 'images': images}, indent=2))

if __name__ == '__main__':
    {'snapshot': snapshot, 'install': install, 'validate':lambda:package(True), 'package': package}[sys.argv[1]]()
