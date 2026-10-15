#!/usr/bin/env python3
"""Expose actual CI results and bounded build diagnostics in the GitHub run summary."""
from pathlib import Path
import os
import re
import xml.etree.ElementTree as ET

project = Path(__file__).resolve().parent.parent
target = os.environ.get('GITHUB_STEP_SUMMARY')
if not target:
    raise SystemExit('This helper runs inside GitHub Actions.')

parts = ['# Contracts & Bounties validation\n']
for report in sorted((project / 'build/test-results/test').glob('TEST-*.xml')):
    root = ET.parse(report).getroot()
    result = f"JUnit {root.get('name')}: tests={root.get('tests')}, failures={root.get('failures')}, errors={root.get('errors')}, skipped={root.get('skipped')}"
    parts.append(result + '\n')
    print('::notice title=JUnit results::' + result.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))

for name in ['build.log', 'gametests.log', 'visual-client.log']:
    log = project / name
    if log.is_file():
        # Fences in compiler output must not escape the diagnostic block.
        tail = '\n'.join(log.read_text(errors='replace').splitlines()[-180:])[-45000:]
        parts.append(f"\n## {name}\n\n```text\n{tail.replace('```', '~~~')}\n```\n")
        if name == 'gametests.log':
            lines = [line for line in log.read_text(errors='replace').splitlines()
                     if re.search(r'\btests?\b', line, re.IGNORECASE)
                     and re.search(r'required|optional|pass|fail|done|finish|running|complete|started', line, re.IGNORECASE)]
            result = '\n'.join(lines[-20:])[-12000:]
            if result:
                escaped = result.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
                print('::notice title=Native server test results::' + escaped)
        if 'BUILD FAILED' in tail or 'FAILURE: Build failed' in tail or name == 'visual-client.log' and 'success=false' in tail:
            # Run summaries and raw logs require a GitHub login in some views.
            # Public check annotations expose these bounded diagnostics as well.
            for index, offset in enumerate(range(0, len(tail), 12000), start=1):
                diagnostic = tail[offset:offset + 12000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
                print(f'::error title={name} diagnostics {index}::{diagnostic}')

visual_report = project / 'qa-results/boards-client/result.json'
if visual_report.exists():
    import json
    report = json.loads(visual_report.read_text())
    message = json.dumps(report, ensure_ascii=False).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    kind = 'notice' if report.get('success') else 'error'
    print(f'::{kind} title=Actual Minecraft client validation::{message}')

Path(target).write_text('\n'.join(parts), encoding='utf-8')
