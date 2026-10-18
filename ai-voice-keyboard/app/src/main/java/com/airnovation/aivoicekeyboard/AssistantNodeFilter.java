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
    public static final class Region {
        public final int nodeId;
        public final Box box;
        public Region(int nodeId, Box box) { this.nodeId = nodeId; this.box = box; }
    }
    public static boolean unobscured(Box message, Box ancestorClip, java.util.List<Integer> ancestors,
            java.util.List<Region> possibleOverlays) {
        if (message.right <= message.left || message.bottom <= message.top || !ancestorClip.contains(message)) return false;
        for (Region region : possibleOverlays)
            if (!ancestors.contains(region.nodeId) && message.overlaps(region.box)) return false;
        return true;
    }
    public static boolean sameUnclippedSize(Box screen, Box local) {
        int width = local.right - local.left, height = local.bottom - local.top;
        return width > 0 && height > 0 && screen.right - screen.left == width
                && screen.bottom - screen.top == height;
    }
    public static boolean allowed(String pkg, String id, boolean visible,
            boolean editable, boolean password, boolean intersectsViewport) {
        return AssistantSession.WHATSAPP.equals(pkg) && visible && intersectsViewport
                && !editable && !password && "com.whatsapp:id/message_text".equals(id);
    }
}
