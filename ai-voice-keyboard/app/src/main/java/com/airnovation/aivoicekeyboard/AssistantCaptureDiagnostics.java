package com.airnovation.aivoicekeyboard;

/** Local counters only. Never accepts text, IDs, names, packages or node objects. */
final class AssistantCaptureDiagnostics {
    int reportedIds, missingIds, listContainers;
    int recognized, noList, clipped, externalOverlap, sizeMismatch;
    int candidates, sameWindowOverlap, missingText, eligible;
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
                + counts(visited) + "\n请只截图这块数字区域，不用包含上方聊天。数字不含消息、联系人或群名，不会自动上传。";
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
                + "\n这些数字仅在本机显示，不含消息、联系人或群名，不会自动上传。";
    }
}
