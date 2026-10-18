package android.view.accessibility;
import android.graphics.Rect;
public final class AccessibilityWindowInfo {
    public static final int TYPE_APPLICATION=1, TYPE_INPUT_METHOD=2, TYPE_SYSTEM=3;
    public int id=7, type=TYPE_APPLICATION, layer=1, rootCalls;
    public boolean focused=true, active=true, pictureInPicture;
    public Rect bounds=new Rect(0,0,1080,1920);
    public AccessibilityNodeInfo root;
    public int getId() { return id; }
    public int getType() { return type; }
    public int getLayer() { return layer; }
    public boolean isFocused() { return focused; }
    public boolean isActive() { return active; }
    public boolean isInPictureInPictureMode() { return pictureInPicture; }
    public AccessibilityNodeInfo getRoot() {
        rootCalls++;
        if (type != TYPE_APPLICATION) throw new AssertionError("Other-window root must never be retrieved");
        return root;
    }
    public void getBoundsInScreen(Rect out) { out.set(bounds.left,bounds.top,bounds.right,bounds.bottom); }
    public void recycle() { }
}
