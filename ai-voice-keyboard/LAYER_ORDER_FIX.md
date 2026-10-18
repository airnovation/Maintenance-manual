# Local candidate: ordered background branches

Status: v0.15 candidate; bounded metadata trust boundary approved for isolated testing.

The v14 on-device diagnostic reported DP=1 UN=1 VIEW=1 BIG=1.
UN follows automatically from different immediate parents. It does not establish
that Android failed to provide drawing order. The old rule classified all
nonancestor intersecting rectangles as foreground obstructions.

The candidate retains the full root-to-node accessibility path. It discounts
only a leaf reporting android.view.View when:
- both paths have a common ancestor and diverge into different sibling branches;
- each divergent path reports a supported ordinary hierarchy class;
- the two sibling branch drawing orders are positive and the mask branch is
  strictly lower than the message branch.

Missing, zero, equal, higher, unsupported or exception-derived order stays
blocked. Nonleaf/container, text, image, explicitly reported SurfaceView or
TextureView, password and editor masks are not exempted. All other masks are
still checked. Exact app/message IDs, list ancestry, clipping, external windows,
focused editor, bounded traversal and session invalidation remain unchanged.
Diagnostics still never call getText or the AI transport.

## Trust boundary and limitation

This is a platform-metadata-based inference for the foreground official WhatsApp
or WhatsApp Business ordinary View layout. It is not a pixel-level visibility
guarantee. Class names are accessibility descriptions, not runtime type proof.
A custom view, delegate, virtual node or independently composed surface can
report android.view.View. In particular AOSP SurfaceView may inherit that class
description and can be composed above its ordinary tree drawing order. Such an
unidentified surface is not positively excluded by this candidate.

Therefore this candidate does not preserve a universal "reject every possibly
hidden pixel" guarantee. It should not be described as fixing all overlays or
as a proven fix on the user's phone. Real branch orders are not present in the
v14 report. The user explicitly approved this normal-app metadata trust boundary for the
isolated test release; it remains a limitation, not a pixel guarantee.

Sources:
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/View.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/ViewGroup.java
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/view/SurfaceView.java
- https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#getDrawingOrder()
- https://developer.android.com/reference/android/view/SurfaceView#setZOrderOnTop(boolean)
