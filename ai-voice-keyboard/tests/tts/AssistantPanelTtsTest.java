package com.airnovation.aivoicekeyboard;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Executes the production AssistantPanel with deterministic fake Android boundaries. */
public final class AssistantPanelTtsTest {
    private interface Checked { void run() throws Exception; }
    private static int assertions, cases;
    private static Harness active;
    private static final AssistantSession SESSION = AssistantSession.INSTANCE;

    public static void main(String[] args) throws Exception {
        run("late initialization cannot speak into recording", () -> {
            Harness h = new Harness(); h.speak(); TextToSpeech engine = TextToSpeech.latest();
            h.record(); String recordingText = h.text();
            check(h.recorder.active, "recording must start");
            engine.complete(TextToSpeech.SUCCESS); Handler.drain();
            equal(0, TextToSpeech.speechCount(), "late init must not speak after microphone starts");
            equal(recordingText, h.text(), "late init must not replace recording status");
        });
        if (args.length > 0 && "--regression-only".equals(args[0])) { report(); return; }
        run("late initialization cannot revive after recording stops", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest();
            h.record(); h.record(); String finished = h.text();
            check(!h.recorder.active, "fake recording finished");
            engine.complete(TextToSpeech.SUCCESS); Handler.drain();
            equal(0, engine.speakCalls, "canceled speech remains canceled after recording");
            equal(finished, h.text(), "no stale status write");
            h.speak(); equal(1, engine.speakCalls, "a new explicit tap can speak");
        });
        run("failed recorder start still cancels speech intent", () -> {
            Harness h = pending(); h.recorder.startError = "synthetic microphone unavailable";
            h.record(); TextToSpeech.latest().complete(TextToSpeech.SUCCESS); Handler.drain();
            equal(0, TextToSpeech.speechCount(), "record failure must not restore canceled read-aloud");
            equal(h.recorder.startError, h.text(), "record failure remains visible");
        });
        run("repeated pending taps coalesce and respond once", () -> {
            Harness h = new Harness(); String previous = h.text();
            h.speak(); h.speak(); h.speak();
            equal(1, TextToSpeech.instances.size(), "one initialization in flight");
            equal(previous, h.text(), "pending taps do not report a false missing-voice error");
            TextToSpeech engine = TextToSpeech.latest(); engine.complete(0); Handler.drain();
            equal(1, engine.speakCalls, "latest explicit intent is fulfilled once");
            engine.complete(0); engine.complete(-1); Handler.drain();
            equal(1, engine.speakCalls, "duplicate callbacks cannot replay");
            equal(0, engine.shutdownCalls, "late contradictory failure is ignored");
            h.speak(); equal(2, engine.speakCalls, "a subsequent explicit tap is honored");
        });
        run("constructor-time callback is processed after assignment", () -> {
            TextToSpeech.synchronousInit = true;
            Harness h = new Harness(); h.speak();
            equal(0, TextToSpeech.speechCount(), "constructor callback is queued");
            Handler.drain(); equal(1, TextToSpeech.speechCount(), "assigned engine speaks after queued init");
        });
        run("background init callback is marshaled onto UI thread", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest();
            Thread worker = new Thread(() -> engine.complete(0)); worker.start(); worker.join();
            equal(0, engine.getVoicesCalls, "background callback does no engine/UI work");
            Handler.drain(); equal(1, engine.speakCalls, "UI thread completes requested speech");
        });
        run("queued init loses to subsequent recording before dispatch", () -> {
            Harness h = pending(); TextToSpeech.latest().complete(0);
            h.record(); Handler.drain(); equal(0, TextToSpeech.speechCount(), "queued callback observes cancellation");
        });
        run("queued init loses to close before dispatch", () -> {
            Harness h = pending(); TextToSpeech.latest().complete(0);
            h.panel.stop(); String closed = h.text(); Handler.drain();
            equal(0, TextToSpeech.speechCount(), "closed request is never spoken");
            equal(closed, h.text(), "closed status remains visible");
        });
        run("new session alone cannot revive old pending intent", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest();
            h.panel.stop(); h.activate("new synthetic explanation"); engine.complete(0); Handler.drain();
            equal(0, engine.speakCalls, "new session needs another read-aloud tap");
            h.speak(); equal(List.of("new synthetic explanation"), engine.spoken, "explicit new request uses new content");
        });
        run("new explicit request may reuse still-initializing engine", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest();
            h.panel.stop(); h.activate("new explicit synthetic explanation"); h.speak();
            engine.complete(0); Handler.drain();
            equal(1, TextToSpeech.instances.size(), "reuse initialization without duplicate engine");
            equal(List.of("new explicit synthetic explanation"), engine.spoken, "new intent is fulfilled");
        });
        run("session token replacement rejects stale pending request", () -> {
            Harness h = pending(); h.activate("replacement synthetic explanation");
            TextToSpeech.latest().complete(0); Handler.drain();
            equal(0, TextToSpeech.speechCount(), "old token cannot speak replacement content");
            check(SESSION.active(), "stale callback must not invalidate unrelated new session");
        });
        run("periodic teardown cancels pending speech", () -> {
            Harness h = pending(); SESSION.invalidate(); Handler.tick(); String changed = h.text();
            TextToSpeech.latest().complete(0); Handler.drain();
            equal(0, TextToSpeech.speechCount(), "periodic invalidation cancels intent");
            equal(changed, h.text(), "no stale UI update");
        });
        run("screen-off cancels pending speech", () -> {
            Harness h = pending(); h.context.receiver.onReceive(h.context, new Intent(Intent.ACTION_SCREEN_OFF));
            TextToSpeech.latest().complete(0); Handler.drain();
            equal(0, TextToSpeech.speechCount(), "locked session cannot speak");
            check(!SESSION.active(), "screen-off ends session");
        });
        run("editor change cancels pending speech", () -> {
            Harness h = pending(); h.panel.editorChanged(false);
            TextToSpeech.latest().complete(0); Handler.drain();
            equal(0, TextToSpeech.speechCount(), "editor switch cancels intent");
        });
        run("destroy before callback ignores old engine completely", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest(); h.panel.destroy();
            engine.complete(0); Handler.drain();
            equal(1, engine.shutdownCalls, "engine is shut down once");
            equal(0, engine.getVoicesCalls, "destroyed callback never queries engine");
            equal(0, engine.speakCalls, "destroyed callback never speaks");
            equal(0, Handler.pending(), "destroy removes periodic callback; init drains harmlessly");
        });
        run("destroy after callback queued ignores old engine", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest(); engine.complete(0); h.panel.destroy();
            Handler.drain(); equal(0, engine.getVoicesCalls, "already queued init cannot access shutdown engine");
        });
        run("destroyed UI cannot start new TTS or recorder", () -> {
            Harness h = new Harness(); h.panel.destroy(); h.activate("other live panel session");
            h.speak(); h.record();
            equal(0, TextToSpeech.instances.size(), "detached button cannot create engine");
            equal(0, h.recorder.startCalls, "detached button cannot start recorder");
        });
        run("teardown continues through TTS stop exception", () -> {
            Harness h = ready(); TextToSpeech engine = TextToSpeech.latest(); engine.stopThrows = true; h.recorder.active = true;
            h.panel.stop();
            check(!SESSION.active(), "session invalidated despite engine exception");
            check(!h.recorder.active, "recorder canceled despite engine exception");
            check(h.text().contains("关闭"), "closed status shown despite engine exception");
            equal(1, engine.shutdownCalls, "teardown tries shutdown when stop throws");
        });
        run("teardown uses shutdown after TTS stop ERROR", () -> {
            Harness h = ready(); TextToSpeech engine = TextToSpeech.latest(); engine.stopResult = -1; h.recorder.active = true;
            h.panel.stop();
            equal(1, engine.shutdownCalls, "teardown tries shutdown when stop returns ERROR");
            check(!h.recorder.active && !SESSION.active(), "other cleanup continues after stop ERROR");
            h.activate("new synthetic context"); h.speak();
            check(TextToSpeech.latest() != engine, "next explicit request gets a fresh engine");
        });
        run("ready engine stop ERROR denies microphone start", () -> {
            Harness h = ready(); TextToSpeech.latest().stopResult = TextToSpeech.ERROR; h.record();
            equal(0, h.recorder.startCalls, "error stop must deny recorder start");
            check(h.text().contains("未开始录音"), "failed stop is reported accurately");
        });
        run("ready engine stop exception denies microphone start", () -> {
            Harness h = ready(); TextToSpeech.latest().stopThrows = true; h.record();
            equal(0, h.recorder.startCalls, "throwing stop must deny recorder start");
        });
        run("explicit retry can record after successful stop", () -> {
            Harness h = ready(); TextToSpeech engine = TextToSpeech.latest(); engine.stopResult = -1; h.record();
            engine.stopResult = 0; h.record();
            check(h.recorder.active, "retry can start after confirmed stop");
            equal(1, engine.speakCalls, "no auto-replay on retry");
        });
        run("uninitialized stop ERROR cannot resurrect speech", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest(); engine.stopResult = -1;
            h.record(); check(h.recorder.active, "never-speaking uninitialized engine is safe to cancel");
            engine.complete(0); Handler.drain(); equal(0, engine.speakCalls, "cancel still blocks late success");
        });
        run("uninitialized stop exception cannot resurrect speech", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest(); engine.stopThrows = true;
            h.record(); check(h.recorder.active, "unready stop exception cannot retain intent");
            engine.complete(0); Handler.drain(); equal(0, engine.speakCalls, "cancel still blocks late success");
        });
        run("initialization failure permits explicit retry", () -> {
            Harness h = pending(); TextToSpeech first = TextToSpeech.latest(); first.complete(-1); Handler.drain();
            equal(0, first.speakCalls, "failed engine never speaks"); equal(1, first.shutdownCalls, "failed engine released");
            check(h.text().contains("离线中文语音"), "offline failure is explained");
            h.speak(); TextToSpeech second = TextToSpeech.latest(); check(second != first, "retry creates new engine");
            first.complete(0); Handler.drain(); equal(0, second.getVoicesCalls, "old callback cannot initialize replacement");
            second.complete(0); Handler.drain(); equal(1, second.speakCalls, "explicit retry responds");
        });
        run("constructor failure permits explicit retry", () -> {
            Harness h = new Harness(); TextToSpeech.constructorThrows = true; h.speak();
            equal(0, TextToSpeech.speechCount(), "constructor failure does not speak");
            check(h.text().contains("离线中文语音"), "constructor failure is reported");
            TextToSpeech.constructorThrows = false; h.speak(); TextToSpeech.latest().complete(0); Handler.drain();
            equal(1, TextToSpeech.speechCount(), "explicit constructor retry succeeds");
        });
        unavailable("null voice collection", null);
        unavailable("empty voice collection", Set.of());
        unavailable("network-only Chinese voice", Set.of(new Voice(Locale.CHINESE, true)));
        unavailable("offline non-Chinese voice", Set.of(new Voice(Locale.ENGLISH, false)));
        unavailable("voice with null locale", Set.of(new Voice(null, false)));
        Set<Voice> nullVoice = new LinkedHashSet<>(); nullVoice.add(null); unavailable("null voice entry", nullVoice);
        run("offline voice selected after unsuitable alternatives", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest();
            engine.voices = new LinkedHashSet<>(List.of(new Voice(Locale.CHINESE, true), new Voice(Locale.ENGLISH, false), new Voice(Locale.TAIWAN, false)));
            engine.complete(0); Handler.drain(); equal(1, engine.speakCalls, "offline Chinese alternative is selected");
            check(!engine.selected.isNetworkConnectionRequired(), "never selects network voice");
        });
        for (String failure : List.of("getVoices", "setVoice", "setVoiceError")) run(failure + " failure fails closed", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest();
            engine.getVoicesThrows = failure.equals("getVoices"); engine.setVoiceThrows = failure.equals("setVoice");
            if (failure.equals("setVoiceError")) engine.voiceResult = -1;
            engine.complete(0); Handler.drain(); equal(0, engine.speakCalls, "voice selection failure forbids speech");
            equal(1, engine.shutdownCalls, "unusable engine is released");
        });
        run("failure after canceled intent cannot replace recording status", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest(); engine.getVoicesThrows = true;
            h.record(); String recording = h.text(); engine.complete(0); Handler.drain();
            equal(recording, h.text(), "late failure must not overwrite recording UI");
            check(h.recorder.active, "voice failure does not cancel desired recording");
        });
        run("shutdown exception does not prevent failure recovery", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest(); engine.shutdownThrows = true;
            engine.complete(-1); Handler.drain();
            h.speak(); TextToSpeech replacement = TextToSpeech.latest(); replacement.complete(0); Handler.drain();
            equal(1, replacement.speakCalls, "explicit retry works after shutdown exception");
        });
        for (boolean throwing : List.of(false, true)) run("speak " + (throwing ? "exception" : "ERROR") + " consumes only one request", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest();
            engine.speakThrows = throwing; engine.speakResult = -1; engine.complete(0); Handler.drain();
            check(h.text().contains("朗读失败"), "speech failure is reported");
            engine.complete(0); Handler.drain(); equal(1, engine.speakCalls, "failed request is not replayed by callback");
            engine.speakThrows = false; engine.speakResult = 0; h.speak(); equal(2, engine.speakCalls, "new explicit retry is honored");
        });
        run("noncurrent snapshot is rejected before engine creation", () -> {
            Harness h = new Harness(); VisibleMessageService.current = false; h.speak();
            equal(0, TextToSpeech.instances.size(), "stale snapshot creates no engine");
            check(!SESSION.active(), "stale snapshot ends session");
        });
        run("snapshot change during init rejects late speech", () -> {
            Harness h = pending(); VisibleMessageService.current = false; TextToSpeech.latest().complete(0); Handler.drain();
            equal(0, TextToSpeech.speechCount(), "changed snapshot cannot speak"); check(!SESSION.active(), "changed snapshot ends session");
        });
        run("read-aloud click while recording does not authorize later speech", () -> {
            Harness h = pending(); h.record(); h.speak(); h.record();
            TextToSpeech.latest().complete(0); Handler.drain(); equal(0, TextToSpeech.speechCount(), "recording-time click is not queued");
        });
        run("speakReady independently refuses active recorder", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest();
            // Fault injection bypasses recordReply cancellation to test defense in depth.
            h.recorder.active = true; engine.complete(0); Handler.drain(); equal(0, engine.speakCalls, "ready callback checks actual recorder state");
            h.recorder.active = false; invoke(h.panel, "speakReady"); equal(0, engine.speakCalls, "blocked intent is revoked, not deferred");
            h.speak(); equal(1, engine.speakCalls, "fresh explicit request remains possible");
        });
        run("empty explanation cannot create speech intent", () -> {
            Harness h = new Harness(); SESSION.accept(SESSION.token(), "", null); h.speak();
            equal(0, TextToSpeech.instances.size(), "empty explanation creates no engine");
        });
        run("recording timeout does not revive canceled speech", () -> {
            Harness h = pending(); h.record(); h.recorder.limit.run();
            check(!h.recorder.active, "timeout cancels recording"); String timeout = h.text();
            TextToSpeech.latest().complete(0); Handler.drain(); equal(0, TextToSpeech.speechCount(), "timeout does not resume old speech");
            equal(timeout, h.text(), "timeout status remains visible");
        });
        run("successful stop occurs before microphone start", () -> {
            Harness h = ready(); TextToSpeech.events.clear(); h.record();
            equal(List.of("stop", "record"), TextToSpeech.events, "TTS stop precedes recording start");
            check(!TextToSpeech.anySpeaking() && h.recorder.active, "speaker and microphone are not active together");
        });
        run("destroy tolerates both stop and shutdown exceptions", () -> {
            Harness h = ready(); TextToSpeech engine = TextToSpeech.latest(); engine.stopThrows = true; engine.shutdownThrows = true;
            h.panel.destroy(); engine.complete(0); Handler.drain();
            equal(1, engine.speakCalls, "destroyed callback never replays earlier utterance");
            check(h.context.receiver == null, "receiver cleanup continues after TTS exception");
            equal(0, Handler.pending(), "periodic work is removed");
        });
        report();
    }
    private static void unavailable(String name, Set<Voice> voices) throws Exception {
        run(name + " fails closed", () -> {
            Harness h = pending(); TextToSpeech engine = TextToSpeech.latest(); engine.voices = voices;
            engine.complete(0); Handler.drain();
            equal(0, engine.speakCalls, "unavailable offline Chinese voice never speaks");
            equal(1, engine.shutdownCalls, "unusable engine released for explicit retry");
            check(h.text().contains("离线中文语音"), "missing voice explained");
        });
    }
    private static Harness pending() throws Exception { Harness h = new Harness(); h.speak(); return h; }
    private static Harness ready() throws Exception { Harness h = pending(); TextToSpeech.latest().complete(0); Handler.drain(); return h; }
    private static void run(String name, Checked body) throws Exception {
        Handler.reset(); TextToSpeech.reset(); SESSION.invalidate(); VisibleMessageService.current = true; active = null;
        try { body.run(); cases++; System.out.println("PASS " + name); }
        catch (Throwable failure) { throw new AssertionError("FAIL " + name + ": " + failure.getMessage(), failure); }
        finally { if (active != null) active.panel.destroy(); Handler.reset(); SESSION.invalidate(); }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual, String message) {
        check(java.util.Objects.equals(expected, actual), message + "; expected=" + expected + ", actual=" + actual);
    }
    private static void invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(target);
    }
    private static void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void report() {
        System.out.println("PASS actual AssistantPanel offline TTS tests: " + cases + " cases, " + assertions + " assertions (fake Android boundaries; no real audio/network/device behavior)");
    }
    private static final class Harness {
        final Context context = new Context();
        final AssistantPanel panel = new AssistantPanel(context, () -> false, (token, english) -> { throw new AssertionError("No insertion expected"); });
        final LinearLayout root = new LinearLayout(context);
        final AssistantRecorder recorder = AssistantRecorder.latest;
        final Button speech, reply;
        final TextView output;
        Harness() throws Exception {
            active = this; panel.addTo(root); panel.editorChanged(true); Handler.drain();
            output = (TextView)((ScrollView)root.children.get(0)).child;
            speech = find(root, "中文朗读"); reply = find(root, "口述回复"); activate("Synthetic Chinese explanation");
        }
        void activate(String explanation) throws Exception {
            long token = SESSION.start();
            SESSION.capture(token, AssistantSession.WHATSAPP, "SYNTHETIC_TTS_TEST", true, true, Collections.singletonList("Synthetic message"));
            SESSION.accept(token, explanation, "Synthetic reply"); field(panel, "shownToken", token);
        }
        void speak() { speech.performClick(); }
        void record() { reply.performClick(); }
        String text() { return output.getText().toString(); }
        private static Button find(LinearLayout root, String text) {
            for (View child : root.children) {
                if (child instanceof Button button && text.contentEquals(button.getText())) return button;
                if (child instanceof LinearLayout row) { Button found = find(row, text); if (found != null) return found; }
            }
            return null;
        }
    }
}
