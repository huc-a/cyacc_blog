#!/usr/bin/env python3
"""Query OSV for the resolved runtime Maven dependencies; fail closed on unavailable scans."""
import argparse
import json
from pathlib import Path
import re
import urllib.request
import concurrent.futures
from datetime import date

parser = argparse.ArgumentParser()
parser.add_argument('dependencies', help='mvn dependency:list -DincludeScope=runtime -DoutputFile=...')
parser.add_argument('--out', required=True)
args = parser.parse_args()
entries = []
for line in Path(args.dependencies).read_text(encoding='utf-8-sig').splitlines():
    match = re.match(r'\s*([\w.-]+):([\w.-]+):jar:(?:[^:\s]+:)?([^:\s]+):(compile|runtime)(?:\s|$)', line)
    if match:
        entries.append({'package': {'ecosystem': 'Maven', 'name': f'{match[1]}:{match[2]}'}, 'version': match[3]})
if not entries:
    raise SystemExit('No resolved dependencies parsed; scan did not run')


def post(data):
    request = urllib.request.Request('https://api.osv.dev/v1/querybatch', data=json.dumps(data).encode(), headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


result = post({'queries': entries})
findings = []
for entry, item in zip(entries, result['results']):
    for vuln in item.get('vulns', []):
        findings.append({**entry, 'id': vuln['id']})
assessed, unassessed = [], []
for finding in findings:
    if finding['id'] in ['GHSA-j9f9-w8pj-32f8', 'GHSA-pc63-qcmh-9cmg'] and finding['package']['name'] == 'org.springframework:spring-webmvc' and finding['version'] == '6.2.19' and date.today() <= date(2026, 12, 20):
        for source in Path('server/src/main/java').rglob('*.java'):
            if re.search(r'SseEmitter|ServerSentEvent|XsltView|ModelAndView|ViewResolver|@Controller\b', source.read_text(encoding='utf-8')):
                raise SystemExit('View/SSE feature added; reassess Spring advisories before release')
        assessed.append({**finding, 'reason': 'REST JSON API only: no XSLT views, view rendering or SSE fragments; review by 2026-12-20'})
    else:
        unassessed.append(finding)
report = {'dependenciesScanned': len(entries), 'unassessed': unassessed, 'assessed': assessed}
Path(args.out).write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps(report, indent=2))
raise SystemExit(1 if unassessed else 0)
