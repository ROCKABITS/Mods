#!/usr/bin/env python3
"""Release three immutable production JARs after native recovery and four real TCP clients."""
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

ROOT = common.ROOT
STATE = ROOT / 'realm-marriage/build/marriage-release'
PROJECTS = {
    'realm-marriage': ('realmmarriage', 'realm-marriage', 'marriageGameTests', 45),
    'crown-ledger': ('kingdomtreasury', 'crown-ledger', 'crownGameTests', 59),
    'realm-identity': ('realmidentity', 'realm-identity', 'identityGameTests', 35),
}
COMPANIONS = {k: v for k, v in common.COMPANION_PINS.items()
              if not k.startswith(('contracts-bounties-', 'realm-notifications-', 'realm-identity-'))}
COMPANIONS.update({
    'contracts-bounties-0.4.0.jar': 'd1bd4726ac210e6d5904829bf9185efef76920aa8070ffb292d6e479689ee07e',
    'realm-notifications-0.1.1.jar': '2fb85c87eb296e49f3f68c0d7defd709ceca8913fa5a3f6ef9442d9b9548c99e',
    'realm-economy-0.4.0.jar': 'eb7b25c11b64e842297d5ed174c067749989b6d5440fbc858834bd0922f55ddf',
})
common.STATE = STATE
common.PROJECTS = PROJECTS
digest = common.digest


def validate_jar(project, path):
    with zipfile.ZipFile(path) as jar:
        assert jar.testzip() is None, 'Damaged production JAR'
        names = jar.namelist()
        metadata = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
        assert next(m for m in metadata['mods'] if m['modId'] == PROJECTS[project][0])['version'] == common.version(project)
        assert not any('/test/' in n or '/visualtest/' in n or '/gametest/' in n or '/structure/' in n
                       or n.startswith(('net/minecraft/', 'net/neoforged/', 'META-INF/jarjar/')) for n in names), 'Fixture/dependency in production'
        required = {
            'realm-marriage': ['pt/realmmarriage/api/MarriagePayments.class', 'pt/realmmarriage/api/MarriageApi.class',
                               'pt/realmmarriage/api/MarriagePermissions.class', 'pt/realmmarriage/service/WalletJournal.class',
                               'pt/realmmarriage/client/MarriageClient.class', 'pt/realmmarriage/client/MarriageScreen.class'],
            'crown-ledger': ['pt/crownledger/api/CrownMarriageApi.class', 'pt/crownledger/api/CrownDailyApi.class'],
            'realm-identity': ['pt/realmidentity/compat/MarriageBridge.class', 'pt/realmidentity/service/IdentityService.class'],
        }
        assert set(required[project]) <= set(names), project
        if project == 'realm-marriage':
            for language in ('en_us', 'pt_pt', 'pt_br'):
                text = json.loads(jar.read('assets/realmmarriage/lang/' + language + '.json'))
                assert text['marriage.hud.objections'] and text['marriage.hud.married']
            for sound in ('opening', 'countdown', 'objection', 'married'):
                assert 'assets/realmmarriage/sounds/ceremony/' + sound + '.ogg' in names
            assert 'assets/realmmarriage/blockstates/wedding_altar.json' in names
            for model in ('wedding_altar', 'engagement_ring', 'wedding_ring', 'marriage_certificate'):
                assert 'assets/realmmarriage/models/item/' + model + '.json' in names


common.validate_jar = validate_jar


def install():
    data = common.manifest()
    target = ROOT / 'realm-marriage/compat-libs'
    target.mkdir(exist_ok=True)
    assert not list(target.glob('*.jar')), 'Unexpected integration binaries'
    pins = {}
    for name, checksum in COMPANIONS.items():
        source = ROOT / 'mod-pronto' / name
        assert digest(source) == checksum == json.loads(source.with_suffix('.build.json').read_text())['jar_sha256'], name
        shutil.copyfile(source, target / name)
        pins[name] = checksum
    for project in ('crown-ledger', 'realm-identity'):
        saved = data['production_jars'][project]
        shutil.copyfile(STATE / saved['filename'], target / saved['filename'])
        pins[saved['filename']] = saved['sha256']
    assert len(pins) == 12
    (STATE / 'companions.json').write_text(json.dumps(pins, indent=2) + '\n')


def integrated():
    assert common.run_gradle('realm-marriage', ['-PmarriageGameTests', '-PintegrationTests', 'runGameTestServer'], 'integration.log', 600)


def validate():
    data = common.manifest()
    results = {}
    for project in PROJECTS:
        log = (ROOT / project / 'build.log').read_text(errors='replace')
        assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log
        results[project] = {'unit_tests': common.unit_count(project), 'standalone_native_tests': common.native_count(project, 'gametests.log')}
    assert results['realm-marriage']['standalone_native_tests'] == 23
    results['realm-marriage']['integrated_native_tests'] = common.native_count('realm-marriage', 'integration.log')
    pins = json.loads((STATE / 'companions.json').read_text())
    assert {p.name: digest(p) for p in (ROOT / 'realm-marriage/compat-libs').glob('*.jar')} == pins
    log = (ROOT / 'realm-marriage/integration.log').read_text(errors='replace')
    for marker in ('JOINT_CROWN', 'SHARED_DIVORCE', 'CROWN_INTERRUPTION', 'COUNCIL', 'INTENT_FAILURE',
                   'WALLET_RECOVERY', 'DURABLE_PLAYER_PROOF', 'SEPARATE', 'REFRESH_CONSENT'):
        assert 'MARRIAGE_' + marker + '_PASSED' in log, marker
    client = json.loads((ROOT / 'realm-marriage/qa-results/marriage-client/result.json').read_text())
    for flag in ('success', 'actual_minecraft_client', 'actual_tcp_lan', 'real_chronicles_wallet', 'real_crown_fee',
                 'real_identity_certificate', 'real_disconnect_during_ceremony', 'real_world_close_and_reopen',
                 'real_guest_key_objection', 'real_both_individual_vows', 'real_witness_consent', 'controls_free',
                 'non_admin_celebrant_and_guests', 'compact_controls_and_local_preferences'):
        assert client.get(flag) is True, flag
    assert client.get('actual_client_processes') == 4
    assert client.get('authenticated_clients') is False and client.get('synthetic_financial_snapshots') is False
    log = (ROOT / 'realm-marriage/visual-client.log').read_text(errors='replace')
    assert 'MARRIAGE_LAN_CLIENT_PASSED' in log and 'BUILD SUCCESSFUL' in log and 'MARRIAGE_LAN_CLIENT_FAILED' not in log
    shots = client['screenshots']
    assert len(shots) >= 8 and len(shots) == len(set(shots))
    assert {'marriage-altar.png', 'marriage-agreement.png', 'marriage-celebration.png',
            'host-identity-certificate.png', 'marriage-after-world-reopen.png'} <= set(shots)
    for name in shots + ['marriage-guest-countdown.png']:
        assert re.fullmatch(r'[a-z0-9-]+\.png', name)
        content = (ROOT / 'realm-marriage/qa-results/marriage-client' / name).read_bytes()
        assert content[:8] == b'\x89PNG\r\n\x1a\n' and struct.unpack('>II', content[16:24]) == (1600, 900), name
    return data, results, client, pins, common.runtime_dependencies()


def package():
    data, results, client, pins, runtime = validate()
    out = ROOT / 'mod-pronto'
    downloads = STATE / 'downloads'
    downloads.mkdir(exist_ok=True)
    run = f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
    shots = client['screenshots'] + ['marriage-guest-countdown.png']
    base = ROOT / 'realm-marriage/qa-results/marriage-client'
    paths = []
    for project, saved in data['production_jars'].items():
        jar = out / saved['filename']
        common.write_fresh(jar, (STATE / saved['filename']).read_bytes())
        archive = jar.with_suffix('.zip')
        temporary = downloads / archive.name
        with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as z:
            z.write(jar, jar.name)
            for name in ('README.md', 'CHANGELOG.md', 'VALIDACAO.md', 'API.md', 'LICENSE', 'TEMPLATE_LICENSE.txt'):
                p = ROOT / project / name
                if p.exists():
                    z.write(p, name)
        common.write_fresh(archive, temporary.read_bytes())
        proof = dict(results[project], version=saved['version'], source_commit=data['source_commit'], tested_run=run,
                     jar_sha256=digest(jar), zip_sha256=digest(archive),
                     project_versions={p: v['version'] for p, v in data['production_jars'].items()},
                     marriage_test_companions=pins, runtime_dependency_checksums=runtime,
                     actual_tcp_lan=True, actual_client_processes=4, authenticated_clients=False,
                     actual_realm_admin=False, realm_admin_provider_contract_tested_natively=True,
                     scope='Four real client processes: consenting ceremony, guest G objection, disconnection pause, real wallet/Crown settlement, Identity certificate, unilateral offline divorce and world reopen. Companion native suites also preserved.')
        proof['screenshots'] = {n: digest(base / n) for n in shots}
        checksum = jar.with_suffix('.sha256')
        common.write_fresh(checksum, ''.join(f'{digest(p)}  {p.name}\n' for p in (jar, archive)).encode())
        metadata = jar.with_suffix('.build.json')
        common.write_fresh(metadata, (json.dumps(proof, indent=2) + '\n').encode())
        for p in (jar, archive, checksum, metadata):
            if p != downloads / p.name:
                shutil.copyfile(p, downloads / p.name)
            paths.append(str(p.relative_to(ROOT)))
        common.notice('notice', 'Verified release', jar.name + ' SHA256=' + digest(jar))
    suite = out / ('realm-marriage-' + common.version('realm-marriage') + '-suite.zip')
    temporary = downloads / suite.name
    with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as z:
        for project, saved in data['production_jars'].items():
            z.write(STATE / saved['filename'], saved['filename'])
            z.write(ROOT / project / 'README.md', project + '/README.md')
        z.writestr('INSTALL.txt', 'Realm Marriage + Crown Ledger + Realm Identity\n\nMinecraft 1.21.1 / NeoForge 21.1.256+ / Java 21.\nExtract the ZIP. Replace older versions of these three mods and copy the three JARs into mods/ on the server and every client. Keep only one JAR per mod. Back up the world and player data together before updating.\nChronicles RP, its Origins/Jupiter requirements and other optional mods are not included. Without Chronicles, use free separate-finance weddings. Shared funds do not automatically replace personal wallet payments in other mods.\nSee realm-marriage/README.md for ceremonies, finance, fees, privacy and divorce.\n')
    common.write_fresh(suite, temporary.read_bytes())
    suite_meta = suite.with_suffix('.build.json')
    common.write_fresh(suite_meta, (json.dumps(dict(source_commit=data['source_commit'], tested_run=run,
        zip_sha256=digest(suite), jars={v['filename']: v['sha256'] for v in data['production_jars'].values()}), indent=2) + '\n').encode())
    suite_sum = suite.with_suffix('.sha256')
    common.write_fresh(suite_sum, (digest(suite) + '  ' + suite.name + '\n').encode())
    for p in (suite, suite_meta, suite_sum):
        if p != downloads / p.name:
            shutil.copyfile(p, downloads / p.name)
        paths.append(str(p.relative_to(ROOT)))
    media = ROOT / 'media/realm-marriage' / common.version('realm-marriage')
    assert not media.exists(), 'Published media already exists'
    media.mkdir(parents=True)
    for name in shots:
        shutil.copyfile(base / name, media / name)
    shutil.copyfile(base / 'result.json', media / 'validation.json')
    shutil.copyfile(ROOT / 'realm-marriage/src/main/resources/assets/realmmarriage/icon.png', media / 'icon.png')
    (media / 'README.md').write_text('# Realm Marriage 0.1.0\n\nReal Minecraft 1.21.1 / NeoForge 21.1.256, four separate client processes on an integrated TCP LAN server using offline test identities. The set, names and starting funds are fixtures; menus, input, vows, objection, wallets, treasury, documents, disconnections and world reopen are real. This is not an authenticated Mojang-account test.\n\n' + ''.join('![' + n.removesuffix('.png') + '](' + n + ')\n\n' for n in shots))
    with zipfile.ZipFile(media / 'realm-marriage-0.1.0-media.zip', 'w', zipfile.ZIP_DEFLATED) as z:
        for name in shots + ['icon.png', 'README.md']:
            z.write(media / name, name)
    paths.append(str(media.relative_to(ROOT)))
    (STATE / 'publish-paths.json').write_text(json.dumps(paths, indent=2) + '\n')


def stage():
    paths = json.loads((STATE / 'publish-paths.json').read_text())
    assert len(paths) == 16
    subprocess.run(['git', 'add', '--', *paths], cwd=ROOT, check=True)


def summary():
    common.summary()
    for name in ('gametests.log', 'integration.log', 'visual-client.log'):
        path = ROOT / 'realm-marriage' / name
        if path.exists():
            for line in path.read_text(errors='replace').splitlines():
                if re.search(r'MARRIAGE_.*(?:PASSED|FAILED)|All \d+ required tests', line):
                    common.notice('notice', 'marriage/' + name, line[:1600])


if __name__ == '__main__':
    actions = {'build': common.build, 'snapshot': common.snapshot, 'native': common.native, 'install': install,
               'integrated': integrated, 'package': package, 'stage': stage, 'summary': summary}
    if len(sys.argv) != 2 or sys.argv[1] not in actions:
        raise SystemExit('Use ' + ', '.join(actions))
    actions[sys.argv[1]]()
