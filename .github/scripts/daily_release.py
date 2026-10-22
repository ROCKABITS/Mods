#!/usr/bin/env python3
"""Publish only immutable production binaries after native and actual-client daily quest checks."""
from pathlib import Path
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tomllib
import zipfile
import economy_release as common

ROOT=common.ROOT
STATE=ROOT/'contracts-bounties/build/daily-release'
PROJECTS={'contracts-bounties':('contracts','contracts-bounties','contractGameTests',82),
          'crown-ledger':('kingdomtreasury','realm-treasury','crownGameTests',59)}
COMPANIONS={k:v for k,v in common.COMPANION_PINS.items()
            if not k.startswith(('contracts-bounties-','realm-notifications-'))}
COMPANIONS.update({
    'realm-notifications-0.1.1.jar':'2fb85c87eb296e49f3f68c0d7defd709ceca8913fa5a3f6ef9442d9b9548c99e',
    'realm-economy-0.4.0.jar':'eb7b25c11b64e842297d5ed174c067749989b6d5440fbc858834bd0922f55ddf',
})
common.STATE=STATE
common.PROJECTS=PROJECTS
digest=common.digest


def validate_jar(project,path):
    with zipfile.ZipFile(path) as jar:
        assert jar.testzip() is None,'Damaged JAR'
        names=jar.namelist()
        metadata=tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
        assert next(m for m in metadata['mods'] if m['modId']==PROJECTS[project][0])['version']==common.version(project)
        assert not any('/test/' in n or '/visualtest/' in n or '/gametest/' in n or 'ClientQA' in n or n.startswith(('data/contracts_daily/','data/daily_fixture/','net/minecraft/','net/neoforged/','META-INF/jarjar/')) for n in names),'Fixture/dependency in production'
        if project=='contracts-bounties':
            for name in ('DailyLedger','DailyEvents','DailyRewardJournal','DailyCatalog','DailyPermissions','CelebrationQueue'):
                assert 'pt/contracts/daily/'+name+'.class' in names,name
            for name in ('DailyScreen','DailyAdminScreen','DailyCinematic'):
                assert 'pt/contracts/client/'+name+'.class' in names,name
            assert len([n for n in names if n.startswith('data/contracts/daily_quest/') and n.endswith('.json')])==57
            for language in ('en_us','pt_pt','pt_br'):
                text=json.loads(jar.read('assets/contracts/lang/'+language+'.json'))
                assert text['daily.contracts.all_complete'] and text['daily.contracts.accept']
            for variant in ('easy','medium','hard','all'):
                for asset in ('scroll','seal'):assert f'assets/contracts/textures/gui/daily/{asset}_{variant}.png' in names
                assert f'assets/contracts/sounds/daily/{variant}.ogg' in names
            for model in ('contract_board','tall_contract_board','wide_contract_board'):
                assert 'assets/contracts/blockstates/'+model+'.json' in names
            assert 'data/contracts/structure/contracts_empty.nbt' not in names
        else:
            for name in ('CrownDailyApi','CrownIncomeApi','CrownEconomyApi','CrownVillagerApi','CrownCasinoApi'):
                assert 'pt/crownledger/api/'+name+'.class' in names,name


common.validate_jar=validate_jar


def daily_native(logfile,integrated=False):
    args=['-PdailyGameTests']+(['-PdailyIntegration'] if integrated else [])+['runGameTestServer']
    assert common.run_gradle('contracts-bounties',args,logfile,600),'Daily native tests failed'


def native():
    common.native()
    daily_native('daily-gametests.log')


def install():
    data=common.manifest();target=ROOT/'contracts-bounties/daily-compat-libs';target.mkdir(exist_ok=True)
    assert not list(target.glob('*.jar')),'Unexpected integration binaries'
    pins={}
    for name,checksum in COMPANIONS.items():
        source=ROOT/'mod-pronto'/name
        assert digest(source)==checksum==json.loads(source.with_suffix('.build.json').read_text())['jar_sha256'],name
        shutil.copyfile(source,target/name);pins[name]=checksum
    crown=data['production_jars']['crown-ledger'];shutil.copyfile(STATE/crown['filename'],target/crown['filename']);pins[crown['filename']]=crown['sha256']
    (STATE/'companions.json').write_text(json.dumps(pins,indent=2)+'\n')


def integrated():
    daily_native('daily-integration.log',True)


def daily_count(logfile):
    expected=sum(len(re.findall(r'@GameTest\s*\(',p.read_text())) for p in (ROOT/'contracts-bounties/src/dailygametest/java').rglob('*.java'))
    assert expected==29,'Daily suite changed: update the documented gate'
    log=(ROOT/'contracts-bounties'/logfile).read_text(errors='replace')
    counts=re.findall(r'All (\d+) required tests passed',log,re.I)
    assert counts and int(counts[-1])==expected and 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log,logfile
    return expected


def validate():
    data=common.manifest();results={}
    for project in PROJECTS:
        log=(ROOT/project/'build.log').read_text(errors='replace');assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log
        results[project]={'unit_tests':common.unit_count(project),'standalone_existing_native_tests':common.native_count(project,'gametests.log')}
    results['contracts-bounties'].update(standalone_daily_native_tests=daily_count('daily-gametests.log'),integrated_daily_native_tests=daily_count('daily-integration.log'))
    pins=json.loads((STATE/'companions.json').read_text())
    assert {p.name:digest(p) for p in (ROOT/'contracts-bounties/daily-compat-libs').glob('*.jar')}==pins,'Companions changed'
    log=(ROOT/'contracts-bounties/daily-integration.log').read_text(errors='replace')
    assert 'DAILY_TAX_INTERRUPTION_PASSED' in log,'Interrupted treasury write/recovery was not tested'
    client=json.loads((ROOT/'contracts-bounties/qa-results/daily-client/result.json').read_text())
    for key in ('success','actual_minecraft_client','actual_integrated_server','real_c2s_widgets','real_s2c_cinematics','real_chronicles_wallet','real_crown_tax','survival_events','all_three_physical_boards','actual_world_close_and_reopen','lan_owner_dual_save','actual_lan_publication','controls_free','manual_skip','muted_real_completion'):
        assert client.get(key) is True,key
    for key in ('synthetic_financial_snapshots','authenticated_two_clients','actual_realm_admin'):assert client.get(key) is False,key
    log=(ROOT/'contracts-bounties/visual-client.log').read_text(errors='replace')
    assert 'DAILY_CLIENT_PASSED' in log and 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log
    images=client['screenshots'];assert len(images)>=16 and len(images)==len(set(images))
    assert {'daily-three-boards.png','daily-cinematic-easy.png','daily-cinematic-medium.png','daily-cinematic-hard.png','daily-cinematic-all.png','daily-after-world-reopen.png','daily-template-editor.png','daily-visual-preferences.png'}<=set(images)
    for name in images:
        assert re.fullmatch(r'[a-z0-9-]+\.png',name)
        content=(ROOT/'contracts-bounties/qa-results/daily-client'/name).read_bytes()
        assert content[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',content[16:24])==(1600,900),name
    assert client['wallet_cents']==100000+client['net_cents'] and client['tax_cents']>0
    return data,results,client,pins,common.runtime_dependencies()


def package():
    data,results,client,pins,runtime=validate();out=ROOT/'mod-pronto';downloads=STATE/'downloads';downloads.mkdir(exist_ok=True)
    paths=[];tested_run=f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
    for project,saved in data['production_jars'].items():
        jar=out/saved['filename'];common.write_fresh(jar,(STATE/saved['filename']).read_bytes());archive=jar.with_suffix('.zip')
        with zipfile.ZipFile(downloads/archive.name,'w',zipfile.ZIP_DEFLATED) as z:
            z.write(jar,jar.name)
            for name in ('README.md','CHANGELOG.md','DAILY_QUESTS.md','LICENSE','TEMPLATE_LICENSE.txt'):
                p=ROOT/project/name
                if p.exists():z.write(p,name)
        common.write_fresh(archive,(downloads/archive.name).read_bytes())
        proof=dict(results[project],version=saved['version'],source_commit=data['source_commit'],tested_run=tested_run,jar_sha256=digest(jar),zip_sha256=digest(archive),project_versions={p:v['version'] for p,v in data['production_jars'].items()},companions=pins,runtime_dependency_checksums=runtime,authenticated_two_clients=False,actual_realm_admin=False,actual_lan_publication=True,actual_world_close_and_reopen=True,lan_owner_dual_save=True)
        proof['scope']='Five daily RNG quests, survival validation, wallet and treasury receipt recovery, shared reputation, governance, physical menus and queued real-client celebrations.' if project=='contracts-bounties' else 'CrownDailyApi v1 idempotent tax, real interrupted save recovery and actual integrated-host wallet/treasury conservation; existing Crown regressions.'
        proof['screenshots']={n:digest(ROOT/'contracts-bounties/qa-results/daily-client'/n) for n in client['screenshots']}
        checksum=jar.with_suffix('.sha256');common.write_fresh(checksum,''.join(f'{digest(p)}  {p.name}\n' for p in (jar,archive)).encode())
        metadata=jar.with_suffix('.build.json');common.write_fresh(metadata,(json.dumps(proof,indent=2)+'\n').encode())
        for p in (jar,archive,checksum,metadata):
            if p!=downloads/p.name:shutil.copyfile(p,downloads/p.name)
            paths.append(str(p.relative_to(ROOT)))
        common.notice('notice','Verified release',jar.name+' SHA256='+digest(jar))
    version=common.version('contracts-bounties');media=ROOT/'media/contracts-bounties'/version
    assert not media.exists(),'Published media already exists';media.mkdir(parents=True)
    for name in client['screenshots']:shutil.copyfile(ROOT/'contracts-bounties/qa-results/daily-client'/name,media/name)
    shutil.copyfile(ROOT/'contracts-bounties/qa-results/daily-client/result.json',media/'validation.json')
    (media/'README.md').write_text('# Contracts & Bounties '+version+'\n\nActual Minecraft 1.21.1 / NeoForge 21.1.256 client, integrated server opened to LAN, Chronicles wallet and Crown treasury. Quest quantities and scene are controlled test fixtures; all menus, progress events, payments and celebrations are real. The test closes/reopens the world. A second authenticated client was not tested.\n\n'+''.join('!['+name.removesuffix('.png')+']('+name+')\n\n' for name in client['screenshots']))
    with zipfile.ZipFile(media/f'contracts-bounties-{version}-media.zip','w',zipfile.ZIP_DEFLATED) as z:
        for name in client['screenshots']+['README.md']:z.write(media/name,name)
    paths.append(str(media.relative_to(ROOT)));(STATE/'publish-paths.json').write_text(json.dumps(paths,indent=2)+'\n')


def stage():
    paths=json.loads((STATE/'publish-paths.json').read_text());assert len(paths)==9
    subprocess.run(['git','add','--',*paths],cwd=ROOT,check=True)


def summary():
    common.summary()
    for name in ('daily-gametests.log','daily-integration.log'):
        path=ROOT/'contracts-bounties'/name
        if path.exists():
            log=path.read_text(errors='replace')
            lines=[l for l in log.splitlines() if re.search(r'All \d+ required tests|DAILY_.*PASSED|failed|Caused by:',l,re.I)]
            for line in lines[-20:]:common.notice('notice','daily/'+name,line[:1000])


if __name__=='__main__':
    actions={'build':common.build,'snapshot':common.snapshot,'native':native,'install':install,'integrated':integrated,'package':package,'stage':stage,'summary':summary}
    if len(sys.argv)!=2 or sys.argv[1] not in actions:raise SystemExit('Use '+', '.join(actions))
    actions[sys.argv[1]]()
