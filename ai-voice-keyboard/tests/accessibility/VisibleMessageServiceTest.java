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
    private static String diagnosticLine(String output, String prefix) {
        for (String line:output.split("\\n")) if (line.startsWith(prefix)) return line.trim();
        throw new AssertionError("Missing diagnostic line "+prefix+": "+output);
    }
    private static void assertLcaReport(String output, String counts, String... details) {
        String line=diagnosticLine(output,"版本：");
        check(line.startsWith("版本：15-host-fixture | LCA："), "Host version must be displayed");
        String[] parts=line.split(" \\| ");
        check(parts[1].trim().equals("LCA："+counts), "Unexpected LCA reason totals: "+output);
        check(parts.length==details.length+2, "Unexpected number of fixed LCA detail fields");
        for (int i=0;i<details.length;i++) check(parts[i+2].equals(details[i]), "Missing exact fixed detail: "+details[i]);
        check(!output.contains("SYNTHETIC_ORDER_ERROR") && !output.contains("example.Custom"), "No raw errors or custom class names");
        check(!output.contains("com.whatsapp.w4b:id/") && !output.contains("SYNTHETIC MESSAGE"), "No identifiers or message content in diagnostic report");
    }
    private static String detail(String reason, String branches, String unsupported, String orders, String shapes) {
        return reason+" B="+branches+" U="+unsupported+" Q="+orders+" P="+shapes+" N=1";
    }
    private static void assertSameLcaReport(String diagnostic, String emptyCapture) {
        check(diagnosticLine(diagnostic,"版本：").equals(diagnosticLine(emptyCapture,"版本：")),
                "Capture and diagnosis must use identical single-line classifier report");
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
            check(result.contains("版本：15-host-fixture | LCA："), "Actual service must show its host build version");
            check(!result.contains("SYNTHETIC_ORDER_ERROR"), "No exception content in diagnostics");
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
        if (node==null) return; // A null advertised child is asserted separately as a refusal.
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
            assertLcaReport(out,"NO_OVERLAP");
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
    private static String legacyCounterSignature(String out, boolean includeMissingText) {
        String[] names={"N","M","L","I","U","K","C","W","S","F","O"};
        int[] expected={25,2,0,22,3,1,0,0,1,1,1};
        StringBuilder signature=new StringBuilder();
        for (int i=0; i<names.length; i++) {
            int actual=counter(out,names[i]);
            check(actual==expected[i], "Synthetic legacy counter "+names[i]+" must equal "+expected[i]+": "+out);
            signature.append(names[i]).append('=').append(actual).append(' ');
        }
        if (includeMissingText) {
            equalCounter(out,"T",0);
            signature.append("T=0");
        }
        return signature.toString();
    }
    private static void assertOverlapMetadata(String output, boolean backgroundLike) {
        String summary=null;
        for (String line:output.split("\\n"))
            if (line.startsWith("遮挡关系：")) summary=line.substring("遮挡关系：".length()).trim();
        check(summary!=null, "Actual service must include overlap metadata");
        String expected=backgroundLike ? "SP=1 UN=1 VIEW=1 BIG=1" : "SP=1 HI=1 TEXT=1 SM=1";
        check(expected.equals(summary), "Unexpected overlap metadata: "+summary);
        equalCounter(summary,"SP",1);
        equalCounter(summary,backgroundLike ? "UN" : "HI",1);
        equalCounter(summary,backgroundLike ? "VIEW" : "TEXT",1);
        equalCounter(summary,backgroundLike ? "BIG" : "SM",1);
        check(!output.contains("synthetic_background") && !output.contains("synthetic_corner_label"), "Do not expose raw resource IDs");
        check(!output.contains("android.view.View") && !output.contains("android.widget.TextView"), "Do not expose raw classes");
        if (backgroundLike) assertLcaReport(output,"MASK_ORDER_ZERO=1",
                detail("MASK_ORDER_ZERO","VIEW/TEXT","NONE/NONE","ZERO/POSITIVE","ONE/ONE"));
        else assertLcaReport(output,"MASK_KIND=1",
                detail("MASK_KIND","NONE/NONE","NONE/NONE","NA/NA","NA/NA"));
    }
    private static String ambiguousOverlapFixture(boolean backgroundLike) {
        try (Fixture f=new Fixture()) {
            f.message.drawingOrder=2;
            AccessibilityNodeInfo mismatch=node("message_text","android.widget.TextView",20,400,600,500);
            mismatch.localBounds=new Rect(0,0,580,140);
            mismatch.drawingOrder=4;
            f.list.children.add(mismatch);
            AccessibilityNodeInfo mask=backgroundLike
                    ? node("synthetic_background","android.view.View",0,100,1080,1500)
                    : node("synthetic_corner_label","android.widget.TextView",570,280,620,320);
            // These explicit host fixtures differ in geometry, class, and sibling order.
            // Neither the class nor drawing order establishes actual visual opacity.
            // Preserve the historical signature with unknown background order.
            // Positive lower-order backgrounds have a separate eligible regression.
            mask.drawingOrder=backgroundLike ? 0 : 3;
            f.list.children.add(mask);
            for (int i=0; i<19; i++) {
                AccessibilityNodeInfo filler=node(i<17 ? "synthetic_structure_"+i : null,
                        "android.view.View",700,600+i*20,780,610+i*20);
                filler.drawingOrder=5+i;
                f.list.children.add(filler);
            }
            String diagnostic=f.diagnose();
            legacyCounterSignature(diagnostic,false);
            assertOverlapMetadata(diagnostic,backgroundLike);
            equalCounter(diagnostic,"E",0);
            // Also reproduce the exact old failure-counter shape, including T=0.
            // All nodes remain poisoned: neither the size-mismatch message nor the
            // overlapped candidate is allowed to reach getText in ordinary capture.
            long token=AssistantSession.INSTANCE.start();
            String empty=VisibleMessageService.captureOnce(token);
            check(empty!=null && empty.contains("未采集到"), "Synthetic rejected capture must produce empty counters");
            String signature=legacyCounterSignature(empty,true);
            assertOverlapMetadata(empty,backgroundLike);
            check(!AssistantSession.INSTANCE.active(), "Rejected capture ends session");
            check(AssistantSession.INSTANCE.visible(token).isEmpty(), "Rejected capture stores no text");
            assertNoTextReads(f.root);
            scenarios++;
            System.out.println("PASS synthetic " + (backgroundLike ? "large background-like View" : "small corner TextView")
                    + ": " + signature + "; all getText poisoned and untouched");
            return signature;
        }
    }
    private static void sameCountersDifferentMasks() {
        String background=ambiguousOverlapFixture(true);
        String corner=ambiguousOverlapFixture(false);
        check(background.equals(corner), "Different mask metadata must reproduce identical legacy counters");
        System.out.println("PASS identical legacy counters do not uniquely identify the overlapping UI region");
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
    private static void unavailableDrawingOrder() {
        try (Fixture f=new Fixture()) {
            f.message.throwDrawingOrder=true;
            AccessibilityNodeInfo mask=node("synthetic_mask", "android.view.View",20,200,600,300);
            mask.drawingOrder=1; f.list.children.add(mask);
            String out=f.diagnose();
            equalCounter(out,"O",1); equalCounter(out,"E",0); equalCounter(out,"UN",1);
            check(!out.contains("SYNTHETIC_ORDER_ERROR"), "Exception content must not enter diagnostics");
            assertLcaReport(out,"MESSAGE_ORDER_ERROR=1",
                    detail("MESSAGE_ORDER_ERROR","VIEW/TEXT","NONE/NONE","POSITIVE/ERROR","ONE/ONE"));
            scenarios++;
        }
    }
    private static void assertOnlyMessageRead(AccessibilityNodeInfo node, AccessibilityNodeInfo message) {
        check(node.textCalls==(node==message ? 1 : 0), "Only the allowed message may be read: "+node.resourceId);
        for (AccessibilityNodeInfo child:node.children) assertOnlyMessageRead(child,message);
    }
    private static String assertNormalOutcome(Fixture f, boolean eligible, String label) {
        if (eligible) f.message.allowText=true;
        long token=AssistantSession.INSTANCE.start();
        String result=VisibleMessageService.captureOnce(token);
        if (eligible) {
            check(result==null, label+" should capture: "+result);
            check(AssistantSession.INSTANCE.active(), label+" capture should keep session");
            check(AssistantSession.INSTANCE.visible(token).equals(Arrays.asList("SYNTHETIC MESSAGE")), label+" stores only the fixture message");
            assertOnlyMessageRead(f.root,f.message);
        } else {
            check(result!=null && result.contains("未采集到"), label+" must be refused: "+result);
            check(!AssistantSession.INSTANCE.active(), label+" rejected capture ends session");
            check(AssistantSession.INSTANCE.visible(token).isEmpty(), label+" leaves no captured text");
            assertNoTextReads(f.root);
        }
        check(result==null || !result.contains("SYNTHETIC_ORDER_ERROR"), "Exception content must not be exposed");
        return result;
    }
    private static AccessibilityNodeInfo[] hierarchy(Fixture f, String maskClass, int maskOrder,
            int branchOrder, int messageOrder, boolean nonleaf) {
        AccessibilityNodeInfo wrapper=node("synthetic_message_branch","android.widget.FrameLayout",0,100,1080,1500);
        wrapper.drawingOrder=branchOrder;
        f.message.drawingOrder=messageOrder;
        f.list.children.clear(); wrapper.children.add(f.message); f.list.children.add(wrapper);
        AccessibilityNodeInfo mask=node("synthetic_hierarchy_mask",maskClass,100,210,300,250);
        mask.drawingOrder=maskOrder; f.list.children.add(mask);
        if (nonleaf) {
            AccessibilityNodeInfo child=node("synthetic_hidden_child","android.view.View",100,210,300,250);
            child.visible=false; mask.children.add(child);
        }
        return new AccessibilityNodeInfo[]{wrapper,mask};
    }
    private static void hierarchyCase(String label, String maskClass, int maskOrder, int branchOrder,
            int messageOrder, boolean nonleaf, int throwsAt, boolean eligible, String expectedKind,
            String expectedReason, String expectedOrders) {
        try (Fixture f=new Fixture()) {
            AccessibilityNodeInfo[] nodes=hierarchy(f,maskClass,maskOrder,branchOrder,messageOrder,nonleaf);
            if (throwsAt==1) nodes[0].throwDrawingOrder=true;
            if (throwsAt==2) nodes[1].throwDrawingOrder=true;
            if (throwsAt==3) f.message.throwDrawingOrder=true;
            String out=f.diagnose();
            equalCounter(out,"M",1); equalCounter(out,"F",1);
            equalCounter(out,"O",eligible ? 0 : 1); equalCounter(out,"E",eligible ? 1 : 0);
            check(!out.contains("SYNTHETIC_ORDER_ERROR"), "Throwing order lookup must not leak its exception");
            if (!eligible) {
                // Existing relation summary intentionally calls different-parent order unknown.
                // It must not pretend that immediate leaf orders are LCA branch orders.
                check(out.contains("遮挡关系：DP=1 UN=1 "+expectedKind+"=1 MID=1"), label+" diagnostic categories: "+out);
            }
            String expectedDetail="MASK_KIND".equals(expectedReason)
                    ? detail(expectedReason,"NONE/NONE","NONE/NONE","NA/NA","NA/NA")
                    : detail(expectedReason,"VIEW/LAYOUT","NONE/NONE",expectedOrders,"ONE/FEW");
            assertLcaReport(out,expectedReason+"=1",expectedDetail);
            String capture=assertNormalOutcome(f,eligible,label);
            if (capture!=null) {
                assertLcaReport(capture,expectedReason+"=1",expectedDetail);
                assertSameLcaReport(out,capture);
            }
            scenarios++;
            System.out.println("PASS hierarchy "+label+": "+(eligible ? "diagnostic E=1 with no text; capture reads only message" : "O=1 E=0; no diagnostic/capture text read"));
        }
    }
    private static void genericReportedViewTrustBoundary() {
        try (Fixture f=new Fixture()) {
            f.message.drawingOrder=2;
            AccessibilityNodeInfo mask=node("synthetic_background","android.view.View",0,100,1080,1500);
            mask.drawingOrder=1; f.list.children.add(mask);
            String out=f.diagnose();
            equalCounter(out,"F",1); equalCounter(out,"O",0); equalCounter(out,"E",1);
            assertLcaReport(out,"ALLOW=1",detail("ALLOW","VIEW/TEXT","NONE/NONE","POSITIVE/POSITIVE","ONE/ONE"));
            assertNormalOutcome(f,true,"plain reported lower sibling View");
            // This fixture exposes ONLY a generic View report. The service has no
            // independent runtime-type or compositor-opacity observation. Admission
            // therefore cannot establish that a real hidden SurfaceView is safe.
            System.out.println("PASS trust-boundary demonstration: lower reported generic View is admitted; actual type/opacity is not proved");
            scenarios++;
        }
    }
    private static void lowerMaskCannotBypassClipping() {
        try (Fixture f=new Fixture()) {
            hierarchy(f,"android.view.View",1,2,10,false);
            f.message.bounds=new Rect(20,80,600,180);
            String out=f.diagnose();
            equalCounter(out,"C",1); equalCounter(out,"F",0); equalCounter(out,"E",0);
            assertNormalOutcome(f,false,"lower branch cannot bypass clipping"); scenarios++;
        }
    }
    private static void lowerMaskCannotBypassExternalWindow() {
        try (Fixture f=new Fixture()) {
            hierarchy(f,"android.view.View",1,2,10,false);
            AccessibilityWindowInfo overlay=new AccessibilityWindowInfo();
            overlay.id=8; overlay.type=AccessibilityWindowInfo.TYPE_SYSTEM; overlay.layer=2;
            overlay.focused=false; overlay.active=false; overlay.bounds=new Rect(100,210,300,250);
            f.service.fixtureWindows.add(overlay);
            String out=f.diagnose();
            equalCounter(out,"W",1); equalCounter(out,"F",0); equalCounter(out,"E",0);
            assertNormalOutcome(f,false,"lower branch cannot bypass external occlusion");
            check(overlay.rootCalls==0,"External root must never be read"); scenarios++;
        }
    }
    private static void separateMaskContainerRemainsBlocked() {
        try (Fixture f=new Fixture()) {
            AccessibilityNodeInfo[] nodes=hierarchy(f,"android.view.View",1,3,99,false);
            f.list.children.remove(nodes[1]);
            AccessibilityNodeInfo maskBranch=node("synthetic_mask_branch","android.widget.FrameLayout",100,210,300,250);
            maskBranch.drawingOrder=1; maskBranch.children.add(nodes[1]); f.list.children.add(maskBranch);
            String out=f.diagnose();
            equalCounter(out,"O",1); equalCounter(out,"E",0); equalCounter(out,"BOX",1);
            assertLcaReport(out,"ALLOW=1 MASK_KIND=1",
                    detail("MASK_KIND","NONE/NONE","NONE/NONE","NA/NA","NA/NA"),
                    detail("ALLOW","LAYOUT/LAYOUT","NONE/NONE","POSITIVE/POSITIVE","FEW/FEW"));
            String capture=assertNormalOutcome(f,false,"overlapping nonleaf mask container");
            assertSameLcaReport(out,capture); scenarios++;
        }
    }
    private static void unsupportedMessagePath(String label, String reportedClass, boolean messageLeaf, String kind) {
        try (Fixture f=new Fixture()) {
            AccessibilityNodeInfo[] nodes=hierarchy(f,"android.view.View",1,2,99,false);
            if (messageLeaf) f.message.className=reportedClass;
            else nodes[0].className=reportedClass;
            String out=f.diagnose();
            equalCounter(out,"F",1); equalCounter(out,"O",1); equalCounter(out,"E",0);
            String branches="VIEW/"+(messageLeaf ? "LAYOUT" : kind);
            String expected=detail("MESSAGE_CLASS",branches,"NONE/"+kind,"POSITIVE/POSITIVE","ONE/FEW");
            assertLcaReport(out,"MESSAGE_CLASS=1",expected);
            String capture=assertNormalOutcome(f,false,label);
            assertLcaReport(capture,"MESSAGE_CLASS=1",expected); assertSameLcaReport(out,capture);
            scenarios++;
            System.out.println("PASS unsupported divergent message path "+label+": O=1 E=0; no text read");
        }
    }
    private static void missingChildRefusesIncompleteHierarchy() {
        try (Fixture f=new Fixture()) {
            // A candidate is discovered before the advertised null child. The
            // service must finish validating the tree before invoking its getter.
            f.list.children.add(null);
            long token=AssistantSession.INSTANCE.start();
            String diagnostic=VisibleMessageService.diagnose(token);
            check(diagnostic!=null && diagnostic.contains("当前页面无法可靠读取"), "Incomplete tree must fail diagnostic safely");
            check(!diagnostic.contains("本机诊断完成"), "Incomplete tree is not a complete metadata report");
            check(!diagnostic.contains("Exception"), "Do not expose hierarchy exceptions");
            check(!AssistantSession.INSTANCE.active(), "Incomplete diagnostic ends session");
            check(AssistantSession.INSTANCE.visible(token).isEmpty(), "Incomplete diagnostic stores no text");
            assertNoTextReads(f.root);
            token=AssistantSession.INSTANCE.start();
            String capture=VisibleMessageService.captureOnce(token);
            check(capture!=null && capture.contains("当前页面无法可靠读取"), "Incomplete tree must fail normal capture safely");
            check(!capture.contains("Exception"), "Do not expose capture exception");
            check(!AssistantSession.INSTANCE.active(), "Incomplete normal capture ends session");
            check(AssistantSession.INSTANCE.visible(token).isEmpty(), "Incomplete capture stores no text");
            assertNoTextReads(f.root);
            scenarios++;
            System.out.println("PASS missing advertised child: both paths refuse before candidate getText; session invalidated");
        }
    }
    private static void unsupportedMaskPath(String reportedClass, String kind) {
        try (Fixture f=new Fixture()) {
            AccessibilityNodeInfo[] nodes=hierarchy(f,"android.view.View",99,3,50,false);
            f.list.children.remove(nodes[1]);
            AccessibilityNodeInfo branch=node("synthetic_unsupported_mask_branch",reportedClass,100,210,300,250);
            branch.drawingOrder=1; branch.children.add(nodes[1]); f.list.children.add(branch);
            String out=f.diagnose();
            equalCounter(out,"F",1); equalCounter(out,"O",1); equalCounter(out,"E",0);
            // The container itself is a rejected mask kind; its plain child has
            // a distinct unsupported-mask-path classification. Neither is lost.
            String simple=detail("MASK_KIND","NONE/NONE","NONE/NONE","NA/NA","NA/NA");
            String path=detail("MASK_CLASS",kind+"/LAYOUT",kind+"/NONE","POSITIVE/POSITIVE","FEW/FEW");
            assertLcaReport(out,"MASK_KIND=1 MASK_CLASS=1",simple,path);
            String capture=assertNormalOutcome(f,false,"unsupported mask path "+kind);
            assertLcaReport(capture,"MASK_KIND=1 MASK_CLASS=1",simple,path);
            assertSameLcaReport(out,capture); scenarios++;
        }
    }
    private static void messageAncestorOfReportedMask() {
        try (Fixture f=new Fixture()) {
            f.message.drawingOrder=2;
            AccessibilityNodeInfo child=node("synthetic_message_child_mask","android.view.View",100,210,300,250);
            child.drawingOrder=1; f.message.children.add(child);
            String out=f.diagnose();
            equalCounter(out,"F",1); equalCounter(out,"O",1); equalCounter(out,"E",0);
            String expected=detail("MESSAGE_ANCESTOR","NONE/NONE","NONE/NONE","NA/NA","NA/NA");
            assertLcaReport(out,"MESSAGE_ANCESTOR=1",expected);
            String capture=assertNormalOutcome(f,false,"message ancestor of mask");
            assertLcaReport(capture,"MESSAGE_ANCESTOR=1",expected); assertSameLcaReport(out,capture);
            scenarios++;
        }
    }
    private static void deepMessagePathUsesFixedShape() {
        try (Fixture f=new Fixture()) {
            AccessibilityNodeInfo[] nodes=hierarchy(f,"android.view.View",1,2,99,false);
            nodes[0].children.clear();
            AccessibilityNodeInfo current=nodes[0];
            for (int i=0;i<3;i++) {
                AccessibilityNodeInfo next=node("synthetic_deep_"+i,"android.widget.LinearLayout",0,100,1080,1500);
                next.drawingOrder=1; current.children.add(next); current=next;
            }
            current.children.add(f.message);
            String out=f.diagnose();
            equalCounter(out,"O",0); equalCounter(out,"E",1);
            assertLcaReport(out,"ALLOW=1",detail("ALLOW","VIEW/LAYOUT","NONE/NONE","POSITIVE/POSITIVE","ONE/MANY"));
            assertNormalOutcome(f,true,"fixed MANY path bucket"); scenarios++;
        }
    }
    private static void lcaHierarchyCases() {
        genericReportedViewTrustBoundary(); missingChildRefusesIncompleteHierarchy();
        hierarchyCase("different parents, lower LCA branch","android.view.View",1,2,99,false,0,true,"VIEW","ALLOW","POSITIVE/POSITIVE");
        hierarchyCase("different parents, higher LCA branch","android.view.View",3,2,1,false,0,false,"VIEW","ORDER_AHEAD","POSITIVE/POSITIVE");
        hierarchyCase("equal LCA order","android.view.View",2,2,99,false,0,false,"VIEW","ORDER_EQUAL","POSITIVE/POSITIVE");
        hierarchyCase("zero mask branch order","android.view.View",0,2,99,false,0,false,"VIEW","MASK_ORDER_ZERO","ZERO/POSITIVE");
        hierarchyCase("missing message branch order","android.view.View",1,0,99,false,0,false,"VIEW","MESSAGE_ORDER_ZERO","POSITIVE/ZERO");
        hierarchyCase("negative mask branch order","android.view.View",-1,2,99,false,0,false,"VIEW","MASK_ORDER_NEGATIVE","NEGATIVE/POSITIVE");
        hierarchyCase("negative message branch order","android.view.View",1,-2,99,false,0,false,"VIEW","MESSAGE_ORDER_NEGATIVE","POSITIVE/NEGATIVE");
        hierarchyCase("throwing message branch order","android.view.View",1,2,99,false,1,false,"VIEW","MESSAGE_ORDER_ERROR","POSITIVE/ERROR");
        hierarchyCase("throwing mask branch order","android.view.View",1,2,99,false,2,false,"VIEW","MASK_ORDER_ERROR","ERROR/POSITIVE");
        hierarchyCase("contradictory low message leaf, higher ancestor","android.view.View",2,3,1,false,0,true,"VIEW","ALLOW","POSITIVE/POSITIVE");
        hierarchyCase("contradictory high message leaf, lower ancestor","android.view.View",2,1,99,false,0,false,"VIEW","ORDER_AHEAD","POSITIVE/POSITIVE");
        hierarchyCase("unused deeper leaf order missing","android.view.View",1,2,0,false,0,true,"VIEW","ALLOW","POSITIVE/POSITIVE");
        hierarchyCase("unused deeper leaf order throws","android.view.View",1,2,99,false,3,true,"VIEW","ALLOW","POSITIVE/POSITIVE");
        hierarchyCase("reported SurfaceView below","android.view.SurfaceView",1,2,99,false,0,false,"SURFACE","MASK_KIND","NA/NA");
        hierarchyCase("reported TextureView below","android.view.TextureView",1,2,99,false,0,false,"SURFACE","MASK_KIND","NA/NA");
        hierarchyCase("nonleaf View below","android.view.View",1,2,99,true,0,false,"BOX","MASK_KIND","NA/NA");
        hierarchyCase("TextView below","android.widget.TextView",1,2,99,false,0,false,"TEXT","MASK_KIND","NA/NA");
        hierarchyCase("ImageView below","android.widget.ImageView",1,2,99,false,0,false,"IMAGE","MASK_KIND","NA/NA");
        hierarchyCase("other reported class below","example.CustomOverlay",1,2,99,false,0,false,"OTHER","MASK_KIND","NA/NA");
        unsupportedMessagePath("custom ancestor","example.CustomContainer",false,"OTHER");
        unsupportedMessagePath("reported Surface ancestor","android.view.SurfaceView",false,"SURFACE");
        unsupportedMessagePath("reported Texture ancestor","android.view.TextureView",false,"SURFACE");
        unsupportedMessagePath("custom message class","example.CustomText",true,"OTHER");
        unsupportedMessagePath("missing reported class",null,true,"MISSING");
        unsupportedMaskPath("example.CustomMaskContainer","OTHER");
        unsupportedMaskPath("android.view.SurfaceView","SURFACE");
        unsupportedMaskPath(null,"MISSING");
        messageAncestorOfReportedMask(); deepMessagePathUsesFixedShape();
        separateMaskContainerRemainsBlocked();
        lowerMaskCannotBypassClipping(); lowerMaskCannotBypassExternalWindow();
        // This validates reported Android metadata only. A real opaque surface
        // misreported as a generic leaf View is outside this fixture's knowledge.
        // Passing these host tests is not proof of pixel-level visibility.
    }
    public static void main(String[] args) {
        unavailableDrawingOrder(); eligibleMetadata(); unsupportedList(); clippedMessage(); externalOverlay();
        sameWindowOverlay(); screenClippedSize(); normalCaptureControl(); invalidTokenEndsSession();
        sameCountersDifferentMasks(); lcaHierarchyCases();
        System.out.println("PASS "+checks+" checks across "+scenarios+" actual VisibleMessageService scenarios (host fakes; no Android/device verification)");
    }
}
