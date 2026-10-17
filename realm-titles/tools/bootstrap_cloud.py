#!/usr/bin/env python3
"""Restore the verified local JDK and secure Java proxy support for this cloud task."""
from pathlib import Path
import hashlib, os, shutil, subprocess, tarfile, urllib.request
root = Path('/workspace/.tools')
root.mkdir(parents=True, exist_ok=True)
archive = root / 'temurin-jdk21.tar.gz'
url = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz'
checksum = 'ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94'
def verified(path):
    return path.is_file() and hashlib.file_digest(path.open('rb'), 'sha256').hexdigest() == checksum
if not verified(archive):
    temporary = archive.with_suffix('.download')
    with urllib.request.urlopen(url) as response, temporary.open('wb') as output:
        shutil.copyfileobj(response, output)
    if not verified(temporary):
        temporary.unlink()
        raise SystemExit('JDK download failed the official SHA-256 check')
    temporary.replace(archive)
jdk = root / 'jdk-21.0.12.1+1'
if not (jdk / 'bin/javac').is_file():
    with tarfile.open(archive) as package:
        package.extractall(root, filter='data')
link = root / 'jdk21'
if not link.exists():
    link.symlink_to(jdk.name, target_is_directory=True)
trust = root / 'java-cacerts'
shutil.copyfile(jdk / 'lib/security/cacerts', trust)
cert = os.environ.get('CODEX_PROXY_CERT')
if cert and Path(cert).is_file():
    subprocess.run([str(jdk / 'bin/keytool'), '-importcert', '-noprompt', '-alias', 'codex-cloud-proxy', '-file', cert, '-keystore', str(trust), '-storepass', 'changeit'], check=True)

subprocess.run([str(jdk / 'bin/javac'), '-version'], check=True)
print('Verified Java21 and proxy truststore prepared; use python3 tools/cloud_gradle.py for Gradle.')
