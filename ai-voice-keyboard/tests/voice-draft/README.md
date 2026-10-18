# Offline voice-only session and transport regression

Run:

```sh
sh tests/voice-draft/run-tests.sh
```

The runner compiles the actual new production `VoiceDraftSession` and
`VoiceDraftTransport` classes. It does not compile or invoke the old
accessibility/context assistant, any editor insertion code, an Android microphone,
a real keystore, or a provider SDK.

## Isolation

- A fresh host JVM replaces all URL handlers before any transport call. Every
  HTTP(S) request is an in-memory `HttpURLConnection`; unexpected requests fail.
  There are no sockets, credentials, device interactions, real recordings, or
  real provider calls. All bytes, text, and keys are clearly synthetic fixtures.
- Android Context, Base64, Looper, Handler and SecureSettings are isolated host
  stubs copied into this suite. UI callbacks are queued and drained explicitly.
  Latches and executor barriers exercise races without sleeps. Both executors are
  shut down and awaited after the test; the test never hides running work with
  `System.exit`.
- The org.json stubs are fixture-backed DOM objects, **not a JSON parser**.
  Registered opaque strings map to prepared response DOMs. Request serialization
  is a host-only placeholder. Unregistered-response rejection and wrong DOM types
  test fail-closed control flow, not real JSON parsing, escaping, wire syntax,
  provider response-schema support, or Android org.json compatibility.
- Source and bytecode target Java 17 through the local JDK compiler module. The
  environment's JDK lacks ct.sym, so this uses `-source 17 -target 17`, not
  `--release 17`. It is not an Android API build.

## Coverage

The suite has 75 scenarios and 853 assertions:

- Per-panel RAM state, fresh-start clearing, explicit revision retaining only its
  accepted draft, stale token rejection, invalidation clearing, and atomic bounds
  checks. A fresh request rejects any supplied prior draft; revision rejects an
  absent, empty, or substituted base before reading a key or sending anything.
- Two successful stages: verbatim audio transcription, then intent drafting from
  only that transcript and, exclusively for explicit revision, the matching prior
  draft. Source audio is deleted before the first HTTP upload. Thought-marked
  response parts are excluded. Transport does not itself mutate panel state.
- Prompt/data separation and prompt requirements for default English, explicit
  Chinese/mixed-language overrides, tone/length, quoted language requests,
  command-only and contradictory-language clarification, AJB/PSA/names/place
  preservation, relative dates/times, negation, no AM/PM/timezone invention, and
  no action execution. Both requests omit deprecated sampling controls.
- Structured draft response has exactly a string `draft` and boolean
  `needs_clarification`. Clarification, extra fields, wrong types, missing/invalid
  fixture DOMs, empty content, and oversized text cannot become a draft. A safe
  error can retain a completed transcript for review.
- Both stages require an exact `STOP` finish reason. Truncated, blocked, unknown
  and empty finish reasons fail closed, even when candidate text is present.
- HTTP 400/401/403/429/500/502/503 and 301/302/303/307/308 fail without reading
  error bodies or following redirects. Only 404 permits same-provider fallback
  through the existing allowlisted Gemini model sequence; no provider switch.
- POST, JSON content type, API key in a header rather than URL, and configured
  15-second connection / 40-second read timeouts. A 90-second whole-operation
  deadline disconnects outstanding work and returns at most one safe error.
  Deadline handling is triggered deterministically, and socket timeouts are
  simulated; this does not prove wall-clock behavior of Android networking.
- 2 MiB audio bounds, 64 KiB response-byte bounds (including UTF-8), 3,000-character
  transcript and 4,000-character draft bounds, file cleanup, and closed streams.
- Local API-key validation rejects null, malformed, whitespace/control/non-ASCII and copied diagnostic input before HTTP; a synthetic header exception cannot expose the key.
- Stale entry, queued invalidation, missing-key late callbacks, cancel during
  either stage, cancel after posting a result, invalidation before output,
  between stages or before 404 fallback, and replacement-operation races.
  `cancel()` suppresses work even without session invalidation. Neither cancel
  nor invalidation can recall bytes already uploaded.

## Remaining validation

Prompt assertions confirm that the intended instructions are sent and routing is
bounded. They **do not demonstrate that a model always follows them**, preserves
proper names, recognizes quoted versus spoken commands, or produces an accurate
transcript. These cases still need explicit real-device/provider testing with
non-sensitive consented recordings, including mixed Chinese/English/Malay speech,
noise, unknown names, revisions, interrupted recording, and host-editor changes.
A real Android compile, UI/lifecycle checks, request serialization/provider
compatibility, and manual insertion behavior are separate verification layers.
