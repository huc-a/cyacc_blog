#!/usr/bin/env python3
"""Verified PostgreSQL + media bundles. Restore never overwrites a database or directory."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import tarfile
from datetime import datetime, timezone


def digest(path):
    with open(path, 'rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def pg(args, tool, *values, capture=False):
    binary = str(Path(args.pg_bin) / (tool + ('.exe' if os.name == 'nt' else ''))) if args.pg_bin else tool
    result = subprocess.run([binary, *map(str, values)], check=True, capture_output=capture, encoding='utf-8' if capture else None,
                            env={**os.environ, 'PGCLIENTENCODING': 'UTF8', 'PGCONNECT_TIMEOUT': '10'})
    return result.stdout.strip() if capture else None


def query(args, database, sql):
    return pg(args, 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-d', database, '-tAc', sql, capture=True)


def counts(args, database):
    tables = query(args, database, "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename").splitlines()
    if not tables:
        raise ValueError('Database has no public tables')
    result = {}
    for table in tables:
        if not re.fullmatch(r'[a-z_][a-z0-9_]*', table):
            raise ValueError('Unexpected table name')
        result[table] = int(query(args, database, f'SELECT count(*) FROM public."{table}"'))
    return result


def inventory(media):
    files = {}
    for path in sorted(media.rglob('*')):
        if path.is_symlink():
            raise ValueError('Media must not contain symbolic links')
        if path.is_file():
            files[path.relative_to(media).as_posix()] = digest(path)
    return files


def check_references(args, database, media):
    keys = query(args, database, 'SELECT storage_key FROM media_asset ORDER BY id').splitlines()
    for key in keys:
        if Path(key).name != key or not (media / key).is_file():
            raise ValueError('Missing or unsafe media reference: ' + key)
    return len(keys)


def verify(bundle):
    manifest = json.loads((bundle / 'manifest.json').read_text(encoding='utf-8'))
    if manifest.get('format') != 'caiya-backup-v1':
        raise ValueError('Unsupported backup format')
    for filename in ['database.dump', 'media.tar']:
        if digest(bundle / filename) != manifest['sha256'][filename]:
            raise ValueError('Backup checksum mismatch: ' + filename)
    return manifest


def backup(args):
    media, out = Path(args.media).resolve(), Path(args.out).resolve()
    if not media.is_dir() or out == media or media in out.parents:
        raise ValueError('Use an existing media directory and a separate new output directory')
    if not args.quiesced:
        try:
            with socket.create_connection(('127.0.0.1', args.app_port), timeout=1):
                raise ValueError('Stop the application before backup so database and media remain consistent')
        except (ConnectionRefusedError, socket.timeout, OSError) as error:
            pass
    out.mkdir(parents=True, exist_ok=False)
    before = inventory(media)
    row_counts = counts(args, args.database)
    refs = check_references(args, args.database, media)
    pg(args, 'pg_dump', '--format=custom', '--no-owner', '--no-acl', '--file', out / 'database.dump', args.database)
    with tarfile.open(out / 'media.tar', 'w') as archive:
        for name in before:
            archive.add(media / name, arcname=name, recursive=False)
    if before != inventory(media) or row_counts != counts(args, args.database):
        raise ValueError('Data changed during backup; stop all writers and make a new bundle')
    manifest = {'format': 'caiya-backup-v1', 'createdAt': datetime.now(timezone.utc).isoformat(), 'sourceDatabase': args.database,
                'tables': row_counts, 'mediaReferences': refs, 'mediaFiles': before,
                'sha256': {name: digest(out / name) for name in ['database.dump', 'media.tar']}}
    (out / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    verify(out)
    print(f'Backup verified: {out}; {len(row_counts)} tables, {len(before)} media files')


def restore(args):
    bundle, media = Path(args.bundle).resolve(), Path(args.media).resolve()
    manifest = verify(bundle)
    if not re.fullmatch(r'caiya_restore_[a-z0-9_]+', args.target):
        raise ValueError('Target must be a new isolated database named caiya_restore_<name>')
    if media.exists():
        raise ValueError('Restore media directory already exists; nothing was overwritten')
    if query(args, 'postgres', f"SELECT 1 FROM pg_database WHERE datname='{args.target}'"):
        raise ValueError('Target database already exists; nothing was overwritten')
    # Validate every archive entry before creating any restore target.
    with tarfile.open(bundle / 'media.tar', 'r') as archive:
        entries = archive.getmembers()
        for member in entries:
            dest = (media / member.name).resolve()
            if not member.isfile() or '\\' in member.name or dest == media or media not in dest.parents:
                raise ValueError('Unsafe media archive entry')
        if sorted(x.name for x in entries) != sorted(manifest['mediaFiles']):
            raise ValueError('Media manifest differs from archive')
        pg(args, 'createdb', '--template=template0', '--encoding=UTF8', args.target)
        media.mkdir(parents=True, exist_ok=False)
        for member in entries:
            dest = media / member.name
            dest.parent.mkdir(parents=True, exist_ok=True)
            with archive.extractfile(member) as source, dest.open('xb') as target:
                shutil.copyfileobj(source, target)
    pg(args, 'pg_restore', '--exit-on-error', '--single-transaction', '--no-owner', '--no-acl', '--dbname', args.target, bundle / 'database.dump')
    if inventory(media) != manifest['mediaFiles'] or counts(args, args.target) != manifest['tables']:
        raise ValueError('Restore verification failed; isolated target retained for diagnosis')
    check_references(args, args.target, media)
    print(f'Restored and verified: {args.target}; media: {media}. Original data was not modified.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pg-bin', default=os.environ.get('PG_BIN', ''), help='Directory containing PostgreSQL client tools; otherwise use PATH')
    sub = parser.add_subparsers(dest='action', required=True)
    create = sub.add_parser('backup')
    create.add_argument('--database', default='caiya'); create.add_argument('--media', required=True); create.add_argument('--out', required=True)
    create.add_argument('--app-port', type=int, default=8080)
    create.add_argument('--quiesced', action='store_true', help='Explicitly assert all application writers have been stopped (for container/remote maintenance)')
    load = sub.add_parser('restore')
    load.add_argument('--bundle', required=True); load.add_argument('--target', required=True); load.add_argument('--media', required=True)
    check = sub.add_parser('verify'); check.add_argument('--bundle', required=True)
    args = parser.parse_args()
    if args.action == 'backup': backup(args)
    elif args.action == 'restore': restore(args)
    else: verify(Path(args.bundle).resolve()); print('Bundle checksums verified')


if __name__ == '__main__':
    main()
