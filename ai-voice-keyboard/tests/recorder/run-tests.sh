#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
src=app/src/main/java/com/airnovation/aivoicekeyboard
find tests/recorder/fakes -name '*.java' -print > "$out/sources"
printf '%s\n' "$src/AssistantRecorder.java" "$src/AssistantAudioCache.java" \
    tests/recorder/AssistantRecorderTest.java >> "$out/sources"
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -Xlint:-options \
    -d "$out/classes" @"$out/sources"
java -cp "$out/classes" com.airnovation.aivoicekeyboard.AssistantRecorderTest
