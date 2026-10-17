from pathlib import Path
import json,os,re,xml.etree.ElementTree as ET
p=Path(__file__).resolve().parents[1]
parts=['# Realm Rewards validation']
def notice(kind,title,value):
    value=str(value).replace('%','%25').replace('\r','%0D').replace('\n','%0A')
    print(f'::{kind} title={title}::{value}')
for file in sorted((p/'build/test-results/test').glob('TEST-*.xml')):
    r=ET.parse(file).getroot(); result='JUnit '+str({k:r.get(k) for k in ['name','tests','failures','errors','skipped']})
    parts.append(result);notice('notice','JUnit',result)
for name in ['build.log','gametests.log','integration.log','visual-client.log']:
    file=p/name
    if not file.exists():continue
    log=file.read_text(errors='replace');lines=log.splitlines()
    tail='\n'.join(lines[-120:])[-20000:]
    parts.append(f'## {name}\n```text\n{tail.replace("```","~~~")}\n```')
    hits=[line for line in lines if re.search(r'All \d+ required tests|REWARDS_\w+_PASSED',line)]
    if hits:notice('notice',name,'\n'.join(hits[-10:]))
    if 'BUILD FAILED' in log:
        indices=set()
        for i,line in enumerate(lines):
            if re.search(r'failed!|test.*failed|GameTestAssertException|AssertionError|Caused by:|Exception:| error:',line,re.I):indices.update(range(max(0,i-2),min(len(lines),i+10)))
        context='\n'.join(lines[i] for i in sorted(indices))[:16000]
        for start in range(0,len(context),2000):notice('error',name+' failure',context[start:start+2000])
        if not context:notice('error',name,tail[-2000:])
report=p/'qa-results/rewards-client/result.json'
if report.exists():
    data=json.loads(report.read_text());notice('notice' if data.get('success') else 'error','Actual Minecraft client',json.dumps(data))
with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a') as out:out.write('\n\n'.join(parts))
