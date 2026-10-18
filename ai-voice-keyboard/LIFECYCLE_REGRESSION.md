# Offline lifecycle and ordinary-Gemini regression, 2026-10-18

This follow-up keeps the optional assistant scope and existing keyboard behavior.
It does not add a provider, Live API, UI redesign, permissions, signing route,
or real-message test. Long-term signing is deferred; distribution remains gated.

## Confirmed fixes

- Recorder finish: a native `release()` exception previously skipped the completion
  callback and temporary-file cleanup. Detach local state first, catch release
  failure, delete the unusable recording, and complete exactly once with an error.
- Read-aloud/recording race: an asynchronous TTS initialization callback could
  start speaking after the user had begun recording in the same assistant session.
  A separate explicit speech request is revoked before microphone startup and on
  teardown. Initialization, duplicate callbacks, engine failures and destruction
  are handled without reviving canceled speech. Stop failure blocks microphone
  startup; teardown attempts engine shutdown if stopping fails.
- Missing-key error callback: add the same active-session/token check used by other
  asynchronous transport callbacks. The panel already performed its own check;
  this is defense in depth, not evidence of a prior chat insertion or data leak.

## Verification approach

`sh tests/run-tests.sh` includes three dependency-free host regression suites:

- `tests/recorder`: compiles actual AssistantRecorder and AssistantAudioCache with
  deterministic Android fakes. The original class fails both release-exception
  regressions; the fixed class passes 24 scenarios / 396 assertions.
- `tests/transport`: compiles actual GeminiAssistantTransport and session/response
  policy classes. HTTP and HTTPS are fully intercepted as in-memory connections;
  other schemes are rejected. 38 scenarios, including five repeated
  successful runs. The missing-key stale-callback test fails before the guard.
- `tests/tts`: compiles the actual AssistantPanel with deterministic Android/TTS
  fakes. All 48 scenarios / 117 assertions pass; the original implementation
  fails the late-initialization-during-recording regression.

The transport suite covers no retry for quota/server errors, existing-model 404
fallback only, rejection of redirects, bounded response reads, simulated timeouts,
stale queued work/results, cancellation/disconnection and owned audio-file cleanup.
Its fake JSON DOM does not test JSON serialization/parsing correctness. Simulated
errors do not establish real network timing or Android audio-engine behavior.

The original 81 logic checks and structural/syntax checks are rerun, together with
7 distribution metadata tests and 10 user-operated signing-script mock tests.
No real API key, chat, audio device, upload, permission grant or signature is used.

## Remaining limitations

Actual WhatsApp layout compatibility, microphone interruptions/audio quality,
offline Chinese voices and native TTS failure behavior remain device checks.
Native `release`, `stop` or `shutdown` failures cannot guarantee platform resource
recovery; the application now fails closed and cleans its own state/files.
Legacy keyboard sources outside these assistant classes remain byte-identical to
the submitted baseline; that is a preservation check, not a new Mic/EN device test.
