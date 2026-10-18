# Offline recorder lifecycle regression tests

Run from the repository root:

    sh tests/recorder/run-tests.sh

Requires Java 17+ with the `jdk.compiler` module; no separate `javac` command,
Android SDK, Gradle, JUnit, device, microphone, permission grant, credentials,
or network access is used.

The runner uses Java 17 source/target compatibility. It intentionally does not
require `--release 17`, because some stripped JDK runtime images include the
compiler but omit older platform signatures. Android API type checking remains
a separate build check.

The harness compiles the **actual production** `AssistantRecorder.java` and
`AssistantAudioCache.java`. Only the required Android interfaces are replaced
by small test fakes. `MediaRecorder` writes invented bytes into an isolated
temporary directory; its prepare/start/stop/release stages can fail independently.
`Handler` uses a deterministic virtual monotonic clock and removes callbacks by
object identity, so deadline and repeated-start tests do not sleep.

Coverage includes permission denial/revocation, unavailable cache, partial startup
failures, stop and release failures (including combined failures), the 500ms
minimum duration, success and file-ownership transfer, repeated cancellation,
60-second cancellation and timer deduplication, callback reentry, private-file
cleanup and preservation of unrelated cache files. A release failure during
finish is treated as a failed recording: completion still happens once, no file
is returned, the local recording state is cleared, and temporary audio is deleted.

These are host-side lifecycle tests. They do not establish Android device/audio
behavior or guarantee that native recorder resources can be recovered if the
platform's release implementation itself fails. Real microphone interruptions,
Android permission UI, and audio quality still require separately authorized
device validation.

## Regression verification

On the original recorder implementation, 22 of 24 scenarios passed. Both finish
scenarios with an injected release failure threw out of `finish()` before
completion. With the cleanup fix, all 24 scenarios and 396 assertions passed.
