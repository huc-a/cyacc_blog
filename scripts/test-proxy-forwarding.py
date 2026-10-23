#!/usr/bin/env python3
"""Loopback-only integration test for the ECS trusted proxy and login limit.

Use a disposable backend with SERVER_FORWARD_HEADERS_STRATEGY=framework and
COOKIE_SECURE=false, plus the host Nginx snippets. No real credentials needed.
"""
import argparse
import http.client
from http.cookies import SimpleCookie
import json
from urllib.parse import urlencode, urlsplit


class Client:
    def __init__(self, url, source='127.0.0.1', forwarded=None):
        self.url = urlsplit(url)
        if self.url.scheme != 'http' or self.url.hostname not in ('localhost', '127.0.0.1') or self.url.path not in ('', '/'):
            raise ValueError('Only disposable loopback HTTP services are allowed')
        self.source, self.forwarded, self.cookies = source, forwarded or {}, SimpleCookie()
        status, _, payload = self.call('GET', '/api/auth/csrf')
        assert status == 200, status
        self.csrf = json.loads(payload)

    def call(self, method, path, headers=None, body=None):
        connection = http.client.HTTPConnection(self.url.hostname, self.url.port or 80, source_address=(self.source, 0), timeout=10)
        try:
            values = {'Host':'cyacc.cn', 'Cookie':'; '.join(f'{k}={v.value}' for k,v in self.cookies.items()), **self.forwarded, **(headers or {})}
            connection.request(method, path, body=body, headers=values)
            response = connection.getresponse()
            for name,value in response.getheaders():
                if name.lower() == 'set-cookie': self.cookies.load(value)
            return response.status, dict(response.getheaders()), response.read()
        finally:
            connection.close()

    def login(self, headers=None):
        return self.call('POST','/api/auth/login', {
            self.csrf['headerName']:self.csrf['token'],
            'Content-Type':'application/x-www-form-urlencoded', **(headers or {})
        }, urlencode({'username':'missing-proxy-test-user','password':'invalid-test-password'}))[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api', required=True)
    parser.add_argument('--proxy')
    parser.add_argument('--api-only', action='store_true', help='Verify backend forwarding only; does not verify the host proxy')
    args = parser.parse_args()
    if not args.api_only and not args.proxy:
        parser.error('--proxy is required unless --api-only is selected')
    first = Client(args.api, forwarded={'X-Forwarded-For':'198.51.100.21'})
    for _ in range(10): assert first.login() == 401
    assert first.login() == 429
    second = Client(args.api, forwarded={'X-Forwarded-For':'198.51.100.22'})
    assert second.login() == 401, 'Backend combined different forwarded client addresses'
    print('PASS backend interprets trusted forwarded addresses and keeps separate login quotas')
    if args.api_only:
        return

    public = Client(args.proxy)
    for i in range(10):
        assert public.login({'X-Forwarded-For':f'203.0.113.{i+1}',
            'Forwarded':f'for=192.0.2.{i+1};proto=https;host=untrusted.example',
            'X-Forwarded-Host':'untrusted.example','X-Forwarded-Prefix':'/untrusted'}) == 401
    assert public.login({'X-Forwarded-For':'203.0.113.99','Forwarded':'for=192.0.2.99'}) == 429
    other = Client(args.proxy, source='127.0.0.2')
    assert other.login() == 401, 'Proxy combined different source addresses'
    print('PASS host proxy overwrites spoofed headers; rotating them cannot bypass limits; second source is independent')


if __name__ == '__main__':
    main()
