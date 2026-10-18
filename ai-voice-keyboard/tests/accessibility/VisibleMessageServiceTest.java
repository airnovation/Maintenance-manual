package com.airnovation.aivoicekeyboard;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Executes the real Android service capture/diagnose control flow on synthetic metadata. */
public final class VisibleMessageServiceTest {
    private static int checks, scenarios;
    private static final String BUSINESS = AssistantSession.WHATSAPP_BUSINESS;
    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static int counter(String output, String name) {
        Matcher m=Pattern.compile("(?:^|\\s)"+name+"=(\\d+)(?:\\s|$)").matcher(output);
        check(m.find(), "Missing counter " + name + " in: " + output);
        return Integer.parseInt(m.group(1));
    }
    private static void equalCounter(String output, String name, int expected) {
        check(counter(output,name)==expected, name+" must equal "+expected+": "+output);
    }
    private static AccessibilityNodeInfo node(String id, String clazz, int l, int t, int r, int b) {
        AccessibilityNodeInfo node=new AccessibilityNodeInfo();
        node.resourceId=id==null ? null : BUSINESS+":id/"+id;
        node.className=clazz;
        node.bounds=new Rect(l,t,r,b);
        node.localBounds=new Rect(0,0,r-l,b-t);
        return node;
    }
    private static final class Fixture implements AutoCloseable {
        final VisibleMessageService service=new VisibleMessageService();
        final AccessibilityNodeInfo root=node(null,"android.widget.FrameLayout",0,0,1080,1920);
        final AccessibilityNodeInfo list=node("conversation","androidx.recyclerview.widget.RecyclerView",0,100,1080,1500);
        final AccessibilityNodeInfo message=node("message_text","android.widget.TextView",20,200,600,300);
        final AccessibilityNodeInfo entry=node("entry","android.widget.EditText",0,1600,1080,1750);
        final AccessibilityWindowInfo window=new AccessibilityWindowInfo();
        Fixture() {
            root.children.add(list); list.children.add(message); root.children.add(entry);
            entry.editable=true; entry.focused=true;
            window.root=root;
            service.fixtureWindows.add(window);
            service.onServiceConnected();
            check(VisibleMessageService.available(), "Real service must connect");
        }
        String diagnose() {
            long token=AssistantSession.INSTANCE.start();
            check(AssistantSession.INSTANCE.active(), "Session starts before diagnosis");
            String result=VisibleMessageService.diagnose(token);
            check(result.contains("本机诊断完成"), "Must complete metadata path, not short-circuit: "+result);
            check(!AssistantSession.INSTANCE.active(), "Diagnosis ends session");
            check(AssistantSession.INSTANCE.visible(token).isEmpty(), "Diagnosis retains no captured text");
            check(AssistantSession.INSTANCE.explanation().isEmpty() && AssistantSession.INSTANCE.draft().isEmpty(), "Diagnosis retains no response");
            assertNoTextReads(root);
            return result;
        }
        @Override public void close() {
            service.onDestroy();
            check(!VisibleMessageService.available(), "Service must disconnect between fixtures");
        }
    }
    private static void assertNoTextReads(AccessibilityNodeInfo node) {
        check(node.textCalls==0, "Diagnostic/rejected node text read: "+node.resourceId);
        for (AccessibilityNodeInfo child:node.children) assertNoTextReads(child);
    }
    private static void eligibleMetadata() {
        try (Fixture f=new Fixture()) {
            String out=f.diagnose();
            equalCounter(out,"N",4); equalCounter(out,"M",1); equalCounter(out,"K",1);
            equalCounter(out,"F",1); equalCounter(out,"E",1);
            equalCounter(out,"L",0); equalCounter(out,"C",0); equalCounter(out,"W",0);
            equalCounter(out,"S",0); equalCounter(out,"O",0);
            check(!out.contains("SYNTHETIC MESSAGE"), "No text in diagnostics");
            check(!out.contains(BUSINESS), "No package in diagnostics");
            scenarios++;
            System.out.println("PASS Business eligible message: M=1 F=1 E=1; all getText poisoned; session ended");
        }
    }
    private static void unsupportedList() {
        try (Fixture f=new Fixture()) {
            f.list.className="example.UnknownContainer";
            String out=f.diagnose();
            equalCounter(out,"M",1); equalCounter(out,"L",1); equalCounter(out,"K",0);
            equalCounter(out,"F",0); equalCounter(out,"E",0); scenarios++;
        }
    }
    private static void clippedMessage() {
        try (Fixture f=new Fixture()) {
            f.message.bounds=new Rect(20,80,600,180); // Crosses conversation ancestor's top edge.
            String out=f.diagnose();
            equalCounter(out,"M",1); equalCounter(out,"C",1); equalCounter(out,"S",0);
            equalCounter(out,"F",0); equalCounter(out,"E",0); scenarios++;
        }
    }
    private static void externalOverlay() {
        try (Fixture f=new Fixture()) {
            AccessibilityWindowInfo overlay=new AccessibilityWindowInfo();
            overlay.id=8; overlay.type=AccessibilityWindowInfo.TYPE_SYSTEM; overlay.layer=2;
            overlay.focused=false; overlay.active=false; overlay.bounds=new Rect(100,210,300,250);
            f.service.fixtureWindows.add(overlay);
            String out=f.diagnose();
            equalCounter(out,"W",1); equalCounter(out,"F",0); equalCounter(out,"E",0);
            check(overlay.rootCalls==0, "External overlay root must not be retrieved"); scenarios++;
        }
    }
    private static void sameWindowOverlay() {
        try (Fixture f=new Fixture()) {
            f.root.children.add(node("overlay","android.view.View",100,210,300,250));
            String out=f.diagnose();
            equalCounter(out,"M",1); equalCounter(out,"F",1); equalCounter(out,"O",1);
            equalCounter(out,"E",0); scenarios++;
        }
    }
    private static void screenClippedSize() {
        try (Fixture f=new Fixture()) {
            f.message.localBounds=new Rect(0,0,580,140); // Reported screen height is only 100.
            String out=f.diagnose();
            equalCounter(out,"S",1); equalCounter(out,"F",0); equalCounter(out,"E",0); scenarios++;
        }
    }
    private static void normalCaptureControl() {
        try (Fixture f=new Fixture()) {
            f.message.allowText=true;
            AccessibilityNodeInfo clipped=node("message_text","android.widget.TextView",650,80,900,180);
            AccessibilityNodeInfo unrelated=node("sender_name","android.widget.TextView",650,350,900,400);
            f.list.children.add(clipped); f.list.children.add(unrelated);
            long token=AssistantSession.INSTANCE.start();
            String result=VisibleMessageService.captureOnce(token);
            check(result==null, "Normal capture must succeed: "+result);
            check(f.message.textCalls==1, "Positive control must actually read eligible message exactly once");
            check(AssistantSession.INSTANCE.visible(token).equals(Arrays.asList("SYNTHETIC MESSAGE")), "Positive control stores synthetic text");
            check(AssistantSession.INSTANCE.active(), "Normal capture keeps session active");
            check(f.root.textCalls==0 && f.list.textCalls==0 && f.entry.textCalls==0, "No root/container/editor text reads");
            assertNoTextReads(clipped); assertNoTextReads(unrelated);
            scenarios++;
            System.out.println("PASS normal capture control: eligible text read once; rejected/unrelated/editor getters untouched");
        }
    }
    private static void invalidTokenEndsSession() {
        try (Fixture f=new Fixture()) {
            long token=AssistantSession.INSTANCE.start();
            String out=VisibleMessageService.diagnose(token-1);
            check(out.contains("会话已结束"), "Invalid token rejected");
            check(!AssistantSession.INSTANCE.active(), "Diagnosis finally invalidates even an invalid token");
            assertNoTextReads(f.root); scenarios++;
        }
    }
    public static void main(String[] args) {
        eligibleMetadata(); unsupportedList(); clippedMessage(); externalOverlay();
        sameWindowOverlay(); screenClippedSize(); normalCaptureControl(); invalidTokenEndsSession();
        System.out.println("PASS "+checks+" checks across "+scenarios+" actual VisibleMessageService scenarios (host fakes; no Android/device verification)");
    }
}
