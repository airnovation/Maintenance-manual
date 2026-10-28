# Offline EN transport lifecycle regression

Run `sh tests/english-transport/run-tests.sh` from the project root. It compiles
actual GeminiEnglish, OpenAiEnglish, EnglishRequest and the shared rewrite policy.
Every URL is intercepted by an in-memory fake; no real credential, socket, provider,
Android device or paid request is used. Existing host Android and fixture JSON DOMs
come from `tests/voice-draft/stubs`; they are not a JSON parser or wire-format test.

The 22 cases exercise both providers: cancellation before worker start, cancellation
before connection attachment, suppression after a response is posted to the main
thread, incomplete responses, invalid keys, safe timeout errors and fixed 429 errors.
Gemini's existing model order and 404-only fallback/exhaustion remain covered.

A deliberately uncooperative response fake ignores interrupts and disconnects.
After cancelling it, the next EN request completes on the second bounded worker
without waiting for the old socket; the late old callback/404 fallback is suppressed.
A second uncooperative socket exhausts the two workers: the next request gets a fixed
retry message instead of being queued. Once the old workers close, a fresh request
succeeds. A separate blocked-disconnect fake proves cancel returns without blocking
the caller, and cleanup is limited to two workers with no backlog. Saturated cleanup
falls back to the interrupted request worker's normal finally/detach path; it never
runs socket closure on the IME thread or spawns unlimited threads.

Cancellation is best effort, not a hard network deadline. It cannot recall uploaded
bytes. Android/socket implementations can ignore interruption or block inside close;
finite connect/read timeouts are unchanged. If both EN workers remain occupied, the
user receives the bounded retry message until capacity returns. Actual Android socket
behavior, UI responsiveness and live-provider latency still need authorized device QA.

## Content-free timing

`EnglishRequest.timing()` exposes an immutable snapshot and
`EnglishRequest.lastTiming()` retains only the latest completed/cancelled snapshot
in process memory. Fields are provider/outcome enums, attempt count, queue milliseconds,
aggregate request milliseconds, callback-wait milliseconds and total milliseconds,
measured using `System.nanoTime()`. Request time covers each attached connection through
its release (including response parsing); total ends at cancellation or callback
acceptance, not completion of editor insertion. No key, input/output, URL, raw error,
conversation identifier or text length is recorded, and no logging, upload or disk
persistence is added. These values are for debugger/test diagnostics, not model-quality
or real-device speed claims. Tests enforce the enum/numeric-only shape and timing bounds.
