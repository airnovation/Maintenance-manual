# Compact automatic-insertion service regression tests

Run `sh tests/compact-service/run-tests.sh` from any directory. Java 17-compatible host tests use no Android device, microphone, account, real API key or network request.

The production `VoiceKeyboardService`, `VoiceDraftSession`, `VoiceEditorBinding`, `ApiKeyValidation` and `SafeApiErrors` run against deterministic Android, recording, translation and voice transport fakes. Coverage includes:

- Only Mic / EN and Delete / Keyboard. No preview, confirmation, revision or fallback-insert control; bounded two-line status; no extracted fullscreen editor.
- Mic records locally. Explicit Stop uploads and successful intent drafting inserts exactly once into the originally bound editor. No automatic Send action.
- Voice never reads extracted/selected host text. Initial selection metadata must identify a collapsed cursor. Selected or unknown cursor positions cannot start capture.
- Any selection, cursor, composition or same-offset update while recording or waiting for the provider cancels the operation. Lifecycle, identity, lock, dismissal and connection checks also invalidate old results.
- Finalize any existing composing span only immediately before voice insertion, preserving preexisting text. The only accepted synchronous callback is this locally initiated composition clear at the exact same old/new collapsed cursor in the same editor. There is no timer or blanket next-callback exemption.
- Known and initially unknown composing text, synchronous clear, changed cursor/range/composition/lifecycle, queued clear/commit updates, failed finalization and uncertain commits are covered.
- Duplicate/stale results, malformed outputs, no implicit revision, API key/provider guards, recording errors, cancellation and the 60-second no-upload limit.
- EN still reads explicitly requested selected/full editor text and replaces it only if its source and cursor remain unchanged. EN finalizes composition before reading the authoritative source and again before replacement. Changed or failed EN results have no fallback UI.
- Synchronous editor changes during extraction, selection, finalization, commit or Delete cannot repopulate a new editor or redirect a fallback Delete action.

## Observation boundary

Voice observes Android editor metadata and selection/lifecycle callbacks, not host text. An app that silently changes text or selection while keeping the same editor connection and emitting no callback cannot be detected without violating the no-host-read boundary. These guards therefore rely on the host following Android input-method contracts; they do not promise detection of every possible silent host edit. Editing during provider processing is intentionally conservative and may require a fresh recording.

## Remaining device checks

This is behavioral boundary coverage, not actual Android rendering or a live provider test. Keyboard size, navigation insets, real callback ordering, composition handoff from Samsung/Gboard, microphone behavior, provider output quality and app-specific editor compatibility still need phone testing. No signing or publication occurs in this suite.
