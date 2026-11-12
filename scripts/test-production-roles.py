#!/usr/bin/env python3
"""Exercise the deployment initialization SQL and migrate-only jar in a new database."""
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time

suffix = str(int(time.time()))
database = 'caiya_restore_roles_' + suffix
migrator, runtime = 'blog_migrator_' + suffix, 'blog_runtime_' + suffix
pg_bin = Path(os.environ.get('PG_BIN', ''))
def binary(name):
    return str(pg_bin / (name + ('.exe' if os.name == 'nt' else ''))) if os.environ.get('PG_BIN') else name
def sql(text, user=None, password=None, expected=True):
    env = {**os.environ, 'PGCLIENTENCODING': 'UTF8'}
    if user: env.update(PGUSER=user, PGPASSWORD=password)
    result = subprocess.run([binary('psql'), '-X', '-v', 'ON_ERROR_STOP=1', '-d', database, '-tA'], input=text, text=True, encoding='utf-8', capture_output=True, env=env)
    if (result.returncode == 0) != expected:
        raise RuntimeError('Unexpected database privilege result: ' + result.stderr)
    return result.stdout.strip()
subprocess.run([binary('createdb'), '--template=template0', database], check=True)
password_m, password_r = secrets.token_hex(24), secrets.token_hex(24)
source = Path('deploy/init-db.sh').read_text(encoding='utf-8').split("<<'SQL'\n", 1)[1].rsplit('\nSQL', 1)[0]
source = source.replace('blog_migrator', migrator).replace('blog_runtime', runtime)
sql(f'\\set migrator_password {password_m}\n\\set runtime_password {password_r}\n' + source)
url = f"jdbc:postgresql://{os.environ.get('PGHOST', '127.0.0.1')}:{os.environ.get('PGPORT', '5432')}/{database}"
jar = sys.argv[1]
subprocess.run(['java', '-jar', jar, '--migrate-only'], check=True,
               env={**os.environ, 'DB_URL': url, 'MIGRATION_USER': migrator, 'MIGRATION_PASSWORD': password_m})
assert sql("SELECT count(*) FROM flyway_schema_history WHERE success", runtime, password_r) == str(len(list(Path('server/src/main/resources/db/migration').glob('V*__*.sql'))))
sql("INSERT INTO site_setting(setting_key,value_jsonb) VALUES ('role.probe','{}'); UPDATE site_setting SET value_jsonb='1' WHERE setting_key='role.probe'; DELETE FROM site_setting WHERE setting_key='role.probe';", runtime, password_r)
sql("INSERT INTO content(content_type,slug) VALUES ('article','role-probe'); DELETE FROM content WHERE slug='role-probe';", runtime, password_r)
sql("INSERT INTO login_throttle(account_key,failure_count,window_started_at) VALUES (repeat('a',64),1,now()); UPDATE login_throttle SET failure_count=2 WHERE account_key=repeat('a',64); DELETE FROM login_throttle WHERE account_key=repeat('a',64);", runtime, password_r)
sql("INSERT INTO admin_user(username,password_hash) VALUES ('role-probe','unused-test-hash'); UPDATE admin_user SET security_version=security_version+1 WHERE username='role-probe'; DELETE FROM admin_user WHERE username='role-probe';", runtime, password_r)
sql('CREATE TABLE forbidden_ddl(id int);', runtime, password_r, expected=False)
sql('ALTER TABLE content ADD COLUMN forbidden_column int;', runtime, password_r, expected=False)
assert sql(f"SELECT rolsuper OR rolcreaterole OR rolcreatedb FROM pg_roles WHERE rolname='{runtime}'") == 'f'
print('Production roles verified: migrations succeed; runtime CRUD succeeds; CREATE/ALTER and elevated role permissions are denied.')
print('Isolated verification database retained: ' + database)
