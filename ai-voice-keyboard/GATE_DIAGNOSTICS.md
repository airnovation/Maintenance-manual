# Complete LCA rejection-gate diagnostics (v0.16)

The installed v15 exposes only immediate-parent overlap metadata. DP always
implies UN there, even when the ancestor branches have valid drawing order.
There is no hidden setting, remote configuration or retained node log that can
recover the missing LCA decision from that installed APK. The latest O1/E0
report therefore does not identify a new actionable extraction bug.

This change instruments the existing v15 decision; it does not widen reading.
The service calls AssistantNodeFilter.assess exactly once per candidate, sends
that same Assessment to diagnostics, and uses its allowed field to gate getText.
All overlapping regions are evaluated so later blockers are counted too.

## What the local output means

The actual generated BuildConfig.VERSION_NAME is displayed as 版本.
Version, all gate totals and bounded details form one selectable logical line;
normal wrapping is allowed. The user copies that line, not a chat screenshot.
Existing N/M/L/I/U/K/C/W/S/F/O/E and overlap codes stay available.

LCA gives counts for each fixed Reason. Counts are candidate-mask decisions,
not necessarily message counts. ALLOW counts an accepted lower background
relation; another blocker can still make that candidate O=1. NOT_EVALUATED
means there were no final candidates; NO_OVERLAP means no relevant overlapping
nonancestor region was evaluated for the final candidates.

Each overlapping pair reports its first decisive gate in the same fixed
precedence as capture. It does not claim to count every simultaneous failure.
Where paths diverge, both sides' class and order states are included even when
an earlier class gate rejects the pair.

The following covers every classifier return path:
- GEOMETRY_INVALID: nonpositive message rectangle.
- OUTSIDE_CLIP: message rectangle not contained by the ancestor clip.
- MASK_KIND: mask is not the already-allowed leaf View category.
- PATH_MISSING: one root-to-node path is empty.
- ROOT_DIFFERENT: no common root.
- PATH_IDENTICAL: same complete path.
- MASK_ANCESTOR / MESSAGE_ANCESTOR: one path ends before divergence.
- MASK_CLASS / MESSAGE_CLASS: unsupported category on that divergent path.
- MASK_ORDER_ERROR / MESSAGE_ORDER_ERROR: branch getter threw; no error text shown.
- MASK_ORDER_ZERO / MESSAGE_ORDER_ZERO: branch reported zero/unknown.
- MASK_ORDER_NEGATIVE / MESSAGE_ORDER_NEGATIVE: branch reported negative.
- ORDER_EQUAL: both positive orders equal.
- ORDER_AHEAD: mask branch later than the candidate branch.
- ALLOW: supported divergent paths and strictly lower positive mask branch.

Detailed records use only fixed enums:
- B: reported branch categories, mask/message.
- U: first unsupported category on each path, or NONE.
- Q: branch order validity POSITIVE/ZERO/NEGATIVE/ERROR/NA, not raw numbers.
- P: divergent path lengths ONE/FEW (2–3)/MANY (4+)/NA.
- N: identical-record count.

Categories are VIEW/TEXT/IMAGE/LAYOUT/LIST/SURFACE/OTHER/MISSING/NONE/ORDINARY.
They are reported metadata categories, never runtime-type or pixel proof.

At most four distinct detailed combinations are displayed. All Reason totals
remain complete; DETAIL_LIMIT counts additional records not expanded. A report
with DETAIL_LIMIT is explicitly incomplete at the per-combination level and
must not be interpreted as identifying every blocking node.

## Privacy and limits

No node text, content description, sender, group, resource ID, coordinate, raw
class name or raw order value enters these records. They remain in the existing
on-device transient result TextView. No logging, persistence, network, recording,
clipboard read, scrolling or new permission is added. The separate diagnostic
continues to invalidate its session without calling getText or a provider.

The Android framework can report custom or independently composed surfaces as
generic View. Instrumentation does not remove the v15 documented trust boundary.
Unsupported or unknown metadata is explained, not automatically allowed.

Normal service traversal filters out several pure-classifier geometry/path cases
before assessment; pure tests cover those cases rather than falsely claiming
the normal service can generate them.

The user approved this v0.16 isolated diagnostic release with a fresh temporary
development signature. It changes reporting, not the v15 reading policy.
