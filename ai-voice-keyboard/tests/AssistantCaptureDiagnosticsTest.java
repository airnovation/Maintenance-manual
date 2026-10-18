package com.airnovation.aivoicekeyboard;
public final class AssistantCaptureDiagnosticsTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError(); }
    public static void main(String[] args) {
        AssistantCaptureDiagnostics d = new AssistantCaptureDiagnostics();
        check(d.emptyResult(17).contains("N=17 M=0 L=0 I=0 U=0 K=0 C=0 W=0 S=0 F=0 O=0 T=0"));
        d.structure(true, true); d.structure(false, false);
        check(d.reportedIds == 1 && d.missingIds == 1 && d.listContainers == 1);
        d.candidate(true, true, true, true);
        check(d.recognized == 1 && d.noList == 0 && d.clipped == 0 && d.externalOverlap == 0 && d.sizeMismatch == 0);
        d.candidate(false, true, true, true); check(d.noList == 1);
        d.candidate(true, false, true, true); check(d.clipped == 1);
        d.candidate(true, true, false, true); check(d.externalOverlap == 1);
        d.candidate(true, true, true, false); check(d.sizeMismatch == 1);
        d.candidate(false, false, false, false);
        check(d.recognized == 6 && d.noList == 2 && d.clipped == 2 && d.externalOverlap == 2 && d.sizeMismatch == 2);
        d.candidates = 2; d.sameWindowOverlap = 1; d.missingText = 1;
        check(d.emptyResult(250).contains("F=2 O=1 T=1"));
        check(d.emptyResult(250).contains("不会自动上传"));
        check(new AssistantCaptureDiagnostics().recognized == 0);
        check(d.metadataResult(99).contains("N=99"));
        check(d.metadataResult(99).contains("未读取消息文字、未联网"));
        check(d.metadataResult(99).contains("只截图这块数字区域"));
        check(!d.metadataResult(99).contains("T="));
        var box = new AssistantNodeFilter.Box(0, 0, 100, 100);
        AssistantCaptureDiagnostics low = new AssistantCaptureDiagnostics();
        low.overlaps(box, 1, 2, java.util.List.of(1), java.util.List.of(new AssistantNodeFilter.Region(2, box, 1, 1, 3)));
        check(low.metadataResult(3).contains("SP=1 LO=1 VIEW=1 BIG=1"));
        AssistantCaptureDiagnostics high = new AssistantCaptureDiagnostics();
        high.overlaps(box, 1, 2, java.util.List.of(1), java.util.List.of(new AssistantNodeFilter.Region(2, new AssistantNodeFilter.Box(90,90,100,100), 1, 3, 1)));
        check(high.metadataResult(3).contains("SP=1 HI=1 TEXT=1 SM=1"));
        AssistantCaptureDiagnostics otherParent = new AssistantCaptureDiagnostics();
        otherParent.overlaps(box, 1, 2, java.util.List.of(1), java.util.List.of(new AssistantNodeFilter.Region(2, box, 9, 1, 5)));
        check(otherParent.metadataResult(3).contains("DP=1 UN=1 SURFACE=1 BIG=1"));
        AssistantCaptureDiagnostics unknown = new AssistantCaptureDiagnostics();
        unknown.overlaps(box, 1, 2, java.util.List.of(1), java.util.List.of(new AssistantNodeFilter.Region(2, box, 1, 0, 6)));
        check(unknown.metadataResult(3).contains("SP=1 UN=1 SENSITIVE=1 BIG=1"));
        AssistantCaptureDiagnostics equal = new AssistantCaptureDiagnostics();
        equal.overlaps(box, 1, 2, java.util.List.of(1), java.util.List.of(new AssistantNodeFilter.Region(2, box, 1, 2, 4)));
        check(equal.metadataResult(3).contains("SP=1 EQ=1 BOX=1 BIG=1"));
        AssistantCaptureDiagnostics ignored = new AssistantCaptureDiagnostics();
        ignored.overlaps(box, 1, 2, java.util.List.of(1), java.util.List.of(new AssistantNodeFilter.Region(1, box), new AssistantNodeFilter.Region(2,new AssistantNodeFilter.Box(100,0,200,100))));
        check(ignored.metadataResult(3).contains("遮挡关系：无"));
        AssistantCaptureDiagnostics bounded = new AssistantCaptureDiagnostics();
        java.util.List<AssistantNodeFilter.Region> many = new java.util.ArrayList<>();
        for (int i=2;i<42;i++) many.add(new AssistantNodeFilter.Region(i,box,1,1,3));
        bounded.overlaps(box,1,2,java.util.List.of(1),many);
        check(bounded.metadataResult(50).contains("SP=32 LO=32 VIEW=32 BIG=32 CUT=1"));
        check(!bounded.metadataResult(50).contains("42"));
        // Summary never changes the actual occlusion decision, even for a low-order View.
        check(!AssistantNodeFilter.unobscured(box,box,java.util.List.of(1),many));
        System.out.println("PASS " + checks + " local diagnostic counter checks; no Android/device verification");
    }
}
