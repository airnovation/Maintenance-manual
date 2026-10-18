package com.airnovation.aivoicekeyboard;

import android.content.Context;

/** Never loads the production keystore or any real secret. */
public final class SecureSettings {
    static final String SYNTHETIC_KEY = "SYNTHETIC-NOT-A-REAL-API-KEY";
    static String key = SYNTHETIC_KEY;
    static int reads;
    private SecureSettings() { }
    public static String getApiKey(Context context, String provider) {
        if (!"gemini".equals(provider)) throw new AssertionError("Unexpected provider");
        reads++;
        return key;
    }
}
