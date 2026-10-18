package com.airnovation.aivoicekeyboard;
import android.content.Context;
import java.io.File;
final class GeminiAssistantTransport implements AssistantGemini.Transport {
    static GeminiAssistantTransport latest;
    int requestCalls, transcribeCalls, cancelCalls;
    boolean allowSyntheticRequests;
    java.util.List<String> lastMessages = java.util.List.of();
    AssistantGemini.Callback pendingCallback;
    long pendingToken;
    void complete(String chinese, String english) { pendingCallback.done(pendingToken, chinese, english, null); }
    GeminiAssistantTransport(Context context) { latest = this; }
    public void request(AssistantSession session, long token, String reply, AssistantGemini.Callback callback) {
        requestCalls++;
        if (!allowSyntheticRequests) throw new AssertionError("No network/transport request is permitted in offline TTS tests");
        lastMessages = java.util.List.copyOf(session.visible(token));
        pendingToken = token; pendingCallback = callback;
    }
    void transcribe(AssistantSession session, long token, File file, AssistantGemini.Callback callback) {
        transcribeCalls++;
        throw new AssertionError("No audio upload is permitted in offline TTS tests");
    }
    void cancel() { cancelCalls++; }
}
