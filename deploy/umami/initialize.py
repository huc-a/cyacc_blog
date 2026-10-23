#!/usr/bin/env python3
"""Initialize only a fresh loopback Umami instance; keep generated secrets on its host."""
import json
import os
from pathlib import Path
import secrets
import urllib.error
import urllib.request

root=Path(__file__).resolve().parent
os.umask(0o077)
credentials=root/'admin-credentials.json'
integration=root/'integration.json'
if integration.exists(): raise SystemExit('Already initialized; existing accounts and website preserved')
def save(path,value):
    temporary=path.with_suffix('.tmp')
    temporary.write_text(json.dumps(value,indent=2)+'\n');temporary.chmod(0o600)
    temporary.replace(path)
def call(method,path,body=None,token=None,expected=200):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request('http://127.0.0.1:3010'+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
    try:
        with urllib.request.urlopen(req,timeout=20) as response: status=response.status;data=response.read()
    except urllib.error.HTTPError as e: status=e.code;data=e.read()
    assert status==expected,f'{method} {path}: HTTP {status}, expected {expected}'
    return json.loads(data) if data else None
if not credentials.exists(): save(credentials,{'username':'admin','password':secrets.token_urlsafe(30),'rotated':False})
admin=json.loads(credentials.read_text())
if not admin['rotated']:
    try:
        call('POST','/api/auth/login',{'username':'admin','password':admin['password']})
    except AssertionError:
        token=call('POST','/api/auth/login',{'username':'admin','password':'umami'})['token']
        call('POST','/api/me/password',{'currentPassword':'umami','newPassword':admin['password']},token)
    admin['rotated']=True;save(credentials,admin)
token=call('POST','/api/auth/login',{'username':'admin','password':admin['password']})['token']
call('POST','/api/auth/login',{'username':'admin','password':'umami'},expected=401)
if not admin.get('teamId'):
    teams=call('GET','/api/teams',token=token)['data']
    matches=[item for item in teams if item.get('name')=='cyacc.cn']
    assert len(matches)<=1, 'Ambiguous team; preserve existing data'
    team=matches[0] if matches else call('POST','/api/teams',{'name':'cyacc.cn'},token)[0]
    admin['teamId']=team['id'];save(credentials,admin)
if not admin.get('websiteId'):
    website=call('POST','/api/websites',{'name':'cyacc.cn','domain':'cyacc.cn','teamId':admin['teamId']},token)
    admin['websiteId']=website['id'];save(credentials,admin)
reader_path=root/'reader-credentials.json'
if not reader_path.exists():
    save(reader_path,{'username':'cyacc-blog-reader','password':secrets.token_urlsafe(30)})
reader=json.loads(reader_path.read_text())
if not reader.get('id'):
    user=call('POST','/api/users',{**reader,'role':'view-only'},token)
    reader['id']=user['id'];save(reader_path,reader)
if not reader.get('teamJoined'):
    call('POST',f"/api/teams/{admin['teamId']}/users",{'userId':reader['id'],'role':'team-view-only'},token)
    reader['teamJoined']=True;save(reader_path,reader)
reader_token=call('POST','/api/auth/login',{'username':reader['username'],'password':reader['password']})['token']
key=call('POST','/api/me/api-keys',{'name':'cyacc-blog-server'},reader_token)
save(integration,{'websiteId':admin['websiteId'],'apiKey':key['key']})
print('Initialized admin with random password; default password rejected; website and read-only team API key created')
