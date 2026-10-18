# WhatsApp Business compatibility fix (v0.12)

The previous v0.11 test build rejected WhatsApp Business before checking Accessibility or Gemini. The EditorInfo allowlist only included `com.whatsapp`.

This fix allows exactly `com.whatsapp` and `com.whatsapp.w4b` through IME start and manual insertion, focused Accessibility root, node filtering and session capture. Resource IDs must match the source package exactly (`<package>:id/entry` and `<package>:id/message_text`). All descendants must match the focused root package. Sessions bind both package and window identity; switching between the two apps invalidates prior context even if a window identity is reused.

Password, focus, editable-draft exclusion, list/geometry/occlusion, explicit consent and manual insertion gates remain. The Accessibility configuration is unchanged: cross-app events are necessary to invalidate sessions, while collection remains restricted to the focused allowed application. No background capture, notification text or history traversal is added.

Actual WhatsApp Business node layouts have not been inspected on a device. Unknown or differently namespaced layouts fail closed with a diagnostic; synthetic helper tests do not prove real-device compatibility.

The new independent test APK uses a new one-time development certificate. It cannot update the previous independent test APK in place. Only that independent test app may need manual removal and configuration again; keep the original keyboard installed. No device is uninstalled or modified automatically.

The assistant status TextView now sets opaque black text and white background explicitly, independent of inherited theme defaults. The demo button shares the same editor gate: Business was previously rejected before reaching its fixed synthetic fixture. The paired fixes address that established path; host click/render tests cover example visibility, simulated reply and insertion refusal without network or recording. Host tests do not validate physical display rendering.
