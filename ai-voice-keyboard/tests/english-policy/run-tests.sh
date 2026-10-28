#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT HUP INT TERM
src=app/src/main/java/com/airnovation/aivoicekeyboard
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -d "$out/classes" \
    "$src/EnglishRewritePolicy.java" tests/english-policy/EnglishRewritePolicyTest.java
java -ea -cp "$out/classes" com.airnovation.aivoicekeyboard.EnglishRewritePolicyTest
python3 - <<'PY'
import json
from pathlib import Path
src=Path('app/src/main/java/com/airnovation/aivoicekeyboard')
for provider in ('GeminiEnglish', 'OpenAiEnglish'):
    body=(src/(provider+'.java')).read_text()
    assert 'EnglishRewritePolicy.INSTRUCTION' in body, provider + ' must use the shared policy'
    assert 'Use a few words when a few words are enough' not in body
fixtures=json.loads(Path('tests/english-policy/fixtures.json').read_text())
assert len(fixtures)==12
assert len({f['id'] for f in fixtures})==len(fixtures)
for f in fixtures:
    assert f['source'] and f['reference'] and f['must_preserve']
print('PASS shared provider policy wiring + 12 human-review fixture schemas; no model outputs tested')
PY
