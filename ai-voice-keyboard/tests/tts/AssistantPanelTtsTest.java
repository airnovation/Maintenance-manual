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
        for (boolean darkDefaults : List.of(false, true)) run("fixed output contrast with " + (darkDefaults ? "dark" : "light") + " fake theme defaults", () -> {
            // Deliberately vary inherited colors; this verifies explicit color wiring,
            // not actual Android theme rendering or device contrast.
            TextView.defaultTextColor = darkDefaults ? 0xFFFFFFFF : 0xFF222222;
            View.defaultBackgroundColor = darkDefaults ? 0xFF111111 : 0x00000000;
            Harness h = new Harness(false);
            check(h.text().contains("助手默认关闭"), "initial status is tested before any transition");
            fixedContrast(h.output, "initial status");
            Harness.find(h.root, "启动助手").performClick();
            equal("请在WhatsApp或WhatsApp Business普通消息输入框打开。助手未启动。", h.text(), "rejected editor names both supported apps");
            fixedContrast(h.output, "not-started error");
            h.panel.editorChanged(true);
            clickShown(Harness.find(h.root, "启动助手"), "start before demo");
            clickShown(Harness.find(h.root, "仅模拟演示（不联网）"), "demo"); Handler.drain();
            check(h.text().contains("模拟消息，不含真实聊天"), "normal synthetic demo status is shown");
            fixedContrast(h.output, "synthetic demo");
            equal(0, h.recorder.startCalls, "demo starts no recording");
            equal(0, TextToSpeech.instances.size(), "demo does not automatically initialize speech");
        });
        run("visible demo button renders fixture and reply while refusing real insertion", () -> {
            Harness h = new Harness(false); h.panel.editorChanged(true); Handler.drain();
            ScrollView scroll = (ScrollView)h.root.children.get(0);
            check(!scroll.isShown() && !h.output.isShown(), "closed panel hides output through scroll ancestor");
            clickShown(Harness.find(h.root, "启动助手"), "start");
            check(h.text().contains("本次助手会话"), "start renders disclosure without capture");
            clickShown(Harness.find(h.root, "仅模拟演示（不联网）"), "synthetic demo"); Handler.drain();
            equal(View.VISIBLE, scroll.getVisibility(), "demo expands scroll");
            equal(View.VISIBLE, h.output.getVisibility(), "demo output is visible");
            check(scroll.isShown() && h.output.isShown(), "demo output has no hidden ancestor");
            check(SESSION.active(), "demo button activates synthetic session");
            check(h.text().contains("模拟消息，不含真实聊天"), "demo source is labeled synthetic");
            check(h.text().contains("Are you available tomorrow at 3 pm?"), "preset English message is rendered");
            check(h.text().contains("对方想知道你明天下午三点是否方便"), "preset Chinese explanation is rendered");
            check(h.text().contains("口述回复按钮只展示模拟英文草稿"), "English-preview next step is explained");
            check(h.text().contains("不读取、不录音、不联网"), "demo boundaries are visible");
            fixedContrast(h.output, "rendered demo");
            String english = "Thanks! I'll check my schedule and get back to you.";
            equal(english, SESSION.draft(), "preset English draft is prepared locally");
            clickShown(h.reply, "synthetic oral reply");
            check(h.output.isShown() && scroll.isShown(), "oral preview remains visible");
            check(h.text().contains("模拟口述意图：我先确认时间再回复你"), "reply renders preset Chinese intent");
            check(h.text().contains("英文预览") && h.text().contains(english), "reply renders preset English preview");
            check(h.text().contains("仅模拟，未录音或联网"), "reply remains labeled synthetic");
            fixedContrast(h.output, "rendered synthetic reply");
            Button insert = Harness.find(h.root, "插入到当前框");
            clickShown(insert, "synthetic insert refusal");
            check(h.text().contains("模拟演示不能插入真实聊天") && h.text().contains(english), "demo insert is refused with draft visible");
            clickShown(insert, "repeated synthetic insert refusal");
            check(h.text().contains("模拟演示不能插入真实聊天"), "repeated insert cannot bypass demo gate");
            equal(0, h.manualInsertCalls, "no actual insertion callback is invoked");
            equal(0, h.recorder.startCalls, "oral demo never starts recorder");
            equal(0, h.recorder.finishCalls, "oral demo never finishes or uploads recorder");
            check(!h.recorder.active, "demo recorder stays inactive");
            equal(0, GeminiAssistantTransport.latest.requestCalls, "demo never requests transport");
            equal(0, GeminiAssistantTransport.latest.transcribeCalls, "demo never transcribes audio");
            equal(0, TextToSpeech.instances.size(), "demo does not automatically speak");
            clickShown(Harness.find(h.root, "关闭"), "close demo");
            check(!scroll.isShown() && !h.output.isShown(), "close hides demo output through scroll ancestor");
            check(!SESSION.active(), "close clears demo session");
        });
        run("demo in disallowed editor renders a visible refusal", () -> {
            Harness h = new Harness(false);
            clickShown(Harness.find(h.root, "启动助手"), "start in disallowed editor");
            clickShown(Harness.find(h.root, "仅模拟演示（不联网）"), "demo in disallowed editor");
            check(h.output.isShown(), "refusal is visible instead of silently hidden");
            check(h.text().contains("WhatsApp或WhatsApp Business") && h.text().contains("助手未启动"), "refusal explains supported message fields");
            check(!SESSION.active(), "disallowed editor does not activate demo");
            equal(0, h.recorder.startCalls, "refused demo does not record");
            equal(0, GeminiAssistantTransport.latest.requestCalls, "refused demo does not request transport");
            fixedContrast(h.output, "visible demo refusal");
        });
        run("local diagnostic click displays metadata and ends its session", () -> {
            Harness h = diagnosticHarness();
            clickShown(Harness.find(h.root, "本机诊断（不读文字）"), "local diagnostic");
            equal(1, VisibleMessageService.diagnosticCalls, "only explicit diagnostic entry is called");
            check(VisibleMessageService.diagnosticSawEmptySession, "diagnostic receives fresh context-free session");
            equal(VisibleMessageService.diagnosticResult, h.text(), "metadata report is displayed unchanged");
            check(h.text().contains("窗口数量：2") && h.text().contains("节点数量：18") && h.text().contains("消息候选：3"), "synthetic counts are visible");
            fixedContrast(h.output, "local diagnostic report");
            diagnosticQuiet(h);
            Handler.tick(); equal(VisibleMessageService.diagnosticResult, h.text(), "timer preserves finished diagnostic report");
            equal("启动助手", Harness.find(h.root, "启动助手").getText().toString(), "prior reading consent is reset");
        });
        run("repeated local diagnostics get new one-shot sessions", () -> {
            Harness h = diagnosticHarness(); Button diagnostic = Harness.find(h.root, "本机诊断（不读文字）");
            clickShown(diagnostic, "first diagnostic"); long firstToken = VisibleMessageService.lastDiagnosticToken;
            VisibleMessageService.diagnosticResult = "本机诊断（不读文字）\n窗口数量：1\n节点数量：7\n消息候选：0";
            clickShown(diagnostic, "second diagnostic");
            equal(2, VisibleMessageService.diagnosticCalls, "each explicit click runs once");
            check(VisibleMessageService.lastDiagnosticToken != firstToken, "repeated diagnostic does not reuse old session token");
            equal(VisibleMessageService.diagnosticResult, h.text(), "latest count report replaces older report");
            diagnosticQuiet(h); Handler.tick(); equal(VisibleMessageService.diagnosticResult, h.text(), "timer preserves latest report");
        });
        run("unavailable diagnostic service gives visible setup guidance", () -> {
            Harness h = diagnosticHarness(); VisibleMessageService.serviceAvailable = false;
            clickShown(Harness.find(h.root, "本机诊断（不读文字）"), "unavailable diagnostic");
            equal(0, VisibleMessageService.diagnosticCalls, "unavailable service is not called");
            check(h.text().contains("本机诊断需要先启用无障碍服务") && h.text().contains("权限设置"), "missing service is explained visibly");
            String guidance = h.text(); diagnosticQuiet(h); Handler.tick(); equal(guidance, h.text(), "timer preserves service guidance");
        });
        run("diagnostic refuses a disallowed editor without inspecting metadata", () -> {
            Harness h = new Harness(false);
            clickShown(Harness.find(h.root, "启动助手"), "start in unsupported editor");
            clickShown(Harness.find(h.root, "本机诊断（不读文字）"), "diagnostic in unsupported editor");
            equal(0, VisibleMessageService.diagnosticCalls, "editor rejection prevents diagnostics");
            check(h.text().contains("WhatsApp或WhatsApp Business") && h.text().contains("助手未启动"), "unsupported editor refusal is explicit");
            diagnosticQuiet(h);
        });
        run("diagnostic refuses while legacy microphone or EN work is busy", () -> {
            Harness h = new Harness(false); h.panel.editorChanged(true); h.legacyBusy = true;
            clickShown(Harness.find(h.root, "启动助手"), "start while legacy busy");
            clickShown(Harness.find(h.root, "本机诊断（不读文字）"), "diagnostic while legacy busy");
            equal(0, VisibleMessageService.diagnosticCalls, "legacy work prevents diagnostic start");
            check(h.text().contains("请先结束现有Mic/EN操作"), "legacy busy refusal is explicit");
            diagnosticQuiet(h);
        });
        run("diagnostic exception clears session and shows bounded failure", () -> {
            Harness h = diagnosticHarness(); VisibleMessageService.diagnosticThrows = true; VisibleMessageService.diagnosticInvalidates = false;
            clickShown(Harness.find(h.root, "本机诊断（不读文字）"), "throwing diagnostic");
            equal(1, VisibleMessageService.diagnosticCalls, "one failed attempt only");
            check(h.text().contains("本机诊断失败，已结束会话"), "diagnostic failure is explicit");
            check(!h.text().contains("synthetic diagnostic failure"), "raw exception details are not displayed");
            String failure = h.text(); diagnosticQuiet(h); Handler.tick(); equal(failure, h.text(), "timer preserves diagnostic failure");
        });
        run("panel invalidates even if diagnostic service forgets cleanup", () -> {
            Harness h = diagnosticHarness(); VisibleMessageService.diagnosticInvalidates = false;
            clickShown(Harness.find(h.root, "本机诊断（不读文字）"), "diagnostic cleanup defense");
            equal(VisibleMessageService.diagnosticResult, h.text(), "report survives panel-owned cleanup");
            diagnosticQuiet(h);
        });
        for (boolean nullReport : List.of(false, true)) run((nullReport ? "null" : "empty") + " diagnostic report fails visibly and clears session", () -> {
            Harness h = diagnosticHarness(); VisibleMessageService.diagnosticResult = nullReport ? null : "";
            clickShown(Harness.find(h.root, "本机诊断（不读文字）"), "empty diagnostic");
            check(h.text().contains("本机诊断未返回结果"), "missing report does not look like a nonresponsive button");
            diagnosticQuiet(h); String failure = h.text(); Handler.tick(); equal(failure, h.text(), "timer preserves empty-report guidance");
        });
        run("diagnostic cancels old pending speech and synthetic context", () -> {
            Harness h = diagnosticHarness();
            clickShown(Harness.find(h.root, "仅模拟演示（不联网）"), "demo before diagnostic");
            h.speak(); TextToSpeech engine = TextToSpeech.latest(); int previousCurrentCalls = VisibleMessageService.currentCalls;
            clickShown(Harness.find(h.root, "本机诊断（不读文字）"), "diagnostic after pending speech");
            check(VisibleMessageService.diagnosticSawEmptySession, "old demo content is cleared before metadata inspection");
            equal(previousCurrentCalls, VisibleMessageService.currentCalls, "diagnostics never use current-snapshot text validation");
            engine.complete(TextToSpeech.SUCCESS); Handler.drain();
            equal(0, engine.speakCalls, "late speech initialization cannot read diagnostic output or old context");
            equal(VisibleMessageService.diagnosticResult, h.text(), "late speech callback preserves report");
            diagnosticQuiet(h);
        });
        run("diagnostic cancels an existing recording without uploading", () -> {
            Harness h = new Harness(); h.record(); check(h.recorder.active, "synthetic recording was active before diagnostic click");
            int previousCurrentCalls = VisibleMessageService.currentCalls;
            clickShown(Harness.find(h.root, "本机诊断（不读文字）"), "diagnostic after recording");
            check(!h.recorder.active && !SESSION.active(), "diagnostic ends recording and session");
            equal(1, h.recorder.startCalls, "diagnostic starts no additional recording");
            equal(0, h.recorder.finishCalls, "diagnostic cancels instead of completing audio");
            equal(0, GeminiAssistantTransport.latest.requestCalls, "no explanation request after diagnostic");
            equal(0, GeminiAssistantTransport.latest.transcribeCalls, "cancelled recording is not uploaded");
            equal(0, h.manualInsertCalls, "diagnostic does not insert");
            equal(0, VisibleMessageService.captureCalls, "diagnostic does not capture messages");
            equal(previousCurrentCalls, VisibleMessageService.currentCalls, "diagnostic does not validate message text");
            check(VisibleMessageService.diagnosticSawEmptySession, "recording context is cleared before metadata inspection");
            equal(VisibleMessageService.diagnosticResult, h.text(), "metadata report remains visible");
        });
        run("completed diagnostic does not authorize speech recording insertion or reading", () -> {
            Harness h = diagnosticHarness();
            clickShown(Harness.find(h.root, "本机诊断（不读文字）"), "one-shot diagnostic");
            clickShown(h.reply, "reply after diagnostic"); check(h.text().contains("请先启动助手"), "diagnostic alone cannot start a reply");
            clickShown(h.speech, "speech after diagnostic"); check(h.text().contains("请先取得中文解释"), "counts are not spoken as an explanation");
            clickShown(Harness.find(h.root, "插入到当前框"), "insert after diagnostic");
            check(h.text().contains("请先完成回复预览"), "counts cannot become an insertion draft");
            equal(0, TextToSpeech.instances.size(), "diagnostic does not initialize speech");
            diagnosticQuiet(h);
            clickShown(Harness.find(h.root, "启动助手"), "fresh start after diagnostic");
            check(h.text().contains("本次助手会话") && Harness.find(h.root, "同意并读取") != null, "ordinary read still requires its own disclosure and consent click");
            equal(0, VisibleMessageService.captureCalls, "new reading consent is not bypassed");
            equal(0, GeminiAssistantTransport.latest.requestCalls, "diagnostic never falls through to explain");
        });
        run("destroyed diagnostic listener cannot inspect a new session", () -> {
            Harness h = diagnosticHarness(); Button diagnostic = Harness.find(h.root, "本机诊断（不读文字）");
            h.panel.destroy(); h.activate("Synthetic replacement explanation"); long token = SESSION.token();
            diagnostic.performClick();
            equal(0, VisibleMessageService.diagnosticCalls, "destroyed listener does no metadata work");
            check(SESSION.active() && token == SESSION.token(), "destroyed listener leaves replacement session unchanged");
        });
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
    private static Harness diagnosticHarness() throws Exception {
        Harness h = new Harness(false); h.panel.editorChanged(true); Handler.drain();
        clickShown(Harness.find(h.root, "启动助手"), "start before local diagnostic");
        return h;
    }
    private static void diagnosticQuiet(Harness h) {
        check(h.output.isShown(), "diagnostic outcome is visible through its ancestors");
        check(!SESSION.active(), "diagnostic leaves no active assistant session");
        check(SESSION.explanation().isEmpty() && SESSION.draft().isEmpty(), "diagnostic leaves no explanation or insertion draft");
        equal(0, h.recorder.startCalls, "diagnostic never records audio");
        equal(0, h.recorder.finishCalls, "diagnostic never finishes audio for upload");
        equal(0, GeminiAssistantTransport.latest.requestCalls, "diagnostic never requests Gemini explanation");
        equal(0, GeminiAssistantTransport.latest.transcribeCalls, "diagnostic never transcribes audio");
        equal(0, h.manualInsertCalls, "diagnostic never inserts content");
        equal(0, VisibleMessageService.captureCalls, "diagnostic never calls message capture");
        equal(0, VisibleMessageService.currentCalls, "diagnostic never revalidates message text");
    }
    private static void clickShown(Button button, String name) {
        check(button != null, name + " button exists in actual panel tree");
        check(button.isShown(), name + " button has no hidden ancestor");
        check(button.performClick(), name + " button has a wired click listener");
    }
    private static void fixedContrast(TextView output, String state) {
        equal(0xFF000000, output.getCurrentTextColor(), state + " keeps opaque black text");
        equal(0xFFFFFFFF, output.backgroundColor, state + " keeps opaque white background");
        equal(1, output.textColorChanges, state + " does not override explicit foreground");
        equal(1, output.backgroundColorChanges, state + " does not override explicit background");
    }
    private static void run(String name, Checked body) throws Exception {
        Handler.reset(); TextToSpeech.reset(); SESSION.invalidate(); VisibleMessageService.reset(); active = null;
        TextView.defaultTextColor = 0; View.defaultBackgroundColor = 0;
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
        int manualInsertCalls;
        boolean legacyBusy;
        final AssistantPanel panel = new AssistantPanel(context, () -> legacyBusy, (token, english) -> { manualInsertCalls++; throw new AssertionError("No insertion expected"); });
        final LinearLayout root = new LinearLayout(context);
        final AssistantRecorder recorder = AssistantRecorder.latest;
        final Button speech, reply;
        final TextView output;
        Harness() throws Exception { this(true); }
        Harness(boolean setupSession) throws Exception {
            active = this; panel.addTo(root);
            output = (TextView)((ScrollView)root.children.get(0)).child;
            speech = find(root, "中文朗读"); reply = find(root, "口述回复");
            if (setupSession) { panel.editorChanged(true); Handler.drain(); activate("Synthetic Chinese explanation"); }
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
