#!/usr/bin/env python3
"""Validate synthetic read fixtures against a read-only captured backend OpenAPI.
Requires Python >=3.10 and jsonschema==4.26.0. Does not import or start the backend, or access a DB.
"""
import argparse
import json
from pathlib import Path
from jsonschema import Draft202012Validator

parser = argparse.ArgumentParser()
parser.add_argument('--openapi', type=Path, required=True)
args = parser.parse_args()
spec = json.loads(args.openapi.read_text())
fixture_dir = Path(__file__).resolve().parent.parent / 'data/src/test/resources/production-v2'
count = 0
for fixture in sorted(fixture_dir.glob('*.json')):
    name = fixture.stem
    schema_name = 'ErrorBody' if name == 'batch_error' else 'ManufacturingPreview' if 'preview' in name else 'TaskRead'
    schema = {'$ref': f'#/components/schemas/{schema_name}', 'components': spec['components']}
    errors = list(Draft202012Validator(schema).iter_errors(json.loads(fixture.read_text())))
    if errors:
        raise SystemExit('\n'.join(f'{fixture.name}: {list(error.path)}: {error.message}' for error in errors))
    count += 1
print(f'{count} synthetic production-v2 fixtures match captured backend OpenAPI read schemas.')
