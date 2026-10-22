#!/usr/bin/env python3
"""Install pinned released companions or gate/package the actual tested Season 8 build."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tomllib
import xml.etree.ElementTree as ET
import zipfile
import zlib

PROJECT = Path(__file__).resolve().parents[1]
ROOT = PROJECT.parent
PINS = json.loads((PROJECT / 'tools/companions.json').read_text())

def digest(data): return hashlib.sha256(data).hexdigest()

def install():
    dest = PROJECT / 'compat-libs'
    dest.mkdir(exist_ok=True)
    for pin in PINS:
        source = ROOT / 'mod-pronto' / pin['file']
        assert digest(source.read_bytes()) == pin['sha256'], f'Companion hash mismatch: {source.name}'
        shutil.copy2(source, dest / source.name)
    assert {p.name for p in dest.glob('*.jar')} == {p['file'] for p in PINS}, 'Unexpected companion JARs'
    print(f'Installed {len(PINS)} hash-verified released companions')

def verify_png(path):
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', path
    w,h,depth,color = struct.unpack('>IIBB',data[16:26])
    assert (w,h) in {(1600,900),(854,480),(640,360)}, (path,w,h)
    assert depth == 8 and color in (2,6), (path,depth,color)
    chunks=[]; offset=8
    while offset < len(data):
        length=int.from_bytes(data[offset:offset+4],'big'); kind=data[offset+4:offset+8]
        if kind == b'IDAT':chunks.append(data[offset+8:offset+8+length])
        offset += length+12
    first=zlib.decompress(b''.join(chunks))
    # The first pixel of a PNG row has no predictors in the row above or to the left.
    assert first[1:4] == bytes((103,81,63)), f'Not a rendered Season 8 frame: {path}'
    return {'width':w,'height':h,'sha256':digest(data)}

def package():
    version=re.search(r'^mod_version=(\d+\.\d+\.\d+)$',(PROJECT/'gradle.properties').read_text(),re.M).group(1)
    assert 'BUILD SUCCESSFUL' in (PROJECT/'build.log').read_text(), 'Missing successful production build'
    suites=[ET.parse(p).getroot() for p in (PROJECT/'build/test-results/test').glob('TEST-*.xml')]
    assert suites and sum(int(s.attrib.get('tests',0)) for s in suites)==2
    assert all(all(int(s.attrib.get(k,0))==0 for k in ('failures','errors','skipped')) for s in suites)
    clients={}
    for mode in ['standalone','integration']:
        result=json.loads((PROJECT/f'qa-results/{mode}/result.json').read_text())
        assert result['success'] is True and result['step']==18, result
        assert all(result[k] is True for k in ['startup_loading','resource_loading','compact_loading','native_icon']), result
        assert result['integration'] == (mode=='integration'), result
        clients[mode]=result
    jar=PROJECT/f'build/release/season-eight-{version}.jar'
    jar_data=jar.read_bytes()
    with zipfile.ZipFile(jar) as archive:
        names=archive.namelist()
        metadata=tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
        assert metadata['mods'][0]['modId']=='seasoneight' and metadata['mods'][0]['version']==version
        assert all(not n.startswith('pt/seasoneight/qa/') for n in names), 'QA code leaked into production'
        mixins=json.loads(archive.read('seasoneight.mixins.json'))
        assert not mixins.get('mixins') and len(mixins['client'])==3, 'Mixins must be client only'
        for name in ['crest','wordmark','icon','icon-16','icon-32','icon-48','loading-pt','loading-en']:
            assert archive.read(f'assets/seasoneight/art/{name}.png').startswith(b'\x89PNG'), name
    image_names=['01-startup-loading','02-main-menu-en','03-main-menu-pt','04-resource-loading-pt','05-main-menu-compact','06-main-menu-small-reduced-motion','07-resource-loading-compact']
    images={name:verify_png(PROJECT/f'qa-results/integration/{name}.png') for name in image_names}
    sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=PROJECT,text=True).strip()
    out=ROOT/'mod-pronto'; out.mkdir(exist_ok=True)
    target=out/f'season-eight-{version}.jar'
    assert not target.exists() or target.read_bytes()==jar_data, 'A different production release already exists'
    target.write_bytes(jar_data)
    zipped=target.with_suffix('.zip')
    with zipfile.ZipFile(zipped,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive: archive.writestr(target.name,jar_data)
    zipped_sha=digest(zipped.read_bytes())
    target.with_suffix('.sha256').write_text(f'{digest(jar_data)}  {target.name}\n{zipped_sha}  {zipped.name}\n')
    report={'version':version,'source_commit':sha,'minecraft':'1.21.1','neoforge':'21.1.256','java':21,
            'client_only':True,'unit_tests':2,'layout_dimensions_checked':3700,'clients':clients,'companions':PINS,
            'images':images,'jar_sha256':digest(jar_data),'zip_sha256':zipped_sha,
            'scope':'Main menu, Minecraft/NeoForge resource-loading overlays, title and X11 window icons. Pre-Minecraft bootstrap and world-generation screens remain original.'}
    target.with_suffix('.build.json').write_text(json.dumps(report,indent=2)+'\n')
    gallery=ROOT/f'media/season-eight/{version}';gallery.mkdir(parents=True,exist_ok=True)
    for name in image_names:shutil.copy2(PROJECT/f'qa-results/integration/{name}.png',gallery/f'{name}.png')
    shutil.copy2(PROJECT/'src/main/resources/assets/seasoneight/art/icon.png',gallery/'season-eight-icon.png')
    (gallery/'README.md').write_text('# Season 8 '+version+' — imagens reais\n\nAs sete capturas foram obtidas no Minecraft 1.21.1 / NeoForge 21.1.256 com os 13 mods publicados de ROCKABITS. O ícone é arte original do mod.\n\n- 01: carregamento inicial de recursos.\n- 02 e 03: menu em inglês e português (1600×900).\n- 04: recarregamento real de recursos em português.\n- 05 e 07: menu e carregamento em janela de 854×480.\n- 06: janela de 640×360 com movimento reduzido.\n\nA janela técnica do NeoForge anterior ao Minecraft não é alterada. Ver o relatório de validação e hashes em `mod-pronto`.\n')
    print(json.dumps({'jar':str(target),'jar_sha256':digest(jar_data),'zip_sha256':zipped_sha,'images':len(images)},indent=2))

if __name__=='__main__':
    if len(sys.argv)!=2 or sys.argv[1] not in ('install','package'): raise SystemExit('Usage: release.py install|package')
    globals()[sys.argv[1]]()
