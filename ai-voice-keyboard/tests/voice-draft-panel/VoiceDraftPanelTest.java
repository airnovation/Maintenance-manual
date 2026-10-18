package com.airnovation.aivoicekeyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Actual production panel, with synthetic text/audio files and deterministic boundary fakes. */
public final class VoiceDraftPanelTest {
    interface Checked { void run() throws Exception; }
    static int cases, assertions;
    static final List<Harness> harnesses = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        run("idle has one clear capture action, read-only bounded preview, and no work", () -> {
            Harness h = new Harness();
            equal(0, h.recorder.startCalls, "construct/open never records");
            equal(0, h.transport.requests.size(), "construct/open never uploads");
            equal(0, h.inserts, "construct/open never inserts");
            check(!h.panel.active(), "idle not active");
            check(h.text().contains("录音及转写交给Gemini整理；不读取聊天，确认后才插入，发送由你操作"), "inline disclosure visible");
            equal(List.of("开始说话", "取消", "确认填入", "修改上一稿"), h.labels(), "only requested controls present");
            check(!h.button("修改上一稿").isEnabled(), "revision unavailable without previous draft");
            check(!h.button("确认填入").isEnabled(), "insertion unavailable without preview");
            ScrollView preview = (ScrollView) h.root.children.get(1);
            equal(144, h.root.params.get(preview).height, "preview has a fixed height");
            check(h.views().stream().noneMatch(v -> v.getClass().getSimpleName().equals("EditText")), "no editable nested input");
            check(!preview.saveEnabled && !preview.child.saveEnabled, "preview cannot restore text from saved state");
            equal(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, preview.child.importantForAutofill, "preview excluded from autofill");
        });
        run("preview height adapts to landscape with readable minimum and fixed maximum", () -> {
            Harness compact = new Harness(360, 1f);
            equal(65, compact.root.params.get(compact.root.children.get(1)).height, "landscape preview about eighteen percent of height");
            Harness tiny = new Harness(200, 1f);
            equal(64, tiny.root.params.get(tiny.root.children.get(1)).height, "tiny preview retains sixty-four dp minimum");
            Harness dense = new Harness(2400, 3f);
            equal(432, dense.root.params.get(dense.root.children.get(1)).height, "density scales the one-hundred-forty-four dp maximum");
        });
        run("explicit start captures, explicit finish uploads, explicit insert commits once", () -> {
            Harness h = new Harness(); h.start();
            equal(1, h.recorder.startCalls, "first click captures exactly once");
            equal(1, h.beginCalls, "fresh editor is bound only at explicit start");
            equal(0, h.transport.requests.size(), "recording alone never uploads");
            check(h.panel.active(), "recording owns panel");
            h.finish();
            equal(1, h.transport.requests.size(), "finish uploads exactly once");
            equal(null, h.transport.requests.get(0).previous(), "new request has no prior draft");
            check(!h.button("开始说话").isEnabled(), "cannot start duplicate request while processing");
            equal(0, h.inserts, "upload never inserts");
            h.complete("Synthetic transcript", "Synthetic draft");
            check(h.text().contains("Synthetic transcript") && h.text().contains("Synthetic draft"), "both transcript and draft reviewed");
            equal(0, h.inserts, "model completion never inserts");
            equal(1, h.beginCalls, "finish and callbacks do not rebind editor");
            h.click("确认填入");
            equal(1, h.inserts, "single explicit click inserts");
            equal("Synthetic draft", h.inserted, "exact reviewed draft passed to host");
            check(!h.panel.active(), "insertion clears active session"); h.assertCleared("Synthetic");
            check(!h.button("确认填入").performClick(), "second click cannot duplicate insertion");
            equal(1, h.inserts, "insertion remains once");
        });
        run("revision is explicit and only snapshots the reviewed prior draft", () -> {
            Harness h = preview(); h.click("修改上一稿");
            equal(2, h.recorder.startCalls, "revision starts a new explicit recording");
            check(!h.text().contains("DRAFT_ONE"), "old draft hidden during revision");
            check(!h.button("确认填入").isEnabled(), "cannot insert stale draft while recording");
            h.finish();
            equal("DRAFT_ONE", h.transport.requests.get(1).previous(), "revision payload uses exact prior draft");
            equal("", h.previous(), "panel releases snapshot after transport takes ownership");
            h.transport.complete(1, "Make it shorter", "DRAFT_TWO", null);
            h.click("确认填入"); equal("DRAFT_TWO", h.inserted, "only revised reviewed draft inserted");
        });
        run("fresh recording never attaches previous draft", () -> {
            Harness h = preview(); h.start(); h.finish();
            equal(null, h.transport.requests.get(1).previous(), "fresh button cannot implicitly revise");
            check(h.session.draft().isEmpty(), "new recording cleared old session draft");
        });
        run("fresh start can rebind after legacy generation change but revision cannot", () -> {
            Harness h = preview(); h.current = false; h.start();
            equal(2, h.beginCalls, "explicit fresh start rebinds editor generation");
            equal(2, h.recorder.startCalls, "fresh start proceeds with new binding");
            h.panel.stop();
            Harness r = preview(); r.current = false; r.click("修改上一稿");
            equal(1, r.beginCalls, "stale revision must not rebind");
            equal(1, r.recorder.startCalls, "stale revision must not record"); r.assertCleared("DRAFT_ONE");
        });
        run("wrong provider prevents recording and never reads key", () -> {
            Harness h = new Harness(); SharedPreferences.provider = "openai"; h.start();
            equal(0, SecureSettings.reads, "wrong provider does not access key");
            equal(0, h.recorder.startCalls, "wrong provider does not record");
            equal(0, h.beginCalls, "wrong provider does not bind an editor");
        });
        run("missing key blocks before recording", () -> {
            Harness h = new Harness(); SecureSettings.key = ""; h.start();
            equal(0, h.recorder.startCalls, "missing key does not record");
            equal(0, h.beginCalls, "missing key does not bind an editor");
        });
        run("legacy operation or disallowed editor blocks before credentials", () -> {
            Harness h = new Harness(); h.legacy = true; h.start();
            equal(0, h.recorder.startCalls, "legacy work prevents simultaneous capture");
            equal(0, SecureSettings.reads, "legacy busy checked before credentials");
            h.legacy = false; h.panel.editorChanged(false); h.start();
            equal(0, h.recorder.startCalls, "sensitive/missing editor cannot record");
            equal(0, SecureSettings.reads, "editor allowance checked before credentials");
        });
        run("failed fresh editor binding or microphone permission cannot leave active session", () -> {
            Harness h = new Harness(); h.beginAllowed = false; h.start();
            equal(0, h.recorder.startCalls, "failed editor binding prevents microphone");
            h.assertCleared("Synthetic");
            h.beginAllowed = true; h.recorder.startError = "Synthetic permission denied"; h.start();
            equal(1, h.recorder.startCalls, "only explicit ready action tries recorder");
            check(!h.panel.active(), "permission error invalidates session");
            equal(0, h.transport.requests.size(), "permission error never uploads");
        });
        run("provider or editor change before finish cancels without upload", () -> {
            Harness h = new Harness(); h.start(); SharedPreferences.provider = "openai"; h.finish();
            equal(0, h.transport.requests.size(), "provider change cancels capture");
            check(!h.recorder.active, "provider change releases recorder");
            SharedPreferences.provider = "gemini";
            Harness e = new Harness(); e.start(); e.current = false; e.finish();
            equal(0, e.transport.requests.size(), "editor change cancels capture"); e.assertCleared("Synthetic");
        });
        run("60 second limit cancels without uploading or retaining old draft", () -> {
            Harness h = preview(); h.click("修改上一稿"); h.recorder.limit.run();
            equal(1, h.transport.requests.size(), "limit did not upload revision");
            check(!h.recorder.active, "limit releases microphone");
            h.assertCleared("DRAFT_ONE"); equal("", h.previous(), "limit clears revision snapshot");
        });
        run("recording errors delete audio and clear session", () -> {
            Harness h = new Harness(); h.start(); h.recorder.finishError = "Synthetic short recording"; h.finish();
            check(!h.recorder.file.exists(), "bad audio file deleted");
            equal(0, h.transport.requests.size(), "failed recording never uploads");
            check(!h.panel.active(), "failed recording does not keep session active");
        });
        run("ambiguous initial transcript shows error and cannot insert or revise", () -> {
            Harness h = new Harness(); h.start(); h.finish();
            h.transport.complete(0, "Make this more polite", null, "请先说出要表达的具体内容。");
            check(h.text().contains("Make this more polite"), "recognized words visible for correction");
            check(!h.button("确认填入").isEnabled(), "ambiguous request has no insertion path");
            check(!h.button("修改上一稿").isEnabled(), "ambiguous request does not invent previous draft");
            equal(0, h.inserts, "error never inserts");
        });
        run("revision provider error cannot insert the old draft", () -> {
            Harness h = preview(); h.click("修改上一稿"); h.finish();
            h.transport.complete(1, "Revision speech", null, "Synthetic provider failure");
            check(!h.text().contains("DRAFT_ONE"), "old draft not presented as new result");
            check(!h.button("确认填入").isEnabled(), "old draft cannot be inserted from error state");
            h.panel.stop(); h.assertCleared("DRAFT_ONE");
        });
        run("missing revision result cannot expose old draft even without a provider error", () -> {
            Harness h = preview(); h.click("修改上一稿"); h.finish();
            h.transport.complete(1, "Revision speech", null, null);
            check(!h.text().contains("DRAFT_ONE"), "missing result never presented as old draft success");
            check(!h.button("确认填入").isEnabled(), "missing result cannot insert old draft");
            check(!h.button("修改上一稿").isEnabled(), "missing result cannot carry old context into another revision");
        });
        run("invalid oversized transcript never renders unbounded text or enables insertion", () -> {
            Harness h = new Harness(); h.start(); h.finish();
            h.complete("x".repeat(VoiceDraftSession.MAX_TRANSCRIPT_CHARS + 1), "Synthetic draft");
            check(h.text().length() < 1000, "oversized text is not rendered into preview");
            check(!h.button("确认填入").isEnabled(), "malformed result cannot insert");
        });
        run("fresh editor check prevents both callback display and insertion", () -> {
            Harness h = new Harness(); h.start(); h.finish(); h.current = false;
            h.complete("STALE_TRANSCRIPT", "STALE_DRAFT"); h.assertCleared("STALE");
            Harness p = preview(); p.current = false; p.click("确认填入");
            equal(0, p.inserts, "stale editor never reaches host insertion"); p.assertCleared("DRAFT_ONE");
        });
        run("host insertion failure retains review and success clears it", () -> {
            Harness h = preview(); h.insertError = "Synthetic host unavailable"; h.click("确认填入");
            check(h.text().contains("DRAFT_ONE"), "host failure keeps draft for review");
            check(h.panel.active(), "failed insertion retains preview");
            h.insertError = null; h.click("确认填入");
            equal(2, h.inserts, "explicit retry only"); h.assertCleared("DRAFT_ONE");
        });
        run("uncertain host insertion invalidates immediately and prevents repeat commit", () -> {
            Harness h = preview(); h.invalidateOnInsert = true;
            h.insertError = "请查看当前输入框，避免重复填入。";
            h.click("确认填入");
            equal(1, h.inserts, "attempted once");
            check(h.text().contains("避免重复填入"), "uncertain outcome instruction retained");
            check(!h.button("确认填入").performClick(), "cannot blindly repeat uncertain commit");
            h.assertCleared("DRAFT_ONE");
        });
        for (String exit : List.of("cancel", "editor", "lock", "stop", "destroy")) {
            run(exit + " clears transcript, draft, snapshot, recorder and late response", () -> {
                Harness h = preview(); h.click("修改上一稿");
                check(!h.previous().isEmpty(), "revision has a pending snapshot before exit");
                h.exit(exit); h.assertCleared("DRAFT_ONE");
                check(!h.recorder.active, "exit cancels recorder"); equal("", h.previous(), "exit clears snapshot");
                h.transport.complete(0, "LATE_TRANSCRIPT", "LATE_DRAFT", null); h.assertCleared("LATE");
            });
            run(exit + " suppresses in-flight response", () -> {
                Harness h = new Harness(); h.start(); h.finish(); h.exit(exit);
                h.transport.complete(0, "LATE_TRANSCRIPT", "LATE_DRAFT", null); h.assertCleared("LATE");
            });
        }
        run("old callback cannot overwrite a newer request or finished preview", () -> {
            Harness h = new Harness(); h.start(); h.finish(); h.click("取消"); h.start(); h.finish();
            h.transport.complete(0, "OLD_TRANSCRIPT", "OLD_DRAFT", null);
            check(!h.text().contains("OLD"), "old generation ignored");
            h.transport.complete(1, "NEW_TRANSCRIPT", "NEW_DRAFT", null);
            h.transport.complete(1, "DUPLICATE_TRANSCRIPT", "DUPLICATE_DRAFT", null);
            check(h.text().contains("NEW_DRAFT") && !h.text().contains("DUPLICATE"), "duplicate completion ignored");
        });
        run("deferred recorder callback after exit deletes file without upload", () -> {
            Harness h = new Harness(); h.recorder.deferFinish = true; h.start(); h.finish();
            File file = h.recorder.file; check(file.exists(), "synthetic recording waits for completion");
            h.click("取消"); h.recorder.completeFinish();
            check(!file.exists(), "stale recording file deleted");
            equal(0, h.transport.requests.size(), "stale recorder callback cannot upload");
        });
        run("multiple panels never share transcripts or drafts", () -> {
            Harness first = preview(); Harness second = new Harness();
            check(second.session.draft().isEmpty(), "new panel has no previous panel draft");
            check(!second.button("修改上一稿").isEnabled(), "new panel cannot revise another panel draft");
            first.panel.stop(); check(!second.panel.active(), "other panel remains idle");
        });
        run("destroy unregisters receiver and prevents new recordings", () -> {
            Harness h = new Harness(); h.panel.destroy(); h.panel.destroy();
            equal(null, h.context.receiver, "receiver unregistered once");
            check(!h.button("开始说话").performClick(), "destroyed start disabled");
            equal(0, h.recorder.startCalls, "destroyed panel never records");
        });
        System.out.println("PASS actual VoiceDraftPanel: " + cases + " cases, " + assertions + " assertions (host-only synthetic boundaries)");
    }

    static Harness preview() throws Exception {
        Harness h = new Harness(); h.start(); h.finish(); h.complete("TRANSCRIPT_ONE", "DRAFT_ONE"); return h;
    }
    static void run(String name, Checked body) throws Exception {
        SharedPreferences.provider = "gemini"; SharedPreferences.reads = 0; SecureSettings.reset();
        try { body.run(); cases++; System.out.println("PASS " + name); }
        catch (Throwable failure) { throw new AssertionError("FAIL " + name + ": " + failure.getMessage(), failure); }
        finally {
            for (Harness h : harnesses) { h.panel.destroy(); if (h.recorder.file != null) h.recorder.file.delete(); }
            harnesses.clear();
        }
    }
    static void check(boolean value, String reason) { assertions++; if (!value) throw new AssertionError(reason); }
    static void equal(Object expected, Object actual, String reason) {
        check(java.util.Objects.equals(expected, actual), reason + "; expected=" + expected + ", actual=" + actual);
    }
    static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    static final class Harness {
        final Context context = new Context();
        boolean legacy, current = true, beginAllowed = true, invalidateOnInsert;
        int beginCalls, inserts;
        String inserted, insertError;
        final VoiceDraftPanel panel = new VoiceDraftPanel(context, () -> legacy,
                () -> { beginCalls++; if (beginAllowed) current = true; return beginAllowed; },
                () -> current, text -> { inserts++; inserted = text; if (invalidateOnInsert) current = false; return insertError; });
        final LinearLayout root = new LinearLayout(context);
        final VoiceDraftTransport transport = VoiceDraftTransport.latest;
        final AssistantRecorder recorder = AssistantRecorder.latest;
        final VoiceDraftSession session;
        Harness() throws Exception { this(1000, 1f); }
        Harness(int heightPixels, float density) throws Exception {
            context.resources.getDisplayMetrics().heightPixels = heightPixels;
            context.resources.getDisplayMetrics().density = density;
            harnesses.add(this); session = (VoiceDraftSession) field(panel, "session");
            panel.addTo(root); panel.editorChanged(true);
        }
        void start() { click("开始说话"); }
        void finish() throws Exception {
            recorder.file = File.createTempFile("voice-draft-synthetic-", ".m4a"); click("结束并整理");
        }
        void complete(String transcript, String draft) { transport.complete(transport.requests.size() - 1, transcript, draft, null); }
        void click(String label) { Button button = button(label); check(button.isShown(), label + " visible"); check(button.performClick(), label + " enabled and wired"); }
        Button button(String label) {
            for (View view : views()) if (view instanceof Button button && label.contentEquals(button.getText())) return button;
            throw new AssertionError("Missing button: " + label);
        }
        List<String> labels() { return views().stream().filter(Button.class::isInstance).map(v -> ((Button) v).getText().toString()).toList(); }
        List<View> views() { ArrayList<View> result = new ArrayList<>(); collect(root, result); return result; }
        void collect(View view, List<View> result) {
            result.add(view);
            if (view instanceof LinearLayout layout) for (View child : layout.children) collect(child, result);
            if (view instanceof ScrollView scroll && scroll.child != null) collect(scroll.child, result);
        }
        String text() {
            StringBuilder result = new StringBuilder();
            for (View view : views()) if (view instanceof TextView text) result.append(text.getText()).append('\n');
            return result.toString();
        }
        String previous() throws Exception { return (String) field(panel, "previousDraft"); }
        void assertCleared(String forbidden) {
            check(!text().contains(forbidden), "old text absent from UI");
            check(session.draft().isEmpty() && session.transcript().isEmpty(), "session RAM text cleared");
            check(!panel.active(), "session not active");
        }
        void exit(String method) {
            switch (method) {
                case "cancel" -> click("取消");
                case "editor" -> panel.editorChanged(true);
                case "lock" -> context.receiver.onReceive(context, new Intent(Intent.ACTION_SCREEN_OFF));
                case "stop" -> panel.stop();
                case "destroy" -> panel.destroy();
                default -> throw new AssertionError("Unknown exit");
            }
        }
    }
}
