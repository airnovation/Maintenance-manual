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
        System.out.println("PASS " + checks + " local diagnostic counter checks; no Android/device verification");
    }
}
