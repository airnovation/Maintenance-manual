package com.airnovation.aivoicekeyboard;
import android.content.Context;
import java.io.File;
final class GeminiAssistantTransport implements AssistantGemini.Transport {
    static GeminiAssistantTransport latest;
    int requestCalls, transcribeCalls;
    GeminiAssistantTransport(Context context) { latest = this; }
    public void request(AssistantSession session, long token, String reply, AssistantGemini.Callback callback) {
        requestCalls++;
        throw new AssertionError("No network/transport request is permitted in offline TTS tests");
    }
    void transcribe(AssistantSession session, long token, File file, AssistantGemini.Callback callback) {
        transcribeCalls++;
        throw new AssertionError("No audio upload is permitted in offline TTS tests");
    }
    void cancel() { }
}
