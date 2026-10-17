package com.airnovation.aivoicekeyboard;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.ScrollView;
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
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        scroll.setFillViewport(true);
        scroll.addView(layout);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 28, 24, 16);

        TextView title = new TextView(this);
        title.setText("AI Voice Keyboard – prototype");
        title.setTextSize(22);
        layout.addView(title);

        TextView details = new TextView(this);
        details.setText("\n1. Allow microphone access.\n2. Enable the keyboard in Android Settings.\n3. Select AI Voice Keyboard as your input method.\n4. Open WhatsApp, Gmail or any text box.\n\nSpeech recognition uses your device's speech service. AI translation sends only the preview text to your selected provider (OpenAI or Gemini) when you tap AI English. This keyboard never sends a message for you.\n");
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

        Button updates = new Button(this);
        updates.setText("Check New Test Builds");
        updates.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/airnovation/Maintenance-manual/actions"))));
        layout.addView(updates);
        TextView updateNote = new TextView(this);
        updateNote.setText("Opens GitHub builds. Automatic installation is not available yet; Android will still require your approval.");
        layout.addView(updateNote);

        TextView providerLabel = new TextView(this);
        providerLabel.setText("AI Provider (select now or later)");
        layout.addView(providerLabel);

        Spinner provider = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"OpenAI (ChatGPT API)", "Google Gemini API"});
        provider.setAdapter(adapter);
        String savedProvider = getSharedPreferences("voice_keyboard_settings", MODE_PRIVATE)
                .getString("selected_provider", "openai");
        provider.setSelection("gemini".equals(savedProvider) ? 1 : 0);
        layout.addView(provider);
        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                getSharedPreferences("voice_keyboard_settings", MODE_PRIVATE).edit()
                        .putString("selected_provider", position == 1 ? "gemini" : "openai").apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        EditText key = new EditText(this);
        key.setSingleLine(true);
        key.setHint("API key for selected provider (optional)");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(key);

        TextView keyInfo = new TextView(this);
        keyInfo.setText("\nAPI key is encrypted locally using Android Keystore. Never paste it in GitHub or WhatsApp. OpenAI and Gemini require different keys. OpenAI API billing is separate from ChatGPT subscriptions. Leave blank if you only want Chinese speech-to-text.\n");
        layout.addView(keyInfo);

        TextView keyStatus = new TextView(this);
        keyStatus.setText("No key is required to test Chinese speech recognition.");
        keyStatus.setTextSize(14);
        layout.addView(keyStatus);

        Button save = new Button(this);
        save.setText("Save API Key Locally");
        save.setOnClickListener(v -> {
            String input = key.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "Enter a key to save, or use Remove Key", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                String selected = provider.getSelectedItemPosition() == 1 ? "gemini" : "openai";
                SecureSettings.saveApiKey(this, input, selected);
                if (!input.equals(SecureSettings.getApiKey(this, selected))) {
                    throw new IllegalStateException("Encrypted key could not be read back");
                }
                key.setText("");
                keyStatus.setText("Saved and verified: " + selected + " key. The key stays on this phone.");
                Toast.makeText(this, "API key saved securely", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                // Show the exception TYPE only. Never print or log the secret key itself.
                keyStatus.setText("Save failed: " + e.getClass().getSimpleName()
                        + ". The key was not confirmed saved.");
                Toast.makeText(this, "Could not save key", Toast.LENGTH_LONG).show();
            }
        });
        layout.addView(save);

        Button remove = new Button(this);
        remove.setText("Remove Saved API Key");
        remove.setOnClickListener(v -> {
            try {
                SecureSettings.saveApiKey(this, "",
                        provider.getSelectedItemPosition() == 1 ? "gemini" : "openai");
                keyStatus.setText("Selected provider's key removed.");
                Toast.makeText(this, "Key for selected provider removed", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not remove key", Toast.LENGTH_LONG).show();
            }
        });
        layout.addView(remove);
        setContentView(scroll);
    }
}
