#!/usr/bin/env python3
"""Expose real unit/runtime/client results and bounded CI diagnostics."""
from pathlib import Path
import json
import os
import re
import xml.etree.ElementTree as ET

project = Path(__file__).resolve().parents[1]
target = os.environ.get('GITHUB_STEP_SUMMARY')
assert target, 'Runs inside GitHub Actions'


def annotation(kind, title, value):
    escaped = str(value).replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::{kind} title={title}::{escaped}')


parts = ['# Chronicles RP validation\n']
for path in sorted((project / 'build/test-results/test').glob('TEST-*.xml')):
    report = ET.parse(path).getroot()
    result = f"JUnit {report.get('name')}: tests={report.get('tests')}, failures={report.get('failures')}, errors={report.get('errors')}, skipped={report.get('skipped')}"
    parts.append(result + '\n')
    annotation('notice', 'JUnit results', result)
for name in ('build.log', 'gametests.log', 'gametests-without-classes.log', 'integration.log',
             'visual-client.log', 'visual-origins-client.log', 'visual-origins-without-classes.log'):
    path = project / name
    if not path.is_file():
        continue
    log = path.read_text(errors='replace')
    tail = '\n'.join(log.splitlines()[-180:])[-45000:]
    parts.append(f"\n## {name}\n\n```text\n{tail.replace('```', '~~~')}\n```\n")
    outcomes = [line for line in log.splitlines()
                if re.search(r'All \d+ required tests|CRP_NAMES_NATIVE_TESTS_PASSED|CRP_NAMES_RUNTIME_DEPENDENCIES_PASSED|CRP_NAMES_CLIENT_QA_PASSED|CRP_ORIGINS_QA (true|false)', line)]
    if outcomes:
        annotation('notice', name + ' results', '\n'.join(outcomes[-12:])[-12000:])
    if 'BUILD FAILED' in log or 'FAILURE: Build failed' in log:
        diagnostic = tail[-16000:]
        for index, offset in enumerate(range(0, len(diagnostic), 2000), 1):
            annotation('error', f'{name} diagnostics {index}', diagnostic[offset:offset + 2000])
for folder in ('client', 'client-without-classes', 'rp-names-client'):
    report = project / 'qa-results' / folder / 'result.json'
    if report.is_file():
        outcome = json.loads(report.read_text())
        annotation('notice' if outcome.get('success') else 'error', folder + ' actual Minecraft validation',
                   json.dumps(outcome, ensure_ascii=False))
with Path(target).open('a', encoding='utf-8') as output:
    output.write('\n'.join(parts) + '\n')
