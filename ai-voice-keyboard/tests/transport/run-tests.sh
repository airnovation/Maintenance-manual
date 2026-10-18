#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT HUP INT TERM
src=app/src/main/java/com/airnovation/aivoicekeyboard
# Only these production classes are compiled. Android/keystore and org.json are
# explicit host-only stubs; HTTPS and HTTP are intercepted in the test JVM.
find tests/transport/stubs -name '*.java' -print > "$out/sources"
printf '%s\n' "$src/AssistantSession.java" "$src/AssistantGemini.java" \
    "$src/AssistantResponsePolicy.java" "$src/GeminiAssistantTransport.java" \
    tests/transport/GeminiAssistantTransportTest.java >> "$out/sources"
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -d "$out/classes" @"$out/sources"
java -ea -cp "$out/classes" com.airnovation.aivoicekeyboard.GeminiAssistantTransportTest "$@"
