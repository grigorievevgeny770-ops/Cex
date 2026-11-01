#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/check-architecture.py
./gradlew test lint assembleDebug
if [[ "${CONNECTED_TESTS:-0}" == "1" ]]; then
  ./gradlew connectedDebugAndroidTest
fi
