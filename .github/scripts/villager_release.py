#!/usr/bin/env python3
"""Build and publish Realm Villager Economy after monetary, native and actual-client validation."""
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

ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / 'realm-villager-economy/build/villager-release'
# project: mod ID, artifact basename, native property, minimum existing JUnit count
PROJECTS = {'realm-villager-economy': ('realmvillagereconomy','realm-villager-economy','villagerGameTests',9),
            'crown-ledger': ('kingdomtreasury','realm-treasury','crownGameTests',54)}
WRAPPER_SHA = '423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9'
DISTRIBUTION_SHA = '72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f'
COMPANION_PINS = {
    'realm-casino-0.3.0.jar': '99c6c457d26be354f6cddddc4c865fe444f765b8d359004de12921aa0fad9117',
    'laws-justice-0.4.0.jar': '78c32b0c3acc8930ffd508c9bb44cc98b794a4b87900438b19ab812c21d36f7b',
    'cronicasrp-0.5.4.jar': '197c2fac53f97eb59159c9a0c26b3065362bdc18c8de1a1c0e61ece4360ba1f5',
    'contracts-bounties-0.3.2.jar': '2ecf125a188f042f8d6c5011af5e1ff2cc5974925835bcd2cc0180f75d07ff15',
    'realmcouncil-0.2.2.jar': '0a77af87a5c4e36f55ad12578662834886a7986864896a908a3dc4217e2c19cb',
    'realm-markets-0.2.1.jar': 'bfb1e73186015ab94a86c841abba1df02bf1b5851a65771e622234624b806bb1',
    'realm-rewards-0.1.2.jar': 'ab8efe3bd9a7b24a4f725bf6dfe1f07dec0b88ecfaac91533b57c45b11cb7bcb',
    'realm-notifications-0.1.0.jar': '5aa70ac5a7696ce626ffa40e2e530038e90c06819dbeea6f43be70d42310822a',
    'realm-titles-0.1.0.jar': '65d854ffd92805f6e8cb0891454f80ba5a6f33394cc0375cf23f64098af2bd16',
    'realm-identity-0.1.1.jar': '4096147ac74847d31ee733291ad85d8d154ff7340e8d6f0874dd7cb7364823b0',
}
RUNTIME_PINS = {
    'origins-neoforge-1375372-8758523.jar': '93383233a047a9838af067b64452964183cc4fe9f2d9c269ffb253141136caf6',
    'jupiter-1072905-7738312.jar': '95af332af250f97fe2d9d288fe6b2057c266673a388348bf33756b0b693ace15',
    'origins-classes-neoforge-1536425-8758587.jar': '14fcc794ae3fc66f752961f7df480356bb5b7cb01d78a2670e9f1e9d18bafb67',
}


def pins(project):
    return {name: checksum for name, checksum in COMPANION_PINS.items()
            if project != 'crown-ledger' or not name.startswith('cronicasrp-')}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def version(project):
    value = re.search(r'^mod_version=(.+)$', (ROOT / project / 'gradle.properties').read_text(), re.M)[1].strip()
    assert re.fullmatch(r'\d+\.\d+\.\d+', value), project + ': invalid version'
    return value


def jar_name(project):
    return f'{PROJECTS[project][1]}-{version(project)}.jar'


def validate_jar(project, path):
    with zipfile.ZipFile(path) as jar:
        assert jar.testzip() is None, project + ': damaged production JAR'
        names = jar.namelist()
        metadata = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
        assert next(m for m in metadata['mods'] if m['modId'] == PROJECTS[project][0])['version'] == version(project)
        assert not any(n.startswith(('net/minecraft/', 'net/neoforged/', 'com/iafenvoy/', 'META-INF/jarjar/')) for n in names), project + ': bundled external dependency'
        assert not any('/test/' in n or '/visualtest/' in n or '/testclient/' in n or '/gametest/' in n or '/structure/' in n or 'ClientQA' in n for n in names), project + ': bundled test fixture'
        assert not any(n.startswith(('data/rp_fixture/', 'data/rewards_fixture/', 'data/notifications_fixture/')) for n in names)
        if project == 'crown-ledger':
            for required in ('pt/crownledger/api/CrownVillagerApi.class','pt/crownledger/api/CrownIncomeApi.class','pt/crownledger/api/CrownCasinoApi.class'):
                assert required in names, 'Crown integration missing: '+required
        else:
            for required in ('pt/realmvillagereconomy/service/TradingService.class','pt/realmvillagereconomy/service/TradeJournal.class','pt/realmvillagereconomy/client/TradingScreen.class','pt/realmvillagereconomy/menu/EconomyMenu.class'):
                assert required in names, 'Villager implementation missing: '+required
            for language in ('en_us','pt_pt','pt_br'):
                text=json.loads(jar.read('assets/realmvillagereconomy/lang/'+language+'.json'))
                assert text.get('gui.realmvillagereconomy.total') and text.get('gui.realmvillagereconomy.untaxed_sale')





def notice(kind, title, message):
    clean = str(message).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::{kind} title={title}::{clean}', flush=True)


def run_gradle(project, arguments, logfile, timeout):
    command = ['./gradlew', '--no-daemon', '--console=plain', *arguments]
    print(f'Running {project}: {" ".join(command)}', flush=True)
    try:
        result = subprocess.run(command, cwd=ROOT / project, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, timeout=timeout)
        output = result.stdout
        code = result.returncode
    except subprocess.TimeoutExpired as error:
        output = error.stdout or ''
        if isinstance(output, bytes):
            output = output.decode(errors='replace')
        output += '\nBUILD FAILED: validation process timed out\n'
        code = 124
    (ROOT / project / logfile).write_text(output)
    print(output, flush=True)
    return code == 0


def build():
    success = True
    for project in PROJECTS:
        path = ROOT / project
        assert digest(path / 'gradle/wrapper/gradle-wrapper.jar') == WRAPPER_SHA, project + ': wrapper checksum mismatch'
        assert f'distributionSha256Sum={DISTRIBUTION_SHA}' in (path / 'gradle/wrapper/gradle-wrapper.properties').read_text(), project + ': distribution checksum mismatch'
        success = run_gradle(project, ['build'], 'build.log', 600) and success
    assert success, 'At least one production build failed; see bounded validation summary'


def snapshot():
    STATE.mkdir(parents=True, exist_ok=True)
    data = {'source_commit': os.environ['GITHUB_SHA'], 'production_jars': {}}
    for project in PROJECTS:
        source = ROOT / project / 'build/libs' / jar_name(project)
        validate_jar(project, source)
        saved = STATE / source.name
        shutil.copyfile(source, saved)
        data['production_jars'][project] = {'filename': saved.name, 'sha256': digest(saved), 'version': version(project)}
    (STATE / 'manifest.json').write_text(json.dumps(data, indent=2) + '\n')


def manifest():
    data = json.loads((STATE / 'manifest.json').read_text())
    assert data['source_commit'] == os.environ['GITHUB_SHA'], 'Production snapshot belongs to different source'
    assert set(data['production_jars']) == set(PROJECTS), 'Missing fresh production artifact'
    for project, saved in data['production_jars'].items():
        assert saved['filename'] == jar_name(project) and saved['version'] == version(project)
        assert digest(STATE / saved['filename']) == saved['sha256'], project + ': immutable snapshot changed'
        assert digest(ROOT / project / 'build/libs' / saved['filename']) == saved['sha256'], project + ': production JAR changed during tests'
        validate_jar(project, STATE / saved['filename'])
    return data


def native():
    success = True
    for project, (_, _, flag, _) in PROJECTS.items():
        success = run_gradle(project, ['-P' + flag, 'runGameTestServer'], 'gametests.log', 360) and success
    assert success, 'At least one native regression suite failed; see bounded validation summary'


def install():
    data = manifest()
    companions = {}
    for name, checksum in COMPANION_PINS.items():
        path = ROOT / 'mod-pronto' / name
        metadata = json.loads(path.with_suffix('.build.json').read_text())
        assert digest(path) == checksum == metadata['jar_sha256'], name + ': release checksum mismatch'
    for project in PROJECTS:
        target = ROOT / project / 'compat-libs'
        target.mkdir(exist_ok=True)
        assert not list(target.glob('*.jar')), project + ': unexpected integration JAR'
        expected = pins(project)
        for other, saved in data['production_jars'].items():
            if other == project:
                continue
            shutil.copyfile(STATE / saved['filename'], target / saved['filename'])
            expected[saved['filename']] = saved['sha256']
        for name in pins(project):
            shutil.copyfile(ROOT / 'mod-pronto' / name, target / name)
        companions[project] = expected
    (STATE / 'companions.json').write_text(json.dumps(companions, indent=2) + '\n')


def integrated():
    success = True
    for project, (_, _, flag, _) in PROJECTS.items():
        success = run_gradle(project, ['-P' + flag, '-PintegrationTests', 'runGameTestServer'], 'integration.log', 360) and success
    assert success, 'Integrated native suites failed; see bounded validation summary'


def unit_count(project):
    reports = [ET.parse(file).getroot() for file in (ROOT / project / 'build/test-results/test').glob('TEST-*.xml')]
    count = sum(int(report.get('tests', 0)) for report in reports)
    assert count >= PROJECTS[project][3], project + ': missing existing unit tests'
    assert not any(int(r.get(key, 0)) for r in reports for key in ('failures', 'errors', 'skipped')), project + ': unit failures/skips'
    return count


def native_count(project, logfile):
    expected = sum(len(re.findall(r'@GameTest\s*\(', path.read_text())) for path in (ROOT / project / 'src/gametest/java').rglob('*.java'))
    assert expected > 0, project + ': no native fixtures'
    log = (ROOT / project / logfile).read_text(errors='replace')
    counts = re.findall(r'All (\d+) required tests passed', log, re.I)
    assert counts and int(counts[-1]) == expected, project + '/' + logfile + ': incomplete native suite'
    assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log, project + '/' + logfile
    return expected


def runtime_dependencies():
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1/curse.maven'
    result = {}
    for filename, checksum in RUNTIME_PINS.items():
        files = list(cache.rglob(filename))
        assert files and all(digest(path) == checksum for path in files), filename + ': pinned real dependency missing/changed'
        result[filename] = checksum
    return result



def validate():
    data=manifest();results={};companions=json.loads((STATE/'companions.json').read_text())
    for project in PROJECTS:
        log=(ROOT/project/'build.log').read_text(errors='replace');assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log
        results[project]={'unit_tests':unit_count(project),'standalone_native_tests':native_count(project,'gametests.log'),'integrated_native_tests':native_count(project,'integration.log')}
        actual={p.name:digest(p) for p in (ROOT/project/'compat-libs').glob('*.jar')}
        assert actual==companions[project], 'Integration binaries changed after the production snapshot'
    base=ROOT/'realm-villager-economy';log=(base/'integration.log').read_text(errors='replace')
    for marker in ('VILLAGER_TAX_INTEGRATION_PASSED','VILLAGER_RECOVERY_PASSED','VILLAGER_NATIVE_PASSED'):assert marker in log,marker
    client=json.loads((base/'qa-results/villager-client/result.json').read_text())
    for flag in ('success','actual_minecraft_client','actual_integrated_server','real_chronicles_wallet','lan_host_dual_save','real_crown_tax'):assert client.get(flag) is True,flag
    assert client.get('synthetic_financial_snapshots') is False
    assert client.get('authenticated_two_clients') is False
    log=(base/'visual-client.log').read_text(errors='replace');assert 'VILLAGER_CLIENT_PASSED' in log and 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log
    screenshots=client.get('screenshots',[]);assert len(screenshots)>=11 and len(screenshots)==len(set(screenshots))
    assert 'realm-villager-crown-history.png' in screenshots
    for name in screenshots:
        assert re.fullmatch(r'[a-z0-9-]+\.png',name);p=base/'qa-results/villager-client'/name
        import struct
        assert p.read_bytes()[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',p.read_bytes()[16:24])==(1600,900),name
    requests=client.get('real_c2s_requests',[])
    assert sum(r['operation']=='trade' for r in requests)==3 and any(r['operation']=='policy' for r in requests)
    for request in requests:
        if request['operation']!='refresh':assert 'nonce' in request and 'quote' in request and not any(key in request for key in ('balance','total','tax','base','player'))
    for project in PROJECTS:results[project]['actual_client_validation']=True
    return data,results,client,runtime_dependencies()


def write_fresh(path,content):
    if path.exists():assert path.read_bytes()==content,'Refusing to overwrite a published release: '+str(path)
    else:path.write_bytes(content)


def package():
    data,results,client,dependencies=validate();out=ROOT/'mod-pronto';out.mkdir(exist_ok=True)
    downloads=STATE/'downloads';downloads.mkdir(exist_ok=True)
    tested_run=f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}";paths=[]
    for project,saved in data['production_jars'].items():
        jar=out/saved['filename'];write_fresh(jar,(STATE/saved['filename']).read_bytes());archive=jar.with_suffix('.zip');temporary=downloads/archive.name
        with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED) as z:
            z.write(jar,jar.name)
            for name in ('README.md','CHANGELOG.md','LICENSE','TEMPLATE_LICENSE.txt'):
                p=ROOT/project/name
                if p.exists():z.write(p,name)
        write_fresh(archive,temporary.read_bytes())
        images=client['screenshots'] if project=='realm-villager-economy' else ['realm-villager-crown-history.png']
        proof=dict(results[project],version=saved['version'],source_commit=data['source_commit'],tested_run=tested_run,jar_sha256=digest(jar),zip_sha256=digest(archive),project_versions={p:v['version'] for p,v in data['production_jars'].items()},companions=json.loads((STATE/'companions.json').read_text())[project],runtime_dependency_checksums=dependencies,authenticated_two_clients=False)
        proof['screenshots']={name:digest(ROOT/'realm-villager-economy/qa-results/villager-client'/name) for name in images}
        proof['scope']='Wallet-funded Villager purchases, base-price sink, public purchase tax, physical emerald sales, durable inventory and merchant receipts'
        checksum=jar.with_suffix('.sha256');write_fresh(checksum,''.join(f'{digest(p)}  {p.name}\n' for p in (jar,archive)).encode())
        metadata=jar.with_suffix('.build.json');write_fresh(metadata,(json.dumps(proof,indent=2)+'\n').encode())
        for p in (jar,archive,checksum,metadata):shutil.copyfile(p,downloads/p.name);paths.append(str(p.relative_to(ROOT)))
        media=ROOT/'media'/project/saved['version'];assert not media.exists(),'Existing media release';media.mkdir(parents=True)
        for name in images:shutil.copyfile(ROOT/'realm-villager-economy/qa-results/villager-client'/name,media/name)
        shutil.copyfile(ROOT/'realm-villager-economy/qa-results/villager-client/result.json',media/'validation.json')
        (media/'README.md').write_text('# '+project+' '+saved['version']+'\n\nActual Minecraft 1.21.1/NeoForge integrated-host captures. Characters, offers and funds are fixtures; financial screens use real server state, native C2S/S2C, the actual Chronicles wallet and Crown treasury. These checks do not establish two authenticated clients.\n\n'+''.join('!['+project+']('+name+')\n\n' for name in images))
        with zipfile.ZipFile(media/(project+'-'+saved['version']+'-media.zip'),'w',zipfile.ZIP_DEFLATED) as z:
            for name in images+['README.md']:z.write(media/name,name)
        paths.append(str(media.relative_to(ROOT)));notice('notice','Verified release',jar.name+' SHA256='+digest(jar))
    (STATE/'publish-paths.json').write_text(json.dumps(paths,indent=2)+'\n')


def stage():
    paths=json.loads((STATE/'publish-paths.json').read_text());assert len(paths)==5*len(PROJECTS)
    subprocess.run(['git','add','--',*paths],cwd=ROOT,check=True)


def summary():
    for project in PROJECTS:
        for name in ('build.log','gametests.log','integration.log','visual-client.log'):
            path=ROOT/project/name
            if not path.exists():continue
            text=path.read_text(errors='replace');lines=[line for line in text.splitlines() if len(line)<1600]
            if 'BUILD FAILED' in text or 'CLIENT_FAILED' in text:
                selected=set()
                for i,line in enumerate(lines):
                    if re.search(r'failed|Caused by:|Exception| error:',line,re.I):selected.update(range(max(0,i-2),min(len(lines),i+9)))
                context='\n'.join(lines[i] for i in sorted(selected))[-18000:]
                for start in range(0,len(context),2400):notice('error',project+'/'+name,context[start:start+2400])
            for line in lines:
                if re.search(r'All \d+ required tests|VILLAGER_.*PASSED',line):notice('notice',project+'/'+name,line)


if __name__=='__main__':
    actions={'build':build,'snapshot':snapshot,'native':native,'install':install,'integrated':integrated,'package':package,'stage':stage,'summary':summary}
    if len(sys.argv)!=2 or sys.argv[1] not in actions:raise SystemExit('Use '+', '.join(actions))
    actions[sys.argv[1]]()
