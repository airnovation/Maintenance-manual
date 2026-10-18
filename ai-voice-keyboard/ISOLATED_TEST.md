## v0.15: bounded Android drawing-order visibility fix

This build uses reported ancestor-branch drawing order to avoid treating an
explicitly lower ordinary leaf View as a foreground obstruction. Unknown,
equal or higher branch order remains blocked. It preserves the message-ID,
editor, clipping, external-window and session checks.

This trusts the ordinary foreground WhatsApp/Business accessibility hierarchy,
not final screen pixels. Independently composed or custom surfaces can report
generic View metadata; see LAYER_ORDER_FIX.md. Device success is not yet verified.

Replace only the old Isolated Test app because this approved release uses a
fresh ephemeral development certificate. Keep the original keyboard installed.
For normal assistant use, configure Gemini on the device and explicitly start
the assistant in a WhatsApp/Business message editor. Local diagnostics still
need no Gemini key and never read message text.

## v0.14: content-free overlap relationship diagnostics

This adds bounded category counts to the existing “本机诊断（不读文字）” action. It distinguishes same/different parent, relative drawing order, reported element category and overlap-area band. Only nonzero fixed codes and numbers are displayed. The diagnostic never reads message text or screenshots. Raw resource IDs, names and coordinates are not displayed, stored or uploaded; existing node metadata is used locally only to calculate the fixed categories. Capture and visibility decisions are unchanged. This is still a diagnosis, not a claim that message reading has been repaired.

No Gemini key is needed for this test. Enable the new isolated keyboard and its Accessibility service, open the WhatsApp Business chat input, tap “启动助手” ONCE to expand the controls, then tap “本机诊断（不读文字）”. Do not tap “同意并读取”. Share only the lower diagnostic-number area, especially the new “遮挡关系” line; do not include the conversation above.

This fresh one-time certificate cannot replace the previous test app in place. Replace ONLY “AI Voice Keyboard - Isolated Test” if needed; keep the original keyboard. There is no automatic uninstall. The temporary signing key is removed in CI; no private key or password is distributed.

## v0.13: local capture diagnosis

Adds “本机诊断（不读文字）”, a separate metadata-only action. It displays counts for recognized message nodes and visibility/filter stages. It never calls node getText, Gemini, microphone or insertion; the temporary session is invalidated immediately. Ordinary assistant operation is unchanged.

For this diagnosis, no Gemini API key is needed. Manually enable the new isolated keyboard and its Accessibility service, open the WhatsApp Business message input, choose this keyboard, open the assistant panel, then tap “本机诊断（不读文字）”. Share only the lower diagnostic-number region, not the conversation above it.

This is a diagnostic build, not a claim that real WhatsApp message extraction has been repaired. No caption ID or broad text fallback was added.

The new one-time certificate cannot update the previous isolated test APK. Replace only “AI Voice Keyboard - Isolated Test” if Android reports a signature conflict, keep the original keyboard. No app is removed automatically. Gemini settings are needed only when returning to the ordinary online assistant features.

## v0.12: WhatsApp Business compatibility

Fixes the v0.11 start gate that rejected WhatsApp Business. Exactly the standard and Business packages are allowed, with package-matched resource IDs and source-bound sessions. Unknown layouts still refuse capture; actual Business device layout validation is pending.

This release uses a NEW one-time test certificate. It cannot install over the previous isolated test app. If Android reports a signature conflict, remove ONLY “AI Voice Keyboard - Isolated Test”, keep the original keyboard, and install this APK. Re-enter Gemini settings and manually enable the new test app’s keyboard/Accessibility permissions. No API key belongs in GitHub or chat.

# AI Voice Keyboard - Isolated Test

This is an explicitly requested, side-by-side development-signed test APK.
Application ID: `com.airnovation.aivoicekeyboard.assistanttest`.
It does not replace or require uninstalling the existing AI Voice Keyboard.
It is not a production release or a promise of future in-place updates.
The one-off signing key is discarded after this build; future test builds may
require uninstalling only this independent test app. Keep the original keyboard.
The installed app is not debuggable. Its public certificate fingerprint and APK
SHA-256 are attached as metadata. No signing key or password is published.

## Setup on your phone

1. Install only the APK from this release. Keep the original keyboard installed.
2. Open **AI Voice Keyboard - Isolated Test**. Select Gemini and enter your own
   Gemini API key on the phone. The new app has separate private settings; it
   cannot import the original keyboard's key. Never send the key in chat.
3. Enable/select **AI Voice Keyboard - Isolated Test** in Android keyboard settings.
   Grant microphone access only if you want voice input.
4. For the optional assistant, manually enable **Input Assistant - Isolated Test**
   in Accessibility settings. Android's permission is broad; app filtering only
   reads supported currently visible WhatsApp messages after explicit session
   consent. The assistant starts off. Do not bypass unexpected security warnings.
5. Start with invented, non-sensitive test messages. Review explanations and
   English previews. Insertion requires two explicit taps; sending stays manual.

Normal Gemini API use may consume your own Gemini quota. No Live API, history,
notification reading, background capture, scrolling or automatic Send is added.
Chinese read-aloud requires an installed offline Chinese voice. Real WhatsApp
layouts, device microphone quality and TTS behavior still require phone testing;
synthetic emulator/host tests and Android CI do not establish these device results.
You can switch back to the original keyboard at any time.
