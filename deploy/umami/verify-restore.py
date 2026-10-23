#!/usr/bin/env python3
"""Restore the newest complete dump into a temporary DB on the isolated Umami DB service."""
import json
from pathlib import Path
import re
import subprocess
import uuid

bundles=sorted(p.parent for p in Path('/var/backups/cyacc-umami').glob('backup-*/COMPLETE'))
assert bundles, 'No complete backup'
bundle=bundles[-1]
database='umami_restore_'+uuid.uuid4().hex
assert re.fullmatch(r'umami_restore_[0-9a-f]{32}',database)
base=['docker','exec','-i','cyacc-umami-db-1']
def sql(db,query):
    return subprocess.check_output(base+['psql','-X','-v','ON_ERROR_STOP=1','-U','postgres','-d',db,'-Atc',query],text=True).strip()
def counts(db):
    tables=sql(db,"select tablename from pg_tables where schemaname='public' order by tablename").splitlines()
    return {table:int(sql(db,'select count(*) from "'+table.replace('"','""')+'"')) for table in tables}
sql('postgres','CREATE DATABASE '+database+' TEMPLATE template0')
try:
    with (bundle/'umami.dump').open('rb') as dump:
        subprocess.run(base+['pg_restore','-U','postgres','-d',database,'--no-owner','--no-privileges','--exit-on-error'],stdin=dump,check=True)
    restored=counts(database)
    assert len(restored)>=20 and restored.get('website',0)>=1
    result={'backup':str(bundle),'tables':len(restored),'websites':restored.get('website'),'restored':True}
    print(json.dumps(result))
finally:
    sql('postgres','DROP DATABASE '+database)
    print('Removed only the temporary restore database')
