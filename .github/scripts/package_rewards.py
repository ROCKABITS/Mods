from pathlib import Path
import hashlib,json,os,re,shutil,tomllib,xml.etree.ElementTree as ET,zipfile
root=Path(__file__).resolve().parents[2];p=root/'realm-rewards'
version=re.search(r'^mod_version=(.+)$',(p/'gradle.properties').read_text(),re.M)[1]
reports=[ET.parse(f).getroot() for f in (p/'build/test-results/test').glob('TEST-*.xml')]
unit=sum(int(r.get('tests',0)) for r in reports)
assert unit>=12 and not any(int(r.get(key,0)) for r in reports for key in ['failures','errors','skipped'])
assert 'BUILD SUCCESSFUL' in (p/'build.log').read_text()
expected=sum(len(re.findall(r'@GameTest\s*\(',f.read_text())) for f in (p/'src/gametest/java').rglob('*.java'))
native={}
for name in ['gametests.log','integration.log']:
    log=(p/name).read_text(errors='replace');counts=re.findall(r'All (\d+) required tests passed',log,re.I)
    assert counts and int(counts[-1])==expected and 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log,name
    assert 'REWARDS_NATIVE_ADVANCEMENTS_PASSED' in log and 'REWARDS_RUNTIME_MODE_PASSED' in log
    if name=='integration.log':assert 'REWARDS_CHRONICLES_PAYMENTS_PASSED' in log and 'REWARDS_AUTOMATIC_RETRY_PASSED' in log
    native[name]=int(counts[-1])
client=json.loads((p/'qa-results/rewards-client/result.json').read_text())
assert client['success'] is True
assert 'REWARDS_CLIENT_PASSED' in (p/'visual-client.log').read_text() and 'BUILD SUCCESSFUL' in (p/'visual-client.log').read_text()
digest=lambda file:hashlib.sha256(file.read_bytes()).hexdigest()
jar=p/f'build/libs/realm-rewards-{version}.jar'
assert digest(jar)==(p/'build/production.sha256').read_text().strip(),'Production jar changed during tests'
with zipfile.ZipFile(jar) as z:
    assert z.testzip() is None;names=z.namelist();meta=tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())
    assert next(m for m in meta['mods'] if m['modId']=='realmrewards')['version']==version
    for required in ['pt/realmrewards/service/RewardService.class','pt/realmrewards/domain/RewardLedger.class','pt/realmrewards/client/RewardScreen.class','assets/realmrewards/lang/pt_pt.json']:assert required in names
    assert not any(n.startswith(('pt/realmrewards/test/','pt/realmrewards/visualtest/','data/rewards_fixture/','data/realmrewards/structure/','pt/cronicasrp/','com/iafenvoy/','META-INF/jarjar/')) for n in names)
out=root/'mod-pronto';out.mkdir(exist_ok=True);published=out/jar.name;shutil.copy2(jar,published)
archive=out/f'realm-rewards-{version}.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    z.write(published,published.name)
    for name in ['README.md','CHANGELOG.md','LICENSE']:z.write(p/name,name)
media=root/f'media/realm-rewards/{version}';media.mkdir(parents=True,exist_ok=True)
images=['realm-rewards-advancements.png','realm-rewards-defaults.png']
for name in images:
    file=p/'qa-results/rewards-client'/name
    assert file.stat().st_size>1024 and file.read_bytes()[:8]==b'\x89PNG\r\n\x1a\n';shutil.copy2(file,media/name)
shutil.copy2(p/'qa-results/rewards-client/result.json',media/'validation.json')
(media/'README.md').write_text('# Realm Rewards\n\nActual Minecraft administration screens using demonstration advancement data.\n\n'+''.join(f'![Realm Rewards administration]({name})\n\n' for name in images))
with zipfile.ZipFile(media/f'realm-rewards-{version}-media.zip','w',zipfile.ZIP_DEFLATED) as z:
    for name in images+['README.md']:z.write(media/name,name)
(out/f'realm-rewards-{version}.sha256').write_text(''.join(f'{digest(file)}  {file.name}\n' for file in [published,archive]))
provenance={'version':version,'source_commit':os.environ['GITHUB_SHA'],'tested_run':f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",'jar_sha256':digest(published),'zip_sha256':digest(archive),'unit_tests':unit,'standalone_native_tests':native['gametests.log'],'chronicles_native_tests':native['integration.log'],'actual_client_validation':True,'chronicles_runtime_tested':True,'screenshots':{name:digest(media/name) for name in images},'companions':json.loads((p/'build/companions.json').read_text())}
(out/f'realm-rewards-{version}.build.json').write_text(json.dumps(provenance,indent=2)+'\n')
print(f'::notice title=Published tested Realm Rewards::unit={unit};native={native};jar={published.name};bytes={published.stat().st_size};SHA256={digest(published)}')
