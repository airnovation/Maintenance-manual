# Offline production-transport regression

Run from the repository root:

```sh
sh tests/transport/run-tests.sh
```

The runner compiles the **actual** `GeminiAssistantTransport`, `AssistantSession`,
`AssistantGemini`, and `AssistantResponsePolicy` production sources. It uses the
JDK compiler module, targets Java 17 source/bytecode, and needs no Android SDK,
Gradle download, network service, API key, device, or signing identity. The local
Java 21 runtime lacks `ct.sym`, so the runner uses `-source 17 -target 17` rather
than `--release 17`; this is a host regression, not Android/API type validation.

## Isolation and scope

- A fresh test JVM installs a `URLStreamHandlerFactory` before any request. Every
  HTTP/HTTPS connection is an in-memory `HttpURLConnection`. An unexpected
  attempt fails the test; other URL schemes are rejected. No code delegates to a
  real network handler or opens a socket.
- The test-only `SecureSettings` returns a clearly synthetic fixed string or an
  empty string. The production keystore/preferences class is not compiled or
  accessed. All messages and temporary audio bytes are invented fixtures.
- Minimal Android `Context`, `Looper`, `Handler`, and Base64 stubs supply only the
  methods needed by the transport. Main-thread callbacks are deliberately queued
  and drained by the test. Latches and executor barriers make cancellation tests
  deterministic, without sleeps. The executor is shut down and awaited at exit;
  the runner does not use `System.exit` to hide outstanding work.
- The `org.json` stubs are **fixture-backed DOM objects, not a JSON parser**.
  The String constructor maps an explicitly registered opaque response body to
  its prepared DOM. Request `toString()` is a host-only placeholder. Thus these
  tests do not validate JSON syntax, escaping, serialization, malformed JSON,
  production `org.json` behavior, or Gemini service compatibility. Response
  structure and policy branches are exercised against the prepared DOM.
- Timeout tests inject `SocketTimeoutException` at output acquisition/input read
  and assert the configured 15,000/40,000 ms timeouts. They do not establish
  wall-clock timeout behavior of Android's network stack.

## Covered behavior

The suite currently contains 46 scenarios and 693 assertions, including worker
executor termination:

- Local review before and after candidate capture rejects explanations, spoken
  replies, and transcription with zero key reads, request DOM construction,
  serialization, or HTTP attempts; denied temporary audio is deleted. The wrapper
  also rejects unapproved review. Counters live only in test stubs.
- The Session API robustness test permits only explicitly supplied selected content (including a synthetic edited-list input) in the fake
  request, excluding original/unselected candidates and later list mutations.
  The production UI is selection-only and offers no text-editing route.
  Empty, blank, oversized, duplicate, and stale approvals are rejected. Stale
  approved tokens cannot construct payloads or read keys; late success/error and
  queued completions are dropped after a replacement local review begins.
- All ordinary transport fixtures use explicitly approved local review sessions.
- Successful Chinese explanation and English reply routing; parts concatenate
  without exposing thought-marked parts; incomplete output is rejected.
- HTTP 400, 401, 403, 429, 500, 502, and 503 stop after one attempt and do not read
  or expose response error bodies.
- Only HTTP 404 advances through the existing three-model list: success on the
  second model, exhaustion, and 404 followed by 429 are covered. There is no
  provider switch or retry after another HTTP failure.
- HTTP 301, 302, 303, 307, and 308 fail without following the Location header;
  POST, bounded timeouts, endpoint, header routing, and disconnection are checked.
- Simulated connection/read timeout errors close resources and do not retry.
- Exactly 65,536 response bytes are accepted; larger responses fail. ASCII and
  multi-byte UTF-8 fixtures show that the limit applies to bytes, not characters.
  Reading stops within the first over-limit 4 KiB read buffer, and closes input.
- Session invalidation prevents queued requests from opening a connection,
  suppresses already-posted success/error callbacks, prevents payload output
  when invalidation happens at connection creation, and prevents 404 fallback
  after invalidation.
- Panel-style cancellation (`session.invalidate()` followed by `cancel()`)
  disconnects an in-flight request and suppresses both that callback and a queued
  stale request. This does not claim that `cancel()` itself invalidates a session
  or can recall bytes already transmitted.
- Synthetic transcription preparation deletes its temporary file before HTTP
  begins. Too-small/too-large files, cancelled queued audio, stale processing
  errors, and cancellation during transcription all clear files/references and
  suppress stale callbacks as appropriate. No microphone or audio codec is used.

## Reproduced defensive fix

The missing-key error previously queued a callback without rechecking the session
and token. A deterministic regression failed against the original production
source and passed after adding the same guard used by network completion:

```sh
sh tests/transport/run-tests.sh missing-key
```

The current-session missing-key error still fires once. `AssistantPanel` already
rechecked the token before accepting results, so this is transport-layer defensive
consistency, not evidence of an actual insertion or private-data leak. Existing
synchronous validation-error semantics are unchanged.
