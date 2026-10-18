package com.airnovation.aivoicekeyboard;

/** Strict allowlist, not a generic screen text scraper. IDs require device validation. */
public final class AssistantNodeFilter {
    private AssistantNodeFilter() { }
    public static final class Box {
        public final int left, top, right, bottom;
        public Box(int left, int top, int right, int bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        boolean contains(Box other) { return left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom; }
        boolean overlaps(Box other) { return left < other.right && right > other.left && top < other.bottom && bottom > other.top; }
    }
    /** One level of the reported accessibility drawing hierarchy, root first. */
    public static final class Layer {
        public final int nodeId, drawingOrder;
        public final boolean supported;
        public Layer(int nodeId, int drawingOrder) { this(nodeId, drawingOrder, false); }
        public Layer(int nodeId, int drawingOrder, boolean supported) {
            this.nodeId = nodeId; this.drawingOrder = drawingOrder; this.supported = supported;
        }
    }
    public static final class Region {
        public final int nodeId;
        public final Box box;
        public final int parentId, drawingOrder, kind;
        public final java.util.List<Layer> path;
        public Region(int nodeId, Box box) { this(nodeId, box, -1, 0, 0);
        }
        public Region(int nodeId, Box box, int parentId, int drawingOrder, int kind) {
            this(nodeId, box, parentId, drawingOrder, kind, java.util.Collections.emptyList());
        }
        public Region(int nodeId, Box box, int parentId, int drawingOrder, int kind, java.util.List<Layer> path) {
            this.nodeId = nodeId; this.box = box;
            this.parentId = parentId; this.drawingOrder = drawingOrder; this.kind = kind;
            this.path = new java.util.ArrayList<>(path);
        }
    }
    public static boolean unobscured(Box message, Box ancestorClip, java.util.List<Integer> ancestors,
            java.util.List<Region> possibleOverlays) {
        if (message.right <= message.left || message.bottom <= message.top || !ancestorClip.contains(message)) return false;
        for (Region region : possibleOverlays)
            if (!ancestors.contains(region.nodeId) && message.overlaps(region.box)) return false;
        return true;
    }
    /**
     * Only a leaf reported as View may be discounted, and only when its branch is
     * explicitly reported below the message branch at their common parent.
     * This uses Android's accessibility drawing-order contract, not screen pixels.
     * Reported class names do not exclude custom/independently composed surfaces.
     * Unknown/zero/tied order remains a blocker. Other mask kinds never qualify.
     */
    public static boolean unobscuredInHierarchy(Box message, Box ancestorClip, java.util.List<Integer> ancestors,
            java.util.List<Layer> messagePath, java.util.List<Region> possibleOverlays) {
        if (message.right <= message.left || message.bottom <= message.top || !ancestorClip.contains(message)) return false;
        for (Region region : possibleOverlays) {
            if (ancestors.contains(region.nodeId) || !message.overlaps(region.box)) continue;
            if (region.kind != 3 || !strictlyBehind(region.path, messagePath)) return false;
        }
        return true;
    }
    static boolean strictlyBehind(java.util.List<Layer> mask, java.util.List<Layer> message) {
        int shared = 0;
        while (shared < mask.size() && shared < message.size()
                && mask.get(shared).nodeId == message.get(shared).nodeId) shared++;
        // No common root, identical node or ancestor/descendant is not a sibling comparison.
        if (shared == 0 || shared == mask.size() || shared == message.size()) return false;
        for (int i = shared; i < mask.size(); i++) if (!mask.get(i).supported) return false;
        for (int i = shared; i < message.size(); i++) if (!message.get(i).supported) return false;
        int lower = mask.get(shared).drawingOrder, upper = message.get(shared).drawingOrder;
        return lower > 0 && upper > 0 && lower < upper;
    }
    public static boolean sameUnclippedSize(Box screen, Box local) {
        int width = local.right - local.left, height = local.bottom - local.top;
        return width > 0 && height > 0 && screen.right - screen.left == width
                && screen.bottom - screen.top == height;
    }
    public static boolean allowed(String pkg, String id, boolean visible,
            boolean editable, boolean password, boolean intersectsViewport) {
        return AssistantSession.isWhatsApp(pkg) && visible && intersectsViewport
                && !editable && !password && AssistantSession.isResource(pkg, id, "message_text");
    }
}
