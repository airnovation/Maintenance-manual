package com.airnovation.aivoicekeyboard;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;

/** Compact voice-to-input. No background host text reading or automatic sending. */
public class VoiceKeyboardService extends InputMethodService {
    private enum Phase { IDLE, RECORDING, PROCESSING, TRANSLATING, INSERTING }
    private static final String IDLE_TEXT = "Mic：说内容、语气；Stop整理并填入。\n默认英文，可说“用中文”；发送由你操作。";
    private static final int MAX_TYPED_CHARS = 8000;
    private final VoiceDraftSession session = new VoiceDraftSession();
    private final VoiceEditorBinding editorBinding = new VoiceEditorBinding();
    private VoiceDraftTransport transport;
    private AssistantRecorder recorder;
    private TextView status;
    private Button mic, improve, delete;
    private Phase phase = Phase.IDLE;
    private long generation, voiceToken = -1;
    private boolean registered, destroyed;
    private final VoiceEditorBinding selectionBinding = new VoiceEditorBinding();
    private long selectionEpoch, selectionVersion, voiceSelectionVersion;
    private int selectionStart = -1, selectionEnd = -1, composingStart = -1, composingEnd = -1;
    private int voiceCursor = -1;
    private boolean finishingVoiceComposition;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            reset("录音及草稿已清除。未确认的录音不会上传。");
        }
    };

    // Keep Android's extracted full-screen editor out of this compact keyboard.
    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public View onCreateInputView() {
        reset(IDLE_TEXT);
        if (transport == null) transport = new VoiceDraftTransport(this);
        if (recorder == null) recorder = new AssistantRecorder(this,
                () -> reset("录音达到60秒上限，已取消且未上传。请重新录音。"));
        if (!registered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenOff, filter);
            registered = true;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(getResources().getDisplayMetrics().density * 12);
        root.setPadding(pad, pad, pad, pad * 2);
        root.setBackgroundColor(0xfff1f2f4);
        root.setSaveEnabled(false);
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottom = Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                    : insets.getSystemWindowInsetBottom();
            view.setPadding(pad, pad, pad, Math.max(pad * 2, bottom + pad));
            return insets;
        });

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xff202020);
        status.setMaxLines(2);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setSaveEnabled(false);
        root.addView(status);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row);
        mic = makeButton(row, "Mic", this::toggleRecording);
        improve = makeButton(row, "EN", this::translate);

        LinearLayout other = new LinearLayout(this);
        other.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(other);
        delete = makeButton(other, "⌫ Delete", this::deleteFromChatInput);
        makeButton(other, "↩ Keyboard", () -> {
            reset(IDLE_TEXT); // Cancel before switching, even if Android cannot switch immediately.
            boolean switched = Build.VERSION.SDK_INT >= 28 && switchToPreviousInputMethod();
            if (!switched) {
                InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (manager != null) manager.showInputMethodPicker();
            }
        });

        showStatus(editorAllowed(getCurrentInputEditorInfo()) ? IDLE_TEXT : unavailableEditor());
        updateButtons();
        return root;
    }

    private Button makeButton(LinearLayout row, String title, Runnable click) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextColor(0xff111111);
        button.setBackgroundTintList(ColorStateList.valueOf(0xffd7e2ef));
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1f));
        button.setOnClickListener(view -> click.run());
        return button;
    }

    private static boolean editorAllowed(EditorInfo info) {
        if (info == null || info.packageName == null || info.packageName.isEmpty()
                || (info.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false;
        int variation = info.inputType & InputType.TYPE_MASK_VARIATION;
        return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD
                && variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                && variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    private boolean editorAvailable() {
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return !destroyed && editorAllowed(getCurrentInputEditorInfo()) && isInputViewShown()
                && getCurrentInputConnection() != null && keyguard != null && !keyguard.isKeyguardLocked();
    }

    private boolean bindEditor() {
        if (!editorAvailable()) return false;
        EditorInfo info = getCurrentInputEditorInfo();
        editorBinding.bind(generation, info.packageName, info.fieldId, info.inputType,
                getCurrentInputConnection(), true);
        return editorCurrent();
    }

    private boolean editorCurrent() {
        if (!editorAvailable()) return false;
        EditorInfo info = getCurrentInputEditorInfo();
        return editorBinding.matches(generation, info.packageName, info.fieldId, info.inputType,
                getCurrentInputConnection());
    }

    private boolean voiceCurrent(long token) {
        if (destroyed || token != voiceToken || !session.active(token)) return false;
        if (editorCurrent() && voiceSelectionCurrent()) return true;
        reset("输入框或光标已变化，录音已取消。请重新开始。");
        return false;
    }

    private static String unavailableEditor() { return "请打开普通文字输入框。密码等敏感输入框不可使用。"; }

    private String selectedProvider() {
        return getSharedPreferences("voice_keyboard_settings", Context.MODE_PRIVATE)
                .getString("selected_provider", "openai");
    }

    private String voiceProviderError() {
        if (!"gemini".equals(selectedProvider())) return "请先在键盘设置选择Gemini，再开始说话。";
        return ApiKeyValidation.validationMessage(SecureSettings.getApiKey(this, "gemini"));
    }

    private boolean busy() {
        return phase == Phase.RECORDING || phase == Phase.PROCESSING
                || phase == Phase.TRANSLATING || phase == Phase.INSERTING;
    }

    private void updateButtons() {
        boolean available = editorAvailable();
        if (mic != null) {
            mic.setText(phase == Phase.RECORDING ? "Stop" : "Mic");
            mic.setEnabled(available && (phase == Phase.RECORDING || !busy()));
        }
        if (improve != null) improve.setEnabled(available && !busy());
        if (delete != null) delete.setEnabled(available && !busy());
    }

    private void toggleRecording() {
        if (phase == Phase.RECORDING) { finishRecording(); return; }
        if (busy() || recorder == null || transport == null) return;
        if (!editorAvailable()) { reset(unavailableEditor()); return; }
        String error = voiceProviderError();
        if (error != null) { showStatus(error); return; }
        if (!collapsedSelectionCurrent()) {
            reset("请取消文字选取，把光标放在要填入的位置，再点Mic。");
            return;
        }
        reset("");
        if (!bindEditor()) { reset(unavailableEditor()); return; }
        voiceCursor = selectionStart;
        voiceSelectionVersion = selectionVersion;
        voiceToken = session.start(false); // Every Mic click starts fresh; never attach earlier text.
        phase = Phase.RECORDING;
        error = recorder.start();
        if (error != null) { reset(error); return; }
        showStatus("Stop：交给Gemini整理；Keyboard取消。\n最长60秒，不会自动上传。");
        updateButtons();
    }

    private void finishRecording() {
        final long token = voiceToken;
        if (phase != Phase.RECORDING || !voiceCurrent(token)) return;
        String error = voiceProviderError();
        if (error != null) { reset(error); return; }
        phase = Phase.PROCESSING; // Repeated Stop cannot upload the same audio twice.
        updateButtons();
        showStatus("Gemini正在转写并整理…\nKeyboard可取消；已发送的请求无法撤回。");
        recorder.finish((file, failure) -> {
            if (!voiceCurrent(token) || phase != Phase.PROCESSING) { deleteFile(file); return; }
            if (failure != null || file == null) {
                deleteFile(file);
                reset(failure == null ? "录音未完成，请重新录音。" : failure);
                return;
            }
            // Transport owns the recording from here. No host text is read or attached.
            transport.process(session, token, file, null, this::voiceResult);
        });
    }

    private void voiceResult(long token, String transcript, String draft, String error) {
        if (!voiceCurrent(token) || phase != Phase.PROCESSING) return;
        if (error != null || transcript == null || draft == null || !session.accept(token, transcript, draft)) {
            reset(error == null ? "未得到完整草稿，请重新说话。" : error);
            return;
        }
        insertVoiceResult(token, session.draft());
    }

    /** EN alone authorizes reading selected/current editor text and translating it in place. */
    private void translate() {
        if (busy()) return;
        if (!editorAvailable()) { reset(unavailableEditor()); return; }
        String provider = selectedProvider();
        String key = SecureSettings.getApiKey(this, provider);
        String error = ApiKeyValidation.validationMessage(key);
        if (error != null) { showStatus(error); return; }
        reset("");
        if (!bindEditor()) { reset(unavailableEditor()); return; }
        final long token = generation;
        final InputConnection originalConnection = getCurrentInputConnection();
        try {
            boolean finished = originalConnection.finishComposingText();
            if (token != generation) return;
            if (!editorCurrent() || originalConnection != getCurrentInputConnection()) {
                reset("输入框已变化，未翻译。"); return;
            }
            if (!finished) { reset("输入框暂不支持安全替换，请用普通键盘重试。"); return; }
        } catch (RuntimeException ignored) {
            if (token == generation) reset("输入连接已失效，原文未替换。");
            return;
        }
        final EditorSnapshot original = readTypedText();
        if (token != generation || !editorCurrent()) return;
        if (original == null || original.source().trim().isEmpty()) {
            showStatus("请先用普通键盘输入文字，再点EN。无法完整读取的输入框不会替换。");
            return;
        }
        phase = Phase.TRANSLATING;
        updateButtons();
        showStatus("正在把选中文字或输入框草稿整理成英文…");
        OpenAiEnglish.Callback callback = (result, failure) -> {
            if (destroyed || token != generation || phase != Phase.TRANSLATING) return;
            if (!editorCurrent()) { reset("输入框已变化，翻译结果已清除。"); return; }
            if (failure != null) { reset(failure); return; }
            if (result == null || result.trim().isEmpty() || result.length() > VoiceDraftSession.MAX_DRAFT_CHARS) {
                reset("翻译为空或过长，原文未改动。请缩短后重试。");
                return;
            }
            String english = result.trim();
            InputConnection finishingConnection = getCurrentInputConnection();
            try {
                boolean finished = finishingConnection.finishComposingText();
                if (token != generation || phase != Phase.TRANSLATING) return;
                if (!editorCurrent() || finishingConnection != getCurrentInputConnection()) {
                    reset("输入框已变化，未替换。"); return;
                }
                if (!finished) { reset("输入框暂不支持安全替换，原文未改动。"); return; }
            } catch (RuntimeException ignored) {
                if (token == generation) reset("输入连接已失效，原文未替换。");
                return;
            }
            EditorSnapshot current = readTypedText();
            if (token != generation || phase != Phase.TRANSLATING) return;
            if (!editorCurrent()) { reset("输入框已变化，翻译结果已清除。"); return; }
            if (!original.same(current)) {
                reset("输入框文字或光标已变化，未替换。请重新点EN。");
                return;
            }
            InputConnection connection = getCurrentInputConnection();
            boolean inserted = false;
            try {
                connection.beginBatchEdit();
                try {
                    if (editorCurrent() && token == generation && connection == getCurrentInputConnection()
                            && connection.setSelection(original.replaceStart(), original.replaceEnd())
                            && editorCurrent() && token == generation && connection == getCurrentInputConnection())
                        inserted = connection.commitText(english, 1);
                } finally { connection.endBatchEdit(); }
            } catch (RuntimeException ignored) {
                if (token == generation) reset("填入结果不确定，请查看输入框，避免重复填入。");
                return;
            }
            if (token != generation) return; // Host callbacks can synchronously end the old editor.
            if (!editorCurrent()) { reset("输入框已变化，请查看当前内容。"); return; }
            if (inserted) reset("英文已填入。请核对后手动发送。");
            else reset("输入框未接受替换。请查看原文，再重新点EN。");
        };
        if ("gemini".equals(provider)) GeminiEnglish.rewrite(key, original.source().trim(), callback::done);
        else OpenAiEnglish.rewrite(key, original.source().trim(), callback);
    }

    // Only translate() calls this. Voice capture, completion and insertion never read host text.
    private EditorSnapshot readTypedText() {
        if (!editorCurrent()) return null;
        final long token = generation;
        final InputConnection connection = getCurrentInputConnection();
        try {
            ExtractedTextRequest request = new ExtractedTextRequest();
            request.hintMaxChars = MAX_TYPED_CHARS + 1;
            ExtractedText text = connection.getExtractedText(request, 0);
            if (token != generation || !editorCurrent() || connection != getCurrentInputConnection()) return null;
            if (text == null || text.text == null || text.startOffset != 0
                    || text.partialStartOffset >= 0 || text.partialEndOffset >= 0
                    || text.text.length() > MAX_TYPED_CHARS
                    || text.selectionStart < 0 || text.selectionEnd < 0
                    || text.selectionStart > text.text.length() || text.selectionEnd > text.text.length()) return null;
            return new EditorSnapshot(text.text.toString(), text.selectionStart, text.selectionEnd);
        } catch (RuntimeException ignored) { return null; }
    }

    private static final class EditorSnapshot {
        final String text;
        final int start, end;
        EditorSnapshot(String text, int start, int end) { this.text = text; this.start = start; this.end = end; }
        int replaceStart() { return start == end ? 0 : Math.min(start, end); }
        int replaceEnd() { return start == end ? text.length() : Math.max(start, end); }
        String source() { return text.substring(replaceStart(), replaceEnd()); }
        boolean same(EditorSnapshot other) {
            return other != null && text.equals(other.text) && start == other.start && end == other.end;
        }
    }

    /** Stop authorizes one insertion into the unchanged, originally bound collapsed cursor. */
    private void insertVoiceResult(long token, String draft) {
        if (phase != Phase.PROCESSING || !voiceCurrent(token)) return;
        final long editorGeneration = generation;
        final InputConnection connection = getCurrentInputConnection();
        try {
            // Finalize composing text only here, immediately before insertion. The only allowed
            // synchronous callback is this call clearing composition at the exact same cursor.
            boolean finished;
            finishingVoiceComposition = true;
            try { finished = connection.finishComposingText(); }
            finally { finishingVoiceComposition = false; }
            if (editorGeneration != generation || !session.active(token)) return;
            if (!finished) { reset("输入框暂不支持安全填入，请重新开始。"); return; }
            if (!voiceCurrent(token) || connection != getCurrentInputConnection()) return;
            phase = Phase.INSERTING; // Own commit callbacks are not a new voice operation.
            updateButtons();
            boolean inserted = connection.commitText(draft, 1);
            if (editorGeneration != generation) return;
            if (!editorCurrent()) { reset("输入框已变化，请查看当前内容。"); return; }
            reset(inserted ? "已填入输入框。请检查内容，再手动发送。"
                    : "输入框未接受填入。请重新点Mic录音。");
        } catch (RuntimeException ignored) {
            if (editorGeneration == generation)
                reset("填入结果不确定，请查看输入框，避免重复填入。");
        }
    }

    private boolean selectionCurrent() {
        EditorInfo info = getCurrentInputEditorInfo();
        return info != null && selectionBinding.matches(selectionEpoch, info.packageName,
                info.fieldId, info.inputType, getCurrentInputConnection());
    }

    private boolean collapsedSelectionCurrent() {
        return selectionCurrent() && selectionStart >= 0 && selectionStart == selectionEnd;
    }

    private boolean voiceSelectionCurrent() {
        return collapsedSelectionCurrent() && selectionStart == voiceCursor
                && selectionVersion == voiceSelectionVersion;
    }

    private void seedSelection(EditorInfo info) {
        selectionEpoch++;
        selectionVersion++;
        selectionStart = info == null ? -1 : info.initialSelStart;
        selectionEnd = info == null ? -1 : info.initialSelEnd;
        composingStart = composingEnd = -1;
        selectionBinding.bind(selectionEpoch, info == null ? null : info.packageName,
                info == null ? 0 : info.fieldId, info == null ? 0 : info.inputType,
                getCurrentInputConnection(), editorAllowed(info));
    }

    @Override public void onUpdateSelection(int oldStart, int oldEnd, int newStart, int newEnd,
            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldStart, oldEnd, newStart, newEnd, candidatesStart, candidatesEnd);
        selectionVersion++;
        selectionStart = selectionCurrent() ? newStart : -1;
        selectionEnd = selectionCurrent() ? newEnd : -1;
        composingStart = candidatesStart;
        composingEnd = candidatesEnd;
        // Same-offset notifications can indicate edits, so recording/network work always cancels.
        // Only the synchronous composition clear we just invoked may retain the exact anchor.
        if (phase == Phase.RECORDING || phase == Phase.PROCESSING) {
            boolean ownClear = phase == Phase.PROCESSING && finishingVoiceComposition
                    && editorCurrent() && selectionCurrent() && oldStart == voiceCursor && oldEnd == voiceCursor
                    && newStart == voiceCursor && newEnd == voiceCursor
                    && candidatesStart == -1 && candidatesEnd == -1;
            if (ownClear) voiceSelectionVersion = selectionVersion;
            else reset("输入框文字或光标已变化，录音已取消。请重新点Mic。");
        }
    }

    /** Existing explicit Delete action, with no AI/network side effect. */
    private void deleteFromChatInput() {
        if (busy() || !editorAvailable()) return;
        final long token = generation;
        InputConnection connection = getCurrentInputConnection();
        try {
            CharSequence selected = connection.getSelectedText(0);
            if (token != generation || !editorAvailable() || connection != getCurrentInputConnection()) return;
            if (selected != null && selected.length() > 0) connection.commitText("", 1);
            else if (!connection.deleteSurroundingTextInCodePoints(1, 0)
                    && token == generation && editorAvailable() && connection == getCurrentInputConnection())
                sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL);
            if (token == generation && editorAvailable() && connection == getCurrentInputConnection())
                showStatus("已从输入框删除文字。");
        } catch (RuntimeException ignored) {
            // An uncertain editor failure must not trigger a second destructive action.
            if (token == generation) showStatus("输入连接已失效，请查看输入框。");
        }
    }

    /** Status accepts only short, control-free text; response bodies/exception messages are never passed here. */
    private void showStatus(String message) {
        if (status == null) return;
        StringBuilder safe = new StringBuilder();
        int lines = 1;
        for (int i = 0; message != null && i < message.length() && safe.length() < 180; ) {
            int codePoint = message.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\n' && lines++ < 2) safe.append('\n');
            else if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT) safe.append(' ');
            else safe.appendCodePoint(codePoint);
        }
        status.setText(safe.toString());
    }

    private static void deleteFile(File file) { if (file != null) file.delete(); }

    private void reset(String message) {
        generation++;
        session.invalidate();
        voiceToken = -1;
        phase = Phase.IDLE;
        if (recorder != null) recorder.cancel();
        if (transport != null) transport.cancel();
        voiceCursor = -1;
        finishingVoiceComposition = false;
        showStatus(message);
        updateButtons();
    }

    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        seedSelection(info);
        reset(editorAllowed(info) ? IDLE_TEXT : unavailableEditor());
    }
    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (!selectionCurrent()) seedSelection(info);
        reset(editorAllowed(info) ? IDLE_TEXT : unavailableEditor());
    }
    @Override public void onFinishInputView(boolean finishingInput) {
        reset(IDLE_TEXT);
        super.onFinishInputView(finishingInput);
    }
    @Override public void onFinishInput() { reset(IDLE_TEXT); seedSelection(null); super.onFinishInput(); }
    @Override public void onWindowShown() { super.onWindowShown(); updateButtons(); }
    @Override public void onWindowHidden() { reset(IDLE_TEXT); super.onWindowHidden(); }
    @Override public void onDestroy() {
        destroyed = true;
        reset("");
        if (registered) { unregisterReceiver(screenOff); registered = false; }
        super.onDestroy();
    }
}
