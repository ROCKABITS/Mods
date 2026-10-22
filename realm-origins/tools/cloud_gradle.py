#!/usr/bin/env python3
"""Run the project wrapper using this cloud machine's supported proxy and CA."""
import os, pathlib, subprocess, sys, urllib.parse
project = pathlib.Path(__file__).resolve().parent.parent
env = os.environ.copy()
env['JAVA_HOME'] = str(pathlib.Path('/workspace/.tools/jdk21'))
env['PATH'] = env['JAVA_HOME'] + '/bin:' + env['PATH']
env['GRADLE_USER_HOME'] = '/workspace/.gradle'
opts = []
for scheme in ('http', 'https'):
    proxy = urllib.parse.urlsplit(env.get(scheme.upper() + '_PROXY', env.get(scheme + '_proxy', '')))
    if proxy.hostname:
        if proxy.username or proxy.password:
            raise SystemExit('Proxy credentials require supported secret binding; not writing them to process arguments.')
        opts.extend(['-D' + scheme + '.proxyHost=' + proxy.hostname, '-D' + scheme + '.proxyPort=' + str(proxy.port or 80)])
no_proxy = env.get('NO_PROXY', env.get('no_proxy', ''))
if no_proxy:
    hosts = '|'.join(('*' + item if item.startswith('.') else item) for item in no_proxy.split(',') if item)
    opts.append('-Dhttp.nonProxyHosts=' + hosts)
trust = pathlib.Path('/workspace/.tools/java-cacerts')
if trust.exists():
    opts.extend(['-Djavax.net.ssl.trustStore=' + str(trust), '-Djavax.net.ssl.trustStorePassword=changeit'])
# JAVA_TOOL_OPTIONS also reaches Gradle's child Java processes, including NeoForm.
env['JAVA_TOOL_OPTIONS'] = ' '.join(opts)
raise SystemExit(subprocess.call([str(project / 'gradlew'), *sys.argv[1:]], cwd=project, env=env))
