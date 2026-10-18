# Explicit local preview and selected-content approval

v0.17 approved isolated test release, based on published v0.16 (`85f68f9e881959391ca4eaf6696d6f582877ca19`). This release uses a fresh one-off development certificate; keep the original keyboard installed.

## Phone flow

1. In a normal WhatsApp / WhatsApp Business message editor, press **启动助手**. Read the warning: **Android提供的当前消息文字，可能含被浮层遮住部分**.
2. Press **同意预览** to acquire a bounded current-viewport snapshot into RAM. This step does not require a Gemini key and cannot invoke the provider, microphone, TTS or insertion.
3. Review the read-only text and uncheck messages you do not want included. This iteration supports message selection, not text editing. At most 12 messages / 3,000 characters are displayed; a long message may end at that character boundary. Only the displayed selected strings are approved.
4. Press **确认上传** to approve exactly those selected strings for the existing device-configured Gemini service, for explanation and contextual reply drafting. If the provider/key is not configured, no request is sent. Closing cannot retract a request already sent.
5. Existing Chinese offline TTS, contextual spoken-reply preview and deliberate manual insertion remain available only after the context is approved. The app never presses WhatsApp Send.

## Acquisition boundary

The new LOCAL_REVIEW path intentionally permits same-window overlap, including unknown or foreground sibling rendering. This is the revised, explicitly accepted local-preview scope: it is not proof every character is unobscured. The old content-free diagnostic and strict capture paths keep their existing behavior.

Exact allowed package/resource identity, recognized conversation-list ancestry, non-password/non-editable message nodes, full ancestor viewport containment, original local/screen size agreement, external-window occlusion, unique focused full-size application window, unlocked state, focused recognized message editor, traversal bounds and token checks remain required. No scrolling, history fetch, notifications, OCR, image upload or arbitrary TextView harvesting is added.

Only a user button initiates text acquisition. Accessibility events invalidate the session rather than acquiring text. Subsequent freshness checks traverse metadata only and cannot call message getText or replace approved text. Package/window/root/editor/candidate/mask geometry changes reject the session. This is an ephemeral window binding, not a unique chat identity guarantee; it relies on delivered Android accessibility events and metadata. Missed events with identical metadata cannot be independently detected without re-reading or a reliable chat identifier. Users should close and restart if the conversation changes.

## Provider boundary and retention

Unapproved candidates are separate from provider-visible context. Request, transcription, queued execution and callback acceptance check the approved-context gate. Selected strings are copied without trimming or silent approval-time truncation; empty selection is refused. Local UI uses read-only selectable TextViews with saved-state/autofill disabled, and sends the saved original strings rather than querying another input connection. There is no editing route into the WhatsApp draft.

Close, lock, editor change, application events and invalidation clear candidate/approved RAM references, preview views, recordings and pending result eligibility. No chat text is logged or persisted by this feature. Java/Android memory is not claimed to be forensically zeroized. Existing device key storage and provider network behavior are unchanged.

## Verification and limits

Host fakes exercise actual Panel, Service and Transport classes with synthetic messages and no credentials or network. They do not establish actual-device accessibility layout, rendering, IME focus or keyboard-resize behavior. The panel reuses the existing fixed-height preview area to reduce geometry changes; real-device usability remains to be tested. Production signing gates, manifest permissions, legacy Mic/EN/Undo and release workflows are unchanged.
