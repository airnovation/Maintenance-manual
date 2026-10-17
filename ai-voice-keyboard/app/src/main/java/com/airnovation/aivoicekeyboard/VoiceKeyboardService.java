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
        status.setText("Tap Mic to record, tap Stop to finish. AI translates after recording.");
        root.addView(status);

        // Keep every action above the preview, away from the phone's bottom navigation bar.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row);

        mic = makeButton(row, "Mic", () -> toggleRecording());
        improve = makeButton(row, "AI English", () -> translate());
        insert = makeButton(row, "Insert", () -> insertText());

        LinearLayout other = new LinearLayout(this);
        other.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(other);
        makeButton(other, "⌫ Delete", () -> deleteFromChatInput());
        makeButton(other, "Clear Draft", () -> {
            discardRecording();
            generation++;
            processingAudio = false;
            translating = false;
            if (mic != null) { mic.setEnabled(true); mic.setText("Mic"); }
            if (improve != null) improve.setEnabled(true);
            if (insert != null) insert.setEnabled(true);
            preview.setText("");
            status.setText("Ready");
        });
        makeButton(other, "Other Keyboard", () -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        preview = new EditText(this);
        preview.setTextSize(16);
        preview.setSingleLine(false);
        preview.setMinLines(2);
        preview.setMaxLines(3);
        preview.setHint("Recognised speech / English preview");
        preview.setBackgroundColor(Color.WHITE);
        preview.setTextColor(Color.BLACK);
        preview.setHintTextColor(0xff666666);
        preview.setPadding(pad, pad, pad, pad);
        root.addView(preview, new LinearLayout.LayoutParams(-1, -2));
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
        sensitiveField = isSensitive(attribute);
        if (preview != null) preview.setText("");
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
                : "Tap Mic to record; tap Stop to finish; review English before Insert.");
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
            MediaRecorder newRecorder = new MediaRecorder();
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            newRecorder.setAudioEncodingBitRate(64000);
            newRecorder.setAudioSamplingRate(16000);
            newRecorder.setOutputFile(file.getAbsolutePath());
            newRecorder.prepare();
            newRecorder.start();
            recordingFile = file;
            recorder = newRecorder;
            recordStarted = android.os.SystemClock.elapsedRealtime();
            recording = true;
            generation++;
            // Every new Mic session starts a fresh message; no need to press Clear.
            if (preview != null) preview.setText("");
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

    private void translate() {
        if (sensitiveField || preview == null || recording || processingAudio) return;
        String original = preview.getText().toString().trim();
        if (original.isEmpty()) {
            status.setText("Speak or type a message first.");
            return;
        }
        String provider = getSharedPreferences("voice_keyboard_settings", Context.MODE_PRIVATE)
                .getString("selected_provider", "openai");
        String key = SecureSettings.getApiKey(this, provider);
        if (key.isEmpty()) {
            status.setText("Save your " + ("gemini".equals(provider) ? "Gemini" : "OpenAI")
                    + " API key in the AI Voice Keyboard app first.");
            return;
        }
        int token = ++generation;
        translating = true;
        if (improve != null) improve.setEnabled(false);
        if (mic != null) mic.setEnabled(false);
        if (insert != null) insert.setEnabled(false);
        status.setText("Converting to natural English…");
        OpenAiEnglish.Callback callback = (result, error) -> {
            if (token != generation || sensitiveField || preview == null) return;
            translating = false;
            if (improve != null) improve.setEnabled(true);
            if (mic != null) mic.setEnabled(true);
            if (insert != null) insert.setEnabled(true);
            if (error != null) {
                status.setText("AI unavailable: " + error + ". Original text retained.");
                return;
            }
            preview.setText(result);
            preview.setSelection(preview.length());
            status.setText("Review/edit English, then tap Insert.");
        };
        if ("gemini".equals(provider)) {
            GeminiEnglish.rewrite(key, original, (result, error) -> callback.done(result, error));
        } else {
            OpenAiEnglish.rewrite(key, original, callback);
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
        connection.commitText(value, 1);
        generation++;
        preview.setText("");
        status.setText("Inserted. Press Send in the app when ready.");
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
        discardRecording();
    }

    @Override public void onDestroy() {
        discardRecording();
        super.onDestroy();
    }
}
