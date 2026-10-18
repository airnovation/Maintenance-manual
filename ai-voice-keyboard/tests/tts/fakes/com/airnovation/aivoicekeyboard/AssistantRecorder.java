package com.airnovation.aivoicekeyboard;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.io.File;
/** Only models recorder state and call order. Does not open a microphone or a file. */
final class AssistantRecorder {
    interface Finished { void done(File file, String error); }
    static AssistantRecorder latest;
    final Runnable limit;
    boolean active;
    int startCalls, finishCalls, cancelCalls;
    String startError;
    AssistantRecorder(Context context, Runnable limit) { latest = this; this.limit = limit; }
    boolean recording() { return active; }
    String start() {
        startCalls++; TextToSpeech.events.add("record");
        if (TextToSpeech.anySpeaking()) throw new AssertionError("Recorder started before successful TTS stop");
        active = startError == null; return startError;
    }
    void finish(Finished callback) { finishCalls++; active = false; callback.done(null, "Synthetic recording finished; no audio or upload"); }
    void cancel() { cancelCalls++; active = false; }
}
