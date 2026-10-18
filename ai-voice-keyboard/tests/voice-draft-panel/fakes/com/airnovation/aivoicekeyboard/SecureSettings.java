package com.airnovation.aivoicekeyboard;
import android.content.Context;
/** In-memory fake. Never reads any real credential. */
final class SecureSettings {
    static String key = "SYNTHETIC_NON_SECRET";
    static int reads;
    static String getApiKey(Context context, String provider) {
        if (!"gemini".equals(provider)) throw new AssertionError("Only Gemini supported");
        reads++; return key;
    }
    static void reset() { key = "SYNTHETIC_NON_SECRET"; reads = 0; }
}
