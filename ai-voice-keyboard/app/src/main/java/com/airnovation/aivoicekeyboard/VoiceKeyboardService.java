package com.airnovation.aivoicekeyboard;

import android.Manifest;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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
    private SpeechRecognizer recognizer;
    private boolean sensitiveField = false;
    private boolean listening = false;
    private int generation = 0;
    private boolean translating = false;

    @Override public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (getResources().getDisplayMetrics().density * 12);
        root.setPadding(pad, pad, pad, pad * 2);
        root.setBackgroundColor(0xfff1f2f4);

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(0xff202020);
        status.setText("Mic → English (if API is set) → Review → Insert");
        root.addView(status);

        // Keep every action above the preview, away from the phone's bottom navigation bar.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row);

        mic = makeButton(row, "Mic", () -> startSpeech());
        improve = makeButton(row, "AI English", () -> translate());
        insert = makeButton(row, "Insert", () -> insertText());

        LinearLayout other = new LinearLayout(this);
        other.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(other);
        makeButton(other, "Clear", () -> {
            generation++;
            translating = false;
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
        stopListening();
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
        if (mic != null) mic.setEnabled(!sensitiveField);
        if (improve != null) improve.setEnabled(!sensitiveField);
        if (insert != null) insert.setEnabled(!sensitiveField && !translating);
        if (preview != null) preview.setEnabled(!sensitiveField);
        if (status != null) status.setText(sensitiveField
                ? "Sensitive field: voice & AI disabled. Switch to a trusted keyboard."
                : "Mic → English (if API is set) → Review → Insert");
    }

    private void startSpeech() {
        if (sensitiveField) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Open AI Voice Keyboard app and allow microphone permission.");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.setText("No Android speech recognition service available on this device.");
            return;
        }
        generation++;
        stopListening();
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { if (status != null) status.setText("Listening…"); }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { if (status != null) status.setText("Recognizing…"); }
                @Override public void onError(int error) {
                    listening = false;
                    if (status != null) status.setText(error == SpeechRecognizer.ERROR_NO_MATCH
                            ? "No speech recognised. Tap Mic once and start talking when Listening appears."
                            : "Speech error (" + error + "). Try again.");
                }
                @Override public void onResults(Bundle results) {
                    listening = false;
                    java.util.ArrayList<String> found = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (found == null || found.isEmpty() || preview == null || sensitiveField) {
                        if (status != null) status.setText("No speech found.");
                        return;
                    }
                    String existing = preview.getText().toString().trim();
                    preview.setText(existing.isEmpty() ? found.get(0) : existing + " " + found.get(0));
                    preview.setSelection(preview.length());
                    if (status != null) status.setText("Transcript ready. Checking AI configuration…");
                    String provider = getSharedPreferences("voice_keyboard_settings", Context.MODE_PRIVATE)
                            .getString("selected_provider", "openai");
                    if (SecureSettings.getApiKey(VoiceKeyboardService.this, provider).isEmpty()) {
                        status.setText("Chinese transcript ready. Add an API key for automatic English.");
                    } else {
                        translate();
                    }
                }
                @Override public void onPartialResults(Bundle results) { }
                @Override public void onEvent(int eventType, Bundle params) { }
            });
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            recognizer.startListening(intent);
            listening = true;
        } catch (Exception e) {
            status.setText("Speech recognition unavailable.");
            stopListening();
        }
    }

    private void translate() {
        if (sensitiveField || preview == null) return;
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
        if (insert != null) insert.setEnabled(false);
        status.setText("Converting to natural English…");
        OpenAiEnglish.Callback callback = (result, error) -> {
            if (token != generation || sensitiveField || preview == null) return;
            translating = false;
            if (improve != null) improve.setEnabled(true);
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
        if (sensitiveField || preview == null || translating) return;
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

    private void stopListening() {
        listening = false;
        if (recognizer != null) {
            try { recognizer.cancel(); recognizer.destroy(); } catch (Exception ignored) { }
            recognizer = null;
        }
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        stopListening();
    }

    @Override public void onDestroy() {
        stopListening();
        super.onDestroy();
    }
}
