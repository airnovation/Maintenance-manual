package com.airnovation.aivoicekeyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** Production panel with synthetic local text and an in-memory provider spy, never real messages/network. */
public final class AssistantPanelPreviewTest {
    interface Checked { void run() throws Exception; }
    static final AssistantSession SESSION = AssistantSession.INSTANCE;
    static Harness active;
    static int cases, assertions;
    static final String CONFIRM = "确认上传";
    public static void main(String[] args) throws Exception {
        run("warning must precede local capture; preview does not inspect key or provider", () -> {
            Harness h = new Harness(); SharedPreferences.provider = "openai";
            h.start(); equal(0, VisibleMessageService.captureCalls, "first start only shows disclosure");
            check(h.allText().contains("Android提供的当前消息文字，可能含被浮层遮住部分"), "overlay visibility limitation is disclosed before reading");
            check(h.allText().contains("仅本机预览；确认所选内容后才交给Gemini"), "local-read and external-send consents are separated");
            h.acceptWarning();
            equal(1, VisibleMessageService.captureCalls, "second explicit click captures once");
            equal(0, SharedPreferences.reads, "preview does not inspect provider");
            equal(0, SecureSettings.reads, "preview needs no key");
            equal(0, h.transport.requestCalls, "preview does not contact provider");
            equal(2, h.candidates().size(), "each synthetic candidate has a read-only text preview");
            equal(2, h.checks().size(), "each synthetic candidate has a checkbox");
            check(h.checks().stream().allMatch(CheckBox::isChecked), "candidates initially selected for explicit review");
            equal("Synthetic candidate one", h.candidates().get(0).getText().toString(), "preview shows candidate text");
            check(SESSION.visible(SESSION.token()).isEmpty(), "unreviewed candidates are not approved provider payload");
            check(h.views().stream().noneMatch(EditText.class::isInstance), "selection-only preview creates no editable field or input connection");
            for (TextView text : h.candidates()) {
                check(!text.saveEnabled, "preview text opts out of saved view state");
                equal(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS, text.importantForAutofill, "preview text opts out of autofill");
            }
            h.button(CONFIRM);
        });
        run("only selected unchanged text reaches synthetic provider after explicit confirmation", () -> {
            Harness h = preview(); h.providerReady();
            h.checks().get(0).setChecked(false); h.checks().get(1).setChecked(true);
            equal(0, h.transport.requestCalls, "selection alone never submits");
            h.confirm();
            equal(1, h.transport.requestCalls, "explicit confirmation submits once");
            equal(List.of("Synthetic candidate two"), h.transport.lastMessages, "selected original text is exact and excluded text absent");
            check(VisibleMessageService.reviewCurrentCalls > 0, "fresh metadata checked before provider");
            h.transport.complete("Synthetic explanation", null); Handler.drain();
            check(h.allText().contains("Synthetic explanation"), "current response displayed");
        });
        run("local preview cannot start microphone or speech before confirmation", () -> {
            Harness h = preview();
            h.click("口述回复"); h.click("中文朗读");
            equal(0, AssistantRecorder.latest.startCalls, "unapproved preview cannot record");
            equal(0, TextToSpeech.instances.size(), "unapproved preview cannot speak");
            equal(0, h.transport.requestCalls, "other actions cannot bypass confirmation");
            equal(0, h.transport.transcribeCalls, "unapproved preview cannot upload audio");
        });
        run("wrong provider blocks confirmation but not local preview", () -> {
            Harness h = preview(); h.selectAll(); SharedPreferences.provider = "openai"; h.confirm();
            equal(0, h.transport.requestCalls, "wrong provider never submits");
            equal(0, SecureSettings.reads, "wrong provider does not read key");
        });
        run("missing key blocks confirmation", () -> {
            Harness h = preview(); h.selectAll(); SecureSettings.allowFakeKeyRead = true; h.confirm();
            equal(0, h.transport.requestCalls, "missing key never submits");
            check(SecureSettings.reads > 0, "confirmation checks configured key");
        });
        run("deselected review cannot submit", () -> {
            Harness h = preview(); h.providerReady();
            for (CheckBox box : h.checks()) box.setChecked(false);
            h.confirm(); equal(0, h.transport.requestCalls, "empty selection does not request provider");
            check(SESSION.visible(SESSION.token()).isEmpty(), "empty selection does not create approved payload");
        });
        run("oversized local fixture is bounded before preview and upload", () -> {
            VisibleMessageService.reviewFixture = List.of("a".repeat(2000), "b".repeat(2000), "c");
            Harness h = preview(); h.providerReady(); h.selectAll();
            equal(2, h.candidates().size(), "only bounded candidates rendered");
            equal(2000, h.candidates().get(0).getText().length(), "first original candidate retained");
            equal(1000, h.candidates().get(1).getText().length(), "last candidate bounded visibly before consent");
            h.confirm(); equal(List.of("a".repeat(2000), "b".repeat(1000)), h.transport.lastMessages,
                    "provider receives exact text that user saw and selected");
        });
        run("initial candidate count is visibly bounded before confirmation", () -> {
            ArrayList<String> fixture = new ArrayList<>();
            for (int i = 0; i < AssistantSession.MAX_MESSAGES + 3; i++) fixture.add("Synthetic row " + i);
            VisibleMessageService.reviewFixture = fixture;
            Harness h = preview(); h.providerReady(); h.selectAll();
            equal(AssistantSession.MAX_MESSAGES, h.candidates().size(), "preview renders bounded row count");
            h.confirm(); equal(fixture.subList(0, AssistantSession.MAX_MESSAGES), h.transport.lastMessages,
                    "only rendered and selected rows are submitted");
        });
        run("metadata change after request discards current-token response", () -> {
            Harness h = preview(); h.providerReady(); h.selectAll(); h.confirm();
            VisibleMessageService.current = false;
            h.transport.complete("STALE_SYNTHETIC_RESPONSE", "STALE_SYNTHETIC_DRAFT"); Handler.drain();
            check(!h.allText().contains("STALE_SYNTHETIC"), "fresh response metadata check prevents stale display");
            h.assertWiped();
        });
        run("metadata change at confirmation refuses upload and wipes preview", () -> {
            Harness h = preview(); h.providerReady(); h.selectAll(); VisibleMessageService.current = false;
            h.confirm(); equal(0, h.transport.requestCalls, "changed chat never submits"); h.assertWiped();
        });
        for (String exit : List.of("close", "focus", "lock", "chat")) run(exit + " wipes local preview", () -> {
            Harness h = preview(); List<TextView> oldEdits = h.candidates(); h.exit(exit); h.assertWiped();
            for (TextView edit : oldEdits) equal("", edit.getText().toString(), "detached widgets also forget local text");
            equal(0, h.transport.requestCalls, "preview exit never submits");
        });
        for (String exit : List.of("close", "focus", "lock", "chat")) run(exit + " discards late synthetic provider callback", () -> {
            Harness h = preview(); h.providerReady(); h.selectAll(); h.confirm();
            equal(1, h.transport.requestCalls, "request was pending before exit"); h.exit(exit);
            h.transport.complete("STALE_SYNTHETIC_RESPONSE", "STALE_SYNTHETIC_DRAFT"); Handler.drain();
            check(!h.allText().contains("STALE_SYNTHETIC"), "late response never redisplays old content");
            check(SESSION.explanation().isEmpty() && SESSION.draft().isEmpty(), "late response never repopulates session");
        });
        System.out.println("PASS actual AssistantPanel local preview tests: " + cases + " cases, " + assertions + " assertions (synthetic boundaries only)");
    }
    static Harness preview() { Harness h = new Harness(); h.start(); h.acceptWarning(); return h; }
    static void run(String name, Checked body) throws Exception {
        Handler.reset(); TextToSpeech.reset(); SESSION.invalidate(); VisibleMessageService.reset(); SecureSettings.reset();
        SharedPreferences.provider = "gemini"; SharedPreferences.reads = 0; active = null;
        try { body.run(); cases++; System.out.println("PASS " + name); }
        catch (Throwable t) { throw new AssertionError("FAIL " + name + ": " + t.getMessage(), t); }
        finally { if (active != null) active.panel.destroy(); Handler.reset(); SESSION.invalidate(); }
    }
    static void check(boolean value, String why) { assertions++; if (!value) throw new AssertionError(why); }
    static void equal(Object expected, Object actual, String why) { check(java.util.Objects.equals(expected, actual), why + "; expected=" + expected + ", actual=" + actual); }
    static final class Harness {
        final Context context = new Context();
        final AssistantPanel panel = new AssistantPanel(context, () -> false, (token, text) -> { throw new AssertionError("No insertion authorized"); });
        final LinearLayout root = new LinearLayout(context);
        final GeminiAssistantTransport transport = GeminiAssistantTransport.latest;
        Harness() { active = this; panel.addTo(root); panel.editorChanged(true); Handler.drain(); }
        void start() { click("启动助手"); }
        void acceptWarning() {
            // First control is the explicit local-read consent button after Start.
            LinearLayout controls = (LinearLayout) root.children.get(1);
            Button consent = (Button) controls.children.get(0);
            check(!"启动助手".contentEquals(consent.getText()), "warning acceptance is a distinct labeled action");
            check(consent.isShown(), "warning acceptance visible"); consent.performClick();
        }
        void providerReady() { SecureSettings.allowFakeKeyRead = true; SecureSettings.key = "SYNTHETIC_NON_SECRET"; transport.allowSyntheticRequests = true; }
        void selectAll() { for (CheckBox box : checks()) box.setChecked(true); }
        void confirm() { click(CONFIRM); }
        void click(String label) { Button b = button(label); check(b.isShown(), label + " shown"); check(b.performClick(), label + " wired"); }
        Button button(String label) { for (View v : views()) if (v instanceof Button b && label.contentEquals(b.getText())) return b; throw new AssertionError("Missing button: " + label); }
        List<TextView> candidates() {
            LinearLayout content = (LinearLayout)((ScrollView)root.children.get(0)).child;
            LinearLayout rows = (LinearLayout)content.children.get(1);
            return rows.children.stream().filter(v -> v instanceof TextView && !(v instanceof Button)).map(TextView.class::cast).toList();
        }
        List<CheckBox> checks() { return views().stream().filter(CheckBox.class::isInstance).map(CheckBox.class::cast).toList(); }
        List<View> views() { ArrayList<View> list = new ArrayList<>(); collect(root, list); return list; }
        void collect(View v, List<View> result) { result.add(v); if (v instanceof LinearLayout l) for (View child : l.children) collect(child, result); if (v instanceof ScrollView s && s.child != null) collect(s.child, result); }
        String allText() { StringBuilder text = new StringBuilder(); for (View v : views()) if (v instanceof TextView t) text.append(t.getText()).append('\n'); return text.toString(); }
        void exit(String exit) {
            switch (exit) {
                case "close" -> click("关闭");
                case "focus" -> panel.editorChanged(false);
                case "lock" -> context.receiver.onReceive(context, new Intent(Intent.ACTION_SCREEN_OFF));
                case "chat" -> { SESSION.invalidate(); Handler.tick(); }
            }
        }
        void assertWiped() {
            check(!SESSION.active(), "session invalidated");
            check(candidates().isEmpty(), "candidate read-only views removed");
            check(!allText().contains("Synthetic candidate"), "candidate text removed from UI");
            check(SESSION.visible(SESSION.token()).isEmpty(), "approved payload empty");
            check(SESSION.reviewCandidates(SESSION.token()).isEmpty(), "local candidate RAM cleared");
        }
    }
}
