#!/usr/bin/env python3
"""Build and jointly publish fresh Realm Titles and Realm Identity after native and actual-client validation."""
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
STATE = ROOT / 'realm-titles/build/titles-suite'
# project: mod ID, artifact basename, native property, minimum existing JUnit count
PROJECTS = {
    'realm-titles': ('realmtitles', 'realm-titles', 'titlesGameTests', 48),
    'realm-identity': ('realmidentity', 'realm-identity', 'identityGameTests', 35),
}
WRAPPER_SHA = '423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9'
DISTRIBUTION_SHA = '72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f'
COMPANION_PINS = {
    'cronicasrp-0.5.4.jar': '197c2fac53f97eb59159c9a0c26b3065362bdc18c8de1a1c0e61ece4360ba1f5',
    'contracts-bounties-0.3.2.jar': '2ecf125a188f042f8d6c5011af5e1ff2cc5974925835bcd2cc0180f75d07ff15',
    'realmcouncil-0.2.2.jar': '0a77af87a5c4e36f55ad12578662834886a7986864896a908a3dc4217e2c19cb',
    'crown-ledger-0.2.1.jar': '120eb1729d5546b76fa27eff2edfeff0fe8e4cb33dc2bb8f7b75a6ac532dcfe8',
    'realm-markets-0.2.1.jar': 'bfb1e73186015ab94a86c841abba1df02bf1b5851a65771e622234624b806bb1',
    'realm-rewards-0.1.2.jar': 'ab8efe3bd9a7b24a4f725bf6dfe1f07dec0b88ecfaac91533b57c45b11cb7bcb',
    'realm-notifications-0.1.0.jar': '5aa70ac5a7696ce626ffa40e2e530038e90c06819dbeea6f43be70d42310822a',
}
RUNTIME_PINS = {
    'origins-neoforge-1375372-8758523.jar': '93383233a047a9838af067b64452964183cc4fe9f2d9c269ffb253141136caf6',
    'jupiter-1072905-7738312.jar': '95af332af250f97fe2d9d288fe6b2057c266673a388348bf33756b0b693ace15',
    'origins-classes-neoforge-1536425-8758587.jar': '14fcc794ae3fc66f752961f7df480356bb5b7cb01d78a2670e9f1e9d18bafb67',
}


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
        required = 'pt/realmtitles/api/TitlesApi.class' if project == 'realm-titles' else 'pt/realmidentity/compat/TitlesBridge.class'
        assert required in names, project + ': titles integration missing'



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
        expected = dict(COMPANION_PINS)
        for other, saved in data['production_jars'].items():
            if other == project:
                continue
            shutil.copyfile(STATE / saved['filename'], target / saved['filename'])
            expected[saved['filename']] = saved['sha256']
        for name in COMPANION_PINS:
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
    data = manifest()
    results = {}
    companions = json.loads((STATE / 'companions.json').read_text())
    for project in PROJECTS:
        log = (ROOT / project / 'build.log').read_text(errors='replace')
        assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log, project + ': production build failed'
        results[project] = {'unit_tests': unit_count(project),
            'standalone_native_tests': native_count(project, 'gametests.log'),
            'integrated_native_tests': native_count(project, 'integration.log')}
        expected = dict(COMPANION_PINS)
        for other, saved in data['production_jars'].items():
            if other != project:
                expected[saved['filename']] = saved['sha256']
        actual = {p.name: digest(p) for p in (ROOT / project / 'compat-libs').glob('*.jar')}
        assert actual == expected == companions[project], project + ': fresh integration companions changed'
    base = ROOT / 'realm-titles'
    for filename, integration in [('gametests.log', 'false'), ('integration.log', 'true')]:
        log = (base / filename).read_text(errors='replace')
        assert 'TITLES_NATIVE_PASSED' in log, filename + ': actual native assertions missing'
        assert f'TITLES_RUNTIME_MODE_PASSED integration={integration}' in log, filename + ': wrong runtime mode'
        if integration == 'true':
            for marker in ('TITLES_COUNCIL_AUTHORITY_PASSED', 'TITLES_NOTIFICATIONS_PASSED'):
                assert marker in log, filename + ': missing ' + marker
    identity_log = (ROOT / 'realm-identity/integration.log').read_text(errors='replace')
    for marker in ('IDENTITY_TITLES_LIVE_SERIAL_OK', 'IDENTITY_TITLES_OFFLINE_EXPIRY_OK', 'IDENTITY_TITLES_OPTIONAL_FAILURE_FORGERY_OK'):
        assert re.search(re.escape(marker) + r'[^\n]*loaded=true', identity_log), 'Identity live titles proof missing: ' + marker
    client = json.loads((base / 'qa-results/titles-client/result.json').read_text())
    assert client.get('success') is True, 'Actual Minecraft titles client failed'
    for flag in ('real_identity_live_titles', 'real_council_sworn_mandate_fixture', 'real_chronicles_created_profile_fixture', 'real_c2s_transport', 'physical_station_closed_when_broken'):
        assert client.get(flag) is True, 'Actual integrated client proof missing: ' + flag
    assert client.get('world_desk_facings_captured') == 4, 'Four actual desk orientations were not captured'
    assert client.get('synthetic_screen_snapshots') is False, 'Client proof must use authoritative snapshots'
    clientlog = (base / 'visual-client.log').read_text(errors='replace')
    assert 'TITLES_CLIENT_PASSED' in clientlog and 'BUILD SUCCESSFUL' in clientlog and 'BUILD FAILED' not in clientlog
    images = client.get('screenshots')
    assert isinstance(images, list) and len(images) >= 2 and len(images) == len(set(images)), 'Missing actual client screenshots'
    for name in images:
        assert isinstance(name, str) and re.fullmatch(r'[a-z0-9-]+\.png', name), 'Unsafe screenshot name'
        path = base / 'qa-results/titles-client' / name
        assert path.stat().st_size > 1024 and path.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n', name
    results['realm-titles']['actual_client_validation'] = True
    return data, results, client, runtime_dependencies()


def write_fresh(path, content):
    if path.exists():
        assert path.read_bytes() == content, 'Refusing to replace an existing release: ' + str(path)
    else:
        path.write_bytes(content)


def package():
    data, results, client, dependencies = validate()
    out = ROOT / 'mod-pronto'
    out.mkdir(exist_ok=True)
    downloads = STATE / 'downloads'
    downloads.mkdir(exist_ok=True)
    files = []
    tested_run = f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
    for project, saved in data['production_jars'].items():
        jar = out / saved['filename']
        write_fresh(jar, (STATE / saved['filename']).read_bytes())
        archive = out / jar.with_suffix('.zip').name
        temp = downloads / archive.name
        with zipfile.ZipFile(temp, 'w', zipfile.ZIP_DEFLATED) as zipped:
            zipped.write(jar, jar.name)
            for name in ('README.md', 'CHANGELOG.md', 'LICENSE', 'FONT_LICENSE.txt', 'TEMPLATE_LICENSE.txt'):
                source = ROOT / project / name
                if source.exists():
                    zipped.write(source, name)
        write_fresh(archive, temp.read_bytes())
        proof = dict(results[project], version=saved['version'], source_commit=data['source_commit'], tested_run=tested_run,
                     jar_sha256=digest(jar), zip_sha256=digest(archive), joint_suite_versions={p: item['version'] for p, item in data['production_jars'].items()},
                     runtime_dependency_checksums=dependencies)
        proof['companions'] = json.loads((STATE / 'companions.json').read_text())[project]
        if project == 'realm-titles':
            proof['native_markers'] = ['TITLES_NATIVE_PASSED', 'TITLES_COUNCIL_AUTHORITY_PASSED', 'TITLES_NOTIFICATIONS_PASSED']
            proof['screenshots'] = {name: digest(ROOT / project / 'qa-results/titles-client' / name) for name in client['screenshots']}
        if project == 'realm-identity':
            proof['network_protocol'] = '2'
            proof['native_markers'] = ['IDENTITY_TITLES_LIVE_SERIAL_OK', 'IDENTITY_TITLES_OFFLINE_EXPIRY_OK', 'IDENTITY_TITLES_OPTIONAL_FAILURE_FORGERY_OK']
        checksum = jar.with_suffix('.sha256')
        write_fresh(checksum, ''.join(f'{digest(path)}  {path.name}\n' for path in (jar, archive)).encode())
        provenance = jar.with_suffix('.build.json')
        write_fresh(provenance, (json.dumps(proof, indent=2) + '\n').encode())
        for path in (jar, archive, checksum, provenance):
            if path != downloads / path.name:
                shutil.copyfile(path, downloads / path.name)
            files.append(str(path.relative_to(ROOT)))
        notice('notice', 'Tested release', f'{jar.name}: {jar.stat().st_size} bytes; SHA256={digest(jar)}; checks={results[project]}')
    release = version('realm-titles')
    media = ROOT / 'media/realm-titles' / release
    assert not media.exists(), 'Refusing to replace an existing media release'
    media.mkdir(parents=True)
    visual = ROOT / 'realm-titles/qa-results/titles-client'
    for name in client['screenshots']:
        shutil.copyfile(visual / name, media / name)
    shutil.copyfile(visual / 'result.json', media / 'validation.json')
    (media / 'README.md').write_text('# Realm Titles\n\nActual Minecraft 1.21.1 NeoForge integrated-client captures of the royal seal desk and honours menus, with demonstration characters and titles. The accompanying native server suites test authority, sessions, persistence and authentic Identity documents. These captures do not establish authenticated multiplayer compatibility.\n\n' + ''.join(f'![Realm Titles]({name})\n\n' for name in client['screenshots']))
    with zipfile.ZipFile(media / f'realm-titles-{release}-media.zip', 'w', zipfile.ZIP_DEFLATED) as zipped:
        for name in client['screenshots'] + ['README.md']:
            zipped.write(media / name, name)
    files.append(str(media.relative_to(ROOT)))
    suite = out / f'realm-titles-{release}-suite.zip'
    temporary = downloads / suite.name
    instructions = '''Realm Titles and Realm Identity update

Minecraft 1.21.1 / NeoForge 21.1.256+ / Java 21

Install Realm Titles on the server and all clients. Realm Identity is optional;
if used, replace its older JAR with the included 0.1.1 on server and all clients.
Do not keep two versions of either mod. Titles has no required companion mods.

With Realm Council, the current sworn governor manages honours at the crafted
Royal Seal Desk. Without Council, an operator can appoint a steward using
/titles steward <onlinePlayer>. /titles desk gives the physical block to an OP.
All players may inspect the public registry by interacting with the desk.

Realm Identity displays current public titles when a valid document is inspected;
existing documents need not be reissued. Chronicles public names and Realm
Notifications are optional integrations. No title changes money or combat stats.

The seven existing roleplay mods and Origins/Jupiter were used for integration
validation and are not included. SHA256SUMS.txt verifies both production JARs.
'''

    with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as zipped:
        checksums = []
        for saved in data['production_jars'].values():
            source = STATE / saved['filename']
            zipped.write(source, source.name)
            checksums.append(f"{saved['sha256']}  {saved['filename']}\n")
        zipped.writestr('README.txt', instructions)
        zipped.writestr('SHA256SUMS.txt', ''.join(checksums))
    write_fresh(suite, temporary.read_bytes())
    suite_checksum = suite.with_suffix('.sha256')
    write_fresh(suite_checksum, f'{digest(suite)}  {suite.name}\n'.encode())
    shutil.copyfile(suite_checksum, downloads / suite_checksum.name)
    files.extend([str(suite.relative_to(ROOT)), str(suite_checksum.relative_to(ROOT))])
    (STATE / 'publish-paths.json').write_text(json.dumps(files, indent=2) + '\n')


def stage():
    # Argument array avoids shell interpolation of repository paths.
    paths = json.loads((STATE / 'publish-paths.json').read_text())
    assert len(paths) == 4 * len(PROJECTS) + 3
    subprocess.run(['git', 'add', '--', *paths], cwd=ROOT, check=True)


def summary():
    parts = ['# Realm Titles suite validation']
    notices = []
    # Emit failures before routine notices: GitHub displays only a bounded annotation set.
    for project in PROJECTS:
        parts.append('## ' + project)
        reports = [ET.parse(file).getroot() for file in (ROOT / project / 'build/test-results/test').glob('TEST-*.xml')]
        counts = {key: sum(int(report.get(key, 0)) for report in reports) for key in ('tests', 'failures', 'errors', 'skipped')}
        notices.append((project + ' JUnit', str(counts)))
        parts.append('JUnit ' + str(counts))
        for filename in ('build.log', 'gametests.log', 'integration.log', 'visual-client.log'):
            file = ROOT / project / filename
            if not file.exists():
                continue
            lines = file.read_text(errors='replace').splitlines()
            tail = '\n'.join(lines[-45:])[-6000:]
            parts.append(f'### {filename}\n```text\n{tail.replace("```", "~~~")}\n```')
            proof = [line for line in lines if re.search(r'All \d+ required tests|TITLES_\w+_PASSED|IDENTITY_TITLES_\w+_OK', line)]
            if proof:
                notices.append((project + '/' + filename, '\n'.join(proof[-10:])))
            if any('BUILD FAILED' in line or 'FAILURE:' in line for line in lines):
                indices = set()
                for index, line in enumerate(lines):
                    if re.search(r'failed!|test.*failed|GameTestAssertException|AssertionError|Caused by:|Exception:| error:|What went wrong', line, re.I):
                        indices.update(range(max(0, index - 2), min(len(lines), index + 9)))
                context = '\n'.join(lines[index] for index in sorted(indices))[:12000] or tail
                for index in range(0, len(context), 2000):
                    notice('error', project + '/' + filename, context[index:index + 2000])
    report = ROOT / 'realm-titles/qa-results/titles-client/result.json'
    if report.exists():
        data = json.loads(report.read_text())
        if not data.get('success'):
            notice('error', 'Actual Minecraft client', json.dumps(data)[:6000])
        else:
            notices.append(('Actual Minecraft client', json.dumps(data)[:6000]))
        parts.append('## Actual client\n```json\n' + json.dumps(data, indent=2)[:12000] + '\n```')
    for title, message in notices:
        notice('notice', title, message)
    summary_file = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary_file:
        with Path(summary_file).open('a') as out:
            out.write('\n\n'.join(parts) + '\n')


if __name__ == '__main__':
    actions = {'build': build, 'snapshot': snapshot, 'native': native, 'install': install, 'integrated': integrated,
               'package': package, 'stage': stage, 'summary': summary}
    if len(sys.argv) != 2 or sys.argv[1] not in actions:
        raise SystemExit('Use ' + ', '.join(actions))
    actions[sys.argv[1]]()
