package com.airnovation.aivoicekeyboard;

/** Deliberately does not suppress callbacks: service stale-result guards are independent. */
public final class EnglishRequest {
    int cancelCalls;
    boolean cancelled;
    public void cancel() { cancelCalls++; cancelled = true; }
}
