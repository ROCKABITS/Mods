#!/usr/bin/env python3
"""Publish the reobfuscated Forge JAR only after its independent validation suites pass."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import struct
import sys
import tomllib
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PROJECT = ROOT / 'contracts-bounties-forge-1.20.1'
STATE = PROJECT / 'build/forge-release'
VERSION = dict(line.split('=', 1) for line in (PROJECT / 'gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
assert re.fullmatch(r'\d+\.\d+\.\d+-forge-1\.20\.1', VERSION)
NAME = f'contracts-bounties-{VERSION}.jar'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def checkjar(path):
    with zipfile.ZipFile(path) as jar:
        assert jar.testzip() is None
        metadata = tomllib.loads(jar.read('META-INF/mods.toml').decode())
        assert metadata['mods'][0]['modId'] == 'contracts'
        assert metadata['mods'][0]['version'] == VERSION
        assert metadata['modLoader'] == 'javafml'
        dependencies = {d['modId']: d for d in metadata['dependencies']['contracts']}
        assert dependencies['minecraft']['versionRange'] == '[1.20.1]'
        assert dependencies['forge']['versionRange'] == '[47.4.0,48)'
        assert json.loads(jar.read('pack.mcmeta'))['pack']['pack_format'] == 15
        names = jar.namelist()
        assert 'META-INF/neoforge.mods.toml' not in names
        service = jar.read('pt/contracts/ContractsService.class')
        assert b'm_150942_' in service and b'isSameItemSameTags' not in service, 'JAR was not reobfuscated for Forge'
        assert not any('/test/' in n or '/visualtest/' in n or '/integrationtest/' in n for n in names)
        assert not any(n.startswith(('net/minecraft/', 'net/minecraftforge/', 'net/neoforged/', 'META-INF/jarjar/')) for n in names)
        for name in names:
            if name.endswith('.class'):
                data = jar.read(name)
                assert struct.unpack('>IHH', data[:8]) == (0xCAFEBABE, 0, 61), name
                assert b'net/neoforged/' not in data, name
        for board in ('contract_board', 'tall_contract_board', 'wide_contract_board'):
            recipe = json.loads(jar.read(f'data/contracts/recipes/{board}.json'))
            assert recipe['result']['item'] == 'contracts:' + board
            assert 'id' not in recipe['result']
            assert f'data/contracts/loot_tables/blocks/{board}.json' in names
        assert 'data/minecraft/tags/blocks/mineable/axe.json' in names
        assert 'data/contracts/tags/items/daily_fishing_treasures.json' in names
        assert len([n for n in names if n.startswith('data/contracts/daily_quest/') and n.endswith('.json')]) == 57


def snapshot():
    source = PROJECT / 'build/libs' / NAME
    checkjar(source)
    STATE.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, STATE / NAME)
    (STATE / 'source.json').write_text(json.dumps({'source_commit': os.environ['GITHUB_SHA'], 'jar_sha256': digest(source)}))


def package():
    state = json.loads((STATE / 'source.json').read_text())
    assert state['source_commit'] == os.environ['GITHUB_SHA']
    source = STATE / NAME
    checkjar(source)
    assert digest(source) == state['jar_sha256']
    reports = [ET.parse(p).getroot() for p in (PROJECT / 'build/test-results/test').glob('TEST-*.xml')]
    units = sum(int(r.get('tests', 0)) for r in reports)
    assert units >= 91 and not any(int(r.get(k, 0)) for r in reports for k in ('failures', 'errors', 'skipped'))
    for name in ('forge-build.log', 'forge-native.log', 'forge-visual.log', 'forge-integration.log'):
        log = (PROJECT / name).read_text(errors='replace')
        assert 'BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log, name
    native = (PROJECT / 'forge-native.log').read_text(errors='replace')
    expected = sum(p.read_text().count('@GameTest(') for p in (PROJECT / 'src/gametest/java').rglob('*.java'))
    counts = re.findall(r'All (\d+) required tests passed', native, re.I)
    assert counts and int(counts[-1]) == expected and expected >= 38
    boardqa = PROJECT / 'qa-results/boards-client'
    integrationqa = PROJECT / 'qa-results/integration-client'
    visual = json.loads((boardqa / 'result.json').read_text())
    integration = json.loads((integrationqa / 'result.json').read_text())
    assert visual.get('success') is True
    assert integration.get('success') is True and integration.get('stage') == 19
    assert all(integration.get(key) is True for key in ('actualMinecraftClient', 'actualIntegratedServer', 'actualForgeChannels'))
    assert len(integration['checks']) >= 6
    images = sorted(boardqa.glob('*.png'))
    assert len(images) == 11
    images += [integrationqa / 'forge-daily-quests.png', integrationqa / 'forge-daily-celebration.png']
    out = ROOT / 'mod-pronto'
    out.mkdir(exist_ok=True)
    jar = out / NAME
    shutil.copyfile(source, jar)
    archive = out / f'contracts-bounties-{VERSION}.zip'
    readme = f'''Contracts & Bounties {VERSION}
Minecraft 1.20.1 / Forge 47.4.0+ (47.x) / Java 17.
Instala apenas o JAR no servidor e em todos os clientes.
Os JARs Realm 1.21.1/NeoForge nao sao compativeis com esta edicao.
Contratos e missoes diarias usam esmeraldas; carteira e reputacao Realm indisponiveis.
Nao faz downgrade de mundos 1.21.1. Guia: contracts-bounties-forge-1.20.1/README.md
'''
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as packagezip:
        packagezip.write(jar, jar.name)
        packagezip.writestr('LEIA-ME.txt', readme)
    gallery = ROOT / 'media/contracts-bounties' / VERSION
    gallery.mkdir(parents=True, exist_ok=True)
    for image in images:
        assert image.read_bytes().startswith(b'\x89PNG\r\n\x1a\n')
        shutil.copyfile(image, gallery / image.name)
    shutil.copyfile(boardqa / 'result.json', gallery / 'visual-result.json')
    shutil.copyfile(integrationqa / 'result.json', gallery / 'integration-result.json')
    run = f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}"
    (gallery / 'README.md').write_text(f'''# Contracts & Bounties — Forge 1.20.1

Capturas do cliente Minecraft 1.20.1 com Forge 47.4.0 e Java 17. [Validacao CI]({run}).

![Tres murais](contracts-boards-minecraft.png)
![Catalogo visual](contracts-item-catalogue.png)
![Pesquisa de materiais](contracts-item-search.png)
![Missoes no servidor integrado](forge-daily-quests.png)
![Celebracao recebida pelo canal Forge](forge-daily-celebration.png)

As onze capturas dos quadros e contratos usam dados de apresentacao para testar modelos, traducoes, selecao e dimensoes da interface. As duas capturas das missoes usam um jogador e servidor integrado reais, com quantidades/recompensas de teste controladas. Os relatórios `visual-result.json` e `integration-result.json` registam o respetivo alcance.
''')
    metadata = {
        'version': VERSION, 'minecraft': '1.20.1', 'loader': 'Forge', 'forge': '47.4.0', 'java': 17,
        'source_commit': state['source_commit'], 'tested_run': run,
        'jar_sha256': digest(jar), 'zip_sha256': digest(archive), 'unit_tests': units,
        'native_tests': expected, 'visual_captures': 11, 'actual_integrated_client': True,
        'integrated_checks': integration['checks'],
        'scope': 'Standalone emerald contracts and quests. Realm companion integrations, authenticated remote multiplayer and world downgrades are not validated or supplied by this port.',
        'screenshots': {p.name: digest(gallery / p.name) for p in images},
    }
    (out / f'contracts-bounties-{VERSION}.build.json').write_text(json.dumps(metadata, indent=2) + '\n')
    (out / f'contracts-bounties-{VERSION}.sha256').write_text(''.join(f'{digest(p)}  {p.name}\n' for p in (jar, archive)))
    print(json.dumps(metadata, indent=2))


if __name__ == '__main__':
    {'snapshot': snapshot, 'package': package}[sys.argv[1]]()
