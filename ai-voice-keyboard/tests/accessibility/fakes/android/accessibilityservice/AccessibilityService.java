package android.accessibilityservice;
import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;
public abstract class AccessibilityService extends Context {
    public List<AccessibilityWindowInfo> fixtureWindows=new ArrayList<>();
    public int windowsCalls;
    public final MagnificationController magnification=new MagnificationController();
    public static final class MagnificationController {
        public float scale=1f;
        public float getScale() { return scale; }
    }
    public List<AccessibilityWindowInfo> getWindows() { windowsCalls++; return fixtureWindows; }
    public MagnificationController getMagnificationController() { return magnification; }
    protected void onServiceConnected() { }
    public abstract void onAccessibilityEvent(AccessibilityEvent event);
    public abstract void onInterrupt();
    public void onDestroy() { }
}
