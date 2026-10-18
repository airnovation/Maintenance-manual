package android.content;
import android.app.KeyguardManager;
import android.content.res.Resources;
public class Context {
    public static final String KEYGUARD_SERVICE="keyguard";
    public static final int RECEIVER_NOT_EXPORTED=4;
    public final KeyguardManager keyguard = new KeyguardManager();
    private final Resources resources = new Resources();
    public BroadcastReceiver registeredReceiver;
    public Object getSystemService(String name) { return KEYGUARD_SERVICE.equals(name) ? keyguard : null; }
    public Resources getResources() { return resources; }
    public String getPackageName() { return "com.airnovation.aivoicekeyboard"; }
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) { registeredReceiver=receiver; return null; }
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) { registeredReceiver=receiver; return null; }
    public void unregisterReceiver(BroadcastReceiver receiver) { if (registeredReceiver==receiver) registeredReceiver=null; }
}
