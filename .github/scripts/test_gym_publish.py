#!/usr/bin/env python3
"""Exercise concurrent release pushes against a local bare Git repository."""
from pathlib import Path
import hashlib,json,os,subprocess,sys,tempfile,unittest

PUBLISHER=Path(__file__).with_name('gym_publish.py')
EDITIONS={'realm-gym':'0.7.0','realm-gym-forge-1.20.1':'0.7.0-forge-1.20.1','realm-gym-fabric-1.21.1':'0.7.0-fabric-1.21.1','realm-gym-forge-1.21.1':'0.7.0-forge-1.21.1'}
RUNNER='''import importlib.util,sys;from pathlib import Path
spec=importlib.util.spec_from_file_location("publisher",sys.argv[1]);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
module.ROOT=Path(sys.argv[2]);project=sys.argv[3];sys.argv=[sys.argv[0],project];module.main()
'''

class PublicationTest(unittest.TestCase):
    def git(self,repo,*args):
        return subprocess.run(['git',*args],cwd=repo,check=True,text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE).stdout.strip()
    def commit(self,repo,message):
        self.git(repo,'add','.');self.git(repo,'-c','user.name=QA','-c','user.email=qa@example.invalid','commit','-m',message)
        return self.git(repo,'rev-parse','HEAD')
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name);self.remote=self.root/'remote.git';self.seed=self.root/'seed';self.seed.mkdir()
        self.git(self.root,'init','--bare','--initial-branch=main',str(self.remote));self.git(self.seed,'init','--initial-branch=main')
        for project,version in EDITIONS.items():
            (self.seed/project).mkdir();(self.seed/project/'gradle.properties').write_text('mod_version='+version+'\n')
        self.source=self.commit(self.seed,'Source');self.git(self.seed,'remote','add','origin',str(self.remote));self.git(self.seed,'push','origin','main')
    def clone(self,name):
        dest=self.root/name;self.git(self.root,'clone',str(self.remote),str(dest));return dest
    def prepare(self,repo,project):
        version=EDITIONS[project];out=repo/'mod-pronto';out.mkdir(exist_ok=True);prefix=out/('realm-gym-'+version)
        jar=b'isolated test JAR';archive=b'isolated test ZIP'
        Path(str(prefix)+'.jar').write_bytes(jar);Path(str(prefix)+'.zip').write_bytes(archive);Path(str(prefix)+'.sha256').write_text('fixture\n')
        Path(str(prefix)+'.build.json').write_text(json.dumps({'source_commit':self.source,'jar_sha256':hashlib.sha256(jar).hexdigest(),'zip_sha256':hashlib.sha256(archive).hexdigest()}))
        gallery=repo/'media/realm-gym'/version;gallery.mkdir(parents=True);(gallery/'README.md').write_text('Test gallery\n')
    def start(self,repo,project):
        env=os.environ.copy();env['GITHUB_SHA']=self.source
        return subprocess.Popen([sys.executable,'-c',RUNNER,str(PUBLISHER),str(repo),project],env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    def test_concurrent_editions_preserve_every_verified_download(self):
        jobs=[]
        for index,project in enumerate(EDITIONS):
            repo=self.clone('job'+str(index));self.prepare(repo,project);jobs.append(self.start(repo,project))
        for job in jobs:
            stdout,stderr=job.communicate(timeout=45);self.assertEqual(0,job.returncode,stdout+stderr)
        tree=self.git(self.remote,'ls-tree','--name-only','-r','main').splitlines()
        for version in EDITIONS.values():
            self.assertIn('mod-pronto/realm-gym-'+version+'.jar',tree);self.assertIn('media/realm-gym/'+version+'/README.md',tree)
    def test_source_changes_block_stale_publication(self):
        project='realm-gym-forge-1.21.1';repo=self.clone('stale');self.prepare(repo,project)
        changed=self.seed/'realm-gym/src/main/java/Changed.java';changed.parent.mkdir(parents=True);changed.write_text('changed shared build input\n')
        self.commit(self.seed,'Change shared source');self.git(self.seed,'push','origin','main')
        job=self.start(repo,project);stdout,stderr=job.communicate(timeout=45);self.assertNotEqual(0,job.returncode);self.assertIn('build inputs changed',stdout+stderr)
        self.assertNotIn('mod-pronto',self.git(self.remote,'ls-tree','--name-only','main'))
    def test_existing_release_is_retained(self):
        project='realm-gym';repo=self.clone('first');self.prepare(repo,project);job=self.start(repo,project);stdout,stderr=job.communicate(timeout=45);self.assertEqual(0,job.returncode,stdout+stderr)
        before=self.git(self.remote,'rev-parse','main');again=self.clone('again');job=self.start(again,project);stdout,stderr=job.communicate(timeout=45)
        self.assertEqual(0,job.returncode,stdout+stderr);self.assertEqual(before,self.git(self.remote,'rev-parse','main'))

if __name__=='__main__':unittest.main()
