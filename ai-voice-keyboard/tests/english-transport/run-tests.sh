#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT HUP INT TERM
src=app/src/main/java/com/airnovation/aivoicekeyboard
find tests/voice-draft/stubs -name '*.java' -print > "$out/sources"
printf '%s\n' "$src/ApiKeyValidation.java" "$src/SafeApiErrors.java" \
    "$src/EnglishRequest.java" "$src/EnglishRewritePolicy.java" \
    "$src/GeminiEnglish.java" "$src/OpenAiEnglish.java" \
    tests/english-transport/EnglishTransportTest.java >> "$out/sources"
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -d "$out/classes" @"$out/sources"
java -ea -cp "$out/classes" com.airnovation.aivoicekeyboard.EnglishTransportTest
