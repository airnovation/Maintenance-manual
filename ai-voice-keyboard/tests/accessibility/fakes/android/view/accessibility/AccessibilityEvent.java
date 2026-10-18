package android.view.accessibility;
public final class AccessibilityEvent {
    public static final int TYPE_WINDOW_CONTENT_CHANGED=2048, TYPE_VIEW_FOCUSED=8;
    public CharSequence packageName;
    public int eventType, windowId=-1;
    public CharSequence getPackageName() { return packageName; }
    public int getEventType() { return eventType; }
    public int getWindowId() { return windowId; }
}
