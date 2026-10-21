#!/usr/bin/env python3
"""Only documented, unpatched build-tool exceptions are accepted; verify production output."""
import argparse
from datetime import date
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('report'); parser.add_argument('--bundle', default='web/.output/server')
args = parser.parse_args()
report = json.loads(Path(args.report).read_text(encoding='utf-8-sig'))
if 'advisories' not in report or 'metadata' not in report:
    raise SystemExit('Missing audit result; scan did not complete')
bundle = Path(args.bundle)
if not (bundle / 'index.mjs').exists():
    raise SystemExit('Build production output before assessing exceptions')
exceptions = {
    'GHSA-86w9-cpqp-85rv': ('node-forge', '1.4.0'),
    'GHSA-vfj7-8cjw-p6xm': ('braces', '3.0.3'),
}
unexpected = []
for advisory in report['advisories'].values():
    key = advisory['github_advisory_id']
    rule = exceptions.get(key)
    if not rule or date.today() > date(2026, 12, 20) or advisory['module_name'] != rule[0] or any(x['version'] != rule[1] for x in advisory['findings']):
        unexpected.append(key); continue
    for path in bundle.rglob('*'):
        if path.name == 'package.json':
            package = json.loads(path.read_text(encoding='utf-8'))
            if package.get('name') == rule[0]:
                raise SystemExit(f'Build-only exception shipped in production: {rule[0]}')
        if path.is_file() and path.suffix in ['.mjs', '.js']:
            # Nitro would retain these import/module markers if the packages entered its runtime graph.
            code = path.read_text(encoding='utf-8')
            if (rule[0] == 'node-forge' and ('node-forge' in code or 'forge.pki' in code)) or (rule[0] == 'braces' and ('require("braces")' in code or "require('braces')" in code)):
                raise SystemExit(f'Unexpected runtime reference: {rule[0]} in {path}')
    print(f'Assessed build-only advisory: {key}; {rule[0]} {rule[1]}; review by 2026-12-20 (see docs/安全检查.md)')
if unexpected:
    raise SystemExit('Unassessed advisories: ' + ', '.join(unexpected))
print('No unassessed dependency advisories. Production bundle checked.')
