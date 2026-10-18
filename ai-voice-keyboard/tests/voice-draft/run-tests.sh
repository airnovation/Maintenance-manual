#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT HUP INT TERM
src=app/src/main/java/com/airnovation/aivoicekeyboard
find tests/voice-draft/stubs -name '*.java' -print > "$out/sources"
printf '%s\n' "$src/VoiceDraftSession.java" "$src/VoiceDraftTransport.java" "$src/ApiKeyValidation.java" "$src/SafeApiErrors.java" \
    tests/voice-draft/VoiceDraftTransportTest.java >> "$out/sources"
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -d "$out/classes" @"$out/sources"
java -ea -cp "$out/classes" com.airnovation.aivoicekeyboard.VoiceDraftTransportTest
