#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 tests/check-compact-boundaries.py
sh tests/api-safety/run-tests.sh
sh tests/voice-draft/run-tests.sh
sh tests/voice-editor/run-tests.sh
sh tests/recorder/run-tests.sh
sh tests/compact-service/run-tests.sh
