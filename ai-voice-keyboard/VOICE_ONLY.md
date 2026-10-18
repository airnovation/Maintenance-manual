# v0.18 voice-only intelligent drafting

This approved isolated test release replaces the active chat-reading assistant UI with voice drafting. It uses a fresh one-off development certificate; preserve the original keyboard and replace only the old Isolated Test app. Earlier releases remain available.

## Flow

- Configure the existing Gemini provider/key on the device and grant microphone permission manually.
- Open a normal text editor. Press **开始说话**, say the content and optional language/tone/length instruction, then press **结束并整理**.
- The recording is transcribed, then the transcript is sent in a separate ordinary Gemini request to produce a draft. The screen shows both the recognized words and the proposed message.
- Default is natural concise English. An explicit request such as “用中文” overrides it. Names/acronyms such as AJB/PSA and relative dates must not be guessed or expanded. Missing essential content or conflicting instructions should produce a clarification request, not an insertable guess.
- Press **确认填入** only after checking the draft and current editor. This commits at the current cursor/selection; it never sends the message.
- **修改上一稿** applies to an uninserted local draft and is the only action that attaches the previous RAM draft to a new request. A fresh recording has no previous-draft context. There is no reading of the current chat draft for the new voice path.
- **取消**, lock, editor lifecycle changes and destruction cancel recording/work and clear local draft state. The 60-second limit cancels without automatically uploading. A request already sent cannot be retracted.

## UI and existing keyboard

The new voice controls are primary. Legacy **Mic / EN / Undo** remain under **旧版**, with the existing provider choice and translation behavior. In particular, legacy Mic still auto-inserts a successful translation, whereas the new flow requires explicit confirmation. Switching into an old action cancels the new voice operation. The keyboard-switch and Delete controls remain available.

Android fullscreen/extracted-editor mode is disabled, including landscape. The preview is read-only; it does not invoke another keyboard or edit the host draft while reviewing. It has bounded adaptive height and scrolls internally. Large-font/landscape/touch behavior still requires device validation.

## Privacy and safety boundaries

Both manifests remove the accessibility-service registration. No chat reader, diagnostic panel, automatic message acquisition, OCR, history, notifications or Live API is reachable from the new UI. Legacy chat-assistant code remains solely as inactive historical source/regression coverage; the IME no longer constructs it.

New provider payloads contain only this explicitly submitted audio, its transcript, and optionally the explicitly selected previous RAM draft. Only the configured Gemini key is used, without vendor switching. Audio is limited to 2 MiB, transcripts to 3,000 characters and drafts to 4,000. Response completion, shape, size, HTTP and cancellation checks reject partial or malformed results. No real user audio, credentials or chat data is used in cloud tests.

The insertion boundary binds IME generation, package, field ID, input type and InputConnection identity. It rechecks the requested-visible/unlocked/non-password editor state and connection immediately before commit. Android isInputViewShown reports the requested IME state rather than proof of physical visibility; device focus behavior remains a separate validation step. Delayed responses cannot be accepted after cancellation or replacement. An uncertain commit exception invalidates the binding, preventing blind repeat insertion. The new insertion path clears any old Undo record without reading the host text; legacy Undo behavior for legacy actions is retained.

Host tests validate implementation boundaries with synthetic data. Prompt tests do not prove the model will always separate instructions correctly or preserve every spoken detail; transcription and draft must be reviewed, especially names, dates, amounts, negation and commitments. Real Gemini, audio capture and device rendering are separate tests.
