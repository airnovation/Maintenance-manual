package com.airnovation.aivoicekeyboard;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reads only on an explicit foreground request; event callbacks only invalidate. */
public final class VisibleMessageService extends AccessibilityService {
    private static VisibleMessageService connected;
    private final AssistantSession session = AssistantSession.INSTANCE;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { session.invalidate(); }
    };
    private boolean registered;
    static boolean available() { return connected != null; }
    static String captureOnce(long token) {
        if (connected == null) return "请先手动启用输入助手的无障碍服务；未读取或上传。";
        return connected.capture(token, false);
    }
    static boolean stillCurrent(long token) {
        return connected != null && connected.capture(token, true) == null;
    }
    @Override protected void onServiceConnected() {
        connected = this;
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenOff, filter);
        registered = true;
    }
    private String capturedWindowGeometry;
    private boolean safeUnchangedStatusBarEvent(AccessibilityEvent event) {
        try { return unchangedStatusBarEvent(event); }
        catch (RuntimeException ignored) { return false; }
    }
    private boolean unchangedStatusBarEvent(AccessibilityEvent event) {
        if (capturedWindowGeometry == null || event.getWindowId() < 0) return false;
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null) return false;
        try {
            if (!capturedWindowGeometry.equals(windowGeometry(windows))) return false;
            int displayHeight = getResources().getDisplayMetrics().heightPixels;
            for (AccessibilityWindowInfo info : windows) {
                if (info.getId() != event.getWindowId()) continue;
                Rect bounds = new Rect(); info.getBoundsInScreen(bounds);
                return info.getType() == AccessibilityWindowInfo.TYPE_SYSTEM
                        && !info.isFocused() && !info.isActive() && !bounds.isEmpty()
                        && bounds.top == 0 && bounds.bottom <= displayHeight * 0.12;
            }
            return false;
        } finally { for (AccessibilityWindowInfo info : windows) info.recycle(); }
    }
    private String windowGeometry(List<AccessibilityWindowInfo> windows) {
        List<String> parts = new ArrayList<>();
        for (AccessibilityWindowInfo info : windows) {
            Rect bounds = new Rect(); info.getBoundsInScreen(bounds);
            parts.add(info.getId() + ":" + info.getType() + ":" + info.getLayer()
                    + ":" + info.isFocused() + ":" + info.isActive() + ":" + bounds.toShortString());
        }
        java.util.Collections.sort(parts);
        return parts.toString();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!session.active()) return;
        String pkg = event.getPackageName() == null ? "" : event.getPackageName().toString();
        int type = event.getEventType();
        // Our own IME button/preview changes are not a navigation signal.
        if (getPackageName().equals(pkg) && (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || type == AccessibilityEvent.TYPE_VIEW_FOCUSED)) return;
        // Status-bar clock changes do not change the chat. Window/focus events still
        // invalidate, and every result/insert revalidates the focused root and snapshot.
        if ("com.android.systemui".equals(pkg)
                && type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && safeUnchangedStatusBarEvent(event)) return;
        // Conservative: app navigation, focus or chat content changes end this snapshot.
        // Never read event.getText(), notification payloads, or refresh history here.
        session.invalidate();
    }
    private String capture(long token, boolean compareOnly) {
        if (!session.active() || session.token() != token) return "会话已结束，请重新启动。";
        KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (keyguard == null || keyguard.isKeyguardLocked()) return fail("屏幕已锁定。");
        if (getMagnificationController().getScale() != 1f) return fail("放大显示尚未支持，请关闭放大后重试。");
        List<AccessibilityWindowInfo> windows = getWindows();
        if (windows == null || windows.isEmpty()) return fail("无法确认当前窗口，未读取。");
        AccessibilityNodeInfo root = null;
        try {
            if (windows.size() > 32) return fail("窗口信息复杂，未读取。");
            AccessibilityWindowInfo focused = null;
            int applications = 0;
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    applications++;
                    if (window.isFocused()) {
                        if (focused != null) return fail("多个输入窗口，未读取。");
                        focused = window;
                    }
                }
            }
            if (focused == null || applications != 1 || focused.isInPictureInPictureMode())
                return fail("只支持唯一的普通全屏聊天窗口，分屏或弹窗不读取。");
            // Retrieve ONLY the focused application root, never any other window root.
            // The user's own IME may be active while WhatsApp retains input focus.
            root = focused.getRoot();
            if (root == null || !AssistantSession.WHATSAPP.contentEquals(
                    root.getPackageName() == null ? "" : root.getPackageName()))
                return fail("WhatsApp不是当前输入应用，未读取消息。");
            int currentWindowId = focused.getId(), currentLayer = focused.getLayer();
            Rect viewport = new Rect(); focused.getBoundsInScreen(viewport);
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            if (viewport.width() < width * 0.95 || viewport.top > height * 0.12
                    || viewport.height() < height * 0.45) return fail("分屏或浮窗尚未支持。");
            // Metadata only: never obtain roots/text from these other windows.
            List<Rect> occlusions = new ArrayList<>();
            for (AccessibilityWindowInfo other : windows) {
                if (other.getId() == currentWindowId) continue;
                if (other.isFocused() || (other.isActive()
                        && other.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD))
                    return fail("其他窗口正在交互，未读取消息。");
                if (other.getLayer() >= currentLayer) {
                    Rect covered = new Rect(); other.getBoundsInScreen(covered);
                    if (covered.isEmpty()) return fail("窗口遮挡信息不完整，未读取。");
                    occlusions.add(covered);
                }
            }
            List<NodeText> found = new ArrayList<>();
            List<AssistantNodeFilter.Region> sameWindowMasks = new ArrayList<>();
            boolean[] editorFound = {false};
            List<String> messages = new ArrayList<>();
            try {
                int[] visited = {0};
                collect(root, viewport, occlusions, found, sameWindowMasks, new ArrayList<>(), false, editorFound, visited);
                if (visited[0] >= 250) return fail("页面节点过多，无法完整检查遮挡；未读取消息。");
                if (!editorFound[0]) return fail("未确认WhatsApp消息输入框；搜索、列表或未知页面不读取。");
                found.sort(Comparator.comparingInt((NodeText n) -> n.top).thenComparingInt(n -> n.left));
                for (NodeText node : found) {
                    boolean unobscured = AssistantNodeFilter.unobscured(box(node.bounds), box(node.clip), node.ancestors, sameWindowMasks);
                    // Only after both clipping and known same-window overlap checks, access message text.
                    if (unobscured && node.node.getText() != null) messages.add(node.node.getText().toString());
                }
            } finally { for (NodeText node : found) node.node.recycle(); }
            if (messages.isEmpty()) return fail("当前可见消息未提供可识别文字。图片、语音和未知布局暂不支持。");
            // This is an ephemeral window binding, NOT proof of unique chat identity.
            // It permits a bounded explanation; automatic insertion remains disabled.
            String snapshotBinding = "unverified-window:" + root.getWindowId();
            if (compareOnly && !bounded(messages).equals(session.visible(token))) return fail("当前可见消息已变化，请重新启动助手。");
            if (!session.capture(token, AssistantSession.WHATSAPP, snapshotBinding, true, true, messages))
                return "会话已变化，请重新启动。";
            capturedWindowGeometry = windowGeometry(windows);
            return null;
        } catch (RuntimeException ignored) { return fail("当前页面无法可靠读取，未上传。"); }
        finally {
            if (root != null) root.recycle();
            for (AccessibilityWindowInfo window : windows) window.recycle();
        }
    }
    private List<String> bounded(List<String> values) {
        List<String> out = new ArrayList<>(); int chars = 0;
        for (String value : values) {
            if (chars == AssistantSession.MAX_CHARS || out.size() == AssistantSession.MAX_MESSAGES) break;
            String text = value.substring(0, Math.min(value.length(), AssistantSession.MAX_CHARS - chars));
            if (!text.trim().isEmpty()) { out.add(text); chars += text.length(); }
        }
        return out;
    }
    private String fail(String reason) { session.invalidate(); return reason; }
    private void collect(AccessibilityNodeInfo node, Rect ancestorClip, List<Rect> occlusions,
            List<NodeText> out, List<AssistantNodeFilter.Region> sameWindowMasks, List<Integer> ancestors, boolean insideConversation,
            boolean[] editorFound, int[] visited) {
        int nodeId = ++visited[0];
        if (nodeId > 250 || !node.isVisibleToUser()) return;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        if (bounds.isEmpty() || !Rect.intersects(ancestorClip, bounds)) return;
        boolean fullyInside = ancestorClip.contains(bounds);
        Rect childClip = new Rect(ancestorClip);
        if (!childClip.intersect(bounds)) return;
        String pkg = node.getPackageName() == null ? "" : node.getPackageName().toString();
        if (!AssistantSession.WHATSAPP.equals(pkg)) return;
        if (node.isPassword()) { sameWindowMasks.add(new AssistantNodeFilter.Region(nodeId, box(bounds))); return; }
        String id = node.getViewIdResourceName();
        String clazz = node.getClassName() == null ? "" : node.getClassName().toString();
        boolean inList = insideConversation || "android.widget.ListView".equals(clazz)
                || "android.widget.ScrollView".equals(clazz)
                || "androidx.recyclerview.widget.RecyclerView".equals(clazz)
                || "android.support.v7.widget.RecyclerView".equals(clazz);
        boolean unobscured = true;
        for (Rect covered : occlusions) if (Rect.intersects(bounds, covered)) { unobscured = false; break; }
        if (node.isEditable()) {
            sameWindowMasks.add(new AssistantNodeFilter.Region(nodeId, box(bounds)));
            if (fullyInside && unobscured && "com.whatsapp:id/entry".equals(id) && node.isFocused()) editorFound[0] = true;
            return; // Never read the existing typed draft through Accessibility.
        }
        boolean message = "com.whatsapp:id/message_text".equals(id);
        // Android can report an already-clipped screen rectangle. A smaller screen
        // size than the node's local size must never permit reading its full text.
        // Unequal/transformed/unknown geometry is rejected conservatively.
        Rect localBounds = new Rect();
        if (message) node.getBoundsInParent(localBounds);
        boolean originalSize = !message || AssistantNodeFilter.sameUnclippedSize(box(bounds), box(localBounds));
        // Treat every overlapping non-ancestor UI region as potential same-window occlusion.
        // Transparent/lower-layer siblings may cause conservative refusal rather than a hidden read.
        if (!message) sameWindowMasks.add(new AssistantNodeFilter.Region(nodeId, box(bounds)));
        if (inList && AssistantNodeFilter.allowed(pkg, id, true, false, false, fullyInside && unobscured && originalSize))
            out.add(new NodeText(bounds, ancestorClip, ancestors, AccessibilityNodeInfo.obtain(node)));
        List<Integer> childAncestors = new ArrayList<>(ancestors); childAncestors.add(nodeId);
        for (int i = 0; i < node.getChildCount() && visited[0] < 250; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) try {
                collect(child, childClip, occlusions, out, sameWindowMasks, childAncestors, inList, editorFound, visited);
            } finally { child.recycle(); }
        }
    }
    private static final class NodeText {
        final int top, left; final Rect bounds, clip; final List<Integer> ancestors; final AccessibilityNodeInfo node;
        NodeText(Rect bounds, Rect clip, List<Integer> ancestors, AccessibilityNodeInfo node) {
            this.top = bounds.top; this.left = bounds.left; this.bounds = new Rect(bounds);
            this.clip = new Rect(clip); this.ancestors = new ArrayList<>(ancestors); this.node = node;
        }
    }
    private static AssistantNodeFilter.Box box(Rect bounds) {
        return new AssistantNodeFilter.Box(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }
    @Override public void onInterrupt() { session.invalidate(); }
    @Override public void onDestroy() {
        session.invalidate(); if (connected == this) connected = null;
        if (registered) unregisterReceiver(screenOff);
        super.onDestroy();
    }
}
