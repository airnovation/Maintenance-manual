package com.airnovation.aivoicekeyboard;
import android.content.Context;
import java.io.File;
/** Deterministic host fake. Never opens a microphone. */
final class AssistantRecorder {
    interface Finished { void done(File file, String error); }
    static AssistantRecorder latest;
    final Runnable limit;
    boolean active, deferFinish;
    int startCalls, finishCalls, cancelCalls;
    String startError, finishError;
    File file;
    Finished finished;
    AssistantRecorder(Context context, Runnable limit) { latest = this; this.limit = limit; }
    boolean recording() { return active; }
    String start() { startCalls++; active = startError == null; return startError; }
    void finish(Finished callback) {
        finishCalls++; active = false; finished = callback;
        if (!deferFinish) completeFinish();
    }
    void completeFinish() { finished.done(file, finishError); }
    void cancel() { cancelCalls++; active = false; }
}
