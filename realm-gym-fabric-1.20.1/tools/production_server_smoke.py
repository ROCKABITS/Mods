#!/usr/bin/env python3
"""Boot the distributed intermediary JAR on a real, isolated Fabric server."""
from pathlib import Path
import hashlib,json,os,queue,shutil,subprocess,threading,time,urllib.parse,urllib.request,zipfile

PROJECT=Path(__file__).resolve().parents[1]
VERSION=dict(line.split('=',1) for line in (PROJECT/'gradle.properties').read_text().splitlines() if '=' in line)['mod_version']
WORK=PROJECT/'build/production-smoke'
WORK.mkdir(parents=True,exist_ok=True)
OUT=PROJECT/'qa-results';OUT.mkdir(exist_ok=True)
JAR=PROJECT/'build/libs'/f'realm-gym-{VERSION}.jar'
def download(url,path):
    if path.exists():return
    with urllib.request.urlopen(url,timeout=60) as response:path.write_bytes(response.read())

def main():
    (WORK/'mods').mkdir(exist_ok=True)
    # The smoke server has its own world, config and loopback socket.
    for old in (WORK/'mods').glob('realm-gym-*.jar'):old.unlink()
    shutil.copyfile(JAR,WORK/'mods'/JAR.name)
    download('https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.16.14/1.1.2/server/jar',WORK/'fabric-server-launch.jar')
    download('https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.92.12+1.20.1/fabric-api-0.92.12+1.20.1.jar',WORK/'mods/fabric-api.jar')
    cached=Path(os.environ.get('GRADLE_USER_HOME',str(Path.home()/'.gradle')))/'caches/fabric-loom/1.20.1/minecraft-server.jar'
    if cached.exists() and not (WORK/'server.jar').exists():shutil.copyfile(cached,WORK/'server.jar')
    (WORK/'eula.txt').write_text('eula=true\n')
    (WORK/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=5\nspawn-protection=0\ngenerate-structures=false\nmax-tick-time=0\n')
    env=os.environ.copy();options=[]
    for scheme in ('http','https'):
        proxy=urllib.parse.urlsplit(env.get(scheme.upper()+'_PROXY',env.get(scheme+'_proxy','')))
        if proxy.username or proxy.password:raise RuntimeError('Proxy credentials require supported secret binding')
        if proxy.hostname:options.extend(['-D'+scheme+'.proxyHost='+proxy.hostname,'-D'+scheme+'.proxyPort='+str(proxy.port or 80)])
    options.append('-Dhttp.nonProxyHosts=localhost|127.0.0.1|::1')
    trust=Path('/workspace/.tools/java-cacerts')
    if trust.exists():options.extend(['-Djavax.net.ssl.trustStore='+str(trust),'-Djavax.net.ssl.trustStorePassword=changeit'])
    env['JAVA_TOOL_OPTIONS']=' '.join(options)
    java=str(Path(env['JAVA_HOME'])/'bin/java') if env.get('JAVA_HOME') else shutil.which('java')
    with zipfile.ZipFile(JAR) as archive:strings=json.loads(archive.read('assets/realmfitness/lang/en_us.json'))
    def contains(line,key):return key in line or strings[key] in line
    history=[]
    for boot in range(2):
        process=subprocess.Popen([java,'-Xmx1G','-jar','fabric-server-launch.jar','nogui'],cwd=WORK,env=env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
        lines=queue.Queue()
        def reader():
            for line in process.stdout:lines.put(line)
        thread=threading.Thread(target=reader,daemon=True);thread.start()
        deadline=time.monotonic()+360;phase=0;output=[]
        try:
            while process.poll() is None or not lines.empty():
                if time.monotonic()>deadline:raise RuntimeError('Production server command/restart test timed out')
                try:line=lines.get(timeout=.2)
                except queue.Empty:continue
                output.append(line)
                if phase==0 and 'Done (' in line:
                    phase=1;process.stdin.write('gym reload\n' if boot==0 else 'gym conditioning status\n');process.stdin.flush()
                expected='fitness.settings_reloaded' if boot==0 else 'fitness.conditioning.status_on'
                if phase==1 and contains(line,expected):
                    config=json.loads((WORK/'config/realm-fitness.json').read_text())
                    assert config['conditioning']['enabled'] is (boot==1),'Persisted mode differs from expected startup state'
                    commands='gym conditioning on\ngym conditioning status\ngym conditioning off\ngym conditioning status\ngym conditioning on\ngym reload\nsave-all\nstop\n' if boot==0 else 'fitness conditioning off\ngym conditioning status\ngym reload\nsave-all\nstop\n'
                    phase=2;process.stdin.write(commands);process.stdin.flush()
            code=process.wait(timeout=10);thread.join(timeout=2)
            while not lines.empty():output.append(lines.get_nowait())
            output=''.join(output);history.append(f'=== Production boot {boot+1} ===\n'+output)
            assert code==0 and phase==2 and 'Saving worlds' in output,output[-6000:]
            assert 'realmfitness '+VERSION in output,output[-6000:]
            required=['fitness.settings_reloaded','fitness.conditioning.status_on','fitness.conditioning.status_off','fitness.conditioning.turned_off']
            if boot==0:required.append('fitness.conditioning.turned_on')
            assert all(contains(output,key) for key in required),output[-6000:]
            assert 'Unknown or incomplete command' not in output and 'Unexpected exception' not in output,output[-6000:]
            assert json.loads((WORK/'config/realm-fitness.json').read_text())['conditioning']['enabled'] is (boot==0)
        finally:
            if process.poll() is None:process.kill();process.wait()
            thread.join(timeout=2)
            if isinstance(output,list):history.append(''.join(output))
            (OUT/'production-server.log').write_text('\n'.join(history))
    assert hashlib.sha256((WORK/'mods'/JAR.name).read_bytes()).digest()==hashlib.sha256(JAR.read_bytes()).digest()
    report={'success':True,'minecraft':'1.20.1','fabric_loader':'0.16.14','remapped_jar_booted':True,'production_jar_sha256':hashlib.sha256(JAR.read_bytes()).hexdigest(),'console_reload':True,'clean_shutdown':True,'conditioning_default_disabled':True,'conditioning_commands':True,'conditioning_restart_persisted':True}
    (OUT/'production-server.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))

if __name__=='__main__':main()
