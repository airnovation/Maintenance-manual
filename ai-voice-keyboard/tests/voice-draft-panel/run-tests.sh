#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
src=app/src/main/java/com/airnovation/aivoicekeyboard
find tests/voice-draft-panel/fakes -name '*.java' -print | sort > "$out/sources"
printf '%s\n' "$src/VoiceDraftPanel.java" "$src/VoiceDraftSession.java" tests/voice-draft-panel/VoiceDraftPanelTest.java >> "$out/sources"
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -d "$out/classes" @"$out/sources"
java -cp "$out/classes" com.airnovation.aivoicekeyboard.VoiceDraftPanelTest
