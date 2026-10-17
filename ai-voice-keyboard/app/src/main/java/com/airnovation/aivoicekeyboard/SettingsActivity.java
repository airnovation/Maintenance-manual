package com.airnovation.aivoicekeyboard;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private static final int MIC_PERMISSION_REQUEST = 10;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 28, 24, 16);

        TextView title = new TextView(this);
        title.setText("AI Voice Keyboard – prototype");
        title.setTextSize(22);
        layout.addView(title);

        TextView details = new TextView(this);
        details.setText("\n1. Allow microphone access.\n2. Enable the keyboard in Android Settings.\n3. Select AI Voice Keyboard as your input method.\n4. Open WhatsApp, Gmail or any text box.\n\nSpeech recognition uses your device's speech service. AI translation sends only the preview text to OpenAI when you tap AI English. This keyboard never sends a message for you.\n");
        layout.addView(details);

        Button permission = new Button(this);
        permission.setText("1 – Allow Microphone");
        permission.setOnClickListener(v -> {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_REQUEST);
            } else {
                Toast.makeText(this, "Microphone already allowed", Toast.LENGTH_SHORT).show();
            }
        });
        layout.addView(permission);

        Button enable = new Button(this);
        enable.setText("2 – Enable Keyboard");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        layout.addView(enable);

        Button select = new Button(this);
        select.setText("3 – Select Keyboard");
        select.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        layout.addView(select);

        EditText key = new EditText(this);
        key.setSingleLine(true);
        key.setHint("OpenAI API key (optional until AI translation)");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(key);

        TextView keyInfo = new TextView(this);
        keyInfo.setText("\nAPI key is encrypted locally using Android Keystore. Never paste it in GitHub or WhatsApp. OpenAI API usage is billed separately from ChatGPT subscriptions. Leave blank if you only want Chinese speech-to-text.\n");
        layout.addView(keyInfo);

        Button save = new Button(this);
        save.setText("Save API Key Locally");
        save.setOnClickListener(v -> {
            String input = key.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "Enter a key to save, or use Remove Key", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                SecureSettings.saveApiKey(this, input);
                key.setText("");
                Toast.makeText(this, "Saved securely", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not save key", Toast.LENGTH_LONG).show();
            }
        });
        layout.addView(save);

        Button remove = new Button(this);
        remove.setText("Remove Saved API Key");
        remove.setOnClickListener(v -> {
            try {
                SecureSettings.saveApiKey(this, "");
                Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not remove key", Toast.LENGTH_LONG).show();
            }
        });
        layout.addView(remove);
        setContentView(layout);
    }
}
