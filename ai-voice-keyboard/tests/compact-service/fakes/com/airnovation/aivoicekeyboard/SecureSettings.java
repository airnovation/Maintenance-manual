package com.airnovation.aivoicekeyboard;
import android.content.Context;
final class SecureSettings {
static String key="SYNTHETIC_NON_SECRET"; static int reads;
static String getApiKey(Context context,String provider) { reads++; return key; }
static void reset() { key="SYNTHETIC_NON_SECRET"; reads=0; }
}
