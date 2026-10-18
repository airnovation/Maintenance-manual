# Bounded overlap metadata diagnosis

The observed legacy counters M=2, S=1, F=1, O=1 locate rejection at the final
same-window mask check for one otherwise eligible candidate. They do not identify
that mask or prove it is harmless. Exact message IDs and list ancestry are already
recognized; adding caption IDs or accepting arbitrary text is not justified.

This change preserves all capture decisions. It adds only categorical counters
for actual overlapping, non-ancestor masks. The existing unobscured,
sameUnclippedSize and allowed decision functions are byte-identical to v13.
The explicit metadata diagnostic still never calls getText or any provider.

Only nonzero codes appear on a new “遮挡关系” line:

- SP / DP: same direct parent / different or unknown parent
- LO / HI / EQ: lower / higher / equal positive drawing order, only within the same parent
- UN: incomparable or unavailable order, including zero, exceptions and cross-parent comparisons
- TEXT / IMAGE / VIEW: exact reported standard leaf class category
- BOX: node with children
- SURFACE: exact SurfaceView or TextureView category
- SENSITIVE: editable or password node
- OTHER: all other reported categories
- SM / MID / BIG: rectangle intersection covers up to 10%, up to 50%, or over 50% of the candidate rectangle
- CUT: relation reporting reached its bound of 32

These are classifications for diagnosis, not trusted proof of rendering or
visibility. In particular, a reported class and order cannot authenticate a
physical View or exclude virtual nodes. Raw IDs, parent IDs, class strings,
coordinates, text, names and descriptions are never printed. Counters may reflect
multiple overlaps of a candidate; they are not message counts. Missing codes mean
zero. Only the lower diagnostic region needs to be shared, not the chat above it.

Two actual-service synthetic fixtures reproduce the same complete old counters
N25 M2 L0 I22 U3 K1 C0 W0 S1 F1 O1 T0 while using different masks:

- Large View-like sibling: SP=1 LO=1 VIEW=1 BIG=1
- Small corner TextView sibling: SP=1 HI=1 TEXT=1 SM=1

Both remain blocked, E=0, and every message getText getter remains poisoned and
untouched. This distinguishes causes without weakening privacy checks. It does
not establish which fixture matches the user's device.

Do not shrink masks to ancestor intersections: clipChildren can be false, so a
child may really draw outside its parent. Do not exempt arbitrary lower-order
nodes: sibling order alone is not a universal surface or virtual-node guarantee.
Official references:
https://developer.android.com/reference/android/view/ViewGroup#setClipChildren(boolean)
https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#getDrawingOrder()
https://developer.android.com/reference/android/view/SurfaceView#setZOrderOnTop(boolean)

The earlier v13 has no code to display these extra relationships and no remote
configuration mechanism. This authorized v0.14 build adds them without changing
extraction decisions. Actual device evidence is
still required before claiming the reading failure is repaired.
