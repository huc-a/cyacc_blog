#!/usr/bin/env python3
"""Verify pinned Docker Hub indexes contain the target runtime platform.

Uses public, anonymous Docker Hub access; never reads local Docker credentials.
Run before shipping an archive, including on hosts without Docker Engine.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import re
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def verify(image, platform):
    reference, expected = image.split('@', 1)
    repository = reference.rsplit(':', 1)[0]
    if repository.startswith('docker.io/'):
        repository = repository[len('docker.io/'):]
    if '/' not in repository:
        repository = 'library/' + repository
    if not re.fullmatch(r'[a-z0-9._/-]+', repository):
        raise ValueError('Unexpected Docker Hub repository')
    query = urlencode({'service': 'registry.docker.io', 'scope': 'repository:' + repository + ':pull'})
    with urlopen('https://auth.docker.io/token?' + query, timeout=20) as response:
        token = json.load(response)['token']
    request = Request('https://registry-1.docker.io/v2/' + repository + '/manifests/' + expected,
        headers={'Authorization': 'Bearer ' + token,
                 'Accept': 'application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json'})
    with urlopen(request, timeout=20) as response:
        data = response.read()
        advertised = response.headers.get('Docker-Content-Digest')
    if expected != 'sha256:' + hashlib.sha256(data).hexdigest() or advertised != expected:
        raise ValueError('Registry digest does not match the pinned index')
    entries = json.loads(data).get('manifests', [])
    platforms = {'/'.join((entry.get('platform', {}).get('os', ''), entry.get('platform', {}).get('architecture', ''))) for entry in entries}
    if platform not in platforms:
        raise ValueError('Pinned index has no ' + platform + ' image (an existing digest alone is insufficient)')
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--platform', default='linux/amd64')
    parser.add_argument('--image', action='append', help='Check specific pinned Docker Hub indexes instead of repository deployment files')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    files = [*root.joinpath('deploy').glob('Dockerfile.*'), root / 'deploy/compose.yaml']
    images = sorted(set(args.image or [match for path in files for match in re.findall(r'([a-z0-9./:_-]+@sha256:[0-9a-f]{64})', path.read_text(encoding='utf-8'))]))
    if not images:
        raise SystemExit('No pinned images found')
    def check(image):
        try:
            verify(image, args.platform)
            return True, 'PASS ' + args.platform + ' ' + image
        except Exception as error:
            return False, 'FAIL ' + image + ': ' + str(error)
    with ThreadPoolExecutor(max_workers=3) as pool:
        results = list(pool.map(check, images))
    for _, message in results:
        print(message)
    raise SystemExit(0 if all(success for success, _ in results) else 1)


if __name__ == '__main__':
    main()
