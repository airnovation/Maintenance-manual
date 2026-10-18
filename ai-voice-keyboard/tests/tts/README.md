# Deterministic AssistantPanel TTS regression tests

Run from the project root:

    sh tests/tts/run-tests.sh

This compiles the actual production `AssistantPanel.java`, `AssistantSession.java`,
and `AssistantGemini.java`. Small fake Android widgets, Handler, TTS, recorder,
visibility service, and transport supply controlled boundaries. Button clicks use
the production listeners; initialization can be delayed, duplicated, queued,
constructor-time, or delivered from another thread. The recorder and transport
fakes never open a microphone, write audio, read messages/credentials, or make a
network request. Unexpected capture, insertion, credential access, and transport
calls fail the test.

Coverage: 65 cases / 485 assertions, including the original same-session race;
record-start failure; start/stop/timeout; late success/failure; repeated taps;
explicit retry; stale session/snapshot; lock/editor/close/destroy; offline Chinese
voice selection; engine method exceptions/ERROR results; UI-thread dispatch;
stop-before-record ordering; and stop-failure shutdown fallback on teardown.
Two color-wiring scenarios vary fake light/dark defaults and check the actual
panel's opaque black text/white background across initial, not-started error,
and synthetic demo states, including the WhatsApp/WhatsApp Business prompt.
Demo interaction tests click actual panel buttons only after checking modeled
ancestor visibility: allowed editor -> Start -> visible Demo -> synthetic oral
reply -> repeated insertion refusal -> Close. They assert displayed preset text,
Chinese explanation, English-preview instructions, and English reply; zero
recorder/transport/insertion calls; and a visible refusal in a disallowed editor.

Thirteen local-diagnostic scenarios exercise the actual metadata-only button
listener with a synthetic count report. They cover visible output, one-shot
session cleanup, timer persistence, repeated clicks, missing service, unsupported
editor, legacy-work refusal, exceptions/empty reports, cancellation of old speech
and recording, and refusal to reuse diagnosis as reading consent or a reply draft.
The fake records calls and fails on message capture, credential access, Gemini
requests, audio uploads, and real insertion. These tests verify panel routing;
the real Accessibility metadata collector requires separate verification.

## Before/after control

Use an unmodified pre-fix AssistantPanel source file without changing the checkout:

    TTS_PANEL_SOURCE=/absolute/path/to/baseline/AssistantPanel.java \
      sh tests/tts/run-tests.sh --regression-only

Verified on 2026-10-18: the pre-fix panel (SHA-256
`27383422723993424a5884a8541b677677dabd90e45c302268359d5515574509`)
fails the first actual-panel test with exit code 1:

    late init must not speak after microphone starts; expected=0, actual=1

The fixed panel passes the same test and all 65 cases. The baseline is not copied
into this directory; `TTS_PANEL_SOURCE` points at a separate preserved snapshot.

## Limits

These are offline logic and lifecycle tests, not Android instrumentation or
acoustic tests. Color properties verify explicit production wiring; they do not
prove real-device light/dark theme rendering, accessibility contrast, or screenshots.
The fake view tree models visibility through parents; it does not lay out pixels
or prove real-device hit targets, clipping, window attachment, or touch delivery.
Fake stop success models an engine acknowledging stop; the tests
do not establish when real speaker output becomes inaudible, audio latency, echo,
OEM TTS behavior, installed Chinese voice availability, or microphone quality.
If stop fails for a ready engine, recording is refused. Teardown then attempts
shutdown, but real stop/shutdown failure cannot be claimed to guarantee silence.
Android API compilation and real-device controlled audio validation remain
separate checks. This runner uses Java 17 source/target syntax with the available
JDK compiler, not `--release 17` (the local JDK 21 has no `ct.sym`).
