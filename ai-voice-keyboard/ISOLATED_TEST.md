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
