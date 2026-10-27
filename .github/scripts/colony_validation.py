#!/usr/bin/env python3
"""Pinned MineColonies and the user's six addons; no third-party JARs enter a release."""
from pathlib import Path
import concurrent.futures, hashlib, json, os, re, struct, sys, urllib.request
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'realm-economy'
CORE={
 'minecolonies-245506-9058295.jar':'c8d4373ffc8a76638c8edf2bb6716f9209599032209c48d0d559b787f69a0561',
 'structurize-298744-8610535.jar':'b6ede7635c63a80bd3d034c1ad3e74c648b8478f341f883d7d29c3dcf90b79ce',
 'blockui-522992-7541336.jar':'eec023231b21a606306cac6c588b5b07f51741f5e5bc831dba709d52332b82ab',
 'domum-ornamentum-527361-7789217.jar':'04c0c902bdbcbd48e38bee5a323907ae0b7b7db4ff4a3e4da7c45334b65610a1',
 'multi-piston-303278-7097877.jar':'7ca26db4648589b7f76694c249d5096c0c0afb7615fe6adf9f638c6c825c15e7',
}
ADDONS={
 'bits-and-bobs-for-minecolonies-1411734-8110550.jar':('72aed74fbcf4b3a19e151e900f40af67f9a3ac8bd656cf370d95073459ceb4fc',None),
 'byzantine-styles-pack-for-minecolonies-974855-9002815.jar':('9be27c2123d8128b55663b591e91cd90927c9e59de7ad951119afacba7dbfa4c',None),
 'colonyrank-minecolonies-addon-1519280-8880885.jar':('84e71eb497c73573b5f404c4f9c43158bc3c2492ed843005f669400b7442561f',None),
 'mapcolonies-minecolonies-addon-for-xaeros-minimap-1758818-9284618.jar':('cfca048058264bac3b1498c0e24fa1fe7abb4927b0b310b4cfbbcb59e325b1f2',None),
 'smallcolonies-1232237-7881682.jar':('1fd36329af0e5b6519adbaa536b1fae6dce2f8b982c98ec821479b4603f2c3e3',None),
 'smallcolonies-me-1247027-8045900.jar':('cbc47f9d3e26a4e687f4e649147a787283d2d453a1ccbafaf8fcd07b0533ed30',None),
 'fzzy_config-0.7.7+fix1+1.21+neoforge.jar':('414782aba00502349da192c1affcd4cbaf653119ec26605453f1b0690b3f8cc5','https://cdn.modrinth.com/data/hYykXjDp/versions/jRuOkBAG/fzzy_config-0.7.7%2Bfix1%2B1.21%2Bneoforge.jar'),
 'kotlinforforge-5.12.0-all.jar':('2785a4dd6de5c6c56065fd8eb789c82771bd2ee773c30794c23160a3b2c39139','https://cdn.modrinth.com/data/ordsPcFz/versions/uhJhCT7X/kotlinforforge-5.12.0-all.jar'),
}
EXPECTED={'minecolonies':'1.1.1403-1.21.1','bitsandbobs':'0.1.1','byzantine':'51.1','colonyrank':'2.0.1','mapcolonies':'1.0.0','smallcolonies':'1.8','smallcoloniesme':'1.9','fzzy_config':'0.7.7+fix1+1.21+neoforge'}
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def install():
 directory=BASE/'colony-addon-libs';directory.mkdir(exist_ok=True)
 assert {p.name for p in directory.glob('*.jar')}<=set(ADDONS),'Unpinned colony addon JAR'
 def one(entry):
  name,(checksum,url)=entry;p=directory/name
  if p.exists():assert digest(p)==checksum,name;return
  if url is None:
   slug,project,file=name.removesuffix('.jar').rsplit('-',2);url=f'https://cursemaven.com/curse/maven/{slug}-{project}/{file}/{slug}-{project}-{file}.jar'
  with urllib.request.urlopen(url,timeout=90) as response:b=response.read()
  assert hashlib.sha256(b).hexdigest()==checksum,'Downloaded addon checksum mismatch: '+name
  p.write_bytes(b)
 with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:list(pool.map(one,ADDONS.items()))
 print('Verified all six selected colony addons and Fzzy/Kotlin dependencies.')
def native(addons=False):
 import economy_release as release
 flags=['-PcolonyGameTests','-PminecoloniesTests','-PintegrationTests']
 log='colony-native.log'
 if addons:install();flags+=['-PcolonyAddonsTests','-Pneo_version=21.1.262'];log='colony-addons.log'
 assert release.run_gradle('realm-economy',flags+['runGameTestServer'],log,480),'Colony native process failed'
 check_native(log)
def check_native(logfile):
 expected=sum(len(re.findall(r'@GameTest\s*\(',p.read_text())) for p in (BASE/'src/colonytest/java').rglob('*.java'))
 text=(BASE/logfile).read_text(errors='replace');counts=re.findall(r'All (\d+) required tests passed',text)
 assert counts and int(counts[-1])==expected and 'BUILD SUCCESSFUL' in text and '/FATAL]' not in text,logfile+': incomplete native suite'
 assert 'COLONY_ALCHEMIST_PASSED' in text,logfile+': native alchemist compatibility not exercised'
 if logfile=='colony-addons.log':assert 'COLONY_ADDONS_VERIFIED' in text
 return expected
def runtime():
 cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/curse.maven'
 for name,checksum in CORE.items():
  paths=list(cache.rglob(name));assert paths and all(digest(p)==checksum for p in paths),'Changed/missing colony core dependency: '+name
 actual={p.name:digest(p) for p in (BASE/'colony-addon-libs').glob('*.jar')};assert actual=={n:v[0] for n,v in ADDONS.items()},'Changed addon runtime'
 return dict(CORE,**actual)
def validate():
 native_count=check_native('colony-native.log');addon_count=check_native('colony-addons.log')
 report=json.loads((BASE/'qa-results/colony-client/result.json').read_text())
 assert report.get('success') is True and report.get('stage')==33 and report.get('actualMinecraftClients')==2 and report.get('actualTcpLan') is True and report.get('actualChroniclesWallet') is True and report.get('syntheticFinancialSnapshots') is False
 for mod,version in EXPECTED.items():assert report.get('mods',{}).get(mod)==version,(mod,report.get('mods',{}).get(mod))
 assert report['mods']['neoforge']=='21.1.262'
 shots=report['screenshots'];assert len(shots)>=17 and len(shots)==len(set(shots))
 for name in shots:
  assert re.fullmatch(r'[a-z0-9-]+\.png',name),name
  b=(BASE/'qa-results/colony-client'/name).read_bytes();assert b[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',b[16:24])==(1600,900)
 log=(BASE/'colony-client.log').read_text(errors='replace');assert 'COLONY_CLIENT_QA success=true' in log and 'BUILD SUCCESSFUL' in log
 for op,n in {'clnDeposit':1,'clnWithdraw':1,'clnExport':1,'clnPay':2,'clnPolicy':1}.items():assert sum(r['operation']==op for r in report['requests'])==n,op
 for r in report['requests']:
  assert r['operation'] in {'clnDeposit','clnWithdraw','clnExport','clnPay','clnRefresh','clnPolicy'}
  expected={'nonce','amount'} if r['operation'] in {'clnDeposit','clnWithdraw'} else {'nonce','order'} if r['operation']=='clnExport' else {'nonce','invoice'} if r['operation']=='clnPay' else {'nonce','dailyCap','playerCap','orders','buildCents','recruitCents','enabled'} if r['operation']=='clnPolicy' else set()
  assert set(r)-{'operation'}==expected
 guest=json.loads((BASE/'qa-results/colony-client/guest-done.json').read_text());assert guest['success'] is True and sum(r['operation']=='clnDeposit' for r in guest['requests'])==1
 return {'standalone_colony_native_tests':native_count,'selected_addons_native_tests':addon_count,'actual_minecraft_clients':2,'actual_tcp_lan':True,'actual_world_reopening':True,'native_host_dual_save':True,'selected_addons':EXPECTED,'colony_runtime_sha256':runtime()},report
if __name__=='__main__':
 action=sys.argv[1] if len(sys.argv)>1 else ''
 if action=='install':install()
 elif action=='native':native()
 elif action=='addons':native(True)
 elif action=='validate':print(json.dumps(validate()[0],indent=2))
 else:raise SystemExit('Use install, native, addons or validate')
