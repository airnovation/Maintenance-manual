# v0.19 compact voice input (v10-based isolated test)

This candidate begins with the exact v0.10 source at f36f5e27cc0f763dbfac7857cf7f5e6e7bdf767f. It is an independently packaged test release, not the original v10 APK. The original v10 release remains unchanged.

## Compact controls

- The first row is Mic / EN. Undo is removed without a replacement button.
- The second row remains Delete / Keyboard. No assistant panel or old/new mode switch.
- Mic starts a recording. Stop sends that recording and its transcription to the user's configured Gemini ordinary API for intent-aware drafting. Natural concise English is the default; spoken language, tone and length requests are supported by the prompt.
- After Stop, the result is inserted automatically only into the original still-current editor at the unchanged collapsed cursor. Review, edit or delete it in the host input field, then send yourself. There is no preview or confirmation button.
- EN preserves the v10 explicit typed-text translation workflow, with natural conversational English rather than stiff word-for-word translation. Negation, uncertainty, names, dates and commitments must retain their meaning; missing facts and acronym expansions must not be invented. Only tapping EN reads the current input text for translation; microphone drafting does not read host text or chat history.
- Undo is removed. Switch to the regular keyboard to type or edit normally.

## Privacy and limitations

No accessibility service, chat reading, OCR, background recording, automatic sending or Live API. Gemini uses the user's own device-configured key; the cloud tests use fake responses only. Audio is temporary and deleted after completion or cancellation. Password/non-text fields, selected ranges, changed cursor positions and stale editor sessions block voice insertion. Android editor metadata and selection callbacks are trusted; a host app that silently changes text without updating its editor state cannot be detected without reading its text, which this voice path does not do. Review dates, names, negation and acronyms; prompt tests do not establish real model accuracy.

API key values are validated locally before storage/request construction. Invalid values produce short fixed messages, never echoed secrets or raw header exceptions. Keys remain encrypted by Android Keystore. A saved key is sent only as authentication to its selected API provider.

## Verification and installation

Offline tests and Android compilation/lint passed; actual phone behavior and live Gemini language quality remain unverified. See ISOLATED_TEST.md for installation. This uses the existing .assistanttest package and a freshly approved one-off development certificate, with no production signing changes. Keep the original v10 keyboard. No new/old switch, preview or confirmation button is present.

Historical assistant classes remain inactive source; neither manifest registers Accessibility and the keyboard service does not construct those classes. No chat acquisition is reachable from the active UI.
