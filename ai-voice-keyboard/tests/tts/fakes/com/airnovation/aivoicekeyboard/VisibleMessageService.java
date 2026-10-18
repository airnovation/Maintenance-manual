package com.airnovation.aivoicekeyboard;
final class VisibleMessageService {
    static boolean current = true;
    static boolean serviceAvailable = true, diagnosticThrows, diagnosticInvalidates = true;
    static boolean diagnosticSawEmptySession;
    static int diagnosticCalls, captureCalls, currentCalls;
    static long lastDiagnosticToken;
    static String diagnosticResult;
    static boolean available() { return serviceAvailable; }
    static String captureOnce(long token) { captureCalls++; throw new AssertionError("No actual message capture is permitted"); }
    static boolean stillCurrent(long token) { currentCalls++; return current; }
    static String diagnose(long token) {
        diagnosticCalls++; lastDiagnosticToken = token;
        AssistantSession session = AssistantSession.INSTANCE;
        if (!session.active() || token != session.token()) throw new AssertionError("Diagnostic needs a fresh active token");
        diagnosticSawEmptySession = session.visible(token).isEmpty() && session.explanation().isEmpty() && session.draft().isEmpty();
        try {
            if (diagnosticThrows) throw new IllegalStateException("synthetic diagnostic failure");
            return diagnosticResult;
        } finally { if (diagnosticInvalidates) session.invalidate(); }
    }
    static void reset() {
        current = serviceAvailable = diagnosticInvalidates = true; diagnosticThrows = diagnosticSawEmptySession = false;
        diagnosticCalls = captureCalls = currentCalls = 0; lastDiagnosticToken = -1;
        diagnosticResult = "本机诊断（不读文字）\n窗口数量：2\n节点数量：18\n消息候选：3\n仅本机元数据，未读取文字或联网。";
    }
}
