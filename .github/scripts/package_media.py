#!/usr/bin/env python3
"""Publish actual Minecraft gallery captures as PNGs and one convenient ZIP."""
from pathlib import Path
import json
import re
import shutil
import zipfile

repo = Path(__file__).resolve().parents[2]
project = repo / 'contracts-bounties'
version = next(line.split('=', 1)[1] for line in (project / 'gradle.properties').read_text().splitlines()
               if line.startswith('mod_version='))
if not re.fullmatch(r'\d+\.\d+\.\d+', version):
    raise SystemExit('Invalid gallery version.')

source = project / 'qa-results/boards-client'
report = json.loads((source / 'result.json').read_text())
shots = [
    ('contracts-boards-minecraft.png', 'Three medieval noticeboards',
     'Small, roofed and wide noticeboards, rendered with the actual Minecraft models and textures.'),
    ('contracts-board-small.png', 'Small noticeboard',
     'A compact wooden noticeboard with pinned parchment notices and ivy. Fits a 1 x 1 block footprint.'),
    ('contracts-board-tall.png', 'Roofed noticeboard',
     'A tall wooden noticeboard with a terracotta tiled roof, parchment notices and ivy. Occupies 1 x 2 blocks.'),
    ('contracts-board-wide.png', 'Wide noticeboard',
     'A larger roofed noticeboard for town squares and guild halls. Occupies 2 x 2 blocks.'),
    ('contracts-menu-browse.png', 'Browse material contracts',
     'The actual contract interface showing example material requests, emerald rewards and collection controls.'),
    ('contracts-menu-create.png', 'Create a material contract',
     'Choose a title, requested item, quantity and reward in the actual contract creation interface.'),
]
if report.get('success') is not True:
    raise SystemExit('Client capture validation failed.')
if not set(name for name, _, _ in shots).issubset(report.get('screenshots', [])):
    raise SystemExit('The client did not report all six gallery captures.')

out = repo / 'media' / version
out.mkdir(parents=True, exist_ok=True)
for name, _, _ in shots:
    path = source / name
    if not path.is_file() or path.stat().st_size < 1024 or path.read_bytes()[:8] != b'\x89PNG\r\n\x1a\n':
        raise SystemExit('Missing or invalid gallery PNG: ' + name)
    shutil.copyfile(path, out / name)

archive_name = f'contracts-bounties-media-{version}.zip'
readme = f'''# Contracts & Bounties {version} — CurseForge media

[Download all six PNG images]({archive_name}), extract the ZIP, then upload the PNGs to the project's Media gallery.

These are actual Minecraft 1.21.1 / NeoForge client captures. The board showcase uses a development-only display of the mod's real item models; the menu images show the production interface populated with demonstration contracts. No generated gameplay images or additional rendering mods are used.

Suggested English titles and captions:

| Image | Title | Caption |
| --- | --- | --- |
'''
for name, title, caption in shots:
    readme += f'| [{name}]({name}) | {title} | {caption} |\n'
(out / 'README.md').write_text(readme, encoding='utf-8')
with zipfile.ZipFile(out / archive_name, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name in [name for name, _, _ in shots] + ['README.md']:
        archive.write(out / name, name)
print(f'::notice title=CurseForge gallery::Published {len(shots)} actual Minecraft captures and {archive_name}.')
