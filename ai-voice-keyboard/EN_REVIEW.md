# EN meaning and cancellation review patch — not a released build

## Authorized validation branch

After the local review stage documented below, the user authorized publishing this patch to a new, independent validation branch and running Android validation only. `validate-en-review.yml` targets only `validation/en-meaning-cancellation-20261028`, uses read-only repository permissions, and disables signing for the isolatedTest build solely through a temporary Gradle init script. It verifies the unchanged `.assistanttest` identity and retains an unsigned, non-installable APK plus reports. It cannot publish a release or install the application. The original prototype/main branches and both release workflows are unchanged. CI results must be checked for the exact new commit; this note does not assert they passed.

## Original local review stage — source and scope

- Repository: `airnovation/Maintenance-manual`
- Branch: `prototype/ai-voice-keyboard`
- Verified base: `919bde4eddeb9b2726690e90ce727f40a23b7334`
- Source version: `0.20-compact-theme`; existing isolated package `.assistanttest`.
- The installed phone version/package/provider remains unknown. This is a review patch against the verified branch, **not confirmation of what is installed**.
- Local cloud edits only. No push, PR, build distribution, signing, release, user-device installation or live provider call. No actual API key was read. Version, package, models/order, timeouts, manifests and release workflows are unchanged.

## Changes

1. Both EN providers share `EnglishRewritePolicy`. Meaning and interpersonal intent precede brevity. Existing direct address (including 老板 → Boss), greetings, thanks, speaker/recipient relationships, suggestion strength and each step/condition must survive. Fillers may be removed, but friendly does not authorize invented greetings, promises or facts.
2. Each EN call returns an `EnglishRequest`. Keyboard resets cancel the active request; connection disconnection and worker interruption are best effort. Already uploaded content cannot be recalled. Cancel-side socket closing runs off the IME thread on at most two cleanup workers with no backlog. If those cleanup workers are stuck, cancellation does not block or create more threads; the original interrupted worker retains its finally/timeout cleanup. Pending main-thread callbacks are suppressed after cancellation. Service generation/editor/text/selection checks remain in place independently.
3. Each provider uses at most two daemon workers with **zero backlog**, instead of an unbounded single-worker queue. A canceled network call that has not unwound cannot queue the next request behind it. If both workers remain occupied, a fixed busy error rejects another request rather than creating unlimited workers or a backlog. This is a resilience change, not measured evidence of faster model inference.
4. Content-free, monotonic `EnglishRequest.Timing` exposes only provider/outcome enums, attempt count and queue/request/callback-wait/total milliseconds. Snapshots are in memory via `timing()` / `lastTiming()`, with no text, key, URL, raw exception, disk telemetry or network reporting. Last timing is process-wide and can be replaced by another terminal request. Total ends at cancellation or callback delivery, **not editor insertion or manual sending**. Request time includes the HTTP attempt setup/write/read and aggregate fallback attempts; it does not separate network latency from model processing. This patch adds no export UI. The existing isolated build is non-debuggable, so real-device collection would require separately authorized diagnostic instrumentation/build rather than assuming a debugger can attach.

No Mic prompt, recording behavior, host-text reading boundary or manual-send behavior was changed.

## Verification

Run from `ai-voice-keyboard/`:

```sh
sh tests/run-tests.sh
python3 -m unittest discover -s tests -p 'test_*.py'
python3 -m unittest discover -s tools -p test_user_setup_signing.py
java tests/SyntaxCheck.java
```

The EN lifecycle suite uses actual service code with fake providers and intentionally late callbacks; the transport suite uses actual provider clients with synthetic HTTP/Android/JSON support. Policy contract tests and `tests/english-policy/fixtures.json` are **not live translation quality tests**. The fixture references are illustrative human-authored alternatives, not recorded model output or exact-match gold standards.

Android Gradle compilation, Android lint and APK build need the project-required Gradle 8.11.1 / Android SDK 35 environment. Neither Gradle, an Android SDK nor a wrapper is present in this cloud workspace. A Java syntax parse and fake-based host tests do not replace Android API type checking, an emulator or actual-device QA.

## Remaining verification before any release

- Confirm installed version/package and selected provider, without sharing any key value.
- In an authorized Android build environment, run compile/lint and unit checks; do not invoke publishing/signing workflows merely to validate this patch. The existing branch push workflow includes distribution/release steps: a local review patch is not permission to push it there.
- On an explicitly authorized test device/build, exercise EN reset/new EN, switching editor/keyboard, repeated taps and edited text while waiting. Check that the original text is preserved whenever replacement is unsafe. Keep the current keyboard installed until identity/signing compatibility is established.
- Only with separate permission for live provider usage: review all 12 synthetic semantic fixtures against their preservation criteria, then collect content-free timings on normal and delayed cases. Actual model quality and the user's intermittent delay cause remain unverified. Do not attribute measured total duration solely to model “thinking.”
