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
import java.lang.reflect.Method;
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
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Actual production code, synthetic fixtures and in-memory HTTP; never a real provider request. */
public final class VoiceDraftTransportTest {
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String[] MODELS = {"gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.6-flash"};
    private static final FakeNetwork NETWORK = new FakeNetwork();
    private static final ExecutorService WORKER = executor("EXECUTOR"), DEADLINES = executor("DEADLINES");
    private static int checks, cases, fixtureId;

    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                if (!"https".equals(protocol) && !"http".equals(protocol))
                    throw new AssertionError("Non-HTTP scheme blocked");
                return NETWORK.open(url);
            }
        });
        try {
            testSession(); testPromptContract(); testSuccess(false); testSuccess(true);
            testRevisionGuard(false, "EXTRANEOUS-OLD-DRAFT");
            testRevisionGuard(true, null); testRevisionGuard(true, ""); testRevisionGuard(true, "WRONG-DRAFT");
            for (int code : new int[] {400, 401, 403, 429, 500, 502, 503, 301, 302, 303, 307, 308}) testHttpError(code, false);
            testHttpError(429, true); testFallback(); testModelExhaustion();
            for (String reason : new String[] {"MAX_TOKENS", "SAFETY", "RECITATION", "", "OTHER"}) {
                testFinishReason(reason, false); testFinishReason(reason, true);
            }
            testClarification(); testMalformedDraft(false); testMalformedDraft(true); testExtraDraftField();
            testEmptyOutput(false); testEmptyOutput(true); testOverlongTranscript(); testOverlongDraft();
            testResponseBound(65536, false); testResponseBound(65537, true); testResponseBound(65538, false);
            testAudioSize(99); testAudioSize(2 * 1024 * 1024 + 1); testNullAudio();
            testMissingKey(false); testMissingKey(true); testStaleInput(); testQueuedCancel(); testQueuedInvalidate();
            testCancelInFlight(false); testCancelInFlight(true); testPostedCallback(false); testPostedCallback(true);
            testInvalidateBeforeOutput(); testInvalidateBetweenStages(); testInvalidationBeforeFallback(); testPostedCancel(); testReplacement();
            testTimeout(true); testTimeout(false); testDeadline();
        } finally {
            NETWORK.releaseAll(); WORKER.shutdownNow(); DEADLINES.shutdownNow();
            check(WORKER.awaitTermination(5, TimeUnit.SECONDS), "worker terminated");
            check(DEADLINES.awaitTermination(5, TimeUnit.SECONDS), "deadline worker terminated");
        }
        System.out.println("PASS " + cases + " voice-draft cases / " + checks + " checks (offline HTTP/DOM fakes)");
    }

    private static void testSession() throws Exception {
        begin(); VoiceDraftSession session = new VoiceDraftSession();
        check(!session.active(0), "new session inactive");
        boolean rejected = false;
        try { session.start(true); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "revision without draft rejected");
        long first = session.start(false);
        check(session.active(first) && session.draft().isEmpty() && session.transcript().isEmpty(), "fresh session empty");
        check(!session.accept(first, "", "draft"), "blank transcript rejected atomically");
        check(!session.accept(first, "t".repeat(3001), "draft"), "oversize transcript rejected");
        check(!session.accept(first, "spoken", "d".repeat(4001)), "oversize draft rejected");
        check(session.draft().isEmpty() && session.transcript().isEmpty(), "invalid accepts leave RAM empty");
        check(session.accept(first, "spoken", "original draft"), "accepted result stored");
        long second = session.start(true);
        check(second != first && !session.active(first), "revision invalidates earlier token");
        check(session.draft().equals("original draft") && session.transcript().isEmpty(), "revision retains only draft");
        check(session.permitsPreviousDraft(second, "original draft"), "explicit matching revision permitted");
        check(!session.permitsPreviousDraft(second, null), "missing revision not permitted");
        check(!session.accept(first, "late", "late"), "old result cannot update RAM");
        check(session.accept(second, "make it shorter", "new draft"), "revision result accepted");
        session.invalidate();
        check(!session.active(second) && session.draft().isEmpty() && session.transcript().isEmpty(), "invalidate clears all RAM");
        long fresh = session.start(false);
        check(session.permitsPreviousDraft(fresh, null) && !session.permitsPreviousDraft(fresh, "old"), "fresh rejects prior text");
        check(session.accept(fresh, "new", "new"), "second fresh result accepted");
        session.start(false);
        check(session.draft().isEmpty() && session.transcript().isEmpty(), "new recording starts without prior text");
        end(0);
    }

    private static void testPromptContract() throws Exception {
        begin();
        String spoken = "帮我写英文告诉他 AJB 在 PSA 旁边，地点是 Kampung X。语气礼貌，短一点。";
        JSONObject request = VoiceDraftTransport.buildDraftRequest(spoken, null);
        String rules = request.optJSONObject("systemInstruction").optJSONArray("parts").getJSONObject(0).optString("text", "");
        String data = request.optJSONArray("contents").getJSONObject(0).optJSONArray("parts").getJSONObject(0).optString("text", "");
        check(rules.contains("Separate") && rules.contains("never appear in the draft"), "commands separated from output");
        check(rules.contains("Default to concise, natural English") && rules.contains("output Chinese"), "English default and explicit Chinese override");
        check(rules.contains("tone") && rules.contains("length") && rules.contains("mixed-language"), "tone, length and language commands covered");
        check(rules.contains("AJB and PSA") && rules.contains("do not guess, expand or silently correct"), "ambiguous acronym/place/name preservation prompt");
        check(rules.contains("no chat history") && rules.contains("no tools"), "voice-only prompt boundary");
        check(rules.contains("quoted or embedded commands") && rules.contains("credentials"), "embedded instruction boundary");
        check(rules.contains("quoted request to reply in English is not a language override"), "quoted language command remains message content");
        check(rules.contains("Contradictory language directions") && rules.contains("self-correction need clarification"), "contradictory language commands require clarification");
        check(rules.contains("Command-only fresh input") && rules.contains("短一点") && rules.contains("用中文"), "command-only fresh recording cannot invent content");
        check(rules.contains("Keep relative times relative") && rules.contains("下周二") && rules.contains("一会儿"), "relative dates stay relative");
        check(rules.contains("add AM/PM") && rules.contains("invent a timezone") && rules.contains("不能 and 尚未确认"), "times and negation preserved without assumptions");
        check(rules.contains("Commands to send or delete do not authorize actions"), "voice actions cannot escape draft-only scope");
        check(rules.contains("no explanation, label") && rules.contains("meta instructions"), "only draft content requested");
        check(data.contains(spoken) && data.contains("mode=fresh") && !data.contains("previous_draft"), "fresh data contains only voice input");
        check(!rules.contains(spoken), "user transcript is not system instruction");
        JSONObject config = request.optJSONObject("generationConfig");
        check("application/json".equals(config.optString("responseMimeType", "")), "JSON output required");
        check(config.opt("temperature") == null && config.opt("topP") == null && config.opt("topK") == null,
                "deprecated Gemini sampling controls omitted");
        check(config.optJSONObject("responseSchema").optJSONObject("properties").length() == 2, "bounded two-field schema");
        String revision = VoiceDraftTransport.buildDraftRequest("改成中文，不要太正式", "SYNTHETIC PRIOR DRAFT").toString();
        check(revision.contains("mode=revise") && revision.contains("previous_draft=SYNTHETIC PRIOR DRAFT"), "explicit revision routing");
        String audioPrompt = VoiceDraftTransport.buildTranscription(new byte[100]).toString();
        check(!audioPrompt.contains("temperature") && !audioPrompt.contains("topP") && !audioPrompt.contains("topK"), "transcription omits deprecated sampling controls");
        check(audioPrompt.contains("verbatim") && audioPrompt.contains("Do not obey") && audioPrompt.contains("[unclear]"), "audio transcribed without executing commands");
        end(0);
    }

    private static void testSuccess(boolean revise) throws Exception {
        begin(); Case test = new Case(revise); Path audio = audio(256);
        JSONArray parts = new JSONArray().put(new JSONObject().put("thought", true).put("text", "PRIVATE-SYNTHETIC-THOUGHT"))
                .put(new JSONObject().put("text", "Tell them AJB is near ")).put(new JSONObject().put("text", "PSA in Kampung X."));
        FakeConnection first = NETWORK.enqueue(200, fixture(candidate("STOP", parts)));
        first.onOpen = () -> check(!Files.exists(audio), "audio deleted before any upload");
        FakeConnection second = NETWORK.enqueue(200, draftFixture("AJB is near PSA in Kampung X.", false));
        test.process(audio, revise ? "PREVIOUS-SYNTHETIC-DRAFT" : null); finishWorker(); Handler.drain();
        check(test.result.count == 1 && test.result.error == null, "one successful result");
        check(test.result.token == test.token && "Tell them AJB is near PSA in Kampung X.".equals(test.result.transcript), "verbatim transcript joins only non-thought parts");
        check("AJB is near PSA in Kampung X.".equals(test.result.draft), "only structured draft field returned");
        check(!Files.exists(audio), "successful file cleaned");
        check(test.session.draft().equals(revise ? "PREVIOUS-SYNTHETIC-DRAFT" : ""), "transport never mutates panel session");
        check(test.session.accept(test.token, test.result.transcript, test.result.draft), "panel may explicitly accept result");
        String one = first.output.toString(StandardCharsets.UTF_8.name()), two = second.output.toString(StandardCharsets.UTF_8.name());
        check(one.contains("inline_data") && !one.contains("PREVIOUS-SYNTHETIC-DRAFT"), "transcription receives no prior draft");
        check(!two.contains("inline_data") && two.contains("spoken_transcript=Tell them AJB"), "second stage receives transcript, not audio");
        check(!two.contains("PRIVATE-SYNTHETIC-THOUGHT") && !two.contains("visible_messages"), "no thought or chat context uploaded");
        check(two.contains("previous_draft=PREVIOUS-SYNTHETIC-DRAFT") == revise, "prior draft sent only explicit revise");
        assertRequest(first, MODELS[0]); assertRequest(second, MODELS[0]); end(2);
    }

    private static void testRevisionGuard(boolean revise, String supplied) throws Exception {
        begin(); Case test = new Case(revise); Path audio = audio(256); int reads = SecureSettings.reads;
        test.process(audio, supplied); finishWorker(); Handler.drain();
        assertError(test, "上一稿"); check(!Files.exists(audio), "invalid revision deletes audio");
        check(SecureSettings.reads == reads, "invalid revision never reads key"); end(0);
    }

    private static void testHttpError(int code, boolean secondStage) throws Exception {
        begin(); Case test = new Case();
        if (secondStage) NETWORK.enqueue(200, fixture("STOP", "SYNTHETIC transcript"));
        FakeConnection failed = NETWORK.enqueue(code, "NEVER READ THIS PRIVATE ERROR BODY");
        Path audio = audio(256); test.process(audio, null); finishWorker(); Handler.drain();
        assertError(test, "HTTP " + code);
        check((test.result.transcript != null) == secondStage, "completed transcript retained only after successful first stage");
        check(failed.inputCalls == 0 && failed.errorStreamCalls == 0 && failed.responseCalls == 1, "HTTP errors do not disclose response body or retry");
        check(!Files.exists(audio), "HTTP error cleans recording"); assertRequest(failed, MODELS[0]); end(secondStage ? 2 : 1);
    }

    private static void testFallback() throws Exception {
        begin(); Case test = new Case();
        FakeConnection missing = NETWORK.enqueue(404, "NOT READ");
        FakeConnection fallback = NETWORK.enqueue(200, fixture("STOP", "Synthetic transcript"));
        NETWORK.enqueue(200, draftFixture("Synthetic draft", false));
        test.process(audio(256), null); finishWorker(); Handler.drain();
        check(test.result.error == null && test.result.count == 1, "404 allows same-provider model fallback");
        assertRequest(missing, MODELS[0]); assertRequest(fallback, MODELS[1]); end(3);
    }

    private static void testModelExhaustion() throws Exception {
        begin(); Case test = new Case();
        for (String model : MODELS) NETWORK.enqueue(404, "NOT READ");
        test.process(audio(256), null); finishWorker(); Handler.drain(); assertError(test, "模型不可用"); end(3);
    }

    private static void testFinishReason(String reason, boolean secondStage) throws Exception {
        begin(); Case test = new Case();
        if (secondStage) NETWORK.enqueue(200, fixture("STOP", "Synthetic transcript"));
        NETWORK.enqueue(200, fixture(reason, "INCOMPLETE OR UNSAFE CONTENT"));
        test.process(audio(256), null); finishWorker(); Handler.drain();
        assertError(test, "未完整"); end(secondStage ? 2 : 1);
    }

    private static void testClarification() throws Exception {
        begin(); Case test = new Case();
        NETWORK.enqueue(200, fixture("STOP", "Tell [unclear] to do that"));
        NETWORK.enqueue(200, draftFixture("MUST NOT LEAK AS DRAFT", true));
        test.process(audio(256), null); finishWorker(); Handler.drain(); assertError(test, "有歧义");
        check(test.result.transcript.contains("[unclear]"), "clarification preserves raw transcript for review"); end(2);
    }

    private static void testMalformedDraft(boolean wrongType) throws Exception {
        begin(); Case test = new Case(); NETWORK.enqueue(200, fixture("STOP", "Synthetic transcript"));
        String inner = wrongType ? fixture(new JSONObject().put("draft", "Draft").put("needs_clarification", "false")) : "UNREGISTERED INVALID JSON";
        NETWORK.enqueue(200, fixture("STOP", inner)); test.process(audio(256), null); finishWorker(); Handler.drain();
        check(test.result.count == 1 && test.result.error != null && test.result.draft == null, "malformed/typed response rejected"); end(2);
    }

    private static void testExtraDraftField() throws Exception {
        begin(); Case test = new Case(); NETWORK.enqueue(200, fixture("STOP", "Synthetic transcript"));
        String inner = fixture(new JSONObject().put("draft", "Draft").put("needs_clarification", false).put("explanation", "EXTRA"));
        NETWORK.enqueue(200, fixture("STOP", inner)); test.process(audio(256), null); finishWorker(); Handler.drain();
        assertError(test, "格式无效"); end(2);
    }

    private static void testEmptyOutput(boolean secondStage) throws Exception {
        begin(); Case test = new Case();
        if (secondStage) NETWORK.enqueue(200, fixture("STOP", "Synthetic transcript"));
        NETWORK.enqueue(200, secondStage ? draftFixture("  ", false) : fixture("STOP", "  "));
        test.process(audio(256), null); finishWorker(); Handler.drain(); assertError(test, "为空"); end(secondStage ? 2 : 1);
    }
    private static void testOverlongTranscript() throws Exception {
        begin(); Case test = new Case(); NETWORK.enqueue(200, fixture("STOP", "x".repeat(3001)));
        test.process(audio(256), null); finishWorker(); Handler.drain(); assertError(test, "过长"); end(1);
    }
    private static void testOverlongDraft() throws Exception {
        begin(); Case test = new Case(); NETWORK.enqueue(200, fixture("STOP", "Synthetic transcript"));
        NETWORK.enqueue(200, draftFixture("x".repeat(4001), false));
        test.process(audio(256), null); finishWorker(); Handler.drain(); assertError(test, "过长"); end(2);
    }
    private static void testResponseBound(int size, boolean multibyte) throws Exception {
        begin(); Case test = new Case();
        String body = multibyte ? "é".repeat(size / 2) + (size % 2 == 0 ? "" : "x") : "x".repeat(size);
        JSONObject.registerFixture(body, candidate("STOP", new JSONArray().put(new JSONObject().put("text", "Synthetic transcript"))));
        FakeConnection conn = NETWORK.enqueue(200, body);
        if (size <= 65536) NETWORK.enqueue(200, draftFixture("Draft", false));
        test.process(audio(256), null); finishWorker(); Handler.drain();
        if (size <= 65536) check(test.result.count == 1 && test.result.error == null, "64 KiB accepted");
        else assertError(test, "响应过长");
        check(conn.input.closed && conn.input.bytesRead <= 65536 + 4096, "response stream closes and bound stops reading"); end(size <= 65536 ? 2 : 1);
    }
    private static void testAudioSize(int size) throws Exception {
        begin(); Case test = new Case(); Path audio = audio(size); test.process(audio, null); finishWorker(); Handler.drain();
        assertError(test, "录音"); check(!Files.exists(audio), "invalid audio deleted"); end(0);
    }
    private static void testNullAudio() throws Exception {
        begin(); Case test = new Case(); test.transport.process(test.session, test.token, null, null, test.result);
        finishWorker(); Handler.drain(); assertError(test, "录音"); end(0);
    }
    private static void testMissingKey(boolean stale) throws Exception {
        begin(); SecureSettings.key = ""; Case test = new Case(); Path audio = audio(256);
        test.process(audio, null); finishWorker(); if (stale) test.session.invalidate(); Handler.drain();
        if (stale) check(test.result.count == 0, "stale missing-key callback suppressed"); else assertError(test, "API Key");
        check(!Files.exists(audio), "missing key deletes audio"); end(0);
    }
    private static void testStaleInput() throws Exception {
        begin(); Case test = new Case(); Path audio = audio(256); test.session.invalidate(); int reads = SecureSettings.reads;
        test.process(audio, null); finishWorker(); Handler.drain();
        check(test.result.count == 0 && !Files.exists(audio) && SecureSettings.reads == reads, "stale entry cleans audio without key/network/callback"); end(0);
    }
    private static void testQueuedCancel() throws Exception {
        begin();
        try (WorkerGate gate = new WorkerGate()) {
            Case test = new Case(); Path audio = audio(256); test.process(audio, null); test.transport.cancel();
            check(!Files.exists(audio), "cancel deletes queued audio immediately");
            gate.release(); finishWorker(); Handler.drain(); check(test.result.count == 0, "cancel alone suppresses queued work"); end(0);
        }
    }
    private static void testQueuedInvalidate() throws Exception {
        begin();
        try (WorkerGate gate = new WorkerGate()) {
            Case test = new Case(); Path audio = audio(256); test.process(audio, null); test.session.invalidate();
            gate.release(); finishWorker(); Handler.drain();
            check(test.result.count == 0 && !Files.exists(audio), "session invalidation cleans queued work"); end(0);
        }
    }
    private static void testCancelInFlight(boolean secondStage) throws Exception {
        begin(); Case test = new Case();
        if (secondStage) NETWORK.enqueue(200, fixture("STOP", "Synthetic transcript"));
        FakeConnection conn = NETWORK.enqueue(200, fixture("STOP", "Never delivered")); conn.blockResponse = true;
        Path audio = audio(256); test.process(audio, null); await(conn.responseEntered, "request entered");
        test.transport.cancel(); check(conn.disconnects > 0 && !Files.exists(audio), "cancel disconnects and deletes");
        finishWorker(); Handler.drain(); check(test.result.count == 0, "cancel suppresses both stages"); end(secondStage ? 2 : 1);
    }
    private static void testPostedCallback(boolean error) throws Exception {
        begin(); Case test = new Case(); NETWORK.enqueue(error ? 503 : 200, fixture("STOP", "Synthetic transcript"));
        if (!error) NETWORK.enqueue(200, draftFixture("Draft", false));
        test.process(audio(256), null); finishWorker(); check(Handler.pendingCount() == 1, "completion posted");
        test.session.start(false); Handler.drain(); check(test.result.count == 0, "new generation suppresses posted result/error"); end(error ? 1 : 2);
    }
    private static void testInvalidateBeforeOutput() throws Exception {
        begin(); Case test = new Case(); FakeConnection conn = NETWORK.enqueue(200, "NOT READ"); conn.onOpen = test.session::invalidate;
        test.process(audio(256), null); finishWorker(); Handler.drain();
        check(conn.outputCalls == 0 && conn.responseCalls == 0 && conn.disconnects > 0 && test.result.count == 0, "invalidation after connection construction prevents upload"); end(1);
    }
    private static void testInvalidateBetweenStages() throws Exception {
        begin(); Case test = new Case(); FakeConnection conn = NETWORK.enqueue(200, fixture("STOP", "Synthetic transcript"));
        conn.onDisconnect = test.session::invalidate;
        test.process(audio(256), null); finishWorker(); Handler.drain(); check(test.result.count == 0, "invalidation blocks drafting request"); end(1);
    }
    private static void testInvalidationBeforeFallback() throws Exception {
        begin(); Case test = new Case(); FakeConnection conn = NETWORK.enqueue(404, "NOT READ");
        conn.onDisconnect = test.session::invalidate;
        test.process(audio(256), null); finishWorker(); Handler.drain();
        check(test.result.count == 0, "invalidation prevents 404 fallback"); end(1);
    }
    private static void testPostedCancel() throws Exception {
        begin(); Case test = new Case(); NETWORK.enqueue(200, fixture("STOP", "Synthetic transcript"));
        NETWORK.enqueue(200, draftFixture("Draft", false));
        test.process(audio(256), null); finishWorker(); test.transport.cancel(); Handler.drain();
        check(test.result.count == 0 && test.session.active(test.token), "cancel alone suppresses already-posted completion"); end(2);
    }
    private static void testReplacement() throws Exception {
        begin(); Case test = new Case();
        FakeConnection old = NETWORK.enqueue(200, "NOT READ"); old.blockResponse = true;
        test.process(audio(256), null); await(old.responseEntered, "old request entered");
        NETWORK.enqueue(200, fixture("STOP", "Replacement transcript")); NETWORK.enqueue(200, draftFixture("Replacement draft", false));
        Result replacement = new Result(); long newer = test.session.start(false); Path audio = audio(256);
        test.transport.process(test.session, newer, audio.toFile(), null, replacement);
        finishWorker(); Handler.drain();
        check(test.result.count == 0 && replacement.count == 1 && "Replacement draft".equals(replacement.draft), "replacement cancels older operation without cancelling new one");
        check(old.disconnects > 0 && !Files.exists(audio), "replacement cleans both operations"); end(3);
    }
    private static void testTimeout(boolean atOutput) throws Exception {
        begin(); Case test = new Case(); FakeConnection conn = NETWORK.enqueue(200, fixture("STOP", "Never delivered"));
        if (atOutput) conn.outputTimeout = true; else conn.input.failOnRead = true;
        test.process(audio(256), null); finishWorker(); Handler.drain(); assertError(test, "未完成");
        check(conn.disconnects > 0, "timeout closes connection"); end(1);
    }
    private static void testDeadline() throws Exception {
        begin(); Case test = new Case(); FakeConnection conn = NETWORK.enqueue(200, "NOT READ"); conn.blockResponse = true;
        test.process(audio(256), null); await(conn.responseEntered, "deadline request entered");
        Field field = VoiceDraftTransport.class.getDeclaredField("current"); field.setAccessible(true); Object operation = field.get(test.transport);
        Method expire = VoiceDraftTransport.class.getDeclaredMethod("expire", operation.getClass()); expire.setAccessible(true); expire.invoke(test.transport, operation);
        finishWorker(); Handler.drain(); assertError(test, "超时"); check(conn.disconnects > 0, "deadline disconnects"); end(1);
    }

    private static void begin() throws Exception {
        finishWorker(); check(Handler.pendingCount() == 0, "no previous main callbacks");
        NETWORK.reset(); JSONObject.clearFixtures(); SecureSettings.key = SecureSettings.SYNTHETIC_KEY; cases++;
    }
    private static void end(int count) throws Exception {
        finishWorker(); check(Handler.pendingCount() == 0, "main callbacks drained"); NETWORK.assertComplete(count);
        for (FakeConnection conn : NETWORK.opened) check(conn.disconnects > 0, "each connection disconnected");
    }
    private static void assertError(Case test, String text) {
        check(test.result.count == 1 && test.result.draft == null && test.result.error != null && test.result.error.contains(text), "safe error with no draft: " + text);
        check(!test.result.error.contains("PRIVATE ERROR"), "private error body not exposed");
    }
    private static void assertRequest(FakeConnection conn, String model) {
        check(conn.getURL().toString().equals(ENDPOINT + model + ":generateContent"), "ordinary Gemini endpoint only");
        check(conn.getURL().getQuery() == null && SecureSettings.SYNTHETIC_KEY.equals(conn.getRequestProperty("x-goog-api-key")), "key in header only");
        check("POST".equals(conn.getRequestMethod()) && conn.getDoOutput(), "POST body enabled");
        check("application/json".equals(conn.getRequestProperty("Content-Type")), "JSON content type");
        check(!conn.getInstanceFollowRedirects(), "redirects disabled");
        check(conn.getConnectTimeout() == 15000 && conn.getReadTimeout() == 40000, "finite socket timeouts");
        check(conn.output.size() > 0 && conn.outputClosed, "output written and closed");
    }
    private static String fixture(String finish, String text) {
        return fixture(candidate(finish, new JSONArray().put(new JSONObject().put("text", text))));
    }
    private static JSONObject candidate(String finish, JSONArray parts) {
        return new JSONObject().put("candidates", new JSONArray().put(new JSONObject().put("finishReason", finish).put("content", new JSONObject().put("parts", parts))));
    }
    private static String fixture(JSONObject object) {
        String body = "opaque-voice-fixture-" + (++fixtureId); JSONObject.registerFixture(body, object); return body;
    }
    private static String draftFixture(String draft, boolean clarify) {
        return fixture("STOP", fixture(new JSONObject().put("draft", draft).put("needs_clarification", clarify)));
    }
    private static Path audio(int bytes) throws IOException {
        Path path = Files.createTempFile("voice-draft-synthetic-", ".m4a");
        byte[] data = new byte[bytes]; Arrays.fill(data, (byte) 0x5a); Files.write(path, data); return path;
    }
    private static ExecutorService executor(String name) {
        try { Field field = VoiceDraftTransport.class.getDeclaredField(name); field.setAccessible(true); return (ExecutorService) field.get(null); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void finishWorker() throws Exception { WORKER.submit(() -> { }).get(5, TimeUnit.SECONDS); }
    private static void await(CountDownLatch latch, String message) throws Exception { check(latch.await(5, TimeUnit.SECONDS), message); }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    private static final class Case {
        final VoiceDraftSession session = new VoiceDraftSession(); final long token;
        final VoiceDraftTransport transport = new VoiceDraftTransport(new Context()); final Result result = new Result();
        Case() { this(false); }
        Case(boolean revise) {
            if (revise) { long first = session.start(false); session.accept(first, "PRIOR TRANSCRIPT", "PREVIOUS-SYNTHETIC-DRAFT"); }
            token = session.start(revise);
        }
        void process(Path audio, String prior) { transport.process(session, token, audio.toFile(), prior, result); }
    }
    private static final class Result implements VoiceDraftTransport.Callback {
        int count; long token; String transcript, draft, error;
        @Override public void done(long token, String transcript, String draft, String error) {
            count++; this.token = token; this.transcript = transcript; this.draft = draft; this.error = error;
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
