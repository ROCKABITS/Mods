#!/usr/bin/env python3
"""Publish independent verified Gym editions without racing other edition releases."""
from pathlib import Path
import hashlib,json,os,subprocess,sys

ROOT=Path(__file__).resolve().parents[2]
EDITIONS={
    'realm-gym':('gym_release.py','build-realm-gym.yml'),
    'realm-gym-forge-1.20.1':('gym_forge_release.py','build-realm-gym-forge.yml'),
    'realm-gym-fabric-1.21.1':('gym_fabric_release.py','build-realm-gym-fabric.yml'),
    'realm-gym-forge-1.21.1':('gym_forge211_release.py','build-realm-gym-forge-1.21.1.yml'),
}
def git(*args,check=True):
    return subprocess.run(['git',*args],cwd=ROOT,check=check,text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
def exists(ref,path):return git('cat-file','-e',ref+':'+path,check=False).returncode==0

def main():
    project=sys.argv[1];packager,workflow=EDITIONS[project]
    props=dict(line.split('=',1) for line in (ROOT/project/'gradle.properties').read_text().splitlines() if '=' in line)
    version=props['mod_version'];prefix='mod-pronto/realm-gym-'+version
    metadata=prefix+'.build.json';source=os.environ['GITHUB_SHA']
    if exists('HEAD',metadata):
        print('This version is already published; retaining its immutable downloads.');return
    report=json.loads((ROOT/metadata).read_text());assert report['source_commit']==source
    assert hashlib.sha256((ROOT/(prefix+'.jar')).read_bytes()).hexdigest()==report['jar_sha256']
    assert hashlib.sha256((ROOT/(prefix+'.zip')).read_bytes()).hexdigest()==report['zip_sha256']
    paths=[prefix+suffix for suffix in ('.jar','.zip','.sha256','.build.json')]+['media/realm-gym/'+version+'/']
    assert all((ROOT/path).exists() for path in paths)
    inputs=[project,'.github/scripts/'+packager,'.github/workflows/'+workflow,'.github/scripts/gym_publish.py','.github/scripts/test_gym_publish.py']
    if project in ('realm-gym-fabric-1.21.1','realm-gym-forge-1.21.1'):inputs+=['realm-gym/src/main','realm-gym/src/test']
    git('add','--',*paths)
    staged=git('diff','--cached','--name-only').stdout.splitlines()
    assert all(path in paths[:4] or path.startswith(paths[4]) for path in staged),'Unexpected staged files outside this release'
    if git('diff','--cached','--quiet',check=False).returncode==0:return
    git('-c','user.name=github-actions[bot]','-c','user.email=41898282+github-actions[bot]@users.noreply.github.com',
        'commit','-m',f'Publish verified Realm Gym {version} Beta [skip ci]')
    for attempt in range(6):
        git('fetch','--no-tags','origin','main')
        assert git('merge-base','--is-ancestor',source,'FETCH_HEAD',check=False).returncode==0,'Main no longer contains the validated source commit'
        assert git('diff','--quiet',source,'FETCH_HEAD','--',*inputs,check=False).returncode==0,'Gym build inputs changed during validation; rerun on current main'
        if exists('FETCH_HEAD',metadata):
            print('Another verified run published this version; retaining its downloads.');return
        # Other editions only add their own downloads and galleries. Preserve those commits.
        git('-c','user.name=github-actions[bot]','-c','user.email=41898282+github-actions[bot]@users.noreply.github.com','rebase','FETCH_HEAD')
        pushed=git('push','origin','HEAD:refs/heads/main',check=False)
        if pushed.returncode==0:
            print(f'Published {version} from validated source {source}');return
        if attempt==5:raise RuntimeError('Concurrent publication did not settle: '+pushed.stderr)

if __name__=='__main__':main()
