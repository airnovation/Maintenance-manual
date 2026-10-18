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
        details.setText("\n1. Allow microphone access.\n2. Enable and select the keyboard in Android Settings.\n3. Select Gemini and save your own API key below.\n\nMic starts recording; Stop sends this recording to Gemini for transcription and intent-aware drafting. The default is natural English. Say 用中文, 客气一点 or 简短一点 to change the output. The completed draft is inserted automatically only into the same unchanged input position. Review or delete/edit it in the input field, then send the message yourself. Moving the cursor, selecting text or switching fields cancels pending voice input. No chat reading or Accessibility permission is used.\n\nEN keeps the original typed-text translation workflow: type with your usual keyboard, switch here, then tap EN for natural, conversational English that preserves your meaning, dates, names and uncertainty. Delete and Keyboard retain their original functions. Temporary recordings are deleted after processing or cancellation.\n");
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
        updates.setText("Open GitHub Test Builds");
        updates.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/airnovation/Maintenance-manual/actions"))));
        layout.addView(updates);
        TextView updateNote = new TextView(this);
        updateNote.setText("Opens GitHub build history. This is not an automatic update checker. You still need to download a new APK and approve installation; debug test signatures may require uninstalling the old version.");
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
        key.setSaveEnabled(false);
        key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        key.setHint("API key for selected provider (optional)");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(key);

        TextView keyInfo = new TextView(this);
        keyInfo.setText("\nAPI key is encrypted locally using Android Keystore. Never paste it in GitHub or WhatsApp. OpenAI and Gemini require different keys. OpenAI API billing is separate from ChatGPT subscriptions. An API key is required for voice transcription in this test version.\n");
        layout.addView(keyInfo);

        TextView keyStatus = new TextView(this);
        keyStatus.setText("Save a Gemini API key to use Mic. OpenAI remains available for EN.");
        keyStatus.setTextSize(14);
        layout.addView(keyStatus);

        Button save = new Button(this);
        save.setText("Save API Key Locally");
        save.setOnClickListener(v -> {
            String input = key.getText().toString();
            if (input.isEmpty()) {
                Toast.makeText(this, "Enter a key to save, or use Remove Key", Toast.LENGTH_SHORT).show();
                return;
            }
            String validation = ApiKeyValidation.validationMessage(input);
            if (validation != null) {
                keyStatus.setText(validation);
                return;
            }
            try {
                String selected = provider.getSelectedItemPosition() == 1 ? "gemini" : "openai";
                SecureSettings.saveApiKey(this, input, selected);
                if (!input.equals(SecureSettings.getApiKey(this, selected))) {
                    throw new IllegalStateException("Encrypted key could not be read back");
                }
                key.setText("");
                keyStatus.setText("Saved and verified: " + selected + " key. Stored encrypted on this phone; used only with the selected API provider.");
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
