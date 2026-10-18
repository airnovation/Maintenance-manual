package com.airnovation.aivoicekeyboard;

/** Reject partial/blocked/unknown completion states before any preview is eligible to insert. */
public final class AssistantResponsePolicy {
    private AssistantResponsePolicy() { }
    public static String requireComplete(String finishReason, String text) {
        if (!"STOP".equals(finishReason)) throw new IllegalArgumentException("Incomplete model response");
        if (text == null || text.trim().isEmpty() || text.length() > 6000)
            throw new IllegalArgumentException("Invalid model text");
        return text.trim();
    }
}
