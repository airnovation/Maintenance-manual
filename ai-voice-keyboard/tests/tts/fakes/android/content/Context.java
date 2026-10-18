package android.content;
import android.content.res.Resources;
public class Context {
    public static final int MODE_PRIVATE = 0, RECEIVER_NOT_EXPORTED = 4;
    public BroadcastReceiver receiver;
    public final Resources resources = new Resources();
    public Resources getResources() { return resources; }
    public SharedPreferences getSharedPreferences(String name, int mode) { return new SharedPreferences(); }
    public void startActivity(Intent intent) { throw new AssertionError("No external activity expected in offline TTS test"); }
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) { this.receiver = receiver; return null; }
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) { return registerReceiver(receiver, filter); }
    public void unregisterReceiver(BroadcastReceiver receiver) { this.receiver = null; }
}
