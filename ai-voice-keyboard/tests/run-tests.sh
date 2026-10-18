#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
src=app/src/main/java/com/airnovation/aivoicekeyboard
java -m jdk.compiler/com.sun.tools.javac.Main -d "$out" "$src/AssistantSession.java" "$src/AssistantCaptureDiagnostics.java" "$src/AssistantNodeFilter.java" "$src/AssistantGemini.java" "$src/AssistantResponsePolicy.java" "$src/AssistantAudioCache.java" tests/AssistantSafetyTest.java tests/AssistantCaptureDiagnosticsTest.java tests/CaptionBoundaryTest.java
java -cp "$out" com.airnovation.aivoicekeyboard.AssistantSafetyTest
java -cp "$out" com.airnovation.aivoicekeyboard.AssistantCaptureDiagnosticsTest
java -cp "$out" com.airnovation.aivoicekeyboard.CaptionBoundaryTest
python3 tests/check-boundaries.py

java tests/SyntaxCheck.java
# Actual lifecycle/transport classes under deterministic, host-only Android fakes.
# These exercise failure ordering without credentials, microphone or network access.
sh tests/recorder/run-tests.sh
sh tests/tts/run-tests.sh
sh tests/transport/run-tests.sh

# Actual Accessibility service metadata-only capture against synthetic nodes.
sh tests/accessibility/run-tests.sh
