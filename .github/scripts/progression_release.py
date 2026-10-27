#!/usr/bin/env python3
"""Publish only a fixture-free Realm Progression JAR backed by native and actual-client evidence."""
from pathlib import Path
import concurrent.futures,hashlib,json,os,re,shutil,struct,subprocess,sys,tomllib,urllib.request,xml.etree.ElementTree as ET,zipfile
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'realm-progression';STATE=BASE/'build/release'
VERSION='0.1.0';NAME=f'realm-progression-{VERSION}'
PINS={
 'realm-treasury-0.4.0.jar':'fb22b77ae2ab11ecfc4b81701c67d22a4618198f3b04d97922625491f5f32f39',
 'realm-origins-0.5.7.jar':'0b90c49b9e3c49d2f0343301ac3662b21d6ce5551ea343fb7fc8ebdab9e09684',
 'realm-economy-0.5.0.jar':'f1dbb1ae42caa1a6982a2e27843baf3776f0c5b146bf415a6290c097883ed439',
 'realm-notifications-0.1.1.jar':'2fb85c87eb296e49f3f68c0d7defd709ceca8913fa5a3f6ef9442d9b9548c99e',
 'contracts-bounties-0.4.0.jar':'d1bd4726ac210e6d5904829bf9185efef76920aa8070ffb292d6e479689ee07e',
 'realmcouncil-0.2.2.jar':'0a77af87a5c4e36f55ad12578662834886a7986864896a908a3dc4217e2c19cb',
}
CREATE='create-1.21.1-6.0.10.jar';CREATE_SHA='ef87fe5709f1ba1f5b8bb20a2925b5afb4669e178fd6d8bf10c167759eefe37a'
CREATE_URL='https://cdn.modrinth.com/data/LNytGWDc/versions/UjX6dr61/'+CREATE
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def install():
 compat=BASE/'compat-libs';compat.mkdir(exist_ok=True)
 assert {p.name for p in compat.glob('*.jar')}<=set(PINS)-{'realm-treasury-0.4.0.jar'},'Unexpected companion JAR'
 for name,checksum in PINS.items():
  p=ROOT/'mod-pronto'/name;assert digest(p)==checksum,name
  if not name.startswith('realm-treasury-'):shutil.copyfile(p,compat/name)
 directory=BASE/'create-libs';directory.mkdir(exist_ok=True);assert {p.name for p in directory.glob('*.jar')}<={CREATE}
 p=directory/CREATE
 if not p.exists():
  with urllib.request.urlopen(CREATE_URL,timeout=120) as response:data=response.read()
  assert hashlib.sha256(data).hexdigest()==CREATE_SHA,'Changed Create download';p.write_bytes(data)
 assert digest(p)==CREATE_SHA
 print('Pinned Treasury, five roleplay companions and Create 6.0.10 verified.')
def validate_jar(p):
 with zipfile.ZipFile(p) as z:
  assert z.testzip() is None
  metadata=tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())
  assert len(metadata['mods'])==1 and metadata['mods'][0]['modId']=='realmprogression' and metadata['mods'][0]['version']==VERSION
  for n in z.namelist():
   assert not n.startswith(('net/minecraft/','net/neoforged/','com/simibubi/','pt/crownledger/','META-INF/jarjar/','pt/realmprogression/test/','pt/realmprogression/visualtest/','data/progression_fixture/','data/realmprogression/structure/')) and 'research_alternative' not in n,n
  for n in ('realmprogression.mixins.json','pt/realmprogression/service/CraftGate.class','pt/realmprogression/data/ProgressStore.class','assets/realmprogression/models/block/research_table.json','data/realmprogression/recipe/research_table.json'):assert n in z.namelist(),n
  model=json.loads(z.read('assets/realmprogression/models/block/research_table.json'));assert len(model['elements'])>=50
  keys=[set(json.loads(z.read('assets/realmprogression/lang/'+lang+'.json'))) for lang in ('en_us','pt_pt','pt_br')];assert keys[0]==keys[1]==keys[2]
def snapshot():
 STATE.mkdir(parents=True,exist_ok=True);jar=BASE/'build/libs'/f'{NAME}.jar';validate_jar(jar);shutil.copyfile(jar,STATE/jar.name)
 data={'source_commit':os.environ['GITHUB_SHA'],'sha256':digest(jar),'version':VERSION};(STATE/'manifest.json').write_text(json.dumps(data,indent=2)+'\n')
def run(flags,log,timeout=600):
 try:
  p=subprocess.run(['./gradlew','--no-daemon','--console=plain',*flags],cwd=BASE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=timeout);output=p.stdout;ok=p.returncode==0
 except subprocess.TimeoutExpired as e:output=(e.stdout or b'').decode(errors='replace') if isinstance(e.stdout,bytes) else (e.stdout or '');output+='\nBUILD FAILED: timeout';ok=False
 (BASE/log).write_text(output);print(output,flush=True);assert ok,log
def count_native(log):
 text=(BASE/log).read_text(errors='replace');expected=sum(len(re.findall(r'@GameTest\s*\(',p.read_text())) for p in (BASE/'src/gametest/java').rglob('*.java'));counts=re.findall(r'All (\d+) required tests passed',text)
 assert counts and int(counts[-1])==expected and 'BUILD SUCCESSFUL' in text and 'BUILD FAILED' not in text and '/FATAL]' not in text,log
 for marker in ('PROGRESSION_FUNDS_PASSED','PROGRESSION_OBSERVER_PASSED'):assert marker in text,marker
 if log=='integration.log':assert 'PROGRESSION_CREATE_PASSED 6.0.10' in text
 return expected
def validate():
 m=json.loads((STATE/'manifest.json').read_text());assert m['source_commit']==os.environ['GITHUB_SHA']
 for jar in (STATE/f'{NAME}.jar',BASE/'build/libs'/f'{NAME}.jar'):validate_jar(jar);assert digest(jar)==m['sha256'],'Production JAR changed during testing'
 tests=[ET.parse(p).getroot() for p in (BASE/'build/test-results/test').glob('TEST-*.xml')];unit=sum(int(x.attrib['tests']) for x in tests);assert unit>=25 and all(int(x.attrib['failures'])==int(x.attrib['errors'])==int(x.attrib.get('skipped',0))==0 for x in tests)
 assert 'BUILD SUCCESSFUL' in (BASE/'build.log').read_text()
 native=count_native('gametests.log');integrated=count_native('integration.log')
 install()
 report=json.loads((BASE/'qa-results/progression-client/result.json').read_text());assert report['success'] is True and report['stage']==27
 for key in ('actualTcpLan','actualCreateCrafts','actualTreasuryDonation','actualWorldReopening'):assert report[key] is True,key
 assert report['actualMinecraftClients']==2 and report['syntheticFinancialSnapshots'] is False
 assert report['mods']['create']=='6.0.10' and report['mods']['kingdomtreasury']=='0.4.0' and report['mods']['realmeconomy']=='0.5.0' and 'progressionfixture' not in report['mods']
 shots=report['screenshots'];assert len(shots)>=14 and len(shots)==len(set(shots))
 for name in shots:
  assert re.fullmatch(r'[a-z0-9-]+\.png',name);b=(BASE/'qa-results/progression-client'/name).read_bytes();assert b[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',b[16:24])==(1600,900),name
 guest=json.loads((BASE/'qa-results/progression-client/guest-done.json').read_text());assert guest['success'] is True and json.loads((BASE/'qa-results/progression-client/guest-denied.json').read_text())['success'] is True
 log=(BASE/'visual-client.log').read_text(errors='replace');assert 'PROGRESSION_CLIENT_QA success=true' in log and 'BUILD SUCCESSFUL' in log and 'PROGRESSION_CLIENT_QA success=false' not in log
 configs=[r for r in report['requests'] if r['operation']=='configure'];assert len(configs)==1 and configs[0]['mod']=='realmeconomy' and configs[0]['goal']==1000000
 for r in report['requests']:
  assert set(r)==({'operation','nonce','revision','mod','goal','enabled','namespaces'} if r['operation']=='configure' else {'operation','search','selected','page','browse'})
 return m,{'unit_tests':unit,'standalone_native_tests':native,'integrated_create_tests':integrated,'actual_minecraft_clients':2,'actual_tcp_lan':True,'actual_world_reopening':True,'actual_native_shift_crafting':True,'non_operator_packet_denied':True},report
def package():
 m,proof,report=validate();proof.update(version=VERSION,source_commit=m['source_commit'],jar_sha256=m['sha256'],tested_run=f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",companions=PINS,create_sha256=CREATE_SHA)
 out=ROOT/'mod-pronto';jar=out/f'{NAME}.jar';assert not jar.exists(),'Existing immutable release';shutil.copyfile(STATE/jar.name,jar)
 archive=out/f'{NAME}.zip'
 with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
  z.write(jar,jar.name)
  for name in ('README.md','CHANGELOG.md','LICENSE'):z.write(BASE/name,name)
 proof['zip_sha256']=digest(archive)
 (out/f'{NAME}.sha256').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in (jar,archive)))
 media=ROOT/'media/realm-progression'/VERSION;assert not media.exists();media.mkdir(parents=True)
 proof['screenshots']={name:digest(BASE/'qa-results/progression-client'/name) for name in report['screenshots']}
 for name in report['screenshots']:shutil.copyfile(BASE/'qa-results/progression-client'/name,media/name)
 shutil.copyfile(BASE/'qa-results/progression-client/result.json',media/'validation.json')
 (media/'README.md').write_text('# Realm Progression '+VERSION+'\n\nActual Minecraft 1.21.1 client captures with Create 6.0.10, Realm Treasury 0.4.0 and Realm Origins. Controlled fixtures seed the starting treasury and personal wallets. Production UI packets configure the second mod; an actual Treasury donation triggers research, native server crafting consumes the ingredients, two offline-identity clients connect by TCP LAN, and the world is really closed and reopened. Custom machine recipes and every other mod are not covered.\n\n'+''.join('!['+name.removesuffix('.png')+']('+name+')\n\n' for name in report['screenshots']))
 with zipfile.ZipFile(media/f'{NAME}-media.zip','w',zipfile.ZIP_DEFLATED) as z:
  for name in report['screenshots']+['README.md']:z.write(media/name,name)
 (out/f'{NAME}.build.json').write_text(json.dumps(proof,indent=2)+'\n')
 paths=[f'mod-pronto/{NAME}{suffix}' for suffix in ('.jar','.zip','.sha256','.build.json')]+[str(media.relative_to(ROOT))]
 (STATE/'publish-paths.json').write_text(json.dumps(paths,indent=2)+'\n')
 print('Verified release:',NAME,m['sha256'])
def stage():subprocess.run(['git','add','--',*json.loads((STATE/'publish-paths.json').read_text())],cwd=ROOT,check=True)
if __name__=='__main__':
 action=sys.argv[1]
 if action=='build':run(['build'],'build.log');snapshot()
 elif action=='native':run(['-PprogressionGameTests','runGameTestServer'],'gametests.log');count_native('gametests.log')
 elif action=='integrated':run(['-PprogressionGameTests','-PcreateTests','-PintegrationTests','runGameTestServer'],'integration.log');count_native('integration.log')
 elif action=='validate':print(json.dumps(validate()[1],indent=2))
 else:{'install':install,'snapshot':snapshot,'package':package,'stage':stage}[action]()
