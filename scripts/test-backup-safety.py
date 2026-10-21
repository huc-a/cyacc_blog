#!/usr/bin/env python3
"""Negative recovery checks; they must fail before connecting to any database."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

bundle, existing_media = map(Path, sys.argv[1:3])
def reject(*args, expected):
    result = subprocess.run([sys.executable, 'scripts/backup.py', *map(str, args)], capture_output=True, text=True, encoding='utf-8')
    assert result.returncode != 0 and expected in result.stderr, result.stderr
reject('restore', '--bundle', bundle, '--target', 'caiya', '--media', existing_media, expected='new isolated database')
reject('restore', '--bundle', bundle, '--target', 'caiya_restore_safety', '--media', existing_media, expected='directory already exists')
with tempfile.TemporaryDirectory(prefix='caiya-backup-safety-') as temp:
    invalid = Path(temp) / 'bundle'
    shutil.copytree(bundle, invalid)
    with (invalid / 'database.dump').open('ab') as stream: stream.write(b'tampered')
    reject('verify', '--bundle', invalid, expected='checksum mismatch')
print('Safety checks passed: original database names, existing media targets and corrupted bundles are rejected')
