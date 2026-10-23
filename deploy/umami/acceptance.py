#!/usr/bin/env python3
"""Bounded trial on a disposable website; never create synthetic production traffic."""
import concurrent.futures
import json
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

root=Path(__file__).resolve().parent
admin=json.loads((root/'admin-credentials.json').read_text())
integration=json.loads((root/'integration.json').read_text())
key=integration['apiKey']
ua='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36'

def call(method,path,body=None,token=None,expected=200,extra=None):
    headers={'Content-Type':'application/json','User-Agent':ua,**(extra or {})}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request('http://127.0.0.1:3010'+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=20) as r: status=r.status;data=r.read()
    except urllib.error.HTTPError as e: status=e.code;data=e.read()
    assert status==expected,f'{method} {path}: HTTP {status}, expected {expected}'
    try: return json.loads(data) if data else None
    except json.JSONDecodeError: return None

def memory():
    result={}
    for name in ['cyacc-umami-umami-1','cyacc-umami-db-1','cyacc-blog-server-1','cyacc-blog-web-1']:
        info=json.loads(subprocess.check_output(['docker','inspect',name],text=True))[0]
        cgroup=next(x[3:] for x in Path(f"/proc/{info['State']['Pid']}/cgroup").read_text().splitlines() if x.startswith('0::'))
        directory=Path('/sys/fs/cgroup')/cgroup.lstrip('/')
        result[name]={'oomKilled':info['State']['OOMKilled'],'restarts':info['RestartCount']}
        for metric in ['memory.current','memory.peak','memory.swap.current','memory.events']:
            result[name][metric]=(directory/metric).read_text().strip()
    result['hostAvailableKiB']=int(next(x.split()[1] for x in Path('/proc/meminfo').read_text().splitlines() if x.startswith('MemAvailable:')))
    result['pressure']=Path('/proc/pressure/memory').read_text().strip()
    return result

token=call('POST','/api/auth/login',{'username':admin['username'],'password':admin['password']})['token']
call('POST','/api/auth/login',{'username':'admin','password':'umami'},expected=401)
start=int(time.time()*1000)-60000
production=integration['websiteId']
query=lambda: '?'+urllib.parse.urlencode({'startAt':start,'endAt':int(time.time()*1000)+60000,'unit':'day','timezone':'Asia/Shanghai'})
call('GET',f'/api/websites/{production}/stats'+query(),expected=401)
call('GET',f'/api/websites/{production}/stats'+query(),token=key)
call('POST',f'/api/websites/{production}',{'name':'cyacc.cn'},key,expected=401)
call('POST','/api/websites',{'name':'Permission probe','domain':'trial.cyacc.invalid'},key,expected=401)
print('PASS default password rejected; anonymous denied; integration key reads but cannot create/update websites',flush=True)
trial_id=str(uuid.uuid4())
trial=call('POST','/api/websites',{'id':trial_id,'name':'Disposable acceptance '+trial_id[:8],'domain':'trial.cyacc.invalid','teamId':admin['teamId']},token)
assert trial['id']==trial_id and trial_id!=production
result={'trialId':trial_id,'before':memory()}
try:
    def event(index,cache=None):
        payload={'website':trial_id,'hostname':'trial.cyacc.invalid','url':'/trial/'+str(index%3),'title':'Isolated acceptance','language':'zh-CN','screen':'1280x800','referrer':'https://example.org/'}
        t=time.monotonic()
        response=call('POST','/api/send',{'type':'event','payload':payload},extra={'x-umami-cache':cache} if cache else None)
        assert response and response.get('sessionId'), 'Tracker did not record the event'
        return time.monotonic()-t,response.get('cache')
    _,cache=event(0)
    def batch(offset):
        times=[]
        for index in range(offset,40,2):
            elapsed,_=event(index,cache);times.append(elapsed);time.sleep(.1)
        return times
    def blog_probes():
        samples=[]
        for _ in range(6):
            for url in ['https://cyacc.cn/','https://cyacc.cn/api/public/site']:
                t=time.monotonic()
                with urllib.request.urlopen(url,timeout=15) as r: assert r.status==200;r.read()
                samples.append(round(time.monotonic()-t,3))
            time.sleep(.5)
        return samples
    with concurrent.futures.ThreadPoolExecutor(3) as pool:
        tasks=[pool.submit(batch,0),pool.submit(batch,1),pool.submit(blog_probes)]
        latency=tasks[0].result()+tasks[1].result();blog=tasks[2].result()
    stats=call('GET',f'/api/websites/{trial_id}/stats'+query()+'&compare=prev',token=key)
    assert stats['pageviews']==41 and stats['visitors']>=1, f'Unexpected aggregate: {stats}'
    trends=call('GET',f'/api/websites/{trial_id}/pageviews'+query(),token=key)
    assert sum(row['y'] for row in trends['pageviews'])==41
    metrics={}
    for dimension in ['path','referrer','device','browser']:
        rows=call('GET',f'/api/websites/{trial_id}/metrics'+query()+'&type='+dimension,token=key)
        assert rows, f'Empty {dimension}';metrics[dimension]=rows
    # Umami v3 ranks paths by distinct visitors, not pageviews.
    assert len(metrics['path'])==3 and all(row['y']==stats['visitors'] for row in metrics['path'])
    call('DELETE',f'/api/websites/{trial_id}',token=key,expected=401)
    result.update({'events':41,'stats':stats,'metrics':metrics,'collectionMaxSeconds':round(max(latency),3),'collectionMeanSeconds':round(sum(latency)/len(latency),3),'blogProbeSeconds':blog,'after':memory()})
    for name,item in result['after'].items():
        if isinstance(item,dict):
            assert not item['oomKilled'] and 'oom_kill 0' in item['memory.events']
    print('PASS 41 events at concurrency 2, PV/UV/trends/rankings, 12 simultaneous blog probes; no container OOM',flush=True)
finally:
    # Only the UUID created by this invocation can be removed.
    assert trial_id!=production
    call('DELETE',f'/api/websites/{trial_id}',token=token)
    print('Removed disposable website and its synthetic data',flush=True)
result['trialRemoved']=True
(root/'acceptance-result.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
