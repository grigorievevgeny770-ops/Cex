#!/usr/bin/env bash
# Build a shareable debug APK. No unit/UI/instrumentation tests are run.
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_dir" ]]; then
    for candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        if [[ -d "$candidate/build-tools" ]]; then sdk_dir="$candidate"; break; fi
    done
fi
if [[ -z "$sdk_dir" || ! -d "$sdk_dir/build-tools" ]]; then
    echo "Set ANDROID_HOME or ANDROID_SDK_ROOT to the Android SDK." >&2
    exit 1
fi
# Only SDK/tool paths are inspected; local.properties and credentials are not read.
build_tools="$(python3 - "$sdk_dir" <<'PY'
from pathlib import Path
import re, sys
usable = [p for p in (Path(sys.argv[1]) / 'build-tools').iterdir()
          if (p / 'aapt').is_file() and (p / 'apksigner').is_file()]
stable = [p for p in usable if re.fullmatch(r'\d+(?:\.\d+)*', p.name)]
if not usable:
    raise SystemExit('Install Android SDK Build Tools (aapt and apksigner required).')
print(max(stable or usable, key=lambda p: tuple(map(int, re.findall(r'\d+', p.name)))))
PY
)"

cd "$project_dir"
./gradlew :app:assembleDebug -Pandroid.injected.testOnly=false --no-configuration-cache

# Use AGP's actual listing, which may point into intermediates after an IDE build.
source_apk="$(python3 - "$project_dir" <<'PY'
import json, sys
from pathlib import Path
root = Path(sys.argv[1])
redirect = root / 'app/build/intermediates/apk_ide_redirect_file/debug/createDebugApkListingFileRedirect/redirect.txt'
metadata = root / 'app/build/outputs/apk/debug/output-metadata.json'
if redirect.is_file():
    listing = next((line.split('=', 1)[1] for line in redirect.read_text().splitlines()
                    if line.startswith('listingFile=')), None)
    if not listing:
        raise SystemExit('AGP debug APK listing is missing.')
    metadata = (redirect.parent / listing).resolve()
content = json.loads(metadata.read_text())
if content.get('variantName') != 'debug' or len(content.get('elements', [])) != 1:
    raise SystemExit('Expected exactly one debug APK in the AGP output listing.')
apk = (metadata.parent / content['elements'][0]['outputFile']).resolve()
if not apk.is_file():
    raise SystemExit('Built debug APK was not found.')
print(apk)
PY
)"

manifest="$("$build_tools/aapt" dump xmltree "$source_apk" AndroidManifest.xml)"
test_only="$(printf '%s\n' "$manifest" | grep 'android:testOnly(' || true)"
if [[ -n "$test_only" ]] && ! printf '%s\n' "$test_only" | grep -Eq '=\(type 0x12\)0x0+[[:space:]]*$'; then
    echo "Refusing to publish a testOnly APK. Rebuild without IDE test injection." >&2
    exit 1
fi
"$build_tools/apksigner" verify --verbose "$source_apk"

canonical_apk="$project_dir/app/build/outputs/apk/debug/app-debug.apk"
if [[ "$source_apk" != "$canonical_apk" ]]; then
    mkdir -p "$(dirname "$canonical_apk")"
    cp "$source_apk" "$canonical_apk.tmp"
    mv "$canonical_apk.tmp" "$canonical_apk"
fi
printf 'Installable debug APK: %s\n' "$canonical_apk"
