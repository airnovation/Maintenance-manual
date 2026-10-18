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
    /** Explicit local preview only; the panel must show its coverage warning first. */
    static String captureForReview(long token) {
        if (!AssistantSession.INSTANCE.beginReview(token)) return "请重新启动本机预览；未读取或上传。";
        VisibleMessageService service=connected;
        if (service == null) {
            AssistantSession.INSTANCE.invalidate();
            return "请先手动启用输入助手的无障碍服务；未读取或上传。";
        }
        service.capturedReviewToken=-1;
        return service.capture(token,false,false,true,false);
    }
    /** Revalidate review metadata without rereading text or changing approved content. */
    static boolean reviewStillCurrent(long token) {
        if (!AssistantSession.INSTANCE.reviewMode(token)) return false;
        if (connected == null) { AssistantSession.INSTANCE.invalidate(); return false; }
        return connected.capture(token,true,false,false,true)==null;
    }
    /** Explicit local diagnostics: no node text access and no provider call. */
    static String diagnose(long token) {
        try {
            if (connected == null) return "无障碍服务未连接；本机诊断未读取文字。";
            return connected.capture(token, false, true);
        } finally { AssistantSession.INSTANCE.invalidate(); }
    }
    static boolean stillCurrent(long token) {
        if (AssistantSession.INSTANCE.reviewMode(token)) return reviewStillCurrent(token);
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
    private long capturedReviewToken=-1;
    private String capturedReviewRootGeometry, capturedReviewEditorGeometry, capturedReviewTreeGeometry;
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
        // Review event callbacks only invalidate; they never inspect roots or text.
        if (session.reviewMode(session.token())) { session.invalidate(); return; }
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
        return capture(token, compareOnly, false);
    }
    private String capture(long token, boolean compareOnly, boolean metadataOnly) {
        return capture(token,compareOnly,metadataOnly,false,false);
    }
    private String capture(long token, boolean compareOnly, boolean metadataOnly, boolean reviewCapture, boolean reviewCheck) {
        if (!session.active() || session.token() != token) return "会话已结束，请重新启动。";
        if (session.reviewMode(token) && !reviewCapture && !reviewCheck && !metadataOnly)
            return fail("本机预览内容必须单独确认，未重新读取或上传。");
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
            String sourcePackage = root == null || root.getPackageName() == null ? "" : root.getPackageName().toString();
            if (!AssistantSession.isWhatsApp(sourcePackage))
                return fail("WhatsApp不是当前输入应用，未读取消息。");
            if (!session.mayReadSource(token, sourcePackage, !compareOnly))
                return fail("输入应用已切换，请重新启动助手；未读取新应用消息。");
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
            String[] editorGeometry = {""};
            List<String> messages = new ArrayList<>();
            AssistantCaptureDiagnostics diagnostics = new AssistantCaptureDiagnostics(BuildConfig.VERSION_NAME);
            int[] visited = {0};
            Rect rootBounds = new Rect(); root.getBoundsInScreen(rootBounds);
            String snapshotBinding = "unverified-window:" + root.getWindowId();
            try {
                collect(root, sourcePackage, viewport, occlusions, found, sameWindowMasks, new ArrayList<>(), new ArrayList<>(), false, editorFound, editorGeometry, visited, diagnostics);
                if (visited[0] >= 250) return fail("页面节点过多，无法完整检查遮挡；未读取消息。");
                if (!editorFound[0]) return fail("未识别当前WhatsApp/Business消息输入框。请点聊天底部输入框；搜索或未知版本布局不读取。");
                if (reviewCheck) {
                    if (capturedReviewToken!=token || !session.matchesWindow(token,sourcePackage,snapshotBinding)
                            || !windowGeometry(windows).equals(capturedWindowGeometry)
                            || !rootBounds.toShortString().equals(capturedReviewRootGeometry)
                            || !editorGeometry[0].equals(capturedReviewEditorGeometry)
                            || !reviewGeometry(found,sameWindowMasks).equals(capturedReviewTreeGeometry))
                        return fail("当前窗口或输入框已变化，请重新打开本机预览。");
                    return null; // Metadata only: no getText and no session.capture/approval mutation.
                }
                String localReviewGeometry=reviewCapture ? reviewGeometry(found,sameWindowMasks) : null;
                if (reviewCapture) capturedReviewTreeGeometry=localReviewGeometry;
                diagnostics.candidates = found.size();
                found.sort(Comparator.comparingInt((NodeText n) -> n.top).thenComparingInt(n -> n.left));
                int previewChars=0;
                for (NodeText node : found) {
                    if (reviewCapture && (messages.size()==AssistantSession.MAX_MESSAGES
                            || previewChars==AssistantSession.MAX_CHARS)) break;
                    AssistantNodeFilter.Assessment assessment = AssistantNodeFilter.assess(
                            box(node.bounds), box(node.clip), node.ancestors, node.path, sameWindowMasks);
                    diagnostics.assessment(assessment);
                    boolean unobscured = assessment.allowed;
                    // collect already enforces clipping, original size and external windows.
                    // Strict capture also requires same-window order; explicit local review
                    // permits covered candidates only for the disclosed on-device preview.
                    if (!unobscured) {
                        diagnostics.sameWindowOverlap++;
                        diagnostics.overlaps(box(node.bounds), node.parentId, node.drawingOrder, node.ancestors, sameWindowMasks);
                        if (!reviewCapture) continue;
                    }
                    diagnostics.eligible++;
                    if (metadataOnly) continue; // Never access text in local diagnostics.
                    CharSequence text = node.node.getText();
                    if (text == null) diagnostics.missingText++;
                    else if (reviewCapture) {
                        String value=text.subSequence(0,Math.min(text.length(),AssistantSession.MAX_CHARS-previewChars)).toString();
                        if (!value.trim().isEmpty()) { messages.add(value); previewChars+=value.length(); }
                    } else messages.add(text.toString());
                }
            } finally { for (NodeText node : found) node.node.recycle(); }
            if (metadataOnly) return fail(diagnostics.metadataResult(visited[0]));
            if (messages.isEmpty()) return fail(diagnostics.emptyResult(visited[0]));
            // This is an ephemeral window binding, NOT proof of unique chat identity.
            // It permits a bounded explanation; automatic insertion remains disabled.
            if (reviewCapture) {
                if (!session.captureReview(token,sourcePackage,snapshotBinding,true,true,messages))
                    return fail("本机预览会话已变化，请重新启动。");
                capturedReviewToken=token;
                capturedWindowGeometry=windowGeometry(windows);
                capturedReviewRootGeometry=rootBounds.toShortString();
                capturedReviewEditorGeometry=editorGeometry[0];
                return null;
            }
            if (compareOnly && !bounded(messages).equals(session.visible(token))) return fail("当前可见消息已变化，请重新启动助手。");
            if (!session.capture(token, sourcePackage, snapshotBinding, true, true, messages))
                return "会话已变化，请重新启动。";
            capturedWindowGeometry = windowGeometry(windows);
            return null;
        } catch (RuntimeException ignored) { return fail("当前页面无法可靠读取，未上传。"); }
        finally {
            if (root != null) root.recycle();
            for (AccessibilityWindowInfo window : windows) window.recycle();
        }
    }
    private String reviewGeometry(List<NodeText> nodes, List<AssistantNodeFilter.Region> masks) {
        StringBuilder out=new StringBuilder();
        for (NodeText node:nodes) {
            out.append("N:").append(node.bounds.toShortString()).append(':').append(node.clip.toShortString());
            appendPath(out,node.path);
        }
        for (AssistantNodeFilter.Region mask:masks) {
            out.append("M:").append(mask.box.left).append(',').append(mask.box.top).append(',')
                    .append(mask.box.right).append(',').append(mask.box.bottom).append(':').append(mask.kind);
            appendPath(out,mask.path);
        }
        return out.toString();
    }
    private void appendPath(StringBuilder out,List<AssistantNodeFilter.Layer> path) {
        for (AssistantNodeFilter.Layer layer:path)
            out.append('/').append(layer.nodeId).append(':').append(layer.drawingOrder).append(':')
                    .append(layer.reportedKind).append(':').append(layer.supported).append(':').append(layer.orderError);
        out.append(';');
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
    private void collect(AccessibilityNodeInfo node, String sourcePackage, Rect ancestorClip, List<Rect> occlusions,
            List<NodeText> out, List<AssistantNodeFilter.Region> sameWindowMasks, List<Integer> ancestors, List<AssistantNodeFilter.Layer> parentPath, boolean insideConversation,
            boolean[] editorFound, String[] editorGeometry, int[] visited, AssistantCaptureDiagnostics diagnostics) {
        int nodeId = ++visited[0];
        List<AssistantNodeFilter.Layer> path = new ArrayList<>(parentPath);
        path.add(drawingLayer(nodeId, node));
        if (nodeId > 250 || !node.isVisibleToUser()) return;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        if (bounds.isEmpty() || !Rect.intersects(ancestorClip, bounds)) return;
        boolean fullyInside = ancestorClip.contains(bounds);
        Rect childClip = new Rect(ancestorClip);
        if (!childClip.intersect(bounds)) return;
        String pkg = node.getPackageName() == null ? "" : node.getPackageName().toString();
        if (!sourcePackage.equals(pkg) || !AssistantSession.isWhatsApp(pkg)) return;
        if (node.isPassword()) { sameWindowMasks.add(mask(nodeId, node, bounds, ancestors, path)); return; }
        String id = node.getViewIdResourceName();
        String clazz = node.getClassName() == null ? "" : node.getClassName().toString();
        boolean knownList = "android.widget.ListView".equals(clazz)
                || "android.widget.ScrollView".equals(clazz)
                || "androidx.recyclerview.widget.RecyclerView".equals(clazz)
                || "android.support.v7.widget.RecyclerView".equals(clazz);
        diagnostics.structure(id != null && !id.isEmpty(), knownList);
        boolean inList = insideConversation || knownList;
        boolean unobscured = true;
        for (Rect covered : occlusions) if (Rect.intersects(bounds, covered)) { unobscured = false; break; }
        if (node.isEditable()) {
            sameWindowMasks.add(mask(nodeId, node, bounds, ancestors, path));
            if (fullyInside && unobscured && AssistantSession.isResource(pkg, id, "entry") && node.isFocused()) {
                editorFound[0] = true;
                editorGeometry[0] += node.getWindowId()+":"+bounds.toShortString()+";";
            }
            return; // Never read the existing typed draft through Accessibility.
        }
        boolean message = AssistantSession.isResource(pkg, id, "message_text");
        // Android can report an already-clipped screen rectangle. A smaller screen
        // size than the node's local size must never permit reading its full text.
        // Unequal/transformed/unknown geometry is rejected conservatively.
        Rect localBounds = new Rect();
        if (message) node.getBoundsInParent(localBounds);
        boolean originalSize = !message || AssistantNodeFilter.sameUnclippedSize(box(bounds), box(localBounds));
        if (message) diagnostics.candidate(inList, fullyInside, unobscured, originalSize);
        // Keep every potential mask. The final pass can discount only a leaf reported as View
        // whose divergent ancestor branch is explicitly ordered below the message.
        if (!message) sameWindowMasks.add(mask(nodeId, node, bounds, ancestors, path));
        if (inList && AssistantNodeFilter.allowed(pkg, id, true, false, false, fullyInside && unobscured && originalSize))
            out.add(new NodeText(bounds, ancestorClip, ancestors, AccessibilityNodeInfo.obtain(node), safeDrawingOrder(node), path));
        List<Integer> childAncestors = new ArrayList<>(ancestors); childAncestors.add(nodeId);
        for (int i = 0; i < node.getChildCount() && visited[0] < 250; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) throw new IllegalStateException("Incomplete accessibility hierarchy");
            try {
                collect(child, sourcePackage, childClip, occlusions, out, sameWindowMasks, childAncestors, path, inList, editorFound, editorGeometry, visited, diagnostics);
            } finally { child.recycle(); }
        }
    }
    // A reported-class allowlist for the normal WhatsApp View hierarchy, not runtime
    // type/pixel proof: custom delegates and independently composed layers can differ.
    private static boolean supportedDrawingClass(AccessibilityNodeInfo node) {
        String clazz = node.getClassName() == null ? "" : node.getClassName().toString();
        return "android.view.View".equals(clazz) || "android.view.ViewGroup".equals(clazz)
                || "android.widget.FrameLayout".equals(clazz) || "android.widget.LinearLayout".equals(clazz)
                || "android.widget.RelativeLayout".equals(clazz) || "android.widget.TextView".equals(clazz)
                || "android.widget.ListView".equals(clazz) || "android.widget.ScrollView".equals(clazz)
                || "androidx.recyclerview.widget.RecyclerView".equals(clazz)
                || "android.support.v7.widget.RecyclerView".equals(clazz);
    }
    private static AssistantNodeFilter.Layer drawingLayer(int id, AccessibilityNodeInfo node) {
        int order=0; boolean error=false;
        try { order=node.getDrawingOrder(); } catch (RuntimeException ignored) { error=true; }
        String name=node.getClassName()==null ? "" : node.getClassName().toString();
        AssistantNodeFilter.Kind kind;
        if (name.isEmpty()) kind=AssistantNodeFilter.Kind.MISSING;
        else if ("android.view.View".equals(name)) kind=AssistantNodeFilter.Kind.VIEW;
        else if ("android.widget.TextView".equals(name)) kind=AssistantNodeFilter.Kind.TEXT;
        else if ("android.widget.ImageView".equals(name)) kind=AssistantNodeFilter.Kind.IMAGE;
        else if ("android.view.SurfaceView".equals(name) || "android.view.TextureView".equals(name)) kind=AssistantNodeFilter.Kind.SURFACE;
        else if ("android.widget.ListView".equals(name) || "android.widget.ScrollView".equals(name)
                || "androidx.recyclerview.widget.RecyclerView".equals(name)
                || "android.support.v7.widget.RecyclerView".equals(name)) kind=AssistantNodeFilter.Kind.LIST;
        else if (supportedDrawingClass(node)) kind=AssistantNodeFilter.Kind.LAYOUT;
        else kind=AssistantNodeFilter.Kind.OTHER;
        return new AssistantNodeFilter.Layer(id,order,supportedDrawingClass(node),kind,error);
    }
    private static int safeDrawingOrder(AccessibilityNodeInfo node) {
        try { return node.getDrawingOrder(); } catch (RuntimeException ignored) { return 0; }
    }
    private static AssistantNodeFilter.Region mask(int id, AccessibilityNodeInfo node, Rect bounds, List<Integer> ancestors, List<AssistantNodeFilter.Layer> path) {
        int kind = 0;
        String clazz = node.getClassName() == null ? "" : node.getClassName().toString();
        if (node.isPassword() || node.isEditable()) kind = 6;
        else if ("android.view.SurfaceView".equals(clazz) || "android.view.TextureView".equals(clazz)) kind = 5;
        else if (node.getChildCount() > 0) kind = 4;
        else if ("android.widget.TextView".equals(clazz)) kind = 1;
        else if ("android.widget.ImageView".equals(clazz)) kind = 2;
        else if ("android.view.View".equals(clazz)) kind = 3;
        int parent = ancestors.isEmpty() ? -1 : ancestors.get(ancestors.size() - 1);
        return new AssistantNodeFilter.Region(id, box(bounds), parent, safeDrawingOrder(node), kind, path);
    }
    private static final class NodeText {
        final List<AssistantNodeFilter.Layer> path;
        final int top, left, parentId, drawingOrder; final Rect bounds, clip; final List<Integer> ancestors; final AccessibilityNodeInfo node;
        NodeText(Rect bounds, Rect clip, List<Integer> ancestors, AccessibilityNodeInfo node, int drawingOrder, List<AssistantNodeFilter.Layer> path) {
            this.top = bounds.top; this.left = bounds.left; this.bounds = new Rect(bounds);
            this.clip = new Rect(clip); this.ancestors = new ArrayList<>(ancestors); this.node = node;
            this.parentId = ancestors.isEmpty() ? -1 : ancestors.get(ancestors.size() - 1);
            this.drawingOrder = drawingOrder; this.path = new ArrayList<>(path);
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
