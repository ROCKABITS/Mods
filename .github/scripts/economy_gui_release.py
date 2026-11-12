#!/usr/bin/env python3
"""Package the Economy GUI patch after standalone rules and real-JEI client checks."""
from pathlib import Path
import hashlib,json,os,re,shutil,sys,tomllib,xml.etree.ElementTree as ET,zipfile
ROOT=Path(__file__).resolve().parents[2]
PROJECT=ROOT/'realm-economy'
STATE=PROJECT/'build/gui-release'
VERSION=dict(line.split('=',1) for line in (PROJECT/'gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))['mod_version']
assert re.fullmatch(r'\d+\.\d+\.\d+',VERSION)
NAME=f'realm-economy-{VERSION}.jar'
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def checkjar(path):
 with zipfile.ZipFile(path) as jar:
  assert jar.testzip() is None
  assert next(m for m in tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())['mods'] if m['modId']=='realmeconomy')['version']==VERSION
  names=jar.namelist()
  assert all(f'pt/realmeconomy/client/{name}Screen.class' in names for name in ('Colony','Trading','Services'))
  assert not any('/test/' in n or '/visualtest/' in n or '/colonytest/' in n or '/colonyvisualtest/' in n or '/jeiguitest/' in n for n in names)
  assert not any(n.startswith(('mezz/','net/mezzdev/','net/minecraft/','net/neoforged/','META-INF/jarjar/')) for n in names)
def snapshot():
 p=PROJECT/'build/libs'/NAME;checkjar(p);STATE.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,STATE/NAME)
 (STATE/'source.json').write_text(json.dumps({'source_commit':os.environ['GITHUB_SHA'],'jar_sha256':digest(p)}))
def package():
 state=json.loads((STATE/'source.json').read_text());assert state['source_commit']==os.environ['GITHUB_SHA']
 source=STATE/NAME;checkjar(source);assert digest(source)==state['jar_sha256']
 reports=[ET.parse(p).getroot() for p in (PROJECT/'build/test-results/test').glob('TEST-*.xml')]
 units=sum(int(r.get('tests',0)) for r in reports)
 assert units>=45 and not any(int(r.get(k,0)) for r in reports for k in ('failures','errors','skipped'))
 for name in ('gui-build.log','gui-native.log','gui-jei-client.log'):
  text=(PROJECT/name).read_text(errors='replace');assert 'BUILD SUCCESSFUL' in text and 'BUILD FAILED' not in text,name
 native=(PROJECT/'gui-native.log').read_text(errors='replace')
 expected=sum(p.read_text().count('@GameTest(') for p in (PROJECT/'src/gametest/java').rglob('*.java'))
 counts=re.findall(r'All (\d+) required tests passed',native,re.I);assert counts and int(counts[-1])==expected and expected>0
 qa=PROJECT/'qa-results/jei-client';result=json.loads((qa/'result.json').read_text())
 assert result.get('success') is True and result.get('actualJei') is True and result.get('actualMinecraftClient') is True
 assert any('Legacy' in x for x in result['checks']) and any('Wide' in x for x in result['checks']) and any('Vanilla' in x for x in result['checks'])
 images=result['screenshots'];assert len(images)==len(set(images))==8
 out=ROOT/'mod-pronto';gallery=ROOT/'media/realm-economy'/VERSION;out.mkdir(exist_ok=True);gallery.mkdir(parents=True,exist_ok=True)
 jar=out/NAME
 if jar.exists():assert digest(jar)==digest(source),'A different JAR already uses this version; increment mod_version.'
 shutil.copyfile(source,jar)
 archive=out/f'realm-economy-{VERSION}.zip'
 with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:z.write(jar,jar.name)
 for name in images:
  p=qa/name;assert p.read_bytes().startswith(b'\x89PNG\r\n\x1a\n') and p.stat().st_size>1024
  shutil.copyfile(p,gallery/name)
 shutil.copyfile(qa/'result.json',gallery/'result.json')
 (gallery/'README.md').write_text(f'''# Realm Economy {VERSION} — JEI GUI fix

Actual Minecraft 1.21.1, NeoForge 21.1.256 and JEI {result['jeiVersion']} client captures. Financial values are presentation fixtures; no money was transferred.

![Corrected colony panel](colony-jei-fixed.png)

![JEI alongside a wide panel](colony-jei-wide.png)

[Legacy-bounds reproduction](colony-jei-legacy-reproduction.png) deliberately restores the old 176 × 166 metadata on the current screen to reproduce the overlap. The [regression report](result.json) verifies real JEI bounds for colony, trading and service screens, resizing, and normal vanilla inventory before/after.

[Validation run](https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}).
''')
 meta={'version':VERSION,'source_commit':state['source_commit'],'tested_run':f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",'jar_sha256':digest(jar),'zip_sha256':digest(archive),'unit_tests':units,'standalone_native_tests':int(counts[-1]),'actual_client_validation':True,'actual_jei_validation':True,'jei_version':result['jeiVersion'],'scope':'Client GUI bounds only. Real JEI with presentation snapshots; financial integrations and MineColonies flows retain their prior 0.5.0 validation.','screenshots':{n:digest(gallery/n) for n in images}}
 (out/f'realm-economy-{VERSION}.build.json').write_text(json.dumps(meta,indent=2)+'\n')
 (out/f'realm-economy-{VERSION}.sha256').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in (jar,archive)))
 print(json.dumps(meta,indent=2))
if sys.argv[1]=='snapshot':snapshot()
elif sys.argv[1]=='package':package()
else:raise SystemExit('snapshot or package required')
