package com.airnovation.aivoicekeyboard;
import android.content.Context;
import java.io.File;
final class GeminiAssistantTransport implements AssistantGemini.Transport {
    GeminiAssistantTransport(Context context) { }
    public void request(AssistantSession session, long token, String reply, AssistantGemini.Callback callback) {
        throw new AssertionError("No network/transport request is permitted in offline TTS tests");
    }
    void transcribe(AssistantSession session, long token, File file, AssistantGemini.Callback callback) {
        throw new AssertionError("No audio upload is permitted in offline TTS tests");
    }
    void cancel() { }
}
