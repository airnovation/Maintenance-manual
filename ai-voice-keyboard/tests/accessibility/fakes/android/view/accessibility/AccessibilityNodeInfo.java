package android.view.accessibility;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;
/** Minimal host fixture. Recycling is deliberately not an Android lifecycle simulation. */
public final class AccessibilityNodeInfo {
    public String packageName="com.whatsapp.w4b", resourceId, className="android.view.View";
    public boolean visible=true, editable, password, focused, allowText;
    public int windowId=7, textCalls;
    public CharSequence fixtureText="SYNTHETIC MESSAGE";
    public Rect bounds=new Rect(), localBounds=new Rect();
    public final List<AccessibilityNodeInfo> children=new ArrayList<>();
    public static AccessibilityNodeInfo obtain(AccessibilityNodeInfo node) { return node; }
    public CharSequence getPackageName() { return packageName; }
    public String getViewIdResourceName() { return resourceId; }
    public CharSequence getClassName() { return className; }
    public boolean isVisibleToUser() { return visible; }
    public boolean isEditable() { return editable; }
    public boolean isPassword() { return password; }
    public boolean isFocused() { return focused; }
    public int getWindowId() { return windowId; }
    public void getBoundsInScreen(Rect out) { copy(bounds, out); }
    public void getBoundsInParent(Rect out) { copy(localBounds, out); }
    private static void copy(Rect from, Rect to) { to.set(from.left, from.top, from.right, from.bottom); }
    public int getChildCount() { return children.size(); }
    public AccessibilityNodeInfo getChild(int index) { return children.get(index); }
    public CharSequence getText() {
        textCalls++;
        if (!allowText) throw new AssertionError("FORBIDDEN getText: " + resourceId);
        return fixtureText;
    }
    public void recycle() { }
}
