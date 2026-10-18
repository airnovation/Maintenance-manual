#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -d "$out" app/src/main/java/com/airnovation/aivoicekeyboard/VoiceEditorBinding.java tests/voice-editor/VoiceEditorBindingTest.java
java -cp "$out" com.airnovation.aivoicekeyboard.VoiceEditorBindingTest
