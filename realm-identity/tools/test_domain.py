#!/usr/bin/env python3
"""Run identity tests independently of Minecraft artifact downloads (JDK 21 required)."""
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
    url = 'https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar'
    with urllib.request.urlopen(url, timeout=60) as response:
        artifact = response.read()
    if hashlib.sha256(artifact).hexdigest() != checksum:
        raise SystemExit('JUnit download failed SHA-256 verification.')
    jar.write_bytes(artifact)

# Gson is already part of Minecraft's runtime; pin the same version for the
# isolated public Titles API contract tests, with normal artifact verification.
gson = cache / 'gson-2.10.1.jar'
gson_checksum = '4241c14a7727c34feea6507ec801318a3d4a90f070e4525681079fb94ee4c593'
if not gson.is_file() or hashlib.sha256(gson.read_bytes()).hexdigest() != gson_checksum:
    cached = list(Path('/workspace/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1').glob('*/*.jar'))
    artifact = next((path.read_bytes() for path in cached if hashlib.sha256(path.read_bytes()).hexdigest() == gson_checksum), None)
    if artifact is None:
        url = 'https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar'
        with urllib.request.urlopen(url, timeout=60) as response:
            artifact = response.read()
    if hashlib.sha256(artifact).hexdigest() != gson_checksum:
        raise SystemExit('Gson download failed SHA-256 verification.')
    gson.write_bytes(artifact)

def executable(name):
    if os.environ.get('JAVA_HOME'):
        candidate = Path(os.environ['JAVA_HOME']) / 'bin' / name
        if candidate.is_file():
            return str(candidate)
    # Prefer the complete, verified cloud JDK over a system runtime whose compiler
    # modules may not match. Compatibility tests use ToolProvider in the same JVM.
    candidate = Path('/workspace/.tools/jdk21/bin') / name
    if candidate.is_file():
        return str(candidate)
    found = shutil.which(name)
    if found:
        return found
    raise SystemExit('JDK 21 required. In this cloud environment run python3 tools/bootstrap_cloud.py first.')

output = project / 'build' / 'domain-check'
if output.exists():
    shutil.rmtree(output)
classes = output / 'classes'
classes.mkdir(parents=True)
sources = sorted((project / 'src/main/java/pt/realmidentity/domain').glob('*.java'))
sources += sorted((project / 'src/test/java/pt/realmidentity/domain').glob('*.java'))
sources.append(project / 'src/main/java/pt/realmidentity/compat/ChroniclesProfileReader.java')
sources.append(project / 'src/main/java/pt/realmidentity/compat/TitlesApiReader.java')
sources += sorted((project / 'src/test/java/pt/realmidentity/compat').glob('*.java'))
classpath = os.pathsep.join(map(str, (jar, gson)))
subprocess.run([executable('javac'), '--release', '21', '-encoding', 'UTF-8', '-cp', classpath,
                '-d', str(classes), *map(str, sources)], check=True)
subprocess.run([executable('java'), '-jar', str(jar), 'execute', '--class-path', os.pathsep.join(map(str, (classes, gson))),
                '--scan-class-path', '--fail-if-no-tests', '--details=summary',
                '--reports-dir', str(output / 'reports')], check=True)
