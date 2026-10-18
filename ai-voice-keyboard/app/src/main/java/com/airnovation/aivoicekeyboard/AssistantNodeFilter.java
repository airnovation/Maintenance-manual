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
        public final Kind reportedKind;
        public final boolean orderError;
        public Layer(int nodeId, int drawingOrder) { this(nodeId, drawingOrder, false); }
        public Layer(int nodeId, int drawingOrder, boolean supported) {
            this(nodeId, drawingOrder, supported, supported ? Kind.ORDINARY : Kind.OTHER, false);
        }
        public Layer(int nodeId, int drawingOrder, boolean supported, Kind reportedKind, boolean orderError) {
            this.nodeId = nodeId; this.drawingOrder = drawingOrder; this.supported = supported;
            this.reportedKind = reportedKind; this.orderError = orderError;
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
    public enum Kind { NONE, ORDINARY, VIEW, TEXT, IMAGE, LAYOUT, LIST, SURFACE, OTHER, MISSING }
    public enum Reason { ALLOW, GEOMETRY_INVALID, OUTSIDE_CLIP, MASK_KIND,
        PATH_MISSING, ROOT_DIFFERENT, PATH_IDENTICAL, MASK_ANCESTOR, MESSAGE_ANCESTOR,
        MASK_CLASS, MESSAGE_CLASS, MASK_ORDER_ERROR, MESSAGE_ORDER_ERROR,
        MASK_ORDER_ZERO, MESSAGE_ORDER_ZERO, MASK_ORDER_NEGATIVE, MESSAGE_ORDER_NEGATIVE,
        ORDER_EQUAL, ORDER_AHEAD }
    public enum Order { NA, POSITIVE, ZERO, NEGATIVE, ERROR }
    public enum Shape { NA, NONE, ONE, FEW, MANY }
    public static final class Decision {
        public final Reason reason;
        public final Kind maskClass, messageClass, maskBranch, messageBranch;
        public final Order maskOrder, messageOrder;
        public final Shape maskPath, messagePath;
        Decision(Reason reason, Kind maskClass, Kind messageClass, Order maskOrder, Order messageOrder,
                Shape maskPath, Shape messagePath, Kind maskBranch, Kind messageBranch) {
            this.reason=reason; this.maskClass=maskClass; this.messageClass=messageClass;
            this.maskOrder=maskOrder; this.messageOrder=messageOrder;
            this.maskPath=maskPath; this.messagePath=messagePath; this.maskBranch=maskBranch; this.messageBranch=messageBranch;
        }
        public boolean allowed() { return reason == Reason.ALLOW; }
    }
    public static final class Assessment {
        public boolean allowed = true;
        public final java.util.List<Decision> decisions = new java.util.ArrayList<>();
        void add(Decision decision) { decisions.add(decision); if (!decision.allowed()) allowed=false; }
    }
    private static Decision simple(Reason reason) {
        return new Decision(reason,Kind.NONE,Kind.NONE,Order.NA,Order.NA,Shape.NA,Shape.NA,Kind.NONE,Kind.NONE);
    }
    private static Shape shape(int length) {
        return length == 0 ? Shape.NONE : length == 1 ? Shape.ONE : length <= 3 ? Shape.FEW : Shape.MANY;
    }
    private static Order order(Layer layer) {
        return layer.orderError ? Order.ERROR : layer.drawingOrder == 0 ? Order.ZERO
                : layer.drawingOrder < 0 ? Order.NEGATIVE : Order.POSITIVE;
    }
    /** Single classifier used for both acquisition and local diagnostics. Never reads text. */
    public static Assessment assess(Box message, Box ancestorClip, java.util.List<Integer> ancestors,
            java.util.List<Layer> messagePath, java.util.List<Region> possibleOverlays) {
        Assessment result = new Assessment();
        if (message.right <= message.left || message.bottom <= message.top) {
            result.add(simple(Reason.GEOMETRY_INVALID)); return result;
        }
        if (!ancestorClip.contains(message)) { result.add(simple(Reason.OUTSIDE_CLIP)); return result; }
        for (Region region : possibleOverlays) {
            if (ancestors.contains(region.nodeId) || !message.overlaps(region.box)) continue;
            result.add(region.kind != 3 ? simple(Reason.MASK_KIND) : classifyPaths(region.path,messagePath));
        }
        return result;
    }
    public static boolean unobscuredInHierarchy(Box message, Box ancestorClip, java.util.List<Integer> ancestors,
            java.util.List<Layer> messagePath, java.util.List<Region> possibleOverlays) {
        return assess(message,ancestorClip,ancestors,messagePath,possibleOverlays).allowed;
    }
    static boolean strictlyBehind(java.util.List<Layer> mask, java.util.List<Layer> message) {
        return classifyPaths(mask,message).allowed();
    }
    static Decision classifyPaths(java.util.List<Layer> mask, java.util.List<Layer> message) {
        if (mask.isEmpty() || message.isEmpty()) return simple(Reason.PATH_MISSING);
        int shared=0;
        while (shared<mask.size() && shared<message.size() && mask.get(shared).nodeId==message.get(shared).nodeId) shared++;
        if (shared==0) return simple(Reason.ROOT_DIFFERENT);
        if (shared==mask.size() && shared==message.size()) return simple(Reason.PATH_IDENTICAL);
        if (shared==mask.size()) return simple(Reason.MASK_ANCESTOR);
        if (shared==message.size()) return simple(Reason.MESSAGE_ANCESTOR);
        Kind maskClass=Kind.NONE, messageClass=Kind.NONE;
        boolean maskUnsupported=false, messageUnsupported=false;
        for (int i=shared;i<mask.size();i++) if (!mask.get(i).supported) {
            maskUnsupported=true; maskClass=mask.get(i).reportedKind; break;
        }
        for (int i=shared;i<message.size();i++) if (!message.get(i).supported) {
            messageUnsupported=true; messageClass=message.get(i).reportedKind; break;
        }
        Layer lower=mask.get(shared), upper=message.get(shared);
        Order maskOrder=order(lower), messageOrder=order(upper);
        Reason reason=Reason.ALLOW;
        if (maskUnsupported) reason=Reason.MASK_CLASS;
        else if (messageUnsupported) reason=Reason.MESSAGE_CLASS;
        else if (maskOrder==Order.ERROR) reason=Reason.MASK_ORDER_ERROR;
        else if (messageOrder==Order.ERROR) reason=Reason.MESSAGE_ORDER_ERROR;
        else if (maskOrder==Order.ZERO) reason=Reason.MASK_ORDER_ZERO;
        else if (messageOrder==Order.ZERO) reason=Reason.MESSAGE_ORDER_ZERO;
        else if (maskOrder==Order.NEGATIVE) reason=Reason.MASK_ORDER_NEGATIVE;
        else if (messageOrder==Order.NEGATIVE) reason=Reason.MESSAGE_ORDER_NEGATIVE;
        else if (lower.drawingOrder==upper.drawingOrder) reason=Reason.ORDER_EQUAL;
        else if (lower.drawingOrder>upper.drawingOrder) reason=Reason.ORDER_AHEAD;
        return new Decision(reason,maskClass,messageClass,maskOrder,messageOrder,
                shape(mask.size()-shared),shape(message.size()-shared),lower.reportedKind,upper.reportedKind);
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
