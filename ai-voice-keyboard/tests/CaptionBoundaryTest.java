package com.airnovation.aivoicekeyboard;
import java.util.List;
public final class CaptionBoundaryTest {
    static int checks;
    static void check(boolean x) { checks++; if (!x) throw new AssertionError(); }
    public static void main(String[] args) {
        // Synthetic rectangles only, not inferred Accessibility nodes from a screenshot.
        var listClip = new AssistantNodeFilter.Box(0, 100, 500, 600);
        var caption = new AssistantNodeFilter.Box(30, 350, 450, 500);
        // Parent album extends above list; its child is fully in the intersected clip.
        check(AssistantNodeFilter.unobscured(caption, listClip, List.of(1, 2), List.of(
            new AssistantNodeFilter.Region(2, new AssistantNodeFilter.Box(20, 20, 470, 550)))));
        check(!AssistantNodeFilter.unobscured(new AssistantNodeFilter.Box(30, 80, 450, 150), listClip, List.of(1, 2), List.of()));
        check(!AssistantNodeFilter.unobscured(caption, listClip, List.of(1, 2), List.of(
            new AssistantNodeFilter.Region(3, new AssistantNodeFilter.Box(20, 300, 470, 550)))));
        for (String pkg : List.of("com.whatsapp", "com.whatsapp.w4b")) {
            check(AssistantNodeFilter.allowed(pkg, pkg+":id/message_text", true, false, false, true));
            check(!AssistantNodeFilter.allowed(pkg, pkg+":id/UNVERIFIED_CAPTION", true, false, false, true));
            check(!AssistantNodeFilter.allowed(pkg, pkg+":id/message_text", true, false, false, false));
        }
        System.out.println("PASS " + checks + " existing caption-boundary checks; synthetic geometry only");
    }
}
