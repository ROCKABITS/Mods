#!/usr/bin/env python3
"""Publish the exact previously tested artifact, verifying its pinned checksum."""
from pathlib import Path
import hashlib
import io
import json
import sys
import tomllib
import zipfile

archive = Path(sys.argv[1])
run = json.loads(Path(sys.argv[2]).read_text())
expected = '534c7803bffd73ee2023504c75558bbc9db46b983eedda7106f5d94839cc6d9d'
payload = archive.read_bytes()
if hashlib.sha256(payload).hexdigest() != expected:
    raise SystemExit('The artifact does not match the tested download checksum.')
if run.get('id') != 38718980393 or run.get('conclusion') != 'success':
    raise SystemExit('The source build did not finish successfully.')

name = 'contracts-bounties-0.1.1.jar'
with zipfile.ZipFile(io.BytesIO(payload)) as package:
    matches = [path for path in package.namelist() if path == name or path.endswith('/' + name)]
    if len(matches) != 1 or package.testzip() is not None:
        raise SystemExit('The tested archive must contain exactly one valid release JAR.')
    jar = package.read(matches[0])

with zipfile.ZipFile(io.BytesIO(jar)) as contents:
    metadata = tomllib.loads(contents.read('META-INF/neoforge.mods.toml').decode())
    mod = next((entry for entry in metadata['mods'] if entry['modId'] == 'contracts'), None)
    if not mod or mod['version'] != '0.1.1':
        raise SystemExit('Wrong mod or version in the tested JAR.')
    required = {
        'pt/contracts/ContractsMod.class',
        'assets/contracts/blockstates/contract_board.json',
        'assets/contracts/models/block/contract_board.json',
        'assets/contracts/models/item/contract_board.json',
        'assets/contracts/textures/block/contract_board_front.png',
        'data/contracts/recipe/contract_board.json',
    }
    if not required.issubset(contents.namelist()) or contents.testzip() is not None:
        raise SystemExit('The release JAR is missing required block resources or is corrupt.')
    if any(path.startswith('pt/contracts/test/') for path in contents.namelist()):
        raise SystemExit('A release JAR must not include the GameTest fixture classes.')

output = Path('mod-pronto')
output.mkdir(exist_ok=True)
jar_hash = hashlib.sha256(jar).hexdigest()
(output / name).write_bytes(jar)
(output / 'contracts-bounties-0.1.1.zip').write_bytes(payload)
(output / 'contracts-bounties-0.1.1.sha256').write_text(
    f'{jar_hash}  {name}\n{expected}  contracts-bounties-0.1.1.zip\n')
(output / 'contracts-bounties-0.1.1.build.json').write_text(json.dumps({
    'version': '0.1.1',
    'source_commit': run['head_sha'],
    'tested_run': run['html_url'],
    'jar_sha256': jar_hash,
    'zip_sha256': expected,
}, indent=2) + '\n')
print(f'::notice title=Verified JAR::{name}: {len(jar)} bytes, SHA-256 {jar_hash}')
