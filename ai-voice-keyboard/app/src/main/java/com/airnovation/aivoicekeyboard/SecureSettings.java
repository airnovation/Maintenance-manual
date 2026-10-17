package com.airnovation.aivoicekeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores the user-provided API key encrypted under Android Keystore; never in the APK. */
public final class SecureSettings {
    private static final String STORE = "voice_keyboard_settings";
    private static final String KEY_ALIAS = "voice_keyboard_key_v1";
    private static final String SECRET = "openai_key";
    private SecureSettings() {}

    public static void saveApiKey(Context context, String value) throws Exception {
        String sanitized = value.trim();
        if (sanitized.isEmpty()) {
            context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().remove(SECRET).apply();
            return;
        }
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(sanitized.getBytes(StandardCharsets.UTF_8));
        String encrypted = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString(SECRET, encrypted).apply();
    }

    public static String getApiKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        String stored = prefs.getString(SECRET, "");
        if (stored == null || stored.isEmpty()) return "";
        try {
            String[] parts = stored.split(":", 2);
            if (parts.length != 2) return "";
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
