#!/usr/bin/env python3
"""Boot the distributed intermediary JAR on a real, isolated Fabric server."""
from pathlib import Path
import hashlib,json,os,re,queue,shutil,subprocess,threading,time,urllib.parse,urllib.request,zipfile

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
    def contains(line,key):return key in line or re.search(re.escape(strings[key]).replace('%s','.+?'),line) is not None
    (WORK/'config/realm-fitness.json').unlink(missing_ok=True)
    history=[]
    for boot in range(3):
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
                    assert (config['conditioning']['enabled'],config['conditioning'].get('timeMode','real'),config['conditioning']['graceDays'])==((False,'real',7),(True,'minecraft',3),(True,'real',5))[boot],'Clock or grace period differs from expected restart state'
                    commands=['gym conditioning days real 2\ngym conditioning days minecraft 3\ngym conditioning on\ngym conditioning status\ngym conditioning off\ngym conditioning status\ngym conditioning on\ngym reload\nsave-all\nstop\n', 'fitness conditioning days real 5\ngym conditioning status\ngym reload\nsave-all\nstop\n', 'fitness conditioning off\ngym conditioning status\ngym reload\nsave-all\nstop\n'][boot]
                    phase=2;process.stdin.write(commands);process.stdin.flush()
            code=process.wait(timeout=10);thread.join(timeout=2)
            while not lines.empty():output.append(lines.get_nowait())
            output=''.join(output);history.append(f'=== Production boot {boot+1} ===\n'+output)
            assert code==0 and phase==2 and 'Saving worlds' in output,output[-6000:]
            assert 'realmfitness '+VERSION in output,output[-6000:]
            required=['fitness.settings_reloaded','fitness.conditioning.schedule']
            if boot==0:required+=['fitness.conditioning.status_on','fitness.conditioning.status_off','fitness.conditioning.turned_on','fitness.conditioning.turned_off','fitness.conditioning.days_set']
            elif boot==1:required+=['fitness.conditioning.status_on','fitness.conditioning.days_set']
            else:required+=['fitness.conditioning.status_on','fitness.conditioning.status_off','fitness.conditioning.turned_off']
            assert all(contains(output,key) for key in required),output[-6000:]
            assert 'Unknown or incomplete command' not in output and 'Unexpected exception' not in output,output[-6000:]
            saved=json.loads((WORK/'config/realm-fitness.json').read_text())['conditioning']
            assert (saved['enabled'],saved['timeMode'],saved['graceDays'])==((True,'minecraft',3),(True,'real',5),(False,'real',5))[boot]
        finally:
            if process.poll() is None:process.kill();process.wait()
            thread.join(timeout=2)
            if isinstance(output,list):history.append(''.join(output))
            (OUT/'production-server.log').write_text('\n'.join(history))
    assert hashlib.sha256((WORK/'mods'/JAR.name).read_bytes()).digest()==hashlib.sha256(JAR.read_bytes()).digest()
    report={'success':True,'minecraft':'1.20.1','fabric_loader':'0.16.14','remapped_jar_booted':True,'production_jar_sha256':hashlib.sha256(JAR.read_bytes()).hexdigest(),'console_reload':True,'clean_shutdown':True,'conditioning_default_disabled':True,'conditioning_commands':True,'conditioning_restart_persisted':True,'conditioning_day_modes':True,'conditioning_days_restart_persisted':True}
    (OUT/'production-server.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))

if __name__=='__main__':main()
