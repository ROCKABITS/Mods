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
    download('https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.16.14/1.1.2/server/jar',WORK/'fabric-server-launch.jar')
    download('https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.116.18+1.21.1/fabric-api-0.116.18+1.21.1.jar',WORK/'mods/fabric-api.jar')
    cached=Path(os.environ.get('GRADLE_USER_HOME',str(Path.home()/'.gradle')))/'caches/fabric-loom/1.21.1/minecraft-server.jar'
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
    process=subprocess.Popen([java,'-Xmx1G','-jar','fabric-server-launch.jar','nogui'],cwd=WORK,env=env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
    lines=queue.Queue()
    def reader():
        for line in process.stdout:lines.put(line)
    threading.Thread(target=reader,daemon=True).start()
    deadline=time.monotonic()+360;started=False;output=[]
    try:
        while process.poll() is None or not lines.empty():
            if time.monotonic()>deadline:raise RuntimeError('Production Fabric server startup/shutdown timed out')
            try:line=lines.get(timeout=.2)
            except queue.Empty:continue
            output.append(line)
            if not started and 'Done (' in line:
                started=True;process.stdin.write('gym reload\nsave-all\nstop\n');process.stdin.flush()
        code=process.wait(timeout=10)
        text=''.join(output);(OUT/'production-server.log').write_text(text)
        assert code==0 and started and 'realmfitness '+VERSION in text and 'Saving worlds' in text,text[-6000:]
        with zipfile.ZipFile(JAR) as archive:expected=json.loads(archive.read('assets/realmfitness/lang/en_us.json'))['fitness.settings_reloaded']
        assert expected in text,text[-6000:]
        assert 'Unknown or incomplete command' not in text and 'Unexpected exception' not in text,text[-6000:]
        report={'success':True,'minecraft':'1.21.1','fabric_loader':'0.16.14','production_jar_sha256':hashlib.sha256(JAR.read_bytes()).hexdigest(),'remapped_jar_booted':True,'console_reload':True,'clean_shutdown':True}
        (OUT/'production-server.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
    finally:
        if process.poll() is None:process.kill();process.wait()
        if output:(OUT/'production-server.log').write_text(''.join(output))

if __name__=='__main__':main()
