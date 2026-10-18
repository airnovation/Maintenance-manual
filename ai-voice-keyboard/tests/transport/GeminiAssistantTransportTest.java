package com.airnovation.aivoicekeyboard;

import android.content.Context;
import android.os.Handler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Actual production transport, in-memory HTTP only, synthetic text/audio/key only. */
public final class GeminiAssistantTransportTest {
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String[] MODELS = {
            "gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.6-flash"};
    private static final FakeNetwork NETWORK = new FakeNetwork();
    private static final ExecutorService WORKER = executor();
    private static int checks, cases, fixtureId;

    public static void main(String[] args) throws Exception {
        // Install before constructing any transport. Both URL schemes are always
        // intercepted; there is no real-network fallback, socket, or proxy.
        URL.setURLStreamHandlerFactory(protocol -> {
            return new URLStreamHandler() {
                @Override protected URLConnection openConnection(URL url) {
                    if (!"https".equals(protocol) && !"http".equals(protocol)) {
                        throw new AssertionError("Unsupported URL scheme blocked: " + protocol);
                    }
                    return NETWORK.open(url);
                }
            };
        });
        try {
            if (args.length == 1 && "missing-key".equals(args[0])) {
                testMissingKeyStaleCallback();
            } else {
                testLocalReviewDenied(false);
                testLocalReviewDenied(true);
                testApprovedEditedPayload();
                testInvalidAndStaleApproval();
                testStaleApprovedToken();
                testApprovedReviewLateCallback(200);
                testApprovedReviewLateCallback(503);
                testQueuedApprovedReviewInvalidated();
                testSuccessfulExplanation();
                testSuccessfulReplyAndThoughtFiltering();
                for (int code : new int[] {400, 401, 403, 429, 500, 502, 503}) testHttpFailure(code);
                test404FallbackSuccess();
                test404Exhaustion();
                test404Then429Stops();
                for (int code : new int[] {301, 302, 303, 307, 308}) testRedirect(code);
                testTimeout(true);
                testTimeout(false);
                testResponseBound(65536, false);
                testResponseBound(65537, false);
                testResponseBound(65536, true);
                testResponseBound(65538, true);
                testIncompleteResponse();
                testQueuedRequestInvalidation();
                testPostedCallbackInvalidation(200);
                testPostedCallbackInvalidation(503);
                testInvalidationBeforeOutput();
                testInvalidationBeforeFallback();
                testInFlightCancelAndQueuedRequest();
                testMissingKeyStaleCallback();
                testMissingKeyCurrentCallback();
                testAudioSuccessAndDeletion();
                testAudioRejectedSize(99);
                testAudioRejectedSize(2 * 1024 * 1024 + 1);
                testQueuedAudioCancellation();
                testAudioErrorCallbackInvalidation();
                testAudioInFlightCancellation();
            }
        } finally {
            NETWORK.releaseAll();
            WORKER.shutdownNow();
            check(WORKER.awaitTermination(5, TimeUnit.SECONDS), "worker terminates");
        }
        System.out.println("PASS " + cases + " transport cases / " + checks + " checks (offline fake HTTP; no JSON parser claims)");
    }

    private static void testLocalReviewDenied(boolean captured) throws Exception {
        begin();
        Case test = new Case(false);
        if (captured) captureCandidates(test);
        int reads = SecureSettings.reads, payloads = JSONObject.serializations, objects = JSONObject.constructions;
        test.request(null);
        assertError(test, "会话");
        Result replyResult = new Result();
        test.transport.request(test.session, test.token, "SYNTHETIC-UNAPPROVED-REPLY", replyResult);
        check(replyResult.count == 1 && replyResult.error != null && replyResult.english == null,
                "local-only spoken reply denied");
        Result wrapperResult = new Result();
        AssistantGemini.explainVisible(test.transport, test.session, wrapperResult);
        check(wrapperResult.count == 1 && wrapperResult.error != null, "wrapper denies local-only review");
        Path audio = syntheticAudio(256);
        try {
            Result audioResult = new Result();
            test.transport.transcribe(test.session, test.token, audio.toFile(), audioResult);
            finishWorker(); Handler.drain();
            check(audioResult.count == 1 && audioResult.error != null && audioResult.english == null,
                    "local-only audio rejected without transcript");
            check(!Files.exists(audio) && pending(test.transport, "pendingAudio") == null,
                    "denied audio deleted and never retained");
            check(SecureSettings.reads == reads, "local-only request and audio never read key");
            check(JSONObject.serializations == payloads && JSONObject.constructions == objects,
                    "local-only request and audio never construct or serialize payload");
            check(!test.session.mayUseProvider(test.token) && test.session.visible(test.token).isEmpty(),
                    "candidates never become provider context before approval");
            end(0);
        } finally { Files.deleteIfExists(audio); }
    }

    private static void captureCandidates(Case test) {
        check(test.session.captureReview(test.token, AssistantSession.WHATSAPP, "SYNTHETIC-CHAT", true, true,
                Arrays.asList("UNSELECTED-SYNTHETIC-SECRET", "ORIGINAL-SYNTHETIC-SELECTED")), "local candidates captured");
    }

    private static void testApprovedEditedPayload() throws Exception {
        begin();
        Case test = new Case(false); captureCandidates(test);
        ArrayList<String> edited = new ArrayList<>(Collections.singletonList("EDITED-SYNTHETIC-APPROVED"));
        check(test.session.approveReview(test.token, edited), "explicit edited selection approved");
        edited.set(0, "MUTATED-AFTER-APPROVAL");
        check(test.session.reviewCandidates(test.token).isEmpty(), "candidate list cleared after approval");
        FakeConnection conn = NETWORK.enqueue(200, fixture("STOP", "Approved result."));
        test.request(null); finishWorker(); Handler.drain();
        String payload = conn.output.toString(StandardCharsets.UTF_8.name());
        check(payload.contains("EDITED-SYNTHETIC-APPROVED"), "approved edit included in provider payload");
        check(!payload.contains("UNSELECTED-SYNTHETIC-SECRET") && !payload.contains("ORIGINAL-SYNTHETIC-SELECTED")
                && !payload.contains("MUTATED-AFTER-APPROVAL"), "unselected, original and later mutation excluded");
        check(test.result.count == 1 && test.result.error == null, "approved selection request succeeds");
        check(!test.session.approveReview(test.token, Collections.singletonList("REAPPROVED")), "duplicate approval rejected");
        assertRequest(conn, MODELS[0]); end(1);
    }

    private static void testInvalidAndStaleApproval() throws Exception {
        begin(); Case test = new Case(false);
        check(!test.session.approveReview(test.token, Collections.singletonList("NO-CAPTURE")), "approval before capture denied");
        captureCandidates(test);
        check(!test.session.approveReview(test.token, Collections.emptyList()), "empty selection denied");
        check(!test.session.approveReview(test.token, Arrays.asList("valid", "  ")), "blank selection denied atomically");
        check(!test.session.approveReview(test.token, Collections.singletonList("x".repeat(3001))), "oversize approval denied");
        check(!test.session.mayUseProvider(test.token) && test.session.visible(test.token).isEmpty(), "invalid approvals leave gate closed");
        test.session.invalidate(); long replacement = test.session.start();
        check(test.session.beginReview(replacement), "replacement enters local review");
        check(test.session.captureReview(replacement, AssistantSession.WHATSAPP, "NEW-SYNTHETIC-CHAT", true, true,
                Collections.singletonList("NEW-SYNTHETIC-CANDIDATE")), "new candidates captured");
        check(!test.session.approveReview(test.token, Collections.singletonList("OLD-SYNTHETIC-APPROVAL")), "stale approval denied");
        check(!test.session.mayUseProvider(replacement) && test.session.visible(replacement).isEmpty(), "stale approval cannot authorize replacement");
        end(0);
    }

    private static void testStaleApprovedToken() throws Exception {
        begin(); Case test = new Case(); test.session.invalidate(); test.session.start();
        int reads = SecureSettings.reads, payloads = JSONObject.serializations, objects = JSONObject.constructions;
        test.request(null);
        Path audio = syntheticAudio(256);
        try {
            Result result = new Result();
            test.transport.transcribe(test.session, test.token, audio.toFile(), result);
            finishWorker(); Handler.drain();
            check(test.result.count == 1 && test.result.error != null && result.count == 1 && result.error != null,
                    "stale request and transcription rejected");
            check(!Files.exists(audio), "stale transcription deletes audio");
            check(SecureSettings.reads == reads && JSONObject.serializations == payloads && JSONObject.constructions == objects, "stale token cannot read key or construct payload");
            end(0);
        } finally { Files.deleteIfExists(audio); }
    }

    private static void testApprovedReviewLateCallback(int status) throws Exception {
        begin(); Case test = new Case();
        NETWORK.enqueue(status, fixture("STOP", "OLD-SYNTHETIC-RESULT"));
        test.request(null); finishWorker();
        check(Handler.pendingCount() == 1, "approved completion queued");
        long replacement = test.session.start(); check(test.session.beginReview(replacement), "new local review started");
        Handler.drain();
        check(test.result.count == 0 && !test.session.mayUseProvider(replacement), "late success/error dropped in replacement local review");
        end(1);
    }

    private static void testQueuedApprovedReviewInvalidated() throws Exception {
        begin();
        try (WorkerGate gate = new WorkerGate()) {
            Case test = new Case(); test.request(null);
            int payloads = JSONObject.serializations;
            long replacement = test.session.start(); test.session.beginReview(replacement);
            gate.release(); finishWorker(); Handler.drain();
            check(test.result.count == 0 && JSONObject.serializations == payloads,
                    "queued approved request cannot serialize or deliver after replacement review");
            end(0);
        }
    }

    private static void testSuccessfulExplanation() throws Exception {
        begin();
        FakeConnection conn = NETWORK.enqueue(200, fixture("STOP", "纯合成中文解释。"));
        Case test = new Case();
        test.request(null);
        finishWorker(); Handler.drain();
        check(test.result.count == 1 && test.result.error == null, "explanation success once");
        check("纯合成中文解释。".equals(test.result.chinese) && test.result.english == null, "explanation output channel");
        check(test.result.token == test.token, "same request token delivered");
        assertRequest(conn, MODELS[0]);
        check(conn.input.closed && conn.outputClosed, "success closes both streams");
        check(conn.disconnects > 0, "success disconnects");
        check(pending(test.transport, "pending") == null, "success clears pending connection");
        end(1);
    }

    private static void testSuccessfulReplyAndThoughtFiltering() throws Exception {
        begin();
        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("thought", true).put("text", "SYNTHETIC INTERNAL THOUGHT"))
                .put(new JSONObject().put("text", "No, I cannot confirm "))
                .put(new JSONObject().put("text", "until tomorrow."));
        NETWORK.enqueue(200, fixture(candidate("STOP", parts)));
        Case test = new Case();
        test.request("Synthetic spoken reply: I cannot confirm until tomorrow.");
        finishWorker(); Handler.drain();
        check(test.result.count == 1 && test.result.error == null, "reply success once");
        check(test.result.chinese == null && "No, I cannot confirm until tomorrow.".equals(test.result.english),
                "reply preserves negation, joins parts, excludes thought DOM");
        end(1);
    }

    private static void testHttpFailure(int code) throws Exception {
        begin();
        FakeConnection conn = NETWORK.enqueue(code, "SYNTHETIC ERROR BODY MUST NOT BE READ");
        Case test = new Case(); test.request(null);
        finishWorker(); Handler.drain();
        assertError(test, "HTTP " + code);
        check(conn.responseCalls == 1 && conn.inputCalls == 0 && conn.errorStreamCalls == 0,
                "HTTP " + code + " no retry and no error-body disclosure");
        check(conn.disconnects > 0 && conn.outputClosed, "HTTP " + code + " cleans up");
        assertRequest(conn, MODELS[0]);
        end(1);
    }

    private static void test404FallbackSuccess() throws Exception {
        begin();
        FakeConnection first = NETWORK.enqueue(404, "NOT READ");
        FakeConnection second = NETWORK.enqueue(200, fixture("STOP", "Fallback synthetic result."));
        Case test = new Case(); test.request(null);
        finishWorker(); Handler.drain();
        check(test.result.count == 1 && test.result.error == null, "404 falls back successfully");
        check("Fallback synthetic result.".equals(test.result.chinese), "fallback result delivered");
        assertRequest(first, MODELS[0]); assertRequest(second, MODELS[1]);
        check(first.disconnects > 0 && second.disconnects > 0 && first.inputCalls == 0, "fallback closes first request");
        end(2);
    }

    private static void test404Exhaustion() throws Exception {
        begin();
        List<FakeConnection> connections = new ArrayList<>();
        for (int i = 0; i < MODELS.length; i++) connections.add(NETWORK.enqueue(404, "NOT READ"));
        Case test = new Case(); test.request(null);
        finishWorker(); Handler.drain();
        assertError(test, "当前Gemini模型不可用");
        for (int i = 0; i < MODELS.length; i++) {
            assertRequest(connections.get(i), MODELS[i]);
            check(connections.get(i).disconnects > 0, "exhausted model disconnected");
        }
        end(3);
    }

    private static void test404Then429Stops() throws Exception {
        begin();
        NETWORK.enqueue(404, "NOT READ"); NETWORK.enqueue(429, "NOT READ");
        Case test = new Case(); test.request(null);
        finishWorker(); Handler.drain();
        assertError(test, "HTTP 429");
        end(2);
    }

    private static void testRedirect(int code) throws Exception {
        begin();
        FakeConnection conn = NETWORK.enqueue(code, "NOT READ");
        Case test = new Case(); test.request(null);
        finishWorker(); Handler.drain();
        assertError(test, "HTTP " + code);
        check(!conn.getInstanceFollowRedirects(), "redirect following disabled for " + code);
        check(conn.inputCalls == 0 && conn.errorStreamCalls == 0, "redirect content never read");
        check(conn.disconnects > 0, "redirect disconnected");
        end(1);
    }

    private static void testTimeout(boolean duringOutput) throws Exception {
        begin();
        FakeConnection conn = NETWORK.enqueue(200, fixture("STOP", "Never delivered."));
        if (duringOutput) conn.outputTimeout = true;
        else conn.input.failOnRead = true;
        Case test = new Case(); test.request(null);
        finishWorker(); Handler.drain();
        assertError(test, "Gemini请求未完成");
        check(conn.getConnectTimeout() == 15000 && conn.getReadTimeout() == 40000, "finite timeout settings");
        check(conn.disconnects > 0, "timeout disconnects");
        if (!duringOutput) check(conn.input.closed, "read timeout closes input");
        end(1);
    }

    private static void testResponseBound(int byteSize, boolean multibyte) throws Exception {
        begin();
        String body = multibyte ? "é".repeat(byteSize / 2) : "s".repeat(byteSize);
        check(body.getBytes(StandardCharsets.UTF_8).length == byteSize, "fixture byte size");
        JSONObject.registerFixture(body, candidate("STOP", new JSONArray().put(new JSONObject().put("text", "Bounded result."))));
        FakeConnection conn = NETWORK.enqueue(200, body);
        Case test = new Case(); test.request(null);
        finishWorker(); Handler.drain();
        if (byteSize <= 65536) {
            check(test.result.count == 1 && test.result.error == null && "Bounded result.".equals(test.result.chinese),
                    "exact 64 KiB accepted, including UTF-8");
        } else assertError(test, "Gemini响应过长");
        check(conn.input.closed && conn.disconnects > 0, "bounded response closes input and connection");
        check(conn.input.bytesRead <= 65536 + 4096, "bounded reading stops within one read buffer");
        end(1);
    }

    private static void testIncompleteResponse() throws Exception {
        begin();
        NETWORK.enqueue(200, fixture("MAX_TOKENS", "No, I can confirm only if"));
        Case test = new Case(); test.request("Synthetic reply");
        finishWorker(); Handler.drain();
        assertError(test, "Gemini未完整生成回复");
        end(1);
    }

    private static void testQueuedRequestInvalidation() throws Exception {
        begin();
        try (WorkerGate gate = new WorkerGate()) {
            Case test = new Case(); test.request(null);
            test.session.invalidate(); test.session.start();
            gate.release(); finishWorker(); Handler.drain();
            check(test.result.count == 0, "stale queued request has no callback");
        }
        end(0);
    }

    private static void testPostedCallbackInvalidation(int code) throws Exception {
        begin();
        NETWORK.enqueue(code, fixture("STOP", "Stale synthetic result."));
        Case test = new Case(); test.request(null);
        finishWorker();
        check(Handler.pendingCount() == 1, "network completion is queued on main");
        test.session.invalidate(); test.session.start(); Handler.drain();
        check(test.result.count == 0, "stale posted success/error suppressed after new session");
        end(1);
    }

    private static void testInvalidationBeforeOutput() throws Exception {
        begin();
        Case test = new Case();
        FakeConnection conn = NETWORK.enqueue(200, fixture("STOP", "Never delivered."));
        conn.onOpen = test.session::invalidate;
        test.request(null); finishWorker(); Handler.drain();
        check(conn.outputCalls == 0 && conn.responseCalls == 0, "invalidation during open prevents payload write");
        check(conn.disconnects > 0 && test.result.count == 0, "invalidated open cleaned without callback");
        end(1);
    }

    private static void testInvalidationBeforeFallback() throws Exception {
        begin();
        Case test = new Case();
        FakeConnection conn = NETWORK.enqueue(404, "NOT READ");
        conn.onDisconnect = test.session::invalidate;
        test.request(null); finishWorker(); Handler.drain();
        check(test.result.count == 0, "invalidation before fallback suppresses callback");
        end(1);
    }

    private static void testInFlightCancelAndQueuedRequest() throws Exception {
        begin();
        FakeConnection conn = NETWORK.enqueue(200, fixture("STOP", "Never delivered."));
        conn.blockResponse = true;
        Case test = new Case(); test.request(null);
        await(conn.responseEntered, "first HTTP request started");
        test.request("Synthetic queued reply");
        test.session.invalidate(); test.session.start(); test.transport.cancel();
        check(conn.disconnects > 0, "cancel immediately disconnects active connection");
        finishWorker(); Handler.drain();
        check(test.result.count == 0, "cancelled active and stale queued requests have no callbacks");
        check(pending(test.transport, "pending") == null, "cancelled connection reference cleared");
        end(1);
    }

    private static void testMissingKeyStaleCallback() throws Exception {
        begin(); SecureSettings.key = "";
        Case test = new Case(); test.request(null);
        check(Handler.pendingCount() == 1, "missing key error queued on main");
        test.session.invalidate(); test.session.start(); Handler.drain();
        check(test.result.count == 0, "missing-key callback suppressed after invalidation");
        end(0);
    }

    private static void testMissingKeyCurrentCallback() throws Exception {
        begin(); SecureSettings.key = "";
        Case test = new Case(); test.request(null); Handler.drain();
        assertError(test, "API Key");
        end(0);
    }

    private static void testAudioSuccessAndDeletion() throws Exception {
        begin();
        Path audio = syntheticAudio(256);
        try {
            FakeConnection conn = NETWORK.enqueue(200, fixture("STOP", "Synthetic transcript."));
            conn.onOpen = () -> check(!Files.exists(audio), "audio deleted before HTTP upload begins");
            Case test = new Case(); test.transport.transcribe(test.session, test.token, audio.toFile(), test.result);
            finishWorker(); Handler.drain();
            check(!Files.exists(audio), "transcription preparation deletes temporary audio");
            check(pending(test.transport, "pendingAudio") == null, "transcription clears audio reference");
            check(test.result.count == 1 && test.result.error == null && test.result.chinese == null
                    && "Synthetic transcript.".equals(test.result.english), "transcription response delivered once");
            check(conn.output.size() > 256, "synthetic audio body written to memory only");
            assertRequest(conn, MODELS[0]);
            end(1);
        } finally { Files.deleteIfExists(audio); }
    }

    private static void testAudioRejectedSize(int bytes) throws Exception {
        begin();
        Path audio = syntheticAudio(bytes);
        try {
            Case test = new Case(); test.transport.transcribe(test.session, test.token, audio.toFile(), test.result);
            finishWorker(); Handler.drain();
            assertError(test, "录音无法处理");
            check(!Files.exists(audio), "invalid audio size deleted");
            check(pending(test.transport, "pendingAudio") == null, "invalid audio reference cleared");
            end(0);
        } finally { Files.deleteIfExists(audio); }
    }

    private static void testQueuedAudioCancellation() throws Exception {
        begin();
        Path audio = syntheticAudio(256);
        try (WorkerGate gate = new WorkerGate()) {
            Case test = new Case(); test.transport.transcribe(test.session, test.token, audio.toFile(), test.result);
            check(Files.exists(audio), "queued audio exists before cancel");
            test.session.invalidate(); test.transport.cancel();
            check(!Files.exists(audio), "cancel deletes queued audio immediately");
            check(pending(test.transport, "pendingAudio") == null, "cancel clears pending audio immediately");
            gate.release(); finishWorker(); Handler.drain();
            check(test.result.count == 0, "cancelled queued audio has no callback");
            end(0);
        } finally { Files.deleteIfExists(audio); }
    }

    private static void testAudioErrorCallbackInvalidation() throws Exception {
        begin();
        Path audio = syntheticAudio(99);
        try {
            Case test = new Case(); test.transport.transcribe(test.session, test.token, audio.toFile(), test.result);
            finishWorker();
            check(Handler.pendingCount() == 1, "invalid audio error queued");
            test.session.invalidate(); Handler.drain();
            check(test.result.count == 0 && !Files.exists(audio), "stale audio error suppressed and file removed");
            end(0);
        } finally { Files.deleteIfExists(audio); }
    }

    private static void testAudioInFlightCancellation() throws Exception {
        begin();
        Path audio = syntheticAudio(256);
        try {
            FakeConnection conn = NETWORK.enqueue(200, fixture("STOP", "Never delivered."));
            conn.blockResponse = true;
            Case test = new Case(); test.transport.transcribe(test.session, test.token, audio.toFile(), test.result);
            await(conn.responseEntered, "audio HTTP request started");
            check(!Files.exists(audio), "source audio already deleted during upload");
            test.session.invalidate(); test.transport.cancel();
            check(conn.disconnects > 0, "cancel disconnects transcription request");
            finishWorker(); Handler.drain();
            check(test.result.count == 0 && pending(test.transport, "pendingAudio") == null,
                    "cancelled transcription has no stale callback or audio reference");
            end(1);
        } finally { Files.deleteIfExists(audio); }
    }

    private static void begin() throws Exception {
        finishWorker();
        check(Handler.pendingCount() == 0, "previous case left no main callbacks");
        NETWORK.reset(); JSONObject.clearFixtures(); SecureSettings.key = SecureSettings.SYNTHETIC_KEY;
        cases++;
    }
    private static void end(int expectedRequests) throws Exception {
        finishWorker();
        check(Handler.pendingCount() == 0, "case drained main callbacks");
        NETWORK.assertComplete(expectedRequests);
    }
    private static void assertError(Case test, String fragment) {
        check(test.result.count == 1, "one error callback");
        check(test.result.chinese == null && test.result.english == null, "error never returns draft text");
        check(test.result.error != null && test.result.error.contains(fragment), "safe expected error: " + fragment);
    }
    private static void assertRequest(FakeConnection conn, String model) {
        check(conn.getURL().toString().equals(ENDPOINT + model + ":generateContent"), "existing allowlisted model endpoint");
        check(conn.getURL().getQuery() == null, "API key never placed in URL");
        check("POST".equals(conn.getRequestMethod()) && conn.getDoOutput(), "POST body enabled");
        check("application/json".equals(conn.getRequestProperty("Content-Type")), "JSON content-type set");
        check(SecureSettings.SYNTHETIC_KEY.equals(conn.getRequestProperty("x-goog-api-key")), "synthetic Gemini key header");
        check(!conn.getInstanceFollowRedirects(), "redirects disabled");
        check(conn.getConnectTimeout() == 15000 && conn.getReadTimeout() == 40000, "bounded connection/read timeout");
        check(conn.output.size() > 0, "request body emitted");
    }
    private static String fixture(String finish, String text) {
        return fixture(candidate(finish, new JSONArray().put(new JSONObject().put("text", text))));
    }
    private static JSONObject candidate(String finish, JSONArray parts) {
        return new JSONObject().put("candidates", new JSONArray().put(new JSONObject()
                .put("finishReason", finish).put("content", new JSONObject().put("parts", parts))));
    }
    private static String fixture(JSONObject value) {
        String body = "opaque-fixture-body-" + (++fixtureId);
        JSONObject.registerFixture(body, value); return body;
    }
    private static Path syntheticAudio(int bytes) throws IOException {
        Path path = Files.createTempFile("assistant-transport-synthetic-", ".m4a");
        byte[] data = new byte[bytes]; Arrays.fill(data, (byte) 0x5a);
        Files.write(path, data); return path;
    }
    private static ExecutorService executor() {
        try {
            Field field = GeminiAssistantTransport.class.getDeclaredField("EXECUTOR");
            field.setAccessible(true); return (ExecutorService) field.get(null);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Object pending(GeminiAssistantTransport transport, String name) throws Exception {
        Field field = GeminiAssistantTransport.class.getDeclaredField(name);
        field.setAccessible(true); return field.get(transport);
    }
    private static void finishWorker() throws Exception {
        // A transcription task schedules its HTTP task onto the same executor.
        // Two FIFO barriers therefore drain that nested enqueue deterministically.
        WORKER.submit(() -> { }).get(5, TimeUnit.SECONDS);
        WORKER.submit(() -> { }).get(5, TimeUnit.SECONDS);
    }
    private static void await(CountDownLatch latch, String label) throws Exception {
        check(latch.await(5, TimeUnit.SECONDS), label);
    }
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }

    private static final class Case {
        final AssistantSession session = new AssistantSession();
        final long token = session.start();
        final GeminiAssistantTransport transport = new GeminiAssistantTransport(new Context());
        final Result result = new Result();
        Case() { this(true); }
        Case(boolean approve) {
            check(session.beginReview(token), "synthetic session enters local review");
            if (approve) {
                check(session.captureReview(token, AssistantSession.WHATSAPP, "SYNTHETIC-CHAT", true, true,
                        Collections.singletonList("Synthetic only: are you free tomorrow?")), "synthetic local candidates accepted");
                check(session.approveReview(token, Collections.singletonList("Synthetic only: are you free tomorrow?")),
                        "synthetic explicit selection approved");
            }
        }
        void request(String spoken) { transport.request(session, token, spoken, result); }
    }
    private static final class Result implements AssistantGemini.Callback {
        int count; long token; String chinese, english, error;
        @Override public void done(long id, String zh, String en, String problem) {
            count++; token = id; chinese = zh; english = en; error = problem;
        }
    }
    private static final class WorkerGate implements AutoCloseable {
        final CountDownLatch entered = new CountDownLatch(1), released = new CountDownLatch(1);
        final Future<?> job;
        WorkerGate() throws Exception {
            job = WORKER.submit(() -> {
                entered.countDown();
                try {
                    if (!released.await(5, TimeUnit.SECONDS)) throw new AssertionError("worker gate timed out");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            await(entered, "worker gate started");
        }
        void release() { released.countDown(); }
        @Override public void close() throws Exception { release(); job.get(5, TimeUnit.SECONDS); }
    }
    private static final class FakeNetwork {
        final Queue<FakeConnection> plans = new ArrayDeque<>();
        final List<FakeConnection> opened = new ArrayList<>();
        volatile AssertionError failure;
        synchronized FakeConnection enqueue(int status, String body) throws Exception {
            FakeConnection connection = new FakeConnection(new URL(ENDPOINT + "placeholder"), status, body);
            plans.add(connection); return connection;
        }
        synchronized FakeConnection open(URL url) {
            FakeConnection connection = plans.poll();
            if (connection == null) {
                failure = new AssertionError("Unexpected HTTP attempt (real network is disabled): " + url.getHost());
                throw failure;
            }
            connection.target(url); opened.add(connection);
            if (connection.onOpen != null) connection.onOpen.run();
            return connection;
        }
        synchronized void assertComplete(int expected) {
            if (failure != null) throw failure;
            check(opened.size() == expected, "exact HTTP attempt count: " + expected);
            check(plans.isEmpty(), "all planned fake connections consumed");
        }
        synchronized void reset() {
            if (failure != null) throw failure;
            check(plans.isEmpty(), "no unused fixtures from previous case");
            opened.clear();
        }
        synchronized void releaseAll() {
            for (FakeConnection connection : opened) connection.responseRelease.countDown();
        }
    }
    private static final class FakeConnection extends HttpURLConnection {
        final int status;
        final TrackingInput input;
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final CountDownLatch responseEntered = new CountDownLatch(1), responseRelease = new CountDownLatch(1);
        volatile int disconnects, outputCalls, responseCalls, inputCalls, errorStreamCalls;
        volatile boolean outputClosed, outputTimeout, blockResponse;
        Runnable onOpen, onDisconnect;
        FakeConnection(URL url, int status, String body) {
            super(url); this.status = status; input = new TrackingInput(body.getBytes(StandardCharsets.UTF_8));
        }
        void target(URL target) { url = target; }
        @Override public void connect() { throw new AssertionError("Unexpected explicit connect"); }
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() {
            disconnects++; responseRelease.countDown();
            if (onDisconnect != null) onDisconnect.run();
        }
        @Override public OutputStream getOutputStream() throws IOException {
            outputCalls++;
            if (outputTimeout) throw new SocketTimeoutException("SYNTHETIC connect timeout");
            return new OutputStream() {
                @Override public void write(int value) { output.write(value); }
                @Override public void write(byte[] bytes, int offset, int count) { output.write(bytes, offset, count); }
                @Override public void close() { outputClosed = true; }
            };
        }
        @Override public int getResponseCode() throws IOException {
            responseCalls++;
            if (getInstanceFollowRedirects()) throw new AssertionError("Redirects must be disabled before request");
            responseEntered.countDown();
            if (blockResponse) {
                try {
                    if (!responseRelease.await(5, TimeUnit.SECONDS)) throw new AssertionError("fake response gate timed out");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                throw new IOException("SYNTHETIC disconnected request");
            }
            return status;
        }
        @Override public String getHeaderField(String name) {
            return "Location".equalsIgnoreCase(name) ? "https://redirect.invalid/synthetic-only" : null;
        }
        @Override public InputStream getInputStream() { inputCalls++; return input; }
        @Override public InputStream getErrorStream() {
            errorStreamCalls++; throw new AssertionError("Error payload must not be read");
        }
    }
    private static final class TrackingInput extends InputStream {
        final byte[] body;
        int bytesRead;
        boolean closed, failOnRead;
        TrackingInput(byte[] body) { this.body = body; }
        @Override public int read() throws IOException {
            if (failOnRead) throw new SocketTimeoutException("SYNTHETIC read timeout");
            return bytesRead == body.length ? -1 : body[bytesRead++] & 255;
        }
        @Override public int read(byte[] target, int offset, int length) throws IOException {
            if (failOnRead) throw new SocketTimeoutException("SYNTHETIC read timeout");
            if (bytesRead == body.length) return -1;
            int count = Math.min(length, body.length - bytesRead);
            System.arraycopy(body, bytesRead, target, offset, count); bytesRead += count; return count;
        }
        @Override public void close() { closed = true; }
    }
}
