#!/usr/bin/env python3
"""Expose actual CI results and bounded build diagnostics in the GitHub run summary."""
from pathlib import Path
import os
import xml.etree.ElementTree as ET

project = Path(__file__).resolve().parent.parent
target = os.environ.get('GITHUB_STEP_SUMMARY')
if not target:
    raise SystemExit('This helper runs inside GitHub Actions.')

parts = ['# Contracts & Bounties validation\n']
for report in sorted((project / 'build/test-results/test').glob('TEST-*.xml')):
    root = ET.parse(report).getroot()
    parts.append(f"JUnit {root.get('name')}: tests={root.get('tests')}, failures={root.get('failures')}, errors={root.get('errors')}, skipped={root.get('skipped')}\n")

for name in ['build.log', 'gametests.log']:
    log = project / name
    if log.is_file():
        # Fences in compiler output must not escape the diagnostic block.
        tail = '\n'.join(log.read_text(errors='replace').splitlines()[-180:])[-45000:]
        parts.append(f"\n## {name}\n\n```text\n{tail.replace('```', '~~~')}\n```\n")
        if 'BUILD FAILED' in tail or 'FAILURE: Build failed' in tail:
            # Run summaries and raw logs require a GitHub login in some views.
            # Public check annotations expose these bounded diagnostics as well.
            for index, offset in enumerate(range(0, len(tail), 12000), start=1):
                diagnostic = tail[offset:offset + 12000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
                print(f'::error title={name} diagnostics {index}::{diagnostic}')

Path(target).write_text('\n'.join(parts), encoding='utf-8')
