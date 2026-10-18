package android.content;
public class Intent {
    public static final String ACTION_SCREEN_OFF = "screen-off";
    public static final int FLAG_ACTIVITY_NEW_TASK = 1;
    public Intent(String action) { }
    public Intent addFlags(int flags) { return this; }
}
