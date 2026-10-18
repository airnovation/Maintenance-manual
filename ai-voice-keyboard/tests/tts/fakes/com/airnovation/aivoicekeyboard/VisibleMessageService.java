package com.airnovation.aivoicekeyboard;
final class VisibleMessageService {
    static boolean current = true;
    static boolean available() { return true; }
    static String captureOnce(long token) { throw new AssertionError("No actual message capture is permitted"); }
    static boolean stillCurrent(long token) { return current; }
}
