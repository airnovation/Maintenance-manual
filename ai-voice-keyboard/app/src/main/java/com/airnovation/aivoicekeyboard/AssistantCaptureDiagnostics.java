package com.airnovation.aivoicekeyboard;

/** Local counters only. Never accepts text, resource-ID strings, names, packages or Android node objects. */
final class AssistantCaptureDiagnostics {
    int reportedIds, missingIds, listContainers;
    int recognized, noList, clipped, externalOverlap, sizeMismatch;
    int candidates, sameWindowOverlap, missingText, eligible;
    // Metadata categories only. They NEVER grant permission to read a node.
    private static final String[] OVERLAP_CODES = {"SP", "DP", "LO", "HI", "EQ", "UN",
            "OTHER", "TEXT", "IMAGE", "VIEW", "BOX", "SURFACE", "SENSITIVE", "SM", "MID", "BIG", "CUT"};
    private final int[] overlap = new int[OVERLAP_CODES.length];
    private int recordedPairs;
    void overlaps(AssistantNodeFilter.Box message, int parentId, int drawingOrder,
            java.util.List<Integer> ancestors, java.util.List<AssistantNodeFilter.Region> masks) {
        for (AssistantNodeFilter.Region mask : masks) {
            if (ancestors.contains(mask.nodeId) || !message.overlaps(mask.box)) continue;
            if (recordedPairs >= 32) { overlap[16] = 1; return; }
            recordedPairs++;
            boolean siblings = parentId > 0 && mask.parentId == parentId;
            overlap[siblings ? 0 : 1]++;
            int order = !siblings || drawingOrder <= 0 || mask.drawingOrder <= 0 ? 5
                    : mask.drawingOrder < drawingOrder ? 2 : mask.drawingOrder > drawingOrder ? 3 : 4;
            overlap[order]++;
            overlap[6 + (mask.kind >= 0 && mask.kind <= 6 ? mask.kind : 0)]++;
            long width = Math.min(message.right, mask.box.right) - (long)Math.max(message.left, mask.box.left);
            long height = Math.min(message.bottom, mask.box.bottom) - (long)Math.max(message.top, mask.box.top);
            double ratio = (double)width * height / ((message.right - (double)message.left) * (message.bottom - (double)message.top));
            overlap[ratio <= 0.10 ? 13 : ratio <= 0.50 ? 14 : 15]++;
        }
    }
    private String overlapSummary() {
        StringBuilder out = new StringBuilder("遮挡关系：");
        for (int i = 0; i < overlap.length; i++) if (overlap[i] > 0)
            out.append(OVERLAP_CODES[i]).append('=').append(overlap[i]).append(' ');
        if (recordedPairs == 0) out.append("无");
        return out.toString();
    }
    void structure(boolean hasId, boolean knownList) {
        if (hasId) reportedIds++; else missingIds++;
        if (knownList) listContainers++;
    }
    void candidate(boolean inList, boolean fullyInside, boolean unobscured, boolean originalSize) {
        recognized++;
        if (!inList) noList++;
        if (!fullyInside) clipped++;
        if (!unobscured) externalOverlap++;
        if (!originalSize) sizeMismatch++;
    }
    String metadataResult(int visited) {
        return "本机诊断完成，未读取消息文字、未联网。\n"
                + counts(visited) + "\n" + overlapSummary() + "\n请只截图这块数字区域，不用包含上方聊天。数字不含消息、联系人或群名，不会自动上传。";
    }
    private String counts(int visited) {
        return "N=" + visited + " M=" + recognized + " L=" + noList
                + " I=" + reportedIds + " U=" + missingIds + " K=" + listContainers
                + " C=" + clipped + " W=" + externalOverlap + " S=" + sizeMismatch
                + " F=" + candidates + " O=" + sameWindowOverlap + " E=" + eligible;
    }
    String emptyResult(int visited) {
        return "未采集到符合可见性要求的文字。照片说明或此版本布局可能尚未识别；未上传。"
                + "\n本机诊断 N=" + visited + " M=" + recognized + " L=" + noList
                + " I=" + reportedIds + " U=" + missingIds + " K=" + listContainers
                + " C=" + clipped + " W=" + externalOverlap + " S=" + sizeMismatch
                + " F=" + candidates + " O=" + sameWindowOverlap + " T=" + missingText
                + "\n" + overlapSummary() + "\n这些数字仅在本机显示，不含消息、联系人或群名，不会自动上传。";
    }
}
