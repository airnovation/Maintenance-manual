package android.content;
public class SharedPreferences {
    public static String provider = "gemini";
    public static int reads;
    public String getString(String name, String fallback) { reads++; return provider; }
}
