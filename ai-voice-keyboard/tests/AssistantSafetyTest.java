package com.airnovation.aivoicekeyboard;

import java.util.Arrays;
import java.util.Collections;

public final class AssistantSafetyTest {
    private static int checks;
    private static void check(boolean value, String label) {
        checks++; if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) throws Exception {
        AssistantSession s = new AssistantSession();
        check(!s.active(), "default off");
        check(!AssistantSession.VERIFIED_INSERT_ENABLED, "unverified insert disabled");
        long a = s.start();
        check(s.capture(a, "com.whatsapp", "chatA", true, true, Arrays.asList("hello", "world")), "active visible fixture");
        check(s.visible(a).size() == 2, "two visible messages");
        check(s.accept(a, "中文解释", "English reply"), "current callback");
        check(!s.mayInsert(a, "chatA"), "no verified production identity");
        check(!s.capture(a, "com.whatsapp", "chatB", true, true, Collections.singletonList("new")), "chat switch rejected");
        check(!s.active() && s.visible(a).isEmpty() && s.draft().isEmpty(), "switch clears RAM");
        check(!s.accept(a, "stale", "stale"), "stale callback rejected");
        long b = s.start();
        check(b != a, "new epoch");
        check(!s.capture(a, "com.whatsapp", "chatA", true, true, Collections.emptyList()), "old capture rejected");
        check(!s.capture(b, "com.whatsapp", null, true, true, Collections.emptyList()), "unknown identity rejected");
        b = s.start();
        check(!s.capture(b, "com.whatsapp", "chatA", true, false, Collections.emptyList()), "lock clears");
        b = s.start();
        check(!s.capture(b, "com.whatsapp", "chatA", false, true, Collections.emptyList()), "focus loss clears");
        b = s.start();
        check(!s.capture(b, "com.other", "chatA", true, true, Collections.emptyList()), "other app rejected");
        b = s.start();
        String huge = "x".repeat(4000);
        check(s.capture(b, "com.whatsapp", "chatA", true, true, Arrays.asList(huge, "other")), "bounded capture");
        check(s.visible(b).size() == 1 && s.visible(b).get(0).length() == 3000, "size cap");
        boolean immutable = false;
        try { s.visible(b).add("bad"); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "immutable snapshot");
        s.invalidate();
        check(s.explanation().isEmpty() && s.visible(b).isEmpty(), "stop clears");
        String id = "com.whatsapp:id/message_text";
        check(AssistantNodeFilter.allowed("com.whatsapp", id, true, false, false, true), "message allowed");
        check(!AssistantNodeFilter.allowed("com.whatsapp", id, false, false, false, true), "hidden rejected");
        check(!AssistantNodeFilter.allowed("com.whatsapp", id, true, true, false, true), "editor rejected");
        check(!AssistantNodeFilter.allowed("com.whatsapp", id, true, false, true, true), "password rejected");
        check(!AssistantNodeFilter.allowed("com.whatsapp", id, true, false, false, false), "offscreen rejected");
        check(!AssistantNodeFilter.allowed("com.other", id, true, false, false, true), "other package rejected");
        check(!AssistantNodeFilter.allowed("com.whatsapp", "title", true, false, false, true), "nonmessage rejected");
        final boolean[] blocked = {false};
        AssistantGemini.explainVisible((state, token, spoken, callback) -> { throw new AssertionError("inactive transport called"); }, s,
                (token, chinese, english, error) -> blocked[0] = error != null && chinese == null && english == null);
        check(blocked[0], "inactive session cannot call transport");
        b = s.start(); final long current = b;
        AssistantGemini.demo(b, (token, chinese, english, error) -> {
            check(token == current && error == null && chinese.contains("模拟"), "clearly synthetic fixture");
            check(s.accept(token, chinese, english), "fixture preview");
        });
        long oldContext = s.token();
        s.capture(oldContext, "com.whatsapp", "chatA", true, true, Collections.singletonList("one"));
        s.accept(s.token(), "解释", "draft");
        oldContext = s.token();
        s.capture(oldContext, "com.whatsapp", "chatA", true, true, Collections.singletonList("two"));
        check(!s.accept(oldContext, "old", "old"), "changed visible context rejects reply in flight");
        check(s.draft().isEmpty(), "changed visible context clears draft");
        long currentRequest = s.token();
        final AssistantGemini.Callback[] delayed = {null};
        final int[] responses = {0};
        AssistantGemini.explainVisible((state, token, spoken, callback) -> {
            check(token == currentRequest && spoken == null, "mock explain request scope");
            check(state.visible(token).size() == 1, "only current bounded snapshot");
            delayed[0] = callback;
        }, s, (token, zh, en, error) -> responses[0]++);
        s.invalidate(); s.start();
        delayed[0].done(currentRequest, "late result", null, null);
        check(responses[0] == 0, "late transport result cannot affect new session");
        long mockEpoch = s.token();
        s.capture(mockEpoch, "com.whatsapp", "mock", true, true, Collections.singletonList("synthetic"));
        AssistantGemini.explainVisible((state, token, spoken, callback) -> callback.done(token, "解释", null, null),
                s, (token, zh, en, error) -> check(s.accept(token, zh, en), "mock success accepted"));
        check(s.explanation().equals("解释"), "explanation preview stored");
        s.accept(mockEpoch, null, "English draft");
        check(s.explanation().equals("解释") && s.draft().equals("English draft"), "reply preserves explanation for TTS");
        AssistantGemini.explainVisible((state, token, spoken, callback) -> callback.done(token, null, null, "offline"),
                s, (token, zh, en, error) -> check(error.equals("offline"), "network error mock returned without insertion"));
        check(!s.mayManuallyInsert(mockEpoch, false), "manual insertion needs explicit current-field confirmation");
        check(s.mayManuallyInsert(mockEpoch, true), "confirmed same-epoch draft can be manually inserted");
        s.clearDraft();
        check(!s.mayManuallyInsert(mockEpoch, true) && s.explanation().equals("解释"), "new recording clears old reply eligibility but preserves explanation");
        s.invalidate();
        check(!s.mayManuallyInsert(mockEpoch, true), "navigation invalidates manual confirmation");
        check(AssistantResponsePolicy.requireComplete("STOP", "No, I cannot confirm yet.").equals("No, I cannot confirm yet."), "complete negation preserved");
        for (String reason : Arrays.asList("MAX_TOKENS", "SAFETY", "RECITATION", "OTHER", "", null)) {
            boolean rejected = false;
            try { AssistantResponsePolicy.requireComplete(reason, "I can confirm only if"); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "truncated conditional/unknown finish cannot enter preview: " + reason);
        }
        for (String text : Arrays.asList("", "   ", null, "x".repeat(6001))) {
            boolean rejected = false;
            try { AssistantResponsePolicy.requireComplete("STOP", text); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid completed response rejected");
        }
        java.nio.file.Path temporary = java.nio.file.Files.createTempDirectory("assistant-cache-test");
        java.io.File directory = AssistantAudioCache.prepare(temporary.toFile());
        java.nio.file.Path stale = directory.toPath().resolve("assistant_reply_123.m4a");
        java.nio.file.Files.writeString(stale, "synthetic recording");
        java.nio.file.Path foreign = directory.toPath().resolve("user-file.m4a");
        java.nio.file.Files.writeString(foreign, "keep");
        java.nio.file.Path outside = temporary.resolve("outside.m4a");
        java.nio.file.Files.writeString(outside, "keep");
        java.nio.file.Path link = directory.toPath().resolve("assistant_reply_456.m4a");
        java.nio.file.Files.createSymbolicLink(link, outside);
        AssistantAudioCache.prepare(temporary.toFile());
        check(!java.nio.file.Files.exists(stale), "own crash-leftover recording cleaned");
        check(java.nio.file.Files.exists(foreign), "unrelated cache file untouched");
        check(java.nio.file.Files.exists(outside) && java.nio.file.Files.isSymbolicLink(link), "symlink and outside file untouched");
        java.nio.file.Files.delete(link); java.nio.file.Files.delete(outside);
        java.nio.file.Files.delete(foreign); java.nio.file.Files.delete(directory.toPath()); java.nio.file.Files.delete(temporary);
        AssistantNodeFilter.Box messageBox = new AssistantNodeFilter.Box(10, 20, 90, 50);
        AssistantNodeFilter.Box listClip = new AssistantNodeFilter.Box(0, 25, 100, 100);
        check(!AssistantNodeFilter.unobscured(messageBox, listClip, Collections.emptyList(), Collections.emptyList()), "partially clipped message rejected");
        AssistantNodeFilter.Box fullClip = new AssistantNodeFilter.Box(0, 0, 100, 100);
        check(AssistantNodeFilter.unobscured(messageBox, fullClip, Collections.singletonList(1),
                Collections.singletonList(new AssistantNodeFilter.Region(1, fullClip))), "own ancestor is not an overlay");
        check(!AssistantNodeFilter.unobscured(messageBox, fullClip, Collections.singletonList(1),
                Collections.singletonList(new AssistantNodeFilter.Region(2, new AssistantNodeFilter.Box(0, 0, 100, 30)))), "same-window overlapping header rejected");
        check(!AssistantNodeFilter.unobscured(messageBox, fullClip, Collections.emptyList(),
                Collections.singletonList(new AssistantNodeFilter.Region(-1, new AssistantNodeFilter.Box(0, 40, 100, 100)))), "keyboard overlap rejected");
        check(AssistantNodeFilter.unobscured(messageBox, fullClip, Collections.emptyList(),
                Collections.singletonList(new AssistantNodeFilter.Region(2, new AssistantNodeFilter.Box(0, 60, 100, 100)))), "non-overlapping sibling allowed");
        check(!AssistantNodeFilter.sameUnclippedSize(new AssistantNodeFilter.Box(0,260,540,315), new AssistantNodeFilter.Box(0,0,540,76)), "Android already-clipped screen bounds rejected");
        check(AssistantNodeFilter.sameUnclippedSize(new AssistantNodeFilter.Box(0,184,540,260), new AssistantNodeFilter.Box(0,0,540,76)), "equal positive local/screen dimensions eligible");
        check(!AssistantNodeFilter.sameUnclippedSize(new AssistantNodeFilter.Box(0,0,540,76), new AssistantNodeFilter.Box(0,0,0,0)), "unknown local bounds rejected");
        check(!AssistantNodeFilter.sameUnclippedSize(new AssistantNodeFilter.Box(0,184,539,260), new AssistantNodeFilter.Box(0,0,540,76)), "clipped width rejected even when height matches");
        check(!AssistantNodeFilter.sameUnclippedSize(new AssistantNodeFilter.Box(0,184,541,260), new AssistantNodeFilter.Box(0,0,540,76)), "expanded or transformed width rejected");
        check(!AssistantNodeFilter.sameUnclippedSize(new AssistantNodeFilter.Box(0,184,540,261), new AssistantNodeFilter.Box(0,0,540,76)), "expanded or transformed height rejected");
        check(!AssistantNodeFilter.sameUnclippedSize(new AssistantNodeFilter.Box(0,0,0,76), new AssistantNodeFilter.Box(0,0,540,76)), "zero screen width rejected");
        check(!AssistantNodeFilter.sameUnclippedSize(new AssistantNodeFilter.Box(0,0,540,0), new AssistantNodeFilter.Box(0,0,540,76)), "zero screen height rejected");
        check(!AssistantNodeFilter.sameUnclippedSize(new AssistantNodeFilter.Box(20,0,10,76), new AssistantNodeFilter.Box(20,0,10,76)), "matching inverted widths are not positive geometry");
        check(!AssistantNodeFilter.sameUnclippedSize(new AssistantNodeFilter.Box(0,20,540,10), new AssistantNodeFilter.Box(0,20,540,10)), "matching inverted heights are not positive geometry");
        check(AssistantNodeFilter.sameUnclippedSize(new AssistantNodeFilter.Box(25,184,565,260), new AssistantNodeFilter.Box(-10,-20,530,56)), "size comparison permits translated coordinate origins");
        check(AssistantNodeFilter.unobscured(messageBox, messageBox, Collections.emptyList(), Collections.emptyList()), "exact ancestor boundary remains contained");
        check(!AssistantNodeFilter.unobscured(messageBox, new AssistantNodeFilter.Box(11,20,90,50), Collections.emptyList(), Collections.emptyList()), "one pixel left clipping rejected");
        check(!AssistantNodeFilter.unobscured(messageBox, new AssistantNodeFilter.Box(10,21,90,50), Collections.emptyList(), Collections.emptyList()), "one pixel top clipping rejected");
        check(!AssistantNodeFilter.unobscured(messageBox, new AssistantNodeFilter.Box(10,20,89,50), Collections.emptyList(), Collections.emptyList()), "one pixel right clipping rejected");
        check(!AssistantNodeFilter.unobscured(messageBox, new AssistantNodeFilter.Box(10,20,90,49), Collections.emptyList(), Collections.emptyList()), "one pixel bottom clipping rejected");
        check(AssistantNodeFilter.unobscured(messageBox, fullClip, Collections.emptyList(),
                Collections.singletonList(new AssistantNodeFilter.Region(2, new AssistantNodeFilter.Box(90,20,100,50)))), "overlay touching the boundary without area overlap allowed");
        check(!AssistantNodeFilter.unobscured(messageBox, fullClip, Collections.emptyList(),
                Collections.singletonList(new AssistantNodeFilter.Region(2, new AssistantNodeFilter.Box(89,20,100,50)))), "one pixel sibling overlap rejected");
        AssistantNodeFilter.Box matchingLocal = new AssistantNodeFilter.Box(0,0,80,30);
        check(!(AssistantNodeFilter.sameUnclippedSize(messageBox, matchingLocal)
                && AssistantNodeFilter.unobscured(messageBox, listClip, Collections.emptyList(), Collections.emptyList())), "matching local dimensions do not bypass ancestor clipping");
        check(!(AssistantNodeFilter.sameUnclippedSize(messageBox, matchingLocal)
                && AssistantNodeFilter.unobscured(messageBox, fullClip, Collections.emptyList(),
                    Collections.singletonList(new AssistantNodeFilter.Region(2, messageBox)))), "matching local dimensions do not bypass same-window occlusion");
        testWhatsAppVariants();
        System.out.println("PASS " + checks + " assistant safety checks");
    }

    private static void testWhatsAppVariants() {
        String[] packages = {"com.whatsapp", "com.whatsapp.w4b"};
        for (String pkg : packages) {
            String messageId = pkg + ":id/message_text";
            String entryId = pkg + ":id/entry";
            String otherPkg = "com.whatsapp".equals(pkg) ? "com.whatsapp.w4b" : "com.whatsapp";
            check(AssistantSession.isWhatsApp(pkg), "exact WhatsApp package allowed: " + pkg);
            check(AssistantSession.isResource(pkg, messageId, "message_text"), "same-package message resource: " + pkg);
            check(AssistantSession.isResource(pkg, entryId, "entry"), "same-package editor resource: " + pkg);
            check(!AssistantSession.isResource(pkg, otherPkg + ":id/message_text", "message_text"), "cross-package message ID rejected: " + pkg);
            check(!AssistantSession.isResource(pkg, otherPkg + ":id/entry", "entry"), "cross-package editor ID rejected: " + pkg);
            check(!AssistantSession.isResource(pkg, entryId, "message_text"), "resource name cannot substitute for message: " + pkg);
            check(!AssistantSession.isResource(pkg, messageId, "entry"), "message cannot substitute for editor: " + pkg);
            check(!AssistantSession.isResource(pkg, null, "message_text"), "null resource ID rejected: " + pkg);
            for (String id : Arrays.asList("prefix." + messageId, messageId + ".suffix",
                    messageId + "/message_text", pkg + ":id/message_text_extra", pkg + ":id/../message_text",
                    " " + messageId, messageId + " ", "android:id/message_text")) {
                check(!AssistantSession.isResource(pkg, id, "message_text"), "nonexact resource ID rejected: " + id);
                check(!AssistantNodeFilter.allowed(pkg, id, true, false, false, true), "filter rejects nonexact resource ID: " + id);
            }
            check(AssistantNodeFilter.allowed(pkg, messageId, true, false, false, true), "visible own-package message allowed: " + pkg);
            check(!AssistantNodeFilter.allowed(pkg, otherPkg + ":id/message_text", true, false, false, true), "filter rejects cross-package ID: " + pkg);
            check(!AssistantNodeFilter.allowed(pkg, messageId, false, false, false, true), "invisible message rejected: " + pkg);
            check(!AssistantNodeFilter.allowed(pkg, messageId, true, true, false, true), "editable message rejected: " + pkg);
            check(!AssistantNodeFilter.allowed(pkg, messageId, true, false, true, true), "password message rejected: " + pkg);
            check(!AssistantNodeFilter.allowed(pkg, messageId, true, false, false, false), "offscreen message rejected: " + pkg);
            check(!AssistantNodeFilter.allowed(pkg, entryId, true, false, false, true), "editor resource never treated as message: " + pkg);
            check(!AssistantNodeFilter.allowed(pkg, null, true, false, false, true), "null message ID rejected: " + pkg);

            AssistantSession variant = new AssistantSession();
            long token = variant.start();
            check(!variant.matchesSource(token, pkg) && !variant.matchesSource(token, otherPkg), "uncaptured session has no source binding: " + pkg);
            check(variant.mayReadSource(token, pkg, true) && variant.mayReadSource(token, otherPkg, true), "initial capture may read either exact allowed package: " + pkg);
            check(!variant.mayReadSource(token, pkg, false) && !variant.mayReadSource(token, otherPkg, false), "uncaptured revalidation cannot read either package: " + pkg);
            check(!variant.mayReadSource(token, "com.whatsapp.w4b.suffix", true) && !variant.mayReadSource(token, null, true), "initial capture rejects spoof or missing package: " + pkg);
            check(variant.capture(token, pkg, "synthetic-chat-a", true, true,
                    Collections.singletonList("Synthetic first message")), "variant session captures synthetic message: " + pkg);
            check(variant.visible(token).equals(Collections.singletonList("Synthetic first message")), "variant snapshot preserved: " + pkg);
            check(variant.matchesSource(token, pkg), "captured current source matches: " + pkg);
            check(variant.mayReadSource(token, pkg, true) && variant.mayReadSource(token, pkg, false), "bound package readable during initial or repeat capture: " + pkg);
            check(!variant.mayReadSource(token, otherPkg, true) && !variant.mayReadSource(token, otherPkg, false), "bound session rejects cross-package read before collection: " + pkg);
            check(!variant.matchesSource(token, otherPkg) && !variant.matchesSource(token, null), "other or missing source rejected: " + pkg);
            check(variant.accept(token, "Synthetic explanation", "Synthetic draft"), "variant current result accepted: " + pkg);
            check(variant.capture(token, pkg, "synthetic-chat-a", true, true,
                    Collections.singletonList("Synthetic changed message")), "variant context can refresh: " + pkg);
            check(variant.token() != token && !variant.accept(token, "Stale explanation", "Stale draft"), "variant context change rejects prior result: " + pkg);
            check(variant.explanation().isEmpty() && variant.draft().isEmpty() && variant.visible(token).isEmpty(), "variant context change clears old preview and snapshot: " + pkg);
            long refreshed = variant.token();
            check(!variant.matchesSource(token, pkg), "stale token cannot match source: " + pkg);
            check(!variant.mayReadSource(token, pkg, true) && !variant.mayReadSource(token, pkg, false), "stale token cannot read formerly bound source: " + pkg);
            check(variant.matchesSource(refreshed, pkg) && !variant.matchesSource(refreshed, otherPkg), "refreshed token retains only original source: " + pkg);
            check(variant.mayReadSource(refreshed, pkg, false) && !variant.mayReadSource(refreshed, otherPkg, true), "refreshed session reads only bound source: " + pkg);
            check(variant.visible(refreshed).equals(Collections.singletonList("Synthetic changed message")), "new token sees refreshed variant context: " + pkg);
            check(variant.accept(refreshed, "New explanation", "New draft"), "refreshed variant result accepted: " + pkg);
            check(!variant.capture(refreshed, pkg, "synthetic-chat-b", true, true,
                    Collections.singletonList("Synthetic different chat")), "variant chat switch rejected: " + pkg);
            check(!variant.active() && variant.visible(refreshed).isEmpty()
                    && variant.explanation().isEmpty() && variant.draft().isEmpty(), "variant chat switch clears RAM: " + pkg);
            check(!variant.accept(refreshed, "Late explanation", "Late draft"), "variant chat switch rejects late result: " + pkg);
            check(!variant.matchesSource(refreshed, pkg) && !variant.matchesSource(variant.token(), pkg), "inactive session rejects old and current-generation source checks: " + pkg);
            check(!variant.mayReadSource(variant.token(), pkg, true) && !variant.mayReadSource(variant.token(), pkg, false), "inactive session cannot read even with current generation or initial flag: " + pkg);
            token = variant.start();
            check(variant.capture(token, pkg, "same-synthetic-identity", true, true,
                    Collections.singletonList("Synthetic identical content")), "source package bound independently of identity: " + pkg);
            check(variant.accept(token, "Bound explanation", "Bound draft"), "source-package-bound result accepted: " + pkg);
            check(!variant.capture(token, otherPkg, "same-synthetic-identity", true, true,
                    Collections.singletonList("Synthetic identical content")), "cross-package switch rejects even identical identity and content: " + pkg);
            check(!variant.active() && variant.visible(token).isEmpty()
                    && variant.explanation().isEmpty() && variant.draft().isEmpty(), "cross-package switch clears RAM: " + pkg);
            check(!variant.accept(token, "Cross-package stale explanation", "Cross-package stale draft"), "cross-package switch rejects stale result: " + pkg);
            check(!variant.matchesSource(token, pkg) && !variant.matchesSource(token, otherPkg), "cross-package invalidation removes source eligibility: " + pkg);
            token = variant.start();
            check(variant.capture(token, otherPkg, "same-synthetic-identity", true, true,
                    Collections.singletonList("Synthetic new session")), "explicit new session may bind other WhatsApp package: " + pkg);
            check(variant.visible(token).equals(Collections.singletonList("Synthetic new session")), "restarted session uses only newly bound package context: " + pkg);
            check(variant.matchesSource(token, otherPkg) && !variant.matchesSource(token, pkg), "new session matches only newly captured package: " + pkg);
            check(variant.mayReadSource(token, otherPkg, true) && variant.mayReadSource(token, otherPkg, false)
                    && !variant.mayReadSource(token, pkg, true) && !variant.mayReadSource(token, pkg, false), "restarted session read gate follows new source binding: " + pkg);
            token = variant.start();
            check(!variant.capture(token, pkg, "synthetic-chat-a", false, true, Collections.singletonList("Synthetic")), "variant focus loss invalidates: " + pkg);
            token = variant.start();
            check(!variant.capture(token, pkg, "synthetic-chat-a", true, false, Collections.singletonList("Synthetic")), "variant lock invalidates: " + pkg);
        }
        for (String pkg : Arrays.asList(null, "", "com.other", "prefix.com.whatsapp", "com.whatsapp.suffix",
                "prefix.com.whatsapp.w4b", "com.whatsapp.w4b.suffix", "COM.WHATSAPP", " com.whatsapp", "com.whatsapp.w4b ")) {
            check(!AssistantSession.isWhatsApp(pkg), "nonexact package rejected: " + pkg);
            check(!AssistantSession.isResource(pkg, pkg + ":id/message_text", "message_text"), "matching-looking resource cannot authorize package: " + pkg);
            check(!AssistantSession.isResource(pkg, "com.whatsapp.w4b:id/entry", "entry"), "Business editor ID cannot authorize another package: " + pkg);
            check(!AssistantNodeFilter.allowed(pkg, pkg + ":id/message_text", true, false, false, true), "filter rejects spoof package: " + pkg);
            AssistantSession rejected = new AssistantSession();
            long token = rejected.start();
            check(!rejected.capture(token, pkg, "synthetic-chat-a", true, true, Collections.singletonList("Synthetic")), "capture rejects spoof package: " + pkg);
            check(!rejected.active() && rejected.visible(token).isEmpty(), "spoof package invalidates without retained context: " + pkg);
        }
    }
}
