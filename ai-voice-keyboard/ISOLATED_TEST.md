# v0.19: compact voice input, based on v10

Only four keyboard controls: **Mic / EN**, **Delete / Keyboard**. Undo, extra panels, new/old switches, preview and fill-confirmation buttons are removed.

## Install without replacing the original v10

This is **AI Voice Keyboard - Isolated Test**, package `com.airnovation.aivoicekeyboard.assistanttest`, version `0.19-compact-voice-isolated-test` (code 19). It uses a newly approved one-off development certificate. Replace ONLY the previous Isolated Test app; its different signature prevents an in-place update. Keep the original **AI Voice Keyboard v0.10** installed. No app is removed automatically, and no original release is overwritten.

After replacing the prior test app, enable/select this test keyboard, allow its microphone, select Gemini and enter your own Gemini API key privately on the phone. Never send the key in chat. Accessibility permission is not needed. The original v10 settings cannot be imported automatically.

## Use

- Put a collapsed cursor in an ordinary text field. Tap **Mic**, speak your message and any language/tone/length request, then tap **Stop**.
- Gemini transcribes and organizes the meaning. Default output is natural English; say “用中文”, “客气一点” or “简短一点” when needed.
- The result is inserted automatically once into that unchanged input position. Check it in the app, edit or delete if needed, and send manually. It never presses Send.
- Cursor changes, selections, editor changes, lock, cancellation or stale responses block voice insertion. **Keyboard** cancels and returns to your usual keyboard. The 60-second recording limit cancels without uploading automatically.
- **EN** translates the selected/current input text into natural conversational English, preserving facts, negation and uncertainty. It retains the explicit v10 typed-text replacement workflow. **Delete** remains backspace.

Audio and its transcript go only to the device-configured Gemini ordinary API after Stop; EN uses the selected existing provider. No chat history, accessibility capture, OCR, background listening or Live API. Requests may use your own provider quota. No API key or signing private key is included in the APK. Invalid pasted keys now produce short safe messages rather than header-value error text.

Offline synthetic tests and Android build/lint passed. Real Gemini wording and phone/OEM behavior still need validation. Android editor metadata/callbacks are trusted; silent host edits without such signals cannot be detected without reading host text, which the voice path does not do. Review names, dates, uncertainty and commitments before sending.

The installed test APK is not debuggable. Its public certificate and SHA256 are attached. The ephemeral CI signing material is removed after the build; future tests may again require replacing only this isolated app. Production stable-signing gates are unchanged.
