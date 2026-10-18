# Voice draft panel host tests

Run `sh tests/voice-draft-panel/run-tests.sh` from the project root.

The tests compile the actual `VoiceDraftPanel` and per-instance `VoiceDraftSession`
against deterministic Android views plus recorder/transport spies. No real key,
microphone, messages, accessibility service, network, or input connection is used.
Synthetic files exercise temporary-audio ownership and stale callback deletion.
The suite covers explicit capture/upload/insertion, fresh and revision payload
separation, lifecycle clearing, provider/editor gates, duplicate clicks, and
cancelled callbacks. It does not replace on-device UI/audio checks.
