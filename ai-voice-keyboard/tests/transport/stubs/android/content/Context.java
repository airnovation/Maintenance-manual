package android.content;

/** Host-only application context. No preferences, device, or credential access. */
public class Context {
    public Context getApplicationContext() { return this; }
}
