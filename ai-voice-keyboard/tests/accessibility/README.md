# Actual service metadata-only capture tests

Run from the repository root:

    sh tests/accessibility/run-tests.sh

This compiles the production `VisibleMessageService.java`, `AssistantSession.java`,
`AssistantNodeFilter.java`, and `AssistantCaptureDiagnostics.java` against minimal
host-only Android fakes. The production service is **not** replaced by a fake.
No Android SDK, emulator, device, real conversation, microphone, credentials,
network connection, signing, installation, or publishing is involved.

## What is exercised

- Connect the actual service, start a real session token, then invoke the static
  `VisibleMessageService.diagnose(token)` entry point.
- Provide a synthetic WhatsApp Business focused application window containing a
  full-screen root, recognized conversation list, geometrically eligible message,
  and focused editable `entry` node. The successful path must report `M=1`,
  `F=1`, and `E=1`, so an early refusal cannot make the privacy assertion vacuous.
- Every synthetic node's `getText()` throws `AssertionError` by default, including
  the eligible message. Diagnostics must finish without invoking any of them.
  `AssertionError` intentionally escapes the service's `RuntimeException` catch.
- Check the diagnostic session has ended and no message/response remains stored.
- Exercise unsupported list (`L`), ancestor clipping (`C`), external occlusion
  (`W`), same-window overlap (`O`), and local/screen size mismatch (`S`) counters.
  Accessing an external system window's root also throws.
- Positive normal-capture control permits exactly the eligible message getter,
  verifies one read and the synthetic captured text, and leaves editor, rejected,
  and unrelated node getters poisoned.
- An invalid-token diagnostic request must also end the active session.

- Two distinct synthetic layouts reproduce the same legacy failure counters:
  `N=25 M=2 L=0 I=22 U=3 K=1 C=0 W=0 S=1 F=1 O=1 T=0`.
  One has a large background-like `android.view.View` sibling; the other has a
  small corner-overlapping `android.widget.TextView` sibling. Their declared
  drawing orders are respectively below and above the message. Both execute
  metadata diagnostics and ordinary rejected capture with every text getter
  still poisoned and untouched. This shows the old aggregate counters cannot
  uniquely identify the overlapping UI element; it does not identify a real
  device's layout or establish actual opacity or z-order.

  The extended diagnostic summary must distinguish these fixtures with
  `SP=1 LO=1 VIEW=1 BIG=1` versus `SP=1 HI=1 TEXT=1 SM=1`, while both remain
  refused (`O=1`, `E=0`) and neither exposes raw class names or resource IDs.
  These summary assertions run on both the metadata-only and ordinary
  empty-capture paths.

The suite currently passes 431 assertions over 11 scenarios.

## Mutation/regression check

`ACCESSIBILITY_SERVICE_SOURCE=/path/to/VisibleMessageService.java` can select an
alternate **actual service source** without changing production files. Removing
only the `if (metadataOnly) continue;` guard from a temporary copy was verified to
make this suite fail with:

    AssertionError: FORBIDDEN getText: com.whatsapp.w4b:id/message_text

This establishes that the eligible-message diagnostic test detects the very text
access it is intended to forbid, while the normal capture control proves the
fixture can reach a real text read.

## Scope limitations

The deterministic Android fakes implement only API calls used by this service.
They do not claim to reproduce Android node lifetime/recycling, event delivery,
window reporting, actual app resource IDs, or physical visibility semantics.
`obtain` returns the same fixture node and `recycle` is a no-op, so getter counters
remain shared. This is a host-side control-flow/privacy regression suite, not
Android instrumentation or a claim of real-device/WhatsApp layout validation.
The root test runner is deliberately unchanged; run this suite separately.

A throwing drawing-order getter is classified as unknown, preserves refusal, and never prints exception details.
