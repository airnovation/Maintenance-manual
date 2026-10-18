#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
src=app/src/main/java/com/airnovation/aivoicekeyboard
find tests/compact-service/fakes -name '*.java' -print | sort > "$out/sources"
for name in VoiceKeyboardService VoiceDraftSession VoiceEditorBinding ApiKeyValidation SafeApiErrors; do printf '%s\n' "$src/$name.java" >> "$out/sources"; done
printf '%s\n' tests/compact-service/CompactServiceTest.java >> "$out/sources"
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -d "$out/classes" @"$out/sources"
java -cp "$out/classes" com.airnovation.aivoicekeyboard.CompactServiceTest
