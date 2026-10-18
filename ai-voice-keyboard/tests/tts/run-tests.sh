#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
src=app/src/main/java/com/airnovation/aivoicekeyboard
# Optional baseline path is for proving the regression test fails on the old actual panel.
panel=${TTS_PANEL_SOURCE:-$src/AssistantPanel.java}
find tests/tts/fakes -name '*.java' -print | sort > "$out/sources"
printf '%s\n' "$panel" "$src/AssistantSession.java" "$src/AssistantGemini.java" \
    tests/tts/AssistantPanelTtsTest.java tests/tts/AssistantPanelPreviewTest.java >> "$out/sources"
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -d "$out/classes" @"$out/sources"
java -cp "$out/classes" com.airnovation.aivoicekeyboard.AssistantPanelTtsTest "$@"

if [ "${1:-}" != "--regression-only" ]; then
    java -cp "$out/classes" com.airnovation.aivoicekeyboard.AssistantPanelPreviewTest
fi
