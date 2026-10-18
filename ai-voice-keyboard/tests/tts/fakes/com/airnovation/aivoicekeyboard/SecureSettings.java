package com.airnovation.aivoicekeyboard;
import android.content.Context;
final class SecureSettings {
    static boolean allowFakeKeyRead;
    static String key = "";
    static int reads;
    static String getApiKey(Context context, String provider) {
        reads++;
        if (!allowFakeKeyRead) throw new AssertionError("No credentials are permitted");
        return key;
    }
    static void reset() { allowFakeKeyRead = false; key = ""; reads = 0; }
}
