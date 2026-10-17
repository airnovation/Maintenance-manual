package com.airnovation.aivoicekeyboard;

import android.Manifest;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Voice-first input method. No automatic messaging or background conversation reading. */
public class VoiceKeyboardService extends InputMethodService {
    private TextView status;
    private EditText preview;
    private Button mic;
    private Button improve;
    private Button insert;
    private MediaRecorder recorder;
    private File recordingFile;
    private long recordStarted;
    private boolean recording = false;
    private boolean processingAudio = false;
    private boolean sensitiveField = false;
    private int generation = 0;
    private boolean translating = false;
    private static final long MAX_RECORD_MS = 3 * 60 * 1000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stopAtLimit = () -> {
        if (recording) {
            finishRecording();
            if (status != null) status.setText("Maximum recording length reached. Processing audio…");
        }
    };

    @Override public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 12);
        root.setPadding(pad, pad, pad, pad * 2);
        root.setBackgroundColor(0xfff1f2f4);

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xff202020);
        status.setText("Mic / Stop: voice. EN: translate text from your regular keyboard.");
        root.addView(status);

        // Keep every action above the preview, away from the phone's bottom navigation bar.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row);

        mic = makeButton(row, "Mic", () -> toggleRecording());
        improve = makeButton(row, "EN", () -> translate());
        insert = makeButton(row, "Insert", () -> insertText());
        insert.setVisibility(View.GONE); // Emergency fallback only when auto-insert fails.

        LinearLayout other = new LinearLayout(this);
        other.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(other);
        makeButton(other, "⌫ Delete", () -> deleteFromChatInput());
        makeButton(other, "Other Keyboard", () -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        preview = new EditText(this);
        preview.setTextSize(16);
        preview.setSingleLine(false);
        preview.setMinLines(2);
        preview.setMaxLines(3);
        preview.setHint("Translation fallback");
        preview.setBackgroundColor(Color.WHITE);
        preview.setTextColor(Color.BLACK);
        preview.setHintTextColor(0xff666666);
        preview.setPadding(pad, pad, pad, pad);
        root.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        preview.setVisibility(View.GONE);

        applySensitiveState();
        return root;
    }

    private Button makeButton(LinearLayout row, String title, Runnable click) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextColor(0xff111111);
        b.setBackgroundTintList(ColorStateList.valueOf(0xffd7e2ef));
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        b.setOnClickListener(v -> click.run());
        return b;
    }


    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        generation++;
        discardRecording();
        processingAudio = false;
        translating = false;
        sensitiveField = isSensitive(attribute);
        if (preview != null) preview.setText("");
        if (insert != null) insert.setVisibility(View.GONE);
        if (preview != null) preview.setVisibility(View.GONE);
        applySensitiveState();
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        sensitiveField = isSensitive(info);
        applySensitiveState();
    }

    private boolean isSensitive(EditorInfo info) {
        if (info == null) return false;
        int type = info.inputType;
        int clazz = type & InputType.TYPE_MASK_CLASS;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        if (clazz == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return true;
        if (clazz != InputType.TYPE_CLASS_TEXT) return false;
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    private void applySensitiveState() {
        if (mic != null) mic.setEnabled(!sensitiveField && !processingAudio && !translating);
        if (improve != null) improve.setEnabled(!sensitiveField && !processingAudio && !recording);
        if (insert != null) insert.setEnabled(!sensitiveField && !processingAudio && !translating && !recording);
        if (preview != null) preview.setEnabled(!sensitiveField);
        if (status != null) status.setText(sensitiveField
                ? "Sensitive field: voice & AI disabled. Switch to a trusted keyboard."
                : "Mic / Stop: translate speech automatically. EN: translate typed chat text.");
    }

    /**
     * Manual start/stop: never use SpeechRecognizer's silence-based end detection.
     * Audio stays in private app cache until Stop is pressed, then is sent to
     * the user's selected provider for transcription and deleted.
     */
    private void toggleRecording() {
        if (recording) {
            finishRecording();
            return;
        }
        if (sensitiveField || processingAudio || translating) return;
        String provider = selectedProvider();
        String key = SecureSettings.getApiKey(this, provider);
        if (key.isEmpty()) {
            status.setText("Set up " + ("gemini".equals(provider) ? "Gemini" : "OpenAI")
                    + " API key in app settings for manual recording.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Allow microphone access from the AI Voice Keyboard app.");
            return;
        }
        discardRecording();
        try {
            File file = File.createTempFile("voice_segment_", ".m4a", getCacheDir());
            recordingFile = file;
            MediaRecorder newRecorder = new MediaRecorder();
            recorder = newRecorder;
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            newRecorder.setAudioEncodingBitRate(64000);
            newRecorder.setAudioSamplingRate(16000);
            newRecorder.setOutputFile(file.getAbsolutePath());
            newRecorder.prepare();
            newRecorder.start();
            recordStarted = android.os.SystemClock.elapsedRealtime();
            recording = true;
            generation++;
            // Every new Mic session starts a fresh message; no need to press Clear.
            if (preview != null) preview.setText("");
            if (insert != null) insert.setVisibility(View.GONE);
            mic.setText("Stop");
            improve.setEnabled(false);
            insert.setEnabled(false);
            status.setText("Recording… speak naturally. Tap Stop when finished.");
            handler.postDelayed(stopAtLimit, MAX_RECORD_MS);
        } catch (Exception e) {
            discardRecording();
            status.setText("Could not start microphone (" + e.getClass().getSimpleName()
                    + "). Check permissions and other apps using the mic.");
        }
    }

    private void finishRecording() {
        if (!recording) return;
        handler.removeCallbacks(stopAtLimit);
        recording = false;
        if (mic != null) mic.setText("Mic");
        File file = recordingFile;
        recordingFile = null;
        MediaRecorder current = recorder;
        recorder = null;
        boolean saved = false;
        try {
            if (current != null) {
                current.stop();
                saved = true;
            }
        } catch (Exception e) {
            if (status != null) status.setText("Recording too short or interrupted. Try again.");
        } finally {
            if (current != null) try { current.release(); } catch (Exception ignored) { }
        }
        if (!saved || file == null || android.os.SystemClock.elapsedRealtime() - recordStarted < 500) {
            if (file != null) file.delete();
            if (status != null) status.setText("Recording was too short. Tap Mic and speak for a moment.");
            if (mic != null) mic.setEnabled(true);
            if (improve != null) improve.setEnabled(true);
            if (insert != null) insert.setEnabled(true);
            return;
        }
        processingAudio = true;
        if (mic != null) mic.setEnabled(false);
        if (improve != null) improve.setEnabled(false);
        if (insert != null) insert.setEnabled(false);
        if (status != null) status.setText("Transcribing Chinese / English / Malay speech…");
        final int token = generation;
        String provider = selectedProvider();
        String key = SecureSettings.getApiKey(this, provider);
        AudioTranscriber.transcribe(provider, key, file, (transcript, error) -> {
            if (token != generation || sensitiveField) return;
            processingAudio = false;
            if (mic != null) mic.setEnabled(true);
            if (improve != null) improve.setEnabled(true);
            if (error != null) {
                if (insert != null) insert.setEnabled(false);
                if (status != null) status.setText("Speech unavailable: " + error
                        + " Tap Mic to record again.");
                return;
            }
            if (preview != null) {
                preview.setText(transcript);
                preview.setSelection(preview.length());
            }
            if (status != null) status.setText("Speech recognised. Converting to English…");
            translate();
        });
    }

    private String selectedProvider() {
        return getSharedPreferences("voice_keyboard_settings", Context.MODE_PRIVATE)
                .getString("selected_provider", "openai");
    }

    private void discardRecording() {
        handler.removeCallbacks(stopAtLimit);
        recording = false;
        MediaRecorder current = recorder;
        recorder = null;
        if (current != null) {
            try { current.stop(); } catch (Exception ignored) { }
            try { current.release(); } catch (Exception ignored) { }
        }
        if (recordingFile != null) recordingFile.delete();
        recordingFile = null;
        if (mic != null) mic.setText("Mic");
    }


    /**
     * Voice: transcribe then translate and insert directly into the host chat draft.
     * Text: tap EN with a draft already typed in the host chat field, then replace
     * that draft with the translated English. Never press the host Send button.
     */
    private void translate() {
        if (sensitiveField || preview == null || recording || processingAudio || translating) return;
        final String speechDraft = preview.getText().toString().trim();
        final boolean fromTypedChat = speechDraft.isEmpty();
        final String originalChat = fromTypedChat ? readChatDraft() : "";
        final String original = fromTypedChat ? originalChat.trim() : speechDraft;
        if (original.isEmpty()) {
            status.setText("Speak using Mic, or type in the chat with your normal keyboard and tap EN.");
            return;
        }
        String provider = selectedProvider();
        String key = SecureSettings.getApiKey(this, provider);
        if (key.isEmpty()) {
            status.setText("Save your " + ("gemini".equals(provider) ? "Gemini" : "OpenAI")
                    + " API key in app settings first.");
            return;
        }

        int token = ++generation;
        translating = true;
        if (improve != null) improve.setEnabled(false);
        if (mic != null) mic.setEnabled(false);
        if (insert != null) { insert.setEnabled(false); insert.setVisibility(View.GONE); }
        status.setText("Translating to English…");
        OpenAiEnglish.Callback callback = (result, error) -> {
            if (token != generation || sensitiveField || preview == null) return;
            translating = false;
            if (improve != null) improve.setEnabled(true);
            if (mic != null) mic.setEnabled(true);
            if (insert != null) insert.setEnabled(true);
            if (error != null) {
                // Speech transcript is retained for a retry; the typed chat is never modified on failure.
                status.setText("Translation failed: " + error + ". Original text retained; tap EN to retry.");
                return;
            }
            if (result == null || result.trim().isEmpty()) {
                status.setText("Translation returned no text. Original retained.");
                return;
            }
            String english = result.trim();
            boolean inserted = false;
            if (fromTypedChat) {
                // Never overwrite typing that changed while the API request was running.
                if (originalChat.equals(readChatDraft())) {
                    InputConnection connection = getCurrentInputConnection();
                    if (connection != null) {
                        connection.beginBatchEdit();
                        try {
                            if (connection.setSelection(0, originalChat.length())) {
                                inserted = connection.commitText(english, 1);
                            }
                        } finally {
                            connection.endBatchEdit();
                        }
                    }
                } else {
                    status.setText("Chat text changed while translating. English is in preview; review before Insert.");
                }
            } else {
                InputConnection connection = getCurrentInputConnection();
                if (connection != null) inserted = connection.commitText(english, 1);
            }
            if (inserted) {
                generation++;
                preview.setText("");
                if (insert != null) insert.setVisibility(View.GONE);
                preview.setVisibility(View.GONE);
                status.setText("English inserted into chat. Review it, then tap Send yourself.");
            } else {
                preview.setText(english);
                preview.setSelection(preview.length());
                preview.setVisibility(View.VISIBLE);
                if (insert != null) insert.setVisibility(View.VISIBLE);
                if (status != null && !status.getText().toString().startsWith("Chat text changed")) {
                    status.setText("Could not insert automatically. Review English and tap Insert.");
                }
            }
        };
        if ("gemini".equals(provider)) {
            GeminiEnglish.rewrite(key, original, callback::done);
        } else {
            OpenAiEnglish.rewrite(key, original, callback);
        }
    }

    private String readChatDraft() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return "";
        try {
            ExtractedTextRequest request = new ExtractedTextRequest();
            ExtractedText extracted = connection.getExtractedText(request, 0);
            // Never replace a partial extraction, or text belonging to another field.
            if (extracted == null || extracted.text == null || extracted.startOffset != 0) return "";
            return extracted.text.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void insertText() {
        if (sensitiveField || preview == null || translating || processingAudio || recording) return;
        String value = preview.getText().toString().trim();
        if (value.isEmpty()) return;
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            status.setText("Tap a message input box first.");
            return;
        }
        if (connection.commitText(value, 1)) {
            generation++;
            preview.setText("");
            if (insert != null) insert.setVisibility(View.GONE);
            preview.setVisibility(View.GONE);
            status.setText("Inserted into chat. Review before sending.");
        } else {
            status.setText("Insert failed. Tap the message input box and retry.");
        }
    }

    /**
     * Deletes from the host app's active editor, NOT from our local preview.
     * One press removes selected text or the code point just before the cursor.
     */
    private void deleteFromChatInput() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            if (status != null) status.setText("Tap the WhatsApp message box first.");
            return;
        }
        try {
            CharSequence selected = connection.getSelectedText(0);
            if (selected != null && selected.length() > 0) {
                connection.commitText("", 1);
            } else if (!connection.deleteSurroundingTextInCodePoints(1, 0)) {
                // Some third-party input boxes don't implement deleteSurroundingTextInCodePoints.
                sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL);
            }
        } catch (Exception e) {
            sendDownUpKeyEvents(android.view.KeyEvent.KEYCODE_DEL);
        }
        if (status != null) status.setText("Deleted from chat input. Clear Draft only clears the preview.");
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        generation++; // Invalidate pending async speech/translation callbacks.
        discardRecording();
        processingAudio = false;
        translating = false;
    }

    @Override public void onDestroy() {
        discardRecording();
        super.onDestroy();
    }
}
