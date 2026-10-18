package com.airnovation.aivoicekeyboard;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import java.util.Collections;
import java.util.function.BooleanSupplier;

/** Assistant preview is never connected to legacy auto-insert or host Send. */
final class AssistantPanel {
    interface ManualInsert { String insert(long token, String english); }
    private final ManualInsert manualInsert;
    private final Context context;
    private final BooleanSupplier legacyBusy;
    private final AssistantSession session = AssistantSession.INSTANCE;
    private final GeminiAssistantTransport transport;
    private final AssistantRecorder recorder;
    private TextView output;
    private ScrollView scroll;
    private LinearLayout actions, examples;
    private Button start, reply, insert;
    private boolean insertionConfirmed;
    private TextToSpeech tts;
    private boolean localVoiceReady, allowedEditor, registered, consentPending, busy, demo;
    // Session validity is not permission to speak: recording can start in the same session.
    private boolean speechRequested, ttsInitializing, destroyed;
    private long speechToken = -1, ttsGeneration;
    private long shownToken = -1;
    private String transcript = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable check = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            if (shownToken != session.token()) {
                teardown();
                if (output != null && shownToken != -1) output.setText("页面或会话已变化。旧上下文已清除，请重新启动助手。");
                shownToken = session.token();
            }
            handler.postDelayed(this, 100);
        }
    };
    private final BroadcastReceiver locked = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { stop(); }
    };
    AssistantPanel(Context context, BooleanSupplier legacyBusy, ManualInsert manualInsert) {
        this.manualInsert = manualInsert;
        this.context = context; this.legacyBusy = legacyBusy;
        transport = new GeminiAssistantTransport(context);
        // Time limit cancels rather than uploading without an explicit Stop press.
        recorder = new AssistantRecorder(context, () -> {
            recorderCancel();
            output.setText("录音达到60秒上限，已取消且未上传。请重新录一段短回复。");
        });
    }
    void addTo(LinearLayout root) {
        output = new TextView(context); output.setTextSize(14); output.setTextIsSelectable(true);
        // Keep preview/error text legible independently of the host's light/dark theme.
        output.setTextColor(0xFF000000); output.setBackgroundColor(0xFFFFFFFF);
        output.setText("助手默认关闭 · 普通Gemini API · 中文离线朗读 · 回复仅预览");
        scroll = new ScrollView(context); scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, (int)(140 * context.getResources().getDisplayMetrics().density)));
        LinearLayout controls = new LinearLayout(context); root.addView(controls);
        start = button(controls, "启动助手", this::startOrConsent);
        button(controls, "关闭", this::stop);
        button(controls, "权限设置", () -> {
            stop();
            context.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        actions = new LinearLayout(context); root.addView(actions);
        button(actions, "中文朗读", this::speak);
        reply = button(actions, "口述回复", this::recordReply);
        insert = button(actions, "插入到当前框", this::confirmInsert);
        examples = new LinearLayout(context); root.addView(examples);
        button(examples, "仅模拟演示（不联网）", this::startDemo);
        if (!registered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(locked, filter, Context.RECEIVER_NOT_EXPORTED);
            else context.registerReceiver(locked, filter);
            registered = true;
        }
        setExpanded(false);
        handler.removeCallbacks(check); handler.post(check);
    }
    private Button button(LinearLayout row, String text, Runnable run) {
        Button b = new Button(context); b.setText(text); b.setAllCaps(false);
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1f)); b.setOnClickListener(v -> run.run()); return b;
    }
    void editorChanged(boolean whatsappNonSensitive) { stop(); allowedEditor = whatsappNonSensitive; }
    private boolean readyEditor() {
        setExpanded(true);
        if (!allowedEditor) { output.setText("请在WhatsApp或WhatsApp Business普通消息输入框打开。助手未启动。"); return false; }
        if (legacyBusy.getAsBoolean()) { output.setText("请先结束现有Mic/EN操作，再启动助手。"); return false; }
        return true;
    }
    private void startOrConsent() {
        if (!readyEditor()) return;
        if (!consentPending) {
            stop(); setExpanded(true); consentPending = true; start.setText("同意并读取");
            output.setText("本次助手会话：只读取WhatsApp当前屏幕可见的文字消息（最多12条、3000字符），发送给Gemini做中文解释和回复校对。文本中可能包含其他人的信息。不会读取历史、通知、截图或附件，不滚动，不自动发送。\n\n口述回复只在你按下后录音；按停止时将录音发给Gemini转写，并连同本次消息生成英文预览。仅使用设备现有Gemini配置。关闭、锁屏或页面变化会结束会话；已发出的请求不能撤回。\n\n只有你手动启用系统无障碍权限后才能读取；该系统权限本身不限于WhatsApp。点击“同意并读取”开始本次会话，或“关闭”取消。请先用无敏感信息的测试聊天验证。");
            return;
        }
        if (!VisibleMessageService.available()) { output.setText("尚未启用无障碍服务。请点“权限设置”，自行启用"
                + (context.getPackageName().endsWith(".assistanttest") ? "Input Assistant - Isolated Test" : "Optional Input Assistant")
                + "，再返回重新确认。当前未读取或上传。"); return; }
        if (!"gemini".equals(context.getSharedPreferences("voice_keyboard_settings", Context.MODE_PRIVATE)
                .getString("selected_provider", "openai"))) { output.setText("助手仅使用Gemini。请先在现有键盘设置选择Gemini，不会自动切换服务商。"); return; }
        if (SecureSettings.getApiKey(context, "gemini").isEmpty()) { output.setText("请先在现有键盘设置保存Gemini API Key；不要把密钥发到聊天。"); return; }
        teardown(); demo = false;
        long token = session.start(); shownToken = token;
        String error = VisibleMessageService.captureOnce(token);
        shownToken = session.token();
        if (error != null) { output.setText(error); return; }
        busy = true; output.setText("正在用Gemini解释本次可见消息… 点击关闭可结束会话。");
        AssistantGemini.explainVisible(transport, session, this::showResponse);
    }
    private boolean current(long token) {
        return !destroyed && session.active() && token == session.token() && (demo || VisibleMessageService.stillCurrent(token));
    }
    private void showResponse(long token, String chinese, String english, String error) {
        if (token != session.token()) return;
        if (!current(token)) { stopWithReason(); return; }
        busy = false;
        if (error != null) { output.setText(error + "\n原聊天草稿未修改。可关闭后重试。"); return; }
        if (!session.accept(token, chinese, english)) return;
        insertionConfirmed = false; insert.setText("插入到当前框");
        if (chinese != null) output.setText("中文解释（请核对）：\n" + chinese + "\n\n可点击中文朗读或口述回复。未修改聊天草稿。");
        else output.setText("识别原话：\n" + transcript + "\n\n英文回复预览（未插入/发送）：\n" + english
                + "\n\n请自行核对当前聊天和英文，可点击插入到当前框。不会自动识别群身份或发送。");
    }
    private void recordReply() {
        if (destroyed) return;
        insertionConfirmed = false; insert.setText("插入到当前框");
        if (!readyEditor()) return;
        if (!session.active()) { output.setText("请先启动助手并读取当前消息。"); return; }
        if (demo) { output.setText("模拟口述意图：我先确认时间再回复你。\n英文预览：\n" + session.draft() + "\n仅模拟，未录音或联网。"); return; }
        if (busy) { output.setText("正在处理上一请求，请等待或关闭助手。"); return; }
        long token = session.token();
        if (!current(token)) { stopWithReason(); return; }
        if (recorder.recording()) {
            reply.setText("口述回复");
            recorder.finish((file, error) -> {
                if (error != null) { output.setText(error); return; }
                if (!current(token)) { file.delete(); stopWithReason(); return; }
                busy = true; output.setText("正在使用Gemini转写本次录音…");
                transport.transcribe(session, token, file, (id, unused, text, problem) -> {
                    if (id != session.token()) return;
                    if (!current(id)) { stopWithReason(); return; }
                    if (problem != null) { busy = false; output.setText(problem); return; }
                    transcript = text;
                    output.setText("识别原话：\n" + text + "\n正在生成上下文英文回复预览…");
                    transport.request(session, id, text, this::showResponse);
                });
            });
        } else {
            session.clearDraft();
            // Revoke the pending read-aloud before touching the microphone, even if start fails.
            if (!cancelSpeech()) {
                output.setText("朗读停止失败，未开始录音。请关闭助手后重试。"); return;
            }
            String error = recorder.start();
            if (error != null) output.setText(error);
            else { reply.setText("停止并转写"); output.setText("正在录音。点击“停止并转写”后发给Gemini；关闭或页面变化将取消。最长60秒。"); }
        }
    }
    private void confirmInsert() {
        if (demo) { output.setText("模拟演示不能插入真实聊天。\n" + session.draft()); return; }
        if (busy || recorder.recording() || session.draft().isEmpty()) {
            output.setText("请先完成回复预览，再核对当前聊天后插入。"); return;
        }
        long token = session.token();
        if (!current(token)) { stopWithReason(); return; }
        if (!insertionConfirmed) {
            insertionConfirmed = true; insert.setText("确认插入当前框");
            output.setText("请自行确认WhatsApp当前打开的是你想回复的聊天。此应用不能自动核实群身份。\n\n将插入光标所在位置（不会发送）：\n"
                    + session.draft() + "\n\n确认后再按“确认插入当前框”；任何页面变化会使此确认失效。");
            return;
        }
        if (!session.mayManuallyInsert(token, true)) return;
        String error = manualInsert.insert(token, session.draft());
        if (error == null) {
            stop(); output.setText("已按你的确认插入当前输入框。请检查目标聊天和英文，再由你手动发送。");
        } else {
            insertionConfirmed = false; insert.setText("插入到当前框");
            output.setText(error + "\n英文预览：\n" + session.draft());
        }
    }
    private void startDemo() {
        if (!readyEditor()) return;
        stop(); setExpanded(true); demo = true;
        long token = session.start(); shownToken = token;
        session.capture(token, AssistantSession.WHATSAPP, "SYNTHETIC_ONLY", true, true,
                Collections.singletonList("Are you available tomorrow at 3 pm?"));
        AssistantGemini.demo(token, (id, chinese, english, error) -> {
            if (session.accept(id, chinese, english)) output.setText("模拟消息，不含真实聊天：\nAre you available tomorrow at 3 pm?\n\n" + chinese
                    + "\n\n口述回复按钮只展示模拟英文草稿。此演示不读取、不录音、不联网。");
        });
    }
    private void speak() {
        if (destroyed) return;
        insertionConfirmed = false; insert.setText("插入到当前框");
        if (!session.active() || session.explanation().isEmpty()) { output.setText("请先取得中文解释。"); return; }
        if (recorder.recording()) { output.setText("请先停止录音，再朗读解释。"); return; }
        long token = session.token();
        if (!current(token)) { stopWithReason(); return; }
        speechRequested = true; speechToken = token;
        if (tts == null) {
            ttsInitializing = true;
            final long generation = ++ttsGeneration;
            try {
                // Always queue on the UI thread, including a constructor-time onInit callback.
                tts = new TextToSpeech(context, result ->
                        handler.post(() -> speechInitialized(generation, result)));
            } catch (RuntimeException ignored) {
                releaseSpeechEngine(); speakReady();
            }
        } else speakReady();
    }
    private void speechInitialized(long generation, int result) {
        if (destroyed || generation != ttsGeneration || tts == null || !ttsInitializing) return;
        ttsInitializing = false; localVoiceReady = false;
        try {
            java.util.Set<Voice> voices = result == TextToSpeech.SUCCESS ? tts.getVoices() : null;
            if (voices != null) for (Voice voice : voices) {
                if (voice != null && voice.getLocale() != null
                        && Locale.CHINESE.getLanguage().equals(voice.getLocale().getLanguage())
                        && !voice.isNetworkConnectionRequired()) {
                    localVoiceReady = tts.setVoice(voice) == TextToSpeech.SUCCESS;
                    if (localVoiceReady) break;
                }
            }
        } catch (RuntimeException ignored) { localVoiceReady = false; }
        // A failed engine must be retryable on another explicit tap; never auto-retry.
        if (!localVoiceReady) releaseSpeechEngine();
        speakReady();
    }
    private void speakReady() {
        if (!speechRequested || destroyed) return;
        if (recorder.recording()) { cancelSpeech(); return; }
        if (speechToken != session.token()) { cancelSpeech(); return; }
        if (!current(speechToken)) { stopWithReason(); return; }
        if (ttsInitializing) return;
        // Consume once. Duplicate init callbacks cannot replay a fulfilled request.
        speechRequested = false; speechToken = -1;
        if (tts == null || !localVoiceReady) {
            output.setText("未找到可用的离线中文语音。请检查系统语音设置后重试；文字解释仍可查看。\n" + session.explanation()); return;
        }
        try {
            if (tts.speak(session.explanation(), TextToSpeech.QUEUE_FLUSH, null, "assistant-" + session.token()) == TextToSpeech.SUCCESS) return;
        } catch (RuntimeException ignored) { }
        output.setText("离线朗读失败，可重试；文字解释仍可查看。\n" + session.explanation());
    }
    private boolean cancelSpeech() {
        speechRequested = false; speechToken = -1;
        if (tts == null) return true;
        // An unready engine has never spoken. A ready engine must acknowledge stopping
        // before recording starts; a TTS failure must not contaminate microphone input.
        boolean unready = ttsInitializing || !localVoiceReady;
        try { return tts.stop() == TextToSpeech.SUCCESS || unready; }
        catch (RuntimeException ignored) { return unready; }
    }
    private void releaseSpeechEngine() {
        ++ttsGeneration; ttsInitializing = false; localVoiceReady = false;
        TextToSpeech old = tts; tts = null;
        if (old != null) try { old.shutdown(); } catch (RuntimeException ignored) { }
    }
    private void recorderCancel() { recorder.cancel(); if (reply != null) reply.setText("口述回复"); }
    private void teardown() {
        // If a ready engine cannot stop, shut it down as a best-effort fallback.
        // Cleanup of the session and recorder must continue even if either API fails.
        if (!cancelSpeech()) releaseSpeechEngine();
        transport.cancel(); recorderCancel(); busy = false; consentPending = false; transcript = "";
        insertionConfirmed = false; if (insert != null) insert.setText("插入到当前框");
        if (start != null) start.setText("启动助手");
    }
    private void stopWithReason() { stop(); setExpanded(true); output.setText("当前页面或消息已变化。请重新启动助手；未修改聊天草稿。"); }
    void stop() {
        session.invalidate(); shownToken = session.token(); teardown(); demo = false;
        if (output != null) output.setText("助手已关闭；上下文、录音和回复已清除。");
        setExpanded(false);
    }
    private void setExpanded(boolean expanded) {
        int visibility = expanded ? View.VISIBLE : View.GONE;
        if (scroll != null) scroll.setVisibility(visibility);
        if (actions != null) actions.setVisibility(visibility);
        if (examples != null) examples.setVisibility(visibility);
    }
    void destroy() {
        destroyed = true;
        stop(); handler.removeCallbacks(check);
        releaseSpeechEngine();
        if (registered) { context.unregisterReceiver(locked); registered = false; }
    }
}
