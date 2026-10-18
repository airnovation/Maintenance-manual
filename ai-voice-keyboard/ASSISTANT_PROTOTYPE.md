# Input assistant: unpublished local functional candidate

Base: f36f5e27cc0f763dbfac7857cf7f5e6e7bdf767f, prototype/ai-voice-keyboard, v0.10.
Local candidate version: 0.11-assistant-prototype / versionCode 11.
No remote write, APK release, phone action, credentials creation, or real API test was performed.

## Implemented source paths

1. Optional assistant starts OFF. “启动助手” first displays a prominent per-session disclosure. Only a separate “同意并读取” press can start reading/upload. No persisted consent or background session restart.
2. Explicit capture through the user-enabled Accessibility service obtains the unique focused WhatsApp application window (the own IME may be the active touched window). It rejects locks, magnification, picture-in-picture, clearly non-fullscreen geometry, non-WhatsApp, unsupported layouts, and screens without the expected focused message editor. The interactive-window flag is required for window metadata and the single focused application root; code enumerates only window geometry/layers to exclude higher-layer occlusion (including the IME); it never obtains other window roots/text, and missing geometry fails closed. It reads only visible, fully-contained, non-editable/non-password message_text nodes, sorted by screen position. Ancestor clipping is intersected down the tree; messages must be inside a recognized conversation-list class. Non-ancestor same-window UI rectangles are conservatively treated as occlusion, including transparent siblings that may cause safe refusal. Only after metadata checks is message text accessed. Text is capped at 12 messages/3000 characters; reaching 250 traversed nodes fails closed because the overlay inventory would be incomplete. No screenshots, attachments, notification payload, scrolling, or older-history navigation.
3. Ordinary Gemini REST generateContent creates concise Chinese explanation. It uses the device's existing Gemini key and the existing keyboard's Gemini model list. It requires Gemini selected, never switches to OpenAI, adds no credentials and performs no Live API/streaming call. Requests have bounded prompts/output, timeouts, no redirects, no tools, no content/key logs, and treat group content as untrusted data. Only finishReason=STOP with nonempty bounded text is accepted; truncated/blocked/unknown finishes cannot become insertion-eligible drafts.
4. Chinese speech uses Android TTS only after an explicit tap, selecting a Chinese voice whose isNetworkConnectionRequired is false. No cloud voice fallback. Missing voice data is explained without blocking text output.
5. A separate assistant recorder starts only after “口述回复”. “停止并转写” sends that recording to Gemini, then the transcript and the approved current snapshot produce an English reply preview. The 60-second limit CANCELS rather than uploads. It is separate from legacy auto-insertion. Closing, lock, focus/navigation changes cancel recording and invalidate pending results.
6. No automatic assistant insertion or sending. After reviewing an English draft, the user must click “插入到当前框”, inspect the displayed current-target warning, then click “确认插入当前框”. The IME rechecks current non-sensitive WhatsApp EditorInfo, visibility, InputConnection, current snapshot and token, and commits only that confirmed draft at the current cursor/selection. It does not claim to have verified the group identity. Existing Undo is populated. No Send/Enter/IME_ACTION_SEND/accessibility click is used.
7. The separate “仅模拟演示（不联网）” exercises a fixed synthetic message/explanation/reply, without capture, microphone or API. Synthetic drafts cannot be inserted into a real chat.

## Session and privacy behavior

- Accessibility callbacks only invalidate on app window/focus/content changes; they do not automatically collect text. IME-owned preview/content/focus events are ignored to avoid self-invalidating the panel. A requested response/read-aloud/manual insert revalidates the current snapshot.
- Changing visible content, chat/window, editor focus, ending input, keyboard dismissal, screen-off, service interruption/destruction, or Stop invalidates the epoch and clears RAM context. This is intentionally conservative: even a new incoming message can require restarting.
- Sender identity and group names are NOT guessed or extracted from unrelated UI fields. Message bodies may themselves contain personal information, as disclosed.
- Conversation title/window ID cannot prove a unique chat. Window binding is explicitly unverified. Automatic insertion remains false. Manual insertion relies on the user's explicit confirmation of the current target plus fresh editor/snapshot checks, not claimed stable identity recognition.
- Network cancellation is best-effort; already transmitted data cannot be recalled. User disclosure states this. Stale queued requests/results check the epoch. Close disconnects the active request and deletes the pending audio file; temporary audio is also deleted after preparing the bounded transcription request.
- Recordings use a private assistant-recordings cache subdirectory. Initialization/new recording removes only direct assistant_reply_<digits>.m4a leftovers, rejects directory symlinks, and never follows file symlinks or touches unrelated files. A process crash can leave a recording until the next assistant initialization; this is not a claim of instant crash-time deletion.
- No message/recording archive, history store, screenshots, attachment upload or content logs. Keyboard configuration/key storage remains unchanged.
- Existing Mic/EN/voice translation/auto-insert/Undo paths remain as before. Invoking ordinary Mic/EN first ends the optional assistant; assistant startup is blocked while the existing recorder/translation is busy. The old translator files and recorder have not been modified.

## Local checks

Run: sh tests/run-tests.sh

- 81 actual pure-Java checks covering defaults, bounds, read-only snapshots, lock/focus/package/identity invalidation, stale content/results, injected transport success/error, explicit manual confirmation and stale confirmation rejection, complete-response gating, strict orphan audio cleanup, ancestor clipping and overlapping header/IME geometry.
- Structural checks for consent, Gemini-only endpoint/key routing, no assistant automatic-send calls, read-only Accessibility event handling, offline voice selection and XML validity.
- Java compiler syntax parse for all 15 source files, plus actual Java 17 type compilation against the official API 35 android.jar: PASS (deprecated-API notes only).
- Original fetched file bytes verified against all 15 Git blob SHA-1 values at the pinned base commit.
- Patch application checked against that exact baseline.

## Not yet verified / blockers to a test APK

Official Gradle 8.11.1, Temurin JDK 17 and Google SDK/API35/Build Tools35 are installed in the cloud workspace under the approved SDK agreement. Gradle :app:compileDebugJavaWithJavac and :app:lintDebug passed, including resource processing and manifest merge. Lint reports 0 errors and 81 warnings (primarily hardcoded UI strings, existing missing icon/backup configuration, and API-specific XML attribute). Unsigned release APK packaging also passed (package/version/minSdk/targetSdk checked; apksigner confirms it is unsigned). Isolated AOSP35 software-emulator synthetic UI and real Accessibility node geometry were exercised. Real WhatsApp layouts, real API response/transcription, TTS audio, and upgrade installation have NOT been verified. Passing host tests or API type compilation does not establish device behavior.

WhatsApp private resource IDs (message_text, entry), focused/active window behavior while the IME is visible, clipping, screen geometry and TalkBack interaction require device or representative emulator validation. A mismatch fails with a clear unsupported-page/no-message prompt; it does not broaden the scrape. Current geometry gates are conservative heuristics, not proof against every overlay/multi-window combination. isVisibleToUser plus bounds does not guarantee exact visibility in all Android situations. Do not deploy for real private groups before testing with synthetic chats and reviewing these gates.

UI height/readability and two-tap confirmation need actual small-screen keyboard tests. Offline Chinese voice availability is device-dependent. API model availability remains the existing keyboard configuration and has not been exercised here. No claim of successful real explanation/upload is made.

## Next verification steps

1. Android compile/lint is complete. Producing/installing a signed test APK remains a separate step: reuse the existing signing identity only after its public certificate is verified; no production signing identity was created here. Isolated short-lived emulator-only test signing was separately authorized; its artifacts are never distributed.
2. The integrated candidate includes the independent fail-closed stable-signing/distribution patch and 7 metadata tests. User approval to update the original repository and release a test version has since been received; stable production signing and safe credential handoff still gate distribution.
3. On a test device/emulator, only synthetic messages first: permission absent, user decline, repeated start/stop, different/duplicate-name chats, delayed results after switching, lock/unlock, incoming messages, other apps, overlays/magnification/split-screen, focus change, microphone interruption/limit, offline TTS unavailable, API failures, repeated insert clicks, manual target confirmation, Undo, and ordinary Mic/EN regression checks.
4. User manually grants any phone permission. Android Accessibility authorization is broad; app filtering is not an OS-level WhatsApp-only permission. Sideload restricted-settings prompts and Google Play disclosure/declaration policies require separate review.
5. No Live API, new credentials, background listener, automatic Send, auto-scroll, or history expansion is in scope.

## Synthetic emulator validation, 2026-10-18

An isolated AOSP35 x86_64 emulator ran without KVM at 540x960. A zero-permission app clearly labeled Synthetic Chat Test — NOT WhatsApp supplies invented messages; a separately test-signed keyboard build substitutes only the allowlisted package/IDs and deterministic offline transport/recording mocks. Test APKs/certificates are never distribution artifacts.

Observed: explicit disclosure/consent -> bounded explanation; partial bottom message excluded; English reply preview leaves editor unchanged; first insert click only confirms; second click commits into current editor without Send; duplicate-title Chat A/B switch clears the pending confirmation and old preview; screen-off/unlock clears session. Final navigation-bar inset fix keeps all assistant controls above Android navigation.

Device findings fixed: Android may return already-clipped getBoundsInScreen, so candidate text additionally requires positive, exactly equal local/screen dimensions before any getText. Equality alone is not proof of complete visibility. SystemUI content events previously cancelled valid previews; only a known noninteractive top system-bar event with identical full window geometry/focus/layer fingerprint is now ignored. Unknown/change/error still invalidates. Ordinary UIAutomator dumps suppress other Accessibility services, so active-session tests use screenshot/input and bounded synthetic metadata diagnostics instead.

Limitations: the software emulator initially had a SystemUI ANR, recovered with temporary smaller display and animations disabled. This is not real-phone compatibility evidence. Production Gemini, microphone/audio quality, offline Chinese voice availability, notifications/hidden overlays and real WhatsApp IDs still require controlled device validation with synthetic chats. No real messages or API keys were used.

The synthetic same-window opaque-header case was also exercised: its overlapping first message was rejected, and the remaining bottom-clipped message was excluded, yielding a no-eligible-message prompt.
