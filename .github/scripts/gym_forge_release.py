#!/usr/bin/env python3
"""Publish the Forge 1.20.1 gym only after native rules and real client controls pass."""
from pathlib import Path
import hashlib,json,os,re,shutil,struct,subprocess,sys,tomllib,zipfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
PROJECT=ROOT/'realm-gym-forge-1.20.1'
VERSION=dict(line.split('=',1) for line in (PROJECT/'gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
assert re.fullmatch(r'\d+\.\d+\.\d+-forge-1\.20\.1',VERSION)
NAME=f'realm-gym-{VERSION}.jar'
STATE=PROJECT/'build/gym-release'
EXERCISES=('bench','run','box','pull','row','squat')
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def checkjar(p):
 with zipfile.ZipFile(p) as j:
  assert j.testzip() is None
  meta=tomllib.loads(j.read('META-INF/mods.toml').decode());assert meta['mods'][0]['modId']=='realmfitness' and meta['mods'][0]['version']==VERSION and meta['mods'][0]['displayName']=='Realm Gym'
  dependencies={d['modId']:d for d in meta['dependencies']['realmfitness']}
  assert dependencies['forge']['mandatory'] is True and dependencies['forge']['versionRange']=='[47.4.0,48)'
  assert dependencies['minecraft']['versionRange']=='[1.20.1]'
  assert json.loads(j.read('pack.mcmeta'))['pack']['pack_format']==15
  names=j.namelist()
  assert not any('/recipe/' in n or '/loot_table/' in n or '/tags/block/' in n for n in names)
  for e in EXERCISES:
   assert json.loads(j.read(f'data/realmfitness/recipes/{e}.json'))['result']['item']==f'realmfitness:{e}'
   assert f'data/realmfitness/loot_tables/blocks/{e}.json' in names
  assert meta['mods'][0]['logoFile']=='realm-gym-icon.png' and 'realm-gym-icon.png' in names
  assert not any('/test/' in n or '/visualtest/' in n or n.endswith('/empty.nbt') for n in names)
  assert not any(n.startswith(('net/minecraft/','net/neoforged/','net/minecraftforge/','pt/cronicasrp/','META-INF/jarjar/')) for n in names)
  for n in names:
   if n.endswith('.class'):assert struct.unpack('>IHH',j.read(n)[:8])==(0xCAFEBABE,0,61),n
  for e in EXERCISES:
   for part in ('lower','upper'):assert f'assets/realmfitness/models/block/{e}_{part}.json' in names
   assert f'data/realmfitness/recipes/{e}.json' in names
   for part in range(18):
    assert f'assets/realmfitness/models/block/{e}_part_{part}.json' in names and f'assets/realmfitness/models/block/{e}_part_{part}_working.json' in names
  assert 'assets/realmfitness/geometry/collisions.json' in names and 'assets/realmfitness/geometry/collisions_working.json' in names
  assert 'pt/realmfitness/GymMount.class' in names and 'pt/realmfitness/client/TrainingHud.class' in names and 'pt/realmfitness/client/TrainingScreen.class' not in names
  for model in ('bench_bar','squat_bar','row_seat','row_handle','row_cable','row_flywheel','box_bag','run_belt'):assert f'assets/realmfitness/models/block/{model}.json' in names
  assert 'assets/realmfitness/geometry/layout.json' in names and 'pt/realmfitness/GymLayout.class' in names
  collection=json.loads(j.read('assets/realmfitness/geometry/collection.json'))
  collisions=json.loads(j.read('assets/realmfitness/geometry/collisions.json'))
  for e in EXERCISES:
   assert collection[e]['width']==2 and collection[e]['height']==3 and collection[e]['depth']==(3 if e in ('bench','run','row') else 2)
   assert len(collisions[e])==18
   for boxes in collisions[e].values():
    for box in boxes:assert len(box)==6 and all(0<=v<=16 for v in box) and all(box[i]<box[i+3] for i in range(3))
   for model in [f'assets/realmfitness/models/item/{e}.json']+[f'assets/realmfitness/models/block/{e}_part_{p}.json' for p in range(18)]:
    for element in json.loads(j.read(model))['elements']:
     assert all(-16<=v<=32 for key in ('from','to') for v in element[key]),model
     assert all(0<=v<=16 for face in element['faces'].values() for v in face['uv']),model
  en=json.loads(j.read('assets/realmfitness/lang/en_us.json'));pt=json.loads(j.read('assets/realmfitness/lang/pt_pt.json'));assert en.keys()==pt.keys()
  for lang in ('en_us','pt_pt','pt_br'):
   strings=json.loads(j.read(f'assets/realmfitness/lang/{lang}.json'));assert strings['itemGroup.realmfitness']==strings['key.categories.realmfitness']=='Realm Gym'
def encode_motion(qa,gallery,result):
 gallery.mkdir(parents=True,exist_ok=True);videos={}
 for e in EXERCISES:
  assert result['motionFrames'][e]==24
  frames=qa/'motion'/e;assert len(list(frames.glob('frame-*.png')))==24,e
  clip=gallery/f'fitness-motion-{e}.mp4'
  subprocess.run(['ffmpeg','-y','-loglevel','error','-framerate','10','-i',str(frames/'frame-%03d.png'),'-an','-c:v','libx264','-threads','2','-preset','fast','-crf','24','-pix_fmt','yuv420p','-movflags','+faststart',str(clip)],check=True,timeout=45)
  videos[clip.name]=digest(clip)
 preview=gallery/'fitness-motion-bench.gif'
 subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(gallery/'fitness-motion-bench.mp4'),'-filter_complex','fps=10,scale=800:-1:flags=neighbor,split[a][b];[a]palettegen[p];[b][p]paletteuse','-loop','0',str(preview)],check=True,timeout=45)
 videos[preview.name]=digest(preview);return videos
def snapshot():
 p=PROJECT/'build/libs'/NAME;checkjar(p);STATE.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,STATE/NAME)
 (STATE/'source.json').write_text(json.dumps({'source_commit':os.environ['GITHUB_SHA'],'jar_sha256':digest(p)}))
def package():
 state=json.loads((STATE/'source.json').read_text());assert state['source_commit']==os.environ['GITHUB_SHA']
 source=STATE/NAME;checkjar(source);assert digest(source)==state['jar_sha256']
 reports=[ET.parse(p).getroot() for p in (PROJECT/'build/test-results/test').glob('TEST-*.xml')];units=sum(int(r.get('tests',0)) for r in reports)
 assert units>=74 and not any(int(r.get(k,0)) for r in reports for k in ('failures','errors','skipped'))
 for n in ('forge-build.log','forge-native.log','forge-client.log'):
  log=(PROJECT/n).read_text(errors='replace');assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log,n
 expected=sum(p.read_text().count('@GameTest(') for p in (PROJECT/'src/gametest/java').rglob('*.java'))
 counts=re.findall(r'All (\d+) required tests passed',(PROJECT/'forge-native.log').read_text(),re.I);assert counts and int(counts[-1])==expected and expected>=20
 qa=PROJECT/'qa-results/client';result=json.loads((qa/'result.json').read_text());assert result['success'] is True and result['stage']==29
 assert any('animated equipment model groups baked' in c for c in result['checks']) and any('Forge jump-height modifier synchronized' in c for c in result['checks'])
 assert result['actualMountedMinigames'] is True and result['hudPresentation']['interpolatedFrames']>0
 assert any('Actual SHIFT input' in c for c in result['checks']) and any('Breaking occupied equipment' in c for c in result['checks']) and any('Actual sneak-right-click' in c for c in result['checks'])
 assert result['actualMinecraftClient'] is True and result['actualIntegratedServer'] is True and result['syntheticSetResults'] is False
 for e in EXERCISES:
  assert any(('Actual '+e.upper()+' minigame') in c for c in result['checks']),e
  assert f'fitness-model-{e}.png' in result['screenshots'] and f'fitness-world-{e}.png' in result['screenshots'],e
 images=result['screenshots'];assert len(images)>=32 and len(images)==len(set(images));assert all(Path(n).name==n and n.endswith('.png') for n in images)
 gallery=ROOT/'media/realm-gym'/VERSION;gallery.mkdir(parents=True,exist_ok=True)
 for n in images:
  p=qa/n;assert p.read_bytes().startswith(b'\x89PNG\r\n\x1a\n');shutil.copyfile(p,gallery/n)
 shutil.copyfile(qa/'result.json',gallery/'result.json')
 videos=encode_motion(qa,gallery,result)
 run=f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
 (gallery/'README.md').write_text(f'''# Realm Gym {VERSION} Beta

Capturas do Minecraft 1.20.1 com Forge 47.4.0 e Java 17. [Validacao CI]({run}).

Botão direito: montar e treinar. Agachar + botão direito: aumentar carga. SHIFT durante o treino: sair. F5: câmara.

Supino: toques repetidos contra a pressão. Boxe: marcador móvel, centro perfeito e sequências. Passadeira: ritmo e fôlego. Elevações: pega e equilíbrio. Remo: força e recuperação. Agachamentos: profundidade, pausa e subida.

Modelos originais redesenhados com armações finas em aço azul-escuro, estofos azul-acinzentados e pontos de contacto partilhados com a animação. As mãos acompanham as barras e a pega; o saco oscila, o tapete desliza e a roda do remo gira.

As cargas baixas tornam-se mais fáceis conforme o nível do respetivo atributo; a fadiga e as cargas próximas do limite continuam a exigir mais.

![Supino montado com HUD discreto](fitness-motion-bench.gif)

Vídeos capturados no cliente real (24 frames por exercício, amostras a 10 fps): [supino](fitness-motion-bench.mp4) · [passadeira](fitness-motion-run.mp4) · [boxe](fitness-motion-box.mp4) · [elevações](fitness-motion-pull.mp4) · [remo](fitness-motion-row.mp4) · [agachamentos](fitness-motion-squat.mp4).

![Maquinas 3D](fitness-six-machines.png)
![Supino redesenhado](fitness-model-bench.png)
![Passadeira redesenhada](fitness-model-run.png)
![Boxe redesenhado](fitness-model-box.png)
![Elevações redesenhadas](fitness-model-pull.png)
![Remo redesenhado](fitness-model-row.png)
![Agachamentos redesenhados](fitness-model-squat.png)
![Equipamento no mundo](fitness-world-bench.png)
![Perfil do atleta](fitness-earned-attributes.png)
![Editor de planos](fitness-plan-editor.png)
![Supino](fitness-minigame-bench.png)
![Boxe](fitness-minigame-box.png)
![Historico real](fitness-real-history.png)
![Recordes no mundo](fitness-live-record-board.png)
![Portugues](fitness-profile-portuguese.png)

O teste de carga usa temporariamente 100 XP no supino para desbloquear 25 kg e repõe esse valor antes das séries. O jogador inicia as séries com uma base controlada de 90 XP por atributo para tornar visivel a subida de nivel. As seis series e os seus resultados usam efetivamente os controlos do cliente e os canais de rede, sem enviar pontuacoes inventadas. O teste verifica o plano personalizado, bonus diario, persistencia e reabertura do mundo. A galeria das maquinas usa os modelos de producao no renderizador Minecraft.
''')
 out=ROOT/'mod-pronto';out.mkdir(exist_ok=True);jar=out/NAME;shutil.copyfile(source,jar)
 archive=jar.with_suffix('.zip')
 with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
  z.write(jar,NAME);z.writestr('LEIA-ME.txt',f'Realm Gym {VERSION} Beta\nMinecraft 1.20.1 / Forge 47.4.0+ / Java 17\nInstala o mesmo JAR no servidor e nos clientes.\nBotao direito: montar; agachar + botao direito: carga; SHIFT: sair.\nPerfil: K ou /gym; maquinas: receitas ou /gym equipment (operador).\nConfiguracao: config/realm-fitness.json.\nGuia: https://github.com/ROCKABITS/Mods/tree/main/realm-gym-forge-1.20.1\n')
 meta={'name':'Realm Gym','previous_name':'Realm Fitness','mod_id':'realmfitness','version':VERSION,'status':'Beta','minecraft':'1.20.1','loader':'Forge','forge':'47.4.0','java':17,'source_commit':state['source_commit'],'tested_run':run,'jar_sha256':digest(jar),'zip_sha256':digest(archive),'unit_tests':units,'native_tests':expected,'actual_minigame_client':True,'actual_mounted_minigames':True,'hud_presentation':result['hudPresentation'],'distinct_minigame_rules':2,'relative_training_difficulty':True,'motion_previews':videos,'checks':result['checks'],'scope':'Standalone integrated-host validation with real inputs, controlled 90-XP baseline and six real results. Remote authenticated multiplayer and third-party stat-mod combinations are not covered.','screenshots':{n:digest(gallery/n) for n in images}}
 (out/f'realm-gym-{VERSION}.build.json').write_text(json.dumps(meta,indent=2)+'\n');(out/f'realm-gym-{VERSION}.sha256').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in (jar,archive)))
 print(json.dumps(meta,indent=2))
if __name__=='__main__':{'snapshot':snapshot,'package':package}[sys.argv[1]]()
