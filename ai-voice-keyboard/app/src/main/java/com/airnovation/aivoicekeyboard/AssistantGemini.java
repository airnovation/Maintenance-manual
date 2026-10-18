package com.airnovation.aivoicekeyboard;

/** Injected provider boundary: production uses Gemini REST; tests use a synthetic fixture. */
public final class AssistantGemini {
    public interface Callback { void done(long token, String chinese, String english, String error); }
    private AssistantGemini() { }
    public interface Transport {
        void request(AssistantSession session, long token, String spokenReply, Callback callback);
    }
    public static void explainVisible(Transport transport, AssistantSession session, Callback callback) {
        long token = session.token();
        if (!session.mayUseProvider(token) || session.visible(token).isEmpty()) {
            callback.done(token, null, null, "请先启动会话并读取当前消息。"); return;
        }
        transport.request(session, token, null, (id, chinese, english, error) -> {
            if (session.mayUseProvider(id)) callback.done(id, chinese, english, error);
        });
    }
    public static void demo(long token, Callback callback) {
        // Fixed synthetic fixture, never a transformation of private user text.
        callback.done(token, "模拟消息：对方想知道你明天下午三点是否方便。请核对时间后再决定回复。",
                "Thanks! I'll check my schedule and get back to you.", null);
    }
}
