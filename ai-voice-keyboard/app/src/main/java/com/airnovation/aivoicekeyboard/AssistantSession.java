package com.airnovation.aivoicekeyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** RAM-only, explicit sessions. No Android or network dependency. */
public final class AssistantSession {
    public static final boolean VERIFIED_INSERT_ENABLED = false;
    public static final AssistantSession INSTANCE = new AssistantSession();
    public static final String WHATSAPP = "com.whatsapp";
    public static final String WHATSAPP_BUSINESS = "com.whatsapp.w4b";
    public static boolean isWhatsApp(String pkg) {
        return WHATSAPP.equals(pkg) || WHATSAPP_BUSINESS.equals(pkg);
    }
    public static boolean isResource(String pkg, String id, String name) {
        return isWhatsApp(pkg) && (pkg + ":id/" + name).equals(id);
    }
    public static final int MAX_MESSAGES = 12, MAX_CHARS = 3000;
    private long generation;
    private boolean active;
    private String windowIdentity, sourcePackage;
    private List<String> visible = Collections.emptyList();
    private String explanation = "", draft = "";

    public synchronized long start() {
        invalidate();
        active = true;
        return generation;
    }
    public synchronized void invalidate() {
        generation++;
        active = false;
        windowIdentity = null;
        sourcePackage = null;
        visible = Collections.emptyList();
        explanation = "";
        draft = "";
    }
    public synchronized boolean active() { return active; }
    public synchronized long token() { return generation; }
    public synchronized boolean capture(long token, String pkg, String identity,
            boolean focused, boolean unlocked, List<String> messages) {
        if (!active || token != generation) return false;
        if (!isWhatsApp(pkg) || !focused || !unlocked || identity == null || identity.isEmpty()) {
            invalidate(); return false;
        }
        if (windowIdentity != null && (!windowIdentity.equals(identity) || !pkg.equals(sourcePackage))) {
            invalidate(); return false;
        }
        windowIdentity = identity;
        sourcePackage = pkg;
        ArrayList<String> bounded = new ArrayList<>();
        int chars = 0;
        for (String value : messages) {
            if (value == null || value.trim().isEmpty()) continue;
            int remaining = MAX_CHARS - chars;
            if (remaining <= 0 || bounded.size() == MAX_MESSAGES) break;
            String text = value.substring(0, Math.min(value.length(), remaining));
            bounded.add(text); chars += text.length();
        }
        if (!visible.isEmpty() && !visible.equals(bounded)) {
            // Invalidate a response in flight when the visible context changes.
            generation++;
            explanation = "";
            draft = "";
        }
        visible = Collections.unmodifiableList(bounded);
        return true;
    }
    public synchronized List<String> visible(long token) {
        return active && token == generation ? visible : Collections.emptyList();
    }
    public synchronized boolean accept(long token, String chinese, String english) {
        if (!active || token != generation) return false;
        if (chinese != null) explanation = chinese;
        if (english != null) draft = english;
        return true;
    }
    public synchronized boolean mayReadSource(long token, String pkg, boolean initialCapture) {
        return active && token == generation && isWhatsApp(pkg)
                && (sourcePackage == null ? initialCapture : sourcePackage.equals(pkg));
    }
    public synchronized boolean matchesSource(long token, String pkg) {
        return active && token == generation && sourcePackage != null && sourcePackage.equals(pkg);
    }
    public synchronized void clearDraft() { draft = ""; }
    public synchronized String explanation() { return explanation; }
    public synchronized String draft() { return draft; }
    public synchronized boolean mayManuallyInsert(long token, boolean confirmedCurrentEditor) {
        return active && token == generation && confirmedCurrentEditor && !draft.isEmpty();
    }
    public synchronized boolean mayInsert(long token, String verifiedIdentity) {
        // Display names / window IDs do NOT prove a unique WhatsApp conversation.
        // No production identity resolver exists in this first prototype.
        return false;
    }
}
