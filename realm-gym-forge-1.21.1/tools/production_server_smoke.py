#!/usr/bin/env python3
"""Boot the distributed JAR on an isolated official Forge server."""
from pathlib import Path
import hashlib,json,os,queue,shutil,subprocess,threading,time,urllib.parse,urllib.request,zipfile

PROJECT=Path(__file__).resolve().parents[1]
PROPS=dict(line.split('=',1) for line in (PROJECT/'gradle.properties').read_text().splitlines() if '=' in line)
VERSION=PROPS['mod_version'];FORGE=PROPS['forge_version'];MC=PROPS['minecraft_version']
WORK=PROJECT/'build/production-smoke';WORK.mkdir(parents=True,exist_ok=True)
OUT=PROJECT/'qa-results';OUT.mkdir(exist_ok=True)
JAR=PROJECT/'build/libs'/f'realm-gym-{VERSION}.jar'

def main():
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
    installer=WORK/'forge-installer.jar'
    if not installer.exists():
        url=f'https://maven.minecraftforge.net/net/minecraftforge/forge/{MC}-{FORGE}/forge-{MC}-{FORGE}-installer.jar'
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'Mozilla/5.0'}),timeout=60) as response:installer.write_bytes(response.read())
    args=WORK/f'libraries/net/minecraftforge/forge/{MC}-{FORGE}/unix_args.txt'
    if not args.exists():
        installed=subprocess.run([java,'-jar',str(installer),'--installServer',str(WORK)],cwd=WORK,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=360)
        (OUT/'production-install.log').write_text(installed.stdout)
        assert installed.returncode==0 and args.exists(),installed.stdout[-6000:]
    (WORK/'mods').mkdir(exist_ok=True)
    for old in (WORK/'mods').glob('realm-gym-*.jar'):old.unlink()
    shutil.copyfile(JAR,WORK/'mods'/JAR.name)
    (WORK/'eula.txt').write_text('eula=true\n')
    (WORK/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=5\nspawn-protection=0\ngenerate-structures=false\nmax-tick-time=0\n')
    process=subprocess.Popen([java,'-Xmx1G','@'+str(args),'nogui'],cwd=WORK,env=env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
    lines=queue.Queue()
    def reader():
        for line in process.stdout:lines.put(line)
    threading.Thread(target=reader,daemon=True).start()
    deadline=time.monotonic()+360;started=False;output=[]
    try:
        while process.poll() is None or not lines.empty():
            if time.monotonic()>deadline:raise RuntimeError('Production Forge server startup/shutdown timed out')
            try:line=lines.get(timeout=.2)
            except queue.Empty:continue
            output.append(line)
            if not started and 'Done (' in line:
                started=True;process.stdin.write('gym reload\nsave-all\nstop\n');process.stdin.flush()
        code=process.wait(timeout=10)
        text=''.join(output);(OUT/'production-server.log').write_text(text)
        assert code==0 and started and 'Starting minecraft server version '+MC in text and 'MinecraftForge v'+FORGE in text and 'java version 21' in text and 'Saving worlds' in text,text[-6000:]
        with zipfile.ZipFile(JAR) as archive:expected=json.loads(archive.read('assets/realmfitness/lang/en_us.json'))['fitness.settings_reloaded']
        # Dedicated Forge consoles can retain a custom translatable component's key.
        assert expected in text or 'fitness.settings_reloaded' in text,text[-6000:]
        assert (WORK/'config/realm-fitness.json').is_file()
        assert hashlib.sha256((WORK/'mods'/JAR.name).read_bytes()).digest()==hashlib.sha256(JAR.read_bytes()).digest()
        assert 'Unknown or incomplete command' not in text and 'Unexpected exception' not in text,text[-6000:]
        assert json.loads((WORK/'config/realm-fitness.json').read_text())['conditioning']['enabled'] is False
        report={'success':True,'minecraft':MC,'forge':FORGE,'java':21,'production_jar_sha256':hashlib.sha256(JAR.read_bytes()).hexdigest(),'official_mapped_jar_booted':True,'console_reload':True,'clean_shutdown':True,'conditioning_default_disabled':True}
        (OUT/'production-server.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
    finally:
        if process.poll() is None:process.kill();process.wait()
        if output:(OUT/'production-server.log').write_text(''.join(output))

if __name__=='__main__':main()
