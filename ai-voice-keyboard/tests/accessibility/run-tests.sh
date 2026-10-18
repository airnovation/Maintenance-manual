#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
src=app/src/main/java/com/airnovation/aivoicekeyboard
# Optional override supports mutation/regression checks against an actual service source.
service=${ACCESSIBILITY_SERVICE_SOURCE:-$src/VisibleMessageService.java}
find tests/accessibility/fakes -name '*.java' -print | sort > "$out/sources"
printf '%s\n' "$service" "$src/AssistantSession.java" "$src/AssistantNodeFilter.java" \
    "$src/AssistantCaptureDiagnostics.java" tests/accessibility/VisibleMessageServiceTest.java >> "$out/sources"
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -d "$out/classes" @"$out/sources"
java -cp "$out/classes" com.airnovation.aivoicekeyboard.VisibleMessageServiceTest "$@"
