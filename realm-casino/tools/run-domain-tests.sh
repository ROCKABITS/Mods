#!/usr/bin/env bash
# Run the pure casino domain tests without downloading Minecraft/NeoForge artifacts.
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
python3 - "$project_root" <<'PY'
from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import sys
import urllib.request

project = Path(sys.argv[1])
cache = project / '.gradle' / 'domain-test'
cache.mkdir(parents=True, exist_ok=True)
filename = 'junit-platform-console-standalone-1.11.4.jar'
jar = cache / filename
checksum = 'b016ef6b1c3454d6d7c2c88ce081dabf289699686af6622d6e4e2e1b54b4a2fc'

def verified(path):
    return path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == checksum

if not verified(jar):
    # Reuse a verified sibling cache in the shared mod workspace when available.
    seed = next((p for p in sorted(project.parent.glob('*/.gradle/domain-test/' + filename))
                 if p != jar and verified(p)), None)
    if seed:
        shutil.copyfile(seed, jar)
    else:
        url = 'https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/' + filename
        with urllib.request.urlopen(url, timeout=60) as response:
            artifact = response.read()
        if hashlib.sha256(artifact).hexdigest() != checksum:
            raise SystemExit('JUnit download failed SHA-256 verification.')
        jar.write_bytes(artifact)

def jdk_executables():
    candidates = []
    if os.environ.get('JAVA_HOME'):
        candidates.append(Path(os.environ['JAVA_HOME']) / 'bin')
    candidates.append(Path('/workspace/.tools/jdk21/bin'))
    for directory in candidates:
        if all((directory / name).is_file() for name in ('java', 'javac')):
            return tuple(str(directory / name) for name in ('java', 'javac'))
    java, javac = shutil.which('java'), shutil.which('javac')
    if java and javac:
        return java, javac
    raise SystemExit('A complete JDK 21 or newer is required (set JAVA_HOME).')

java, javac = jdk_executables()
output = project / 'build' / 'domain-check'
if output.exists():
    shutil.rmtree(output)
classes = output / 'classes'
classes.mkdir(parents=True)
sources = sorted((project / 'src/main/java/pt/realmcasino/domain').rglob('*.java'))
sources += [project / 'src/main/java/pt/realmcasino/data/GameState.java']
sources += sorted((project / 'src/test/java/pt/realmcasino/domain').rglob('*.java'))
subprocess.run([javac, '--release', '21', '-encoding', 'UTF-8', '-cp', str(jar),
                '-d', str(classes), *map(str, sources)], check=True)
subprocess.run([java, '-jar', str(jar), 'execute', '--class-path', str(classes),
                '--scan-class-path', '--fail-if-no-tests', '--details=summary',
                '--reports-dir', str(output / 'reports')], check=True)
PY
