#!/usr/bin/env python3
"""Release only Notifications, testing against verified existing companion releases."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tomllib
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2]
PROJECT=ROOT/'realm-notifications'
STATE=PROJECT/'build/notifications-release'
PINS={
    'cronicasrp-0.5.4.jar':'197c2fac53f97eb59159c9a0c26b3065362bdc18c8de1a1c0e61ece4360ba1f5',
    'contracts-bounties-0.3.2.jar':'2ecf125a188f042f8d6c5011af5e1ff2cc5974925835bcd2cc0180f75d07ff15',
    'realmcouncil-0.2.2.jar':'0a77af87a5c4e36f55ad12578662834886a7986864896a908a3dc4217e2c19cb',
    'crown-ledger-0.2.5.jar':'661825b0a006066dc53a93ed90ef402b6cd25c678a038f3e5d7411c5e2d24afa',
    'realm-markets-0.2.1.jar':'bfb1e73186015ab94a86c841abba1df02bf1b5851a65771e622234624b806bb1',
    'realm-rewards-0.1.2.jar':'ab8efe3bd9a7b24a4f725bf6dfe1f07dec0b88ecfaac91533b57c45b11cb7bcb',
    'realm-identity-0.1.1.jar':'4096147ac74847d31ee733291ad85d8d154ff7340e8d6f0874dd7cb7364823b0',
    'realm-titles-0.1.0.jar':'65d854ffd92805f6e8cb0891454f80ba5a6f33394cc0375cf23f64098af2bd16',
    'laws-justice-0.4.0.jar':'78c32b0c3acc8930ffd508c9bb44cc98b794a4b87900438b19ab812c21d36f7b',
    'realm-casino-0.3.0.jar':'99c6c457d26be354f6cddddc4c865fe444f765b8d359004de12921aa0fad9117',
    'realm-economy-0.4.0.jar':'eb7b25c11b64e842297d5ed174c067749989b6d5440fbc858834bd0922f55ddf',
}
RUNTIME={
    'origins-neoforge-1375372-8758523.jar':'93383233a047a9838af067b64452964183cc4fe9f2d9c269ffb253141136caf6',
    'jupiter-1072905-7738312.jar':'95af332af250f97fe2d9d288fe6b2057c266673a388348bf33756b0b693ace15',
}
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def version():
    v=re.search(r'^mod_version=(.+)$',(PROJECT/'gradle.properties').read_text(),re.M)[1].strip()
    assert re.fullmatch(r'\d+\.\d+\.\d+',v)
    return v
def jarname():return 'realm-notifications-'+version()+'.jar'
def checkjar(p):
    with zipfile.ZipFile(p) as z:
        assert z.testzip() is None
        mods=tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())['mods']
        assert next(m for m in mods if m['modId']=='realmnotifications')['version']==version()
        assert 'pt/realmnotifications/domain/NotificationBurst.class' in z.namelist()
        assert not any('/test/' in n or '/visualtest/' in n or '/structure/' in n or n.startswith(('net/minecraft/','net/neoforged/','META-INF/jarjar/')) for n in z.namelist())
def snapshot():
    assert digest(PROJECT/'gradle/wrapper/gradle-wrapper.jar')=='423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9'
    assert 'distributionSha256Sum=72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f' in (PROJECT/'gradle/wrapper/gradle-wrapper.properties').read_text()
    STATE.mkdir(parents=True,exist_ok=True);p=PROJECT/'build/libs'/jarname();checkjar(p)
    shutil.copyfile(p,STATE/p.name)
    (STATE/'manifest.json').write_text(json.dumps({'source_commit':os.environ['GITHUB_SHA'],'jar_sha256':digest(p)})+'\n')
def install():
    target=PROJECT/'compat-libs';target.mkdir(exist_ok=True);assert not list(target.glob('*.jar'))
    for name,want in PINS.items():
        p=ROOT/'mod-pronto'/name;assert digest(p)==want==json.loads(p.with_suffix('.build.json').read_text())['jar_sha256'],name
        shutil.copyfile(p,target/name)
def nativecount(log):
    n=sum(len(re.findall(r'@GameTest\s*\(',p.read_text())) for p in (PROJECT/'src/gametest/java').rglob('*.java'))
    counts=re.findall(r'All (\d+) required tests passed',log,re.I)
    assert n>0 and counts and int(counts[-1])==n,'Incomplete native suite'
    return n
def fresh(path,data):
    if path.exists():assert path.read_bytes()==data,'Refusing to overwrite '+str(path)
    else:path.write_bytes(data)
def package():
    manifest=json.loads((STATE/'manifest.json').read_text());assert manifest['source_commit']==os.environ['GITHUB_SHA']
    source=STATE/jarname();checkjar(source)
    assert digest(source)==manifest['jar_sha256']==digest(PROJECT/'build/libs'/jarname())
    reports=[ET.parse(p).getroot() for p in (PROJECT/'build/test-results/test').glob('TEST-*.xml')]
    units=sum(int(r.get('tests',0)) for r in reports);assert units>=18
    assert not any(int(r.get(k,0)) for r in reports for k in ('failures','errors','skipped'))
    logs={n:(PROJECT/n).read_text(errors='replace') for n in ('build.log','gametests.log','integration.log','visual-client.log')}
    assert all('BUILD SUCCESSFUL' in s and 'BUILD FAILED' not in s for s in logs.values())
    standalone=nativecount(logs['gametests.log']);integrated=nativecount(logs['integration.log'])
    for marker in ('NOTIFICATIONS_CHRONICLES_CROWN_HOOKS_PASSED','NOTIFICATIONS_ACTUAL_CONTRACT_ESCROW_AND_CLAIM_PASSED',
                   'NOTIFICATIONS_ACTIVITY_HOOKS_PASSED','NOTIFICATIONS_MONEY_HOOKS_PASSED','NOTIFICATIONS_FRESH_COMPANION_BRIDGES_PASSED',
                   'NOTIFICATIONS_RUNTIME_MODE_PASSED integration=true'):
        assert marker in logs['integration.log'],marker
    assert {p.name:digest(p) for p in (PROJECT/'compat-libs').glob('*.jar')}==PINS
    cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/curse.maven'
    for name,want in RUNTIME.items():
        files=list(cache.rglob(name));assert files and all(digest(p)==want for p in files),name
    qa=PROJECT/'qa-results/notifications-client';client=json.loads((qa/'result.json').read_text())
    assert all(client.get(k) is True for k in ('success','actual_minecraft_client','actual_integrated_server','real_s2c_transport'))
    assert any('200 actual server API' in c for c in client['checks'])
    assert 'NOTIFICATIONS_CLIENT_PASSED' in logs['visual-client.log']
    images=client['screenshots'];assert len(images)==len(set(images))==5 and 'realm-notifications-burst.png' in images
    import struct
    for n in images:
        assert re.fullmatch(r'[a-z0-9-]+\.png',n)
        b=(qa/n).read_bytes();assert b[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',b[16:24])==(1600,900)
    out=ROOT/'mod-pronto';out.mkdir(exist_ok=True);jar=out/jarname();fresh(jar,source.read_bytes())
    archive=jar.with_suffix('.zip');temp=STATE/archive.name
    with zipfile.ZipFile(temp,'w',zipfile.ZIP_DEFLATED) as z:
        z.write(jar,jar.name)
        for name in ('README.md','CHANGELOG.md','API.md','LICENSE'):
            p=PROJECT/name
            if p.exists():z.write(p,name)
    fresh(archive,temp.read_bytes())
    proof=dict(manifest,version=version(),tested_run=f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
               unit_tests=units,standalone_native_tests=standalone,integrated_native_tests=integrated,
               actual_client_validation=True,real_s2c_flood_messages=200,authenticated_two_clients=False,
               companions=PINS,runtime_dependency_checksums=RUNTIME,zip_sha256=digest(archive),
               screenshots={n:digest(qa/n) for n in images})
    checksum=jar.with_suffix('.sha256');fresh(checksum,''.join(f'{digest(p)}  {p.name}\n' for p in (jar,archive)).encode())
    meta=jar.with_suffix('.build.json');fresh(meta,(json.dumps(proof,indent=2)+'\n').encode())
    media=ROOT/'media/realm-notifications'/version();assert not media.exists();media.mkdir(parents=True)
    for name in images:shutil.copyfile(qa/name,media/name)
    shutil.copyfile(qa/'result.json',media/'validation.json')
    (media/'README.md').write_text('# Realm Notifications '+version()+'\n\nActual Minecraft 1.21.1 / NeoForge client and integrated host. Flood screenshot: 200 real S2C notices grouped by the production HUD. Demonstration texts; no financial transactions in these screenshots.\n\n'+''.join('!['+n+']('+n+')\n\n' for n in images))
    paths=[str(p.relative_to(ROOT)) for p in (jar,archive,checksum,meta,media)]
    (STATE/'publish-paths.json').write_text(json.dumps(paths)+'\n')
    print(f'Validated {units} JUnit, {standalone}+{integrated} GameTests, 200-message actual-client flood; SHA256 {digest(jar)}')
def stage():
    paths=json.loads((STATE/'publish-paths.json').read_text());assert len(paths)==5
    subprocess.run(['git','add','--',*paths],cwd=ROOT,check=True)
def summary():
    for n in ('build.log','gametests.log','integration.log','visual-client.log'):
        p=PROJECT/n
        if p.exists():
            lines=p.read_text(errors='replace').splitlines()
            if any('BUILD FAILED' in l or 'CLIENT_FAILED' in l for l in lines):print('\n'.join(lines[-90:])[-14000:])
            for l in lines:
                if re.search(r'All \d+ required tests|NOTIFICATIONS_.*PASSED',l):print(n+': '+l)
if __name__=='__main__':
    actions={'snapshot':snapshot,'install':install,'package':package,'stage':stage,'summary':summary}
    if len(sys.argv)!=2 or sys.argv[1] not in actions:raise SystemExit('Use '+', '.join(actions))
    actions[sys.argv[1]]()
