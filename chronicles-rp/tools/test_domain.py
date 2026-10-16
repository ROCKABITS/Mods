#!/usr/bin/env python3
"""Run the pure Chronicles domain tests without Minecraft/DFU downloads (JDK 21)."""
from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import urllib.request

project = Path(__file__).resolve().parent.parent
cache = project / '.gradle' / 'domain-test'
cache.mkdir(parents=True, exist_ok=True)
jar = cache / 'junit-platform-console-standalone-1.11.4.jar'
checksum = 'b016ef6b1c3454d6d7c2c88ce081dabf289699686af6622d6e4e2e1b54b4a2fc'
if not jar.is_file() or hashlib.sha256(jar.read_bytes()).hexdigest() != checksum:
    # Reuse a verified local artifact when another mod already ran these tests.
    candidates = project.parent.glob('*/.gradle/domain-test/' + jar.name)
    cached = next((candidate for candidate in candidates
                   if candidate != jar and hashlib.sha256(candidate.read_bytes()).hexdigest() == checksum), None)
    if cached:
        artifact = cached.read_bytes()
    else:
        url = 'https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/' + jar.name
        with urllib.request.urlopen(url, timeout=60) as response:
            artifact = response.read()
    if hashlib.sha256(artifact).hexdigest() != checksum:
        raise SystemExit('JUnit download failed SHA-256 verification.')
    jar.write_bytes(artifact)


def executable(name):
    if os.environ.get('JAVA_HOME'):
        candidate = Path(os.environ['JAVA_HOME']) / 'bin' / name
        if candidate.is_file():
            return str(candidate)
    candidate = Path('/workspace/.tools/jdk21/bin') / name
    if candidate.is_file():
        return str(candidate)
    found = shutil.which(name)
    if found:
        return found
    raise SystemExit('JDK 21 required. Run chronicles-rp/tools/bootstrap_cloud.py in this cloud environment.')


output = project / 'build' / 'domain-check'
if output.exists():
    shutil.rmtree(output)
classes = output / 'classes'
classes.mkdir(parents=True)
sources = sorted(p for p in (project / 'src/main/java/pt/cronicasrp/domain').glob('*.java')
                 if p.name != 'ProfileStorage.java')
sources += sorted(p for p in (project / 'src/test/java/pt/cronicasrp/domain').glob('*.java')
                  if p.name not in {'ProfileStorageTest.java', 'RpProgressTest.java'})
print('Local subset: excludes ProfileStorage, RpProgressTest, network and localization tests; full Gradle suite remains required in CI.', flush=True)
subprocess.run([executable('javac'), '--release', '21', '-encoding', 'UTF-8', '-cp', str(jar),
                '-d', str(classes), *map(str, sources)], check=True)
subprocess.run([executable('java'), '-jar', str(jar), 'execute', '--class-path', str(classes),
                '--scan-class-path', '--fail-if-no-tests', '--details=summary',
                '--reports-dir', str(output / 'reports')], check=True)
