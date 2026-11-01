#!/usr/bin/env python3
"""Fail when implementation dependencies cross the agreed module boundaries."""
from pathlib import Path
import re
root = Path(__file__).resolve().parent.parent
allowed = {
    'app': {'domain', 'data', 'core:ui', 'feature:auth', 'feature:tasks', 'feature:stock'},
    'domain': set(), 'data': {'domain'}, 'core:ui': {'domain'},
    'feature:auth': {'domain', 'core:ui'},
    'feature:tasks': {'domain', 'core:ui'},
    'feature:stock': {'domain', 'core:ui'},
}
failures = []
for module, dependencies in allowed.items():
    path = root.joinpath(*module.split(':'))
    build = path / 'build.gradle.kts'
    if not build.exists():
        failures.append(f'{module}: missing build file')
        continue
    for dependency in re.findall(r'project\(\":([^\"]+)\"\)', build.read_text()):
        if dependency not in dependencies:
            failures.append(f'{module} -> {dependency}: forbidden dependency')
    for source in path.glob('src/**/*.kt'):
        content = source.read_text()
        if module == 'domain' and re.search(r'^import (android\.|androidx\.|retrofit2\.|okhttp3\.|dagger\.|javax.inject\.)', content, re.M):
            failures.append(f'{source}: platform dependency in domain')
        if module.startswith('feature:') and re.search(r'^import com\.logunovinc\.workshopmanager\.data\.', content, re.M):
            failures.append(f'{source}: data implementation in feature')
if failures:
    raise SystemExit('\n'.join(failures))
print('Architecture boundaries verified (7 modules).')
