package android.speech.tts;
import android.content.Context;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
/** Deterministic engine: no audio, network, Android runtime, or installed voice data. */
public class TextToSpeech {
    public interface OnInitListener { void onInit(int result); }
    public static final int SUCCESS = 0, ERROR = -1, QUEUE_FLUSH = 0;
    public static final List<TextToSpeech> instances = new ArrayList<>();
    public static boolean constructorThrows, synchronousInit;
    public static Thread uiThread;
    public static final List<String> events = new ArrayList<>();
    private final OnInitListener callback;
    public Set<Voice> voices = new LinkedHashSet<>(List.of(new Voice(Locale.CHINESE, false)));
    public Voice selected;
    public boolean getVoicesThrows, setVoiceThrows, speakThrows, stopThrows, shutdownThrows;
    public boolean speaking, shutdown;
    public int voiceResult = SUCCESS, speakResult = SUCCESS, stopResult = SUCCESS;
    public int voiceCalls, getVoicesCalls, speakCalls, stopCalls, shutdownCalls;
    public final List<String> spoken = new ArrayList<>();
    public TextToSpeech(Context context, OnInitListener callback) {
        checkThread();
        if (constructorThrows) throw new IllegalStateException("synthetic constructor failure");
        this.callback = callback; instances.add(this);
        if (synchronousInit) callback.onInit(SUCCESS);
    }
    public void complete(int status) { callback.onInit(status); }
    private static void checkThread() {
        if (uiThread != null && Thread.currentThread() != uiThread) throw new AssertionError("TTS operation off UI thread");
    }
    private void live() { checkThread(); if (shutdown) throw new AssertionError("TTS operation after shutdown"); }
    public Set<Voice> getVoices() { live(); getVoicesCalls++; if (getVoicesThrows) throw new IllegalStateException("voices"); return voices; }
    public int setVoice(Voice voice) { live(); voiceCalls++; if (setVoiceThrows) throw new IllegalStateException("voice"); if (voiceResult == SUCCESS) selected = voice; return voiceResult; }
    public int speak(String text, int queue, Object bundle, String id) {
        live(); speakCalls++; events.add("speak");
        if (selected == null || selected.isNetworkConnectionRequired() || !"zh".equals(selected.getLocale().getLanguage()))
            throw new AssertionError("Speech without an explicitly selected offline Chinese voice");
        if (speakThrows) throw new IllegalStateException("speak");
        if (speakResult == SUCCESS) { spoken.add(text); speaking = true; }
        return speakResult;
    }
    public int stop() { live(); stopCalls++; events.add("stop"); if (stopThrows) throw new IllegalStateException("stop"); if (stopResult == SUCCESS) speaking = false; return stopResult; }
    public void shutdown() { checkThread(); shutdownCalls++; events.add("shutdown"); if (shutdownThrows) throw new IllegalStateException("shutdown"); shutdown = true; speaking = false; }
    public static boolean anySpeaking() { return instances.stream().anyMatch(t -> t.speaking); }
    public static TextToSpeech latest() { return instances.get(instances.size() - 1); }
    public static int speechCount() { return instances.stream().mapToInt(t -> t.speakCalls).sum(); }
    public static void reset() { instances.clear(); events.clear(); constructorThrows = false; synchronousInit = false; uiThread = Thread.currentThread(); }
}
