package com.airnovation.aivoicekeyboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.function.BooleanSupplier;

/** Explicit voice capture, read-only review, and one user-requested insertion. */
final class VoiceDraftPanel {
    interface ManualInsert { String insert(String draft); }
    private enum Phase { IDLE, RECORDING, PROCESSING, PREVIEW, ERROR }
    private static final String IDLE_TEXT = "说出你想表达的内容，默认整理成英文草稿。也可以说“用中文”或“简短一点”。整理后先核对，再确认填入。";
    private final Context context;
    private final BooleanSupplier legacyBusy, beginEditor, editorCurrent;
    private final ManualInsert manualInsert;
    private final VoiceDraftSession session = new VoiceDraftSession();
    private final VoiceDraftTransport transport;
    private final AssistantRecorder recorder;
    private TextView output;
    private Button start, insert, revise;
    private Phase phase = Phase.IDLE;
    private long token = -1;
    private boolean allowedEditor, registered, destroyed;
    private String previousDraft = "";
    private final BroadcastReceiver locked = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) { stop(); }
    };

    VoiceDraftPanel(Context context, BooleanSupplier legacyBusy, BooleanSupplier beginEditor,
                    BooleanSupplier editorCurrent,
                    ManualInsert manualInsert) {
        this.context = context;
        this.legacyBusy = legacyBusy;
        this.beginEditor = beginEditor;
        this.editorCurrent = editorCurrent;
        this.manualInsert = manualInsert;
        transport = new VoiceDraftTransport(context);
        // The limit never finishes/uploads a recording on the user's behalf.
        recorder = new AssistantRecorder(context, () -> reset(
                "录音达到60秒上限，已取消且未上传。请重新录一段。"));
    }

    void addTo(LinearLayout root) {
        if (destroyed || output != null) return;
        TextView disclosure = text(false);
        disclosure.setTextSize(12);
        disclosure.setText("录音及转写交给Gemini整理；不读取聊天，确认后才插入，发送由你操作。");
        root.addView(disclosure);
        output = text(true);
        output.setText(IDLE_TEXT);
        ScrollView scroll = new ScrollView(context);
        scroll.setSaveEnabled(false);
        scroll.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        scroll.addView(output);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float density = metrics.density > 0 ? metrics.density : 1f;
        float screenDp = metrics.heightPixels / density;
        int previewHeight = Math.round(Math.max(64f, Math.min(144f, screenDp * 0.18f)) * density);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, previewHeight));
        LinearLayout primary = new LinearLayout(context);
        root.addView(primary);
        start = button(primary, "开始说话", this::startOrFinish);
        button(primary, "取消", this::stop);
        LinearLayout secondary = new LinearLayout(context);
        root.addView(secondary);
        insert = button(secondary, "确认填入", this::insertDraft);
        revise = button(secondary, "修改上一稿", () -> startRecording(true));
        if (!registered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33)
                context.registerReceiver(locked, filter, Context.RECEIVER_NOT_EXPORTED);
            else context.registerReceiver(locked, filter);
            registered = true;
        }
        updateButtons();
    }

    private TextView text(boolean selectable) {
        TextView text = new TextView(context);
        text.setTextSize(14);
        text.setTextColor(0xFF000000);
        text.setBackgroundColor(0xFFFFFFFF);
        text.setTextIsSelectable(selectable);
        text.setSaveEnabled(false);
        text.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        return text;
    }

    private Button button(LinearLayout row, String label, Runnable action) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1f));
        return button;
    }

    void editorChanged(boolean allowed) {
        stop();
        allowedEditor = allowed;
        if (!allowed) show("请先打开普通文字输入框。密码等敏感输入框不可使用。");
    }

    /** A retained preview also owns this panel until inserted, replaced, or cancelled. */
    boolean active() { return !destroyed && phase != Phase.IDLE; }

    private boolean readyEditor() {
        if (destroyed || output == null) return false;
        if (!allowedEditor || !editorCurrent.getAsBoolean()) {
            reset("输入框或页面已变化，录音及草稿已清除。请重新打开普通文字输入框。");
            return false;
        }
        if (legacyBusy.getAsBoolean()) {
            show("请先结束现有Mic/EN操作，再开始说话。");
            return false;
        }
        return true;
    }

    private boolean current(long expected) {
        if (destroyed || expected != token || !session.active(expected)) return false;
        if (!allowedEditor || !editorCurrent.getAsBoolean()) {
            reset("输入框或页面已变化，录音及草稿已清除。");
            return false;
        }
        return true;
    }

    private String providerError() {
        if (!"gemini".equals(context.getSharedPreferences("voice_keyboard_settings", Context.MODE_PRIVATE)
                .getString("selected_provider", "openai")))
            return "请先在键盘设置选择Gemini，再开始说话。不会自动切换服务商。";
        if (SecureSettings.getApiKey(context, "gemini").isEmpty())
            return "请先在键盘设置保存Gemini API Key，再开始说话。不要把密钥发到聊天。";
        return null;
    }

    private void startOrFinish() {
        if (phase == Phase.RECORDING) finishRecording();
        else if (phase != Phase.PROCESSING) startRecording(false);
    }

    private void startRecording(boolean revision) {
        if (destroyed || output == null || phase == Phase.RECORDING || phase == Phase.PROCESSING) return;
        if (!allowedEditor) { reset("请先打开普通文字输入框。密码等敏感输入框不可使用。"); return; }
        if (legacyBusy.getAsBoolean()) { show("请先结束现有Mic/EN操作，再开始说话。"); return; }
        if (revision && (phase != Phase.PREVIEW || session.draft().isEmpty() || !current(token))) return;
        String error = providerError();
        if (error != null) { show(error); return; }
        // Only a fresh explicit recording action may bind a new IME/editor generation.
        if (!beginEditor.getAsBoolean()) {
            reset("当前输入框不可用。请重新打开普通文字输入框后开始说话。");
            return;
        }
        transport.cancel();
        recorder.cancel();
        // Never infer revision mode from speech. Only this button attaches a prior draft.
        previousDraft = revision ? session.draft() : "";
        token = session.start(revision);
        phase = Phase.RECORDING;
        show("");
        error = recorder.start();
        if (error != null) { reset(error); return; }
        show(revision
                ? "正在录音修改上一稿。说出修改要求，再点“结束并整理”。取消不会上传；最长60秒。"
                : "正在录音。说出你想表达的内容，再点“结束并整理”交给Gemini。取消不会上传；最长60秒。");
        updateButtons();
    }

    private void finishRecording() {
        final long recordingToken = token;
        if (phase != Phase.RECORDING || !current(recordingToken)) return;
        if (legacyBusy.getAsBoolean()) { reset("Mic/EN操作已开始，本次录音已取消且未上传。"); return; }
        String providerProblem = providerError();
        if (providerProblem != null) { reset(providerProblem + " 本次录音已取消且未上传。"); return; }
        // Change phase first: repeated clicks cannot finish or upload the same audio twice.
        phase = Phase.PROCESSING;
        updateButtons();
        show("正在把本次录音交给Gemini转写并整理…取消可清除本机内容，已发送的请求无法撤回。");
        recorder.finish((file, error) -> {
            if (!current(recordingToken) || phase != Phase.PROCESSING) { delete(file); return; }
            if (error != null || file == null) {
                delete(file);
                reset(error != null ? error : "录音未完成，请重试。");
                return;
            }
            String prior = previousDraft;
            previousDraft = "";
            // Transport owns this temporary audio file from here, including cancellation.
            transport.process(session, recordingToken, file, prior.isEmpty() ? null : prior,
                    this::showResponse);
        });
    }

    private void showResponse(long responseToken, String transcript, String draft, String error) {
        if (!current(responseToken) || phase != Phase.PROCESSING) return;
        previousDraft = "";
        if (error != null || transcript == null || draft == null
                || !session.accept(responseToken, transcript, draft)) {
            phase = Phase.ERROR;
            show((transcript == null || transcript.isEmpty() || transcript.length() > VoiceDraftSession.MAX_TRANSCRIPT_CHARS ? "" : "识别原话：\n" + transcript + "\n\n")
                    + (error == null ? "未得到完整草稿，请重新说话。" : error)
                    + "\n未插入或发送。可重新开始说话，或取消。");
            updateButtons();
            return;
        }
        phase = Phase.PREVIEW;
        show("识别原话：\n" + session.transcript() + "\n\n草稿（未插入）：\n" + session.draft()
                + "\n\n请核对草稿和当前输入框，再点“确认填入”；发送由你操作。");
        updateButtons();
    }

    private void insertDraft() {
        if (phase != Phase.PREVIEW || !current(token) || !readyEditor() || session.draft().isEmpty()) return;
        final long insertingToken = token;
        String error = manualInsert.insert(session.draft());
        // The editor callback itself may synchronously invalidate the old panel/session.
        if (destroyed || insertingToken != token || !session.active(insertingToken)) return;
        if (error == null) reset("草稿已插入当前输入框。请检查内容，再由你手动发送。");
        else if (!allowedEditor || !editorCurrent.getAsBoolean()) reset(error);
        else show(error + "\n\n草稿（尚未确认插入）：\n" + session.draft());
    }

    private void updateButtons() {
        if (start == null) return;
        start.setText(phase == Phase.RECORDING ? "结束并整理" : "开始说话");
        start.setEnabled(!destroyed && phase != Phase.PROCESSING);
        boolean preview = !destroyed && phase == Phase.PREVIEW && !session.draft().isEmpty();
        insert.setEnabled(preview);
        revise.setEnabled(preview);
    }

    private void show(String message) { if (output != null) output.setText(message); }
    private static void delete(File file) { if (file != null) file.delete(); }
    private void reset(String message) {
        session.invalidate();
        token = -1;
        previousDraft = "";
        phase = Phase.IDLE;
        recorder.cancel();
        transport.cancel();
        show(message);
        updateButtons();
    }

    void stop() { reset(IDLE_TEXT); }

    void destroy() {
        if (destroyed) return;
        destroyed = true;
        stop();
        if (registered) {
            context.unregisterReceiver(locked);
            registered = false;
        }
    }
}
