package com.airnovation.aivoicekeyboard;

import android.os.Handler;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
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
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLException;

/** Actual transports, fake Android/JSON DOM/HTTP only. No real key, socket or provider. */
public final class ApiSafetyTest {
    private static final String KEY = "SYNTHETIC-ONLY-NOT-A-REAL-KEY";
    private static final String PRIVATE = "SYNTHETIC-PRIVATE-MESSAGE-MUST-NOT-LEAK";
    private static final Network NETWORK = new Network();
    private static final ExecutorService[] WORKERS = {
        executor(GeminiEnglish.class, "EXECUTOR"), executor(OpenAiEnglish.class, "EXECUTOR"),
        executor(AudioTranscriber.class, "IO")
    };
    private static int checks, cases, fixtures;
    private enum Route { GEMINI_EN, OPENAI_EN, GEMINI_SPEECH, OPENAI_SPEECH }

    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                if (!"https".equals(protocol)) throw new AssertionError("Only fake HTTPS is allowed");
                return NETWORK.open(url);
            }
        });
        try {
            testValidation(); testErrorSanitizer();
            for (Route route : Route.values()) {
                testInvalidKeys(route); testSuccess(route); testHeaderFailure(route);
                testTimeout(route); testMalformedResponse(route); testResponseBound(route);
                for (int code : new int[] {400, 401, 403, 429, 500, 503, 301, 302, 303, 307, 308})
                    testHttpFailure(route, code);
                if (isGemini(route)) { testFallback(route); testModelExhaustion(route); testIncomplete(route); }
            }
            testIncomplete(Route.OPENAI_EN);
        } finally {
            for (ExecutorService worker : WORKERS) worker.shutdownNow();
            for (ExecutorService worker : WORKERS)
                check(worker.awaitTermination(5, TimeUnit.SECONDS), "executor terminated");
        }
        System.out.println("PASS " + cases + " API safety cases / " + checks + " checks (offline only)");
    }

    private static void testValidation() throws Exception {
        cases++;
        for (String valid : new String[] {KEY, "abcdefghijklmnop", "x".repeat(4096),
                "future:token/V1+with_underscores-and.dots==", "new-provider-!#$%&()*+,/:;<=>?@[]^_{|}~"}) {
            check(ApiKeyValidation.validationMessage(valid) == null, "broad printable key accepted");
            ApiKeyValidation.requireValid(valid);
        }
        for (int c = 0; c <= 0x20; c++) reject("SYNTHETIC-KEY" + (char)c + "SUFFIX");
        for (char c : new char[] {0x7f, 0x80, 0xa0, 0x200b, 0x2028, 0x2029, '\u4e2d', '\ud83d'})
            reject("SYNTHETIC-KEY" + c + "SUFFIX");
        for (String invalid : new String[] {null, "", "x".repeat(15), "x".repeat(4097),
                " " + KEY, KEY + " ", "\"" + KEY + "\"", "'" + KEY + "'", "`" + KEY + "`",
                "https://aistudio.google.com/apikey", "http://example.test/key",
                "Authorization:" + KEY, "x-goog-api-key:" + KEY,
                "java.lang.IllegalArgumentException:private", "java.io.IOException:private",
                "Unexpectedchar0x2028at0", "Unexpectedcharacter0x7f",
                "IllegalArgumentException:private", "{\"error\":\"private\"}", "{\"message\":\"private\"}"}) reject(invalid);
    }
    private static void reject(String input) throws Exception {
        String message = ApiKeyValidation.validationMessage(input);
        check(message != null, "malformed input rejected");
        check(!message.contains(KEY) && !message.contains(PRIVATE), "input absent from validation message");
        try { ApiKeyValidation.requireValid(input); throw new AssertionError("invalid key accepted"); }
        catch (SafeApiErrors.Failure expected) {
            check(message.equals(SafeApiErrors.message(expected)), "fixed error agrees with local validation");
        }
    }
    private static void testErrorSanitizer() {
        cases++;
        for (Throwable error : new Throwable[] {null, new IllegalArgumentException(KEY + PRIVATE),
                new IOException(KEY + PRIVATE), new RuntimeException(KEY + PRIVATE),
                new SocketTimeoutException(KEY + PRIVATE), new SSLException(KEY + PRIVATE)}) {
            String message = SafeApiErrors.message(error);
            check(message != null && !message.isEmpty(), "fixed error supplied");
            check(!message.contains(KEY) && !message.contains(PRIVATE), "raw exception never displayed");
        }
    }
    private static void testInvalidKeys(Route route) throws Exception {
        for (String key : new String[] {null, "", "short", KEY + "\n", KEY + "\r", KEY + "\t", KEY + " ",
                " " + KEY, KEY + "\u2028", KEY + "中文", "Unexpected char 0x2028 in x-goog-api-key", "https://example.test/api-key"}) {
            begin(); Result result = call(route, key); finish(); assertError(result);
            check(result.error.equals(ApiKeyValidation.validationMessage(key)), "local validation message is used");
            end(0);
        }
    }
    private static void testSuccess(Route route) throws Exception {
        begin(); Connection conn = NETWORK.enqueue(200, success(route, "Synthetic output"));
        Result result = call(route, KEY); finish();
        check(result.count == 1 && result.error == null && "Synthetic output".equals(result.text), "successful output preserved");
        check(!conn.getInstanceFollowRedirects(), "redirects disabled");
        check("POST".equals(conn.getRequestMethod()) && conn.getDoOutput(), "POST body enabled");
        check(conn.getURL().getQuery() == null && !conn.getURL().toString().contains(KEY), "key not in URL");
        check((isGemini(route) ? KEY : "Bearer " + KEY).equals(conn.getRequestProperty(isGemini(route) ? "x-goog-api-key" : "Authorization")), "key only in intended header");
        check(conn.getConnectTimeout() > 0 && conn.getReadTimeout() > 0, "finite network timeouts");
        String payload = conn.output.toString(StandardCharsets.UTF_8);
        check(!payload.contains(KEY), "key absent from request body");
        if (route == Route.GEMINI_EN || route == Route.OPENAI_EN) {
            check(payload.contains(PRIVATE), "only explicit input forwarded for EN");
            check(payload.contains("NATURAL, CONCISE everyday English"), "legacy EN instructions retained");
            for (String rule : new String[]{"natural conversational phrasing", "stiff word-for-word", "ordinary contractions", "negation", "uncertainty", "commitments", "place names and relative dates", "AM/PM", "AJB and PSA", "never expand them by guessing", "not yet confirmed is not a promise", "quoted requests and instructions"})
                check(payload.contains(rule), "EN prompt contract: " + rule);
        }
        check(conn.outputClosed, "request output closed"); end(1);
    }
    private static void testHttpFailure(Route route, int code) throws Exception {
        begin(); NETWORK.enqueue(code, PRIVATE + KEY);
        Result result = call(route, KEY); finish(); assertError(result);
        check(result.error.contains("HTTP " + code), "only numeric status included"); end(1);
    }
    private static void testHeaderFailure(Route route) throws Exception {
        begin(); Connection conn = NETWORK.enqueue(200, PRIVATE); conn.headerFailure = true;
        Result result = call(route, KEY); finish(); assertError(result);
        check(conn.outputCalls == 0, "header failure writes no payload"); end(1);
    }
    private static void testTimeout(Route route) throws Exception {
        begin(); Connection conn = NETWORK.enqueue(200, PRIVATE); conn.timeout = true;
        Result result = call(route, KEY); finish(); assertError(result);
        check(result.error.contains("timed out"), "timeout safe guidance"); end(1);
    }
    private static void testMalformedResponse(Route route) throws Exception {
        begin(); NETWORK.enqueue(200, "UNREGISTERED-" + PRIVATE + KEY);
        Result result = call(route, KEY); finish(); assertError(result); end(1);
    }
    private static void testResponseBound(Route route) throws Exception {
        begin(); NETWORK.enqueue(200, "x".repeat(65537));
        Result result = call(route, KEY); finish(); assertError(result);
        check(result.error.contains("too large"), "response size error is fixed"); end(1);
    }
    private static void testFallback(Route route) throws Exception {
        begin(); NETWORK.enqueue(404, PRIVATE + KEY); NETWORK.enqueue(200, success(route, "Fallback text"));
        Result result = call(route, KEY); finish();
        check(result.count == 1 && result.error == null && "Fallback text".equals(result.text), "404-only same-provider fallback"); end(2);
    }
    private static void testModelExhaustion(Route route) throws Exception {
        begin(); for (int i = 0; i < 3; i++) NETWORK.enqueue(404, PRIVATE + KEY);
        Result result = call(route, KEY); finish(); assertError(result); end(3);
    }
    private static void testIncomplete(Route route) throws Exception {
        begin(); JSONObject body = isGemini(route) ? gemini("MAX_TOKENS", "Truncated")
                : openai("incomplete", "Truncated");
        NETWORK.enqueue(200, fixture(body)); Result result = call(route, KEY); finish(); assertError(result); end(1);
    }

    private static boolean isGemini(Route route) { return route == Route.GEMINI_EN || route == Route.GEMINI_SPEECH; }
    private static Result call(Route route, String key) throws Exception {
        Result result = new Result();
        switch (route) {
            case GEMINI_EN: GeminiEnglish.rewrite(key, PRIVATE, result::done); break;
            case OPENAI_EN: OpenAiEnglish.rewrite(key, PRIVATE, result::done); break;
            default:
                result.audio = Files.createTempFile("synthetic-audio-", ".m4a");
                Files.write(result.audio, new byte[256]);
                AudioTranscriber.transcribe(isGemini(route) ? "gemini" : "openai", key, result.audio.toFile(), result::done);
        }
        return result;
    }
    private static void assertError(Result result) {
        check(result.count == 1 && result.text == null && result.error != null && !result.error.isEmpty(), "one error, no output");
        check(!result.error.contains(KEY) && !result.error.contains(PRIVATE), "error does not contain key/content");
    }
    private static String success(Route route, String text) {
        return fixture(isGemini(route) ? gemini("STOP", text)
                : route == Route.OPENAI_EN ? openai("completed", text) : new JSONObject().put("text", text));
    }
    private static JSONObject gemini(String finish, String text) {
        return new JSONObject().put("candidates", new JSONArray().put(new JSONObject().put("finishReason", finish)
                .put("content", new JSONObject().put("parts", new JSONArray()
                        .put(new JSONObject().put("thought", true).put("text", "Hidden thought ignored"))
                        .put(new JSONObject().put("text", text))))));
    }
    private static JSONObject openai(String status, String text) {
        return new JSONObject().put("status", status).put("output", new JSONArray().put(new JSONObject().put("type", "message")
                .put("content", new JSONArray().put(new JSONObject().put("type", "output_text").put("text", text)))));
    }
    private static String fixture(JSONObject value) {
        String body = "opaque-api-safety-fixture-" + (++fixtures); JSONObject.registerFixture(body, value); return body;
    }
    private static void begin() throws Exception {
        finish(); check(Handler.pendingCount() == 0, "no old callbacks"); NETWORK.reset(); JSONObject.clearFixtures(); cases++;
    }
    private static void end(int requests) {
        check(Handler.pendingCount() == 0, "callbacks drained"); NETWORK.verify(requests);
    }
    private static void finish() throws Exception {
        for (ExecutorService worker : WORKERS) worker.submit(() -> { }).get(5, TimeUnit.SECONDS);
        Handler.drain();
    }
    private static ExecutorService executor(Class<?> owner, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return (ExecutorService) field.get(null); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
    private static final class Result {
        int count; String text, error; Path audio;
        void done(String text, String error) {
            this.text = text; this.error = error; count++;
            check(audio == null || !Files.exists(audio), "audio deleted before callback even on failure");
        }
    }
    private static final class Network {
        final Queue<Connection> plans = new ArrayDeque<>(); final List<Connection> opened = new ArrayList<>();
        AssertionError unexpected;
        synchronized Connection enqueue(int status, String body) throws Exception {
            Connection connection = new Connection(new URL("https://synthetic.invalid/"), status, body); plans.add(connection); return connection;
        }
        synchronized Connection open(URL url) {
            Connection connection = plans.poll();
            if (connection == null) { unexpected = new AssertionError("Unexpected HTTP attempt; real network is blocked"); throw unexpected; }
            connection.target(url); opened.add(connection); return connection;
        }
        synchronized void verify(int count) {
            if (unexpected != null) throw unexpected;
            check(opened.size() == count && plans.isEmpty(), "exact fake request count");
            for (Connection connection : opened) {
                check(connection.disconnects > 0, "connection disconnected");
                check(connection.errorReads == 0, "provider error body not read");
                check(connection.inputCalls == 0 || connection.inputClosed, "response stream closed");
            }
        }
        synchronized void reset() { verify(opened.size()); opened.clear(); }
    }
    private static final class Connection extends HttpURLConnection {
        final int code; final byte[] response; final ByteArrayOutputStream output = new ByteArrayOutputStream();
        int disconnects, errorReads, inputCalls, outputCalls; boolean headerFailure, timeout, outputClosed, inputClosed;
        Connection(URL url, int code, String body) { super(url); this.code=code; response=body.getBytes(StandardCharsets.UTF_8); }
        void target(URL target) { url=target; }
        @Override public void setRequestProperty(String key, String value) {
            if (headerFailure && ("x-goog-api-key".equals(key) || "Authorization".equals(key)))
                throw new IllegalArgumentException(PRIVATE + value);
            super.setRequestProperty(key, value);
        }
        @Override public void connect() { throw new AssertionError("Unexpected explicit connect"); }
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() { disconnects++; }
        @Override public OutputStream getOutputStream() throws IOException {
            outputCalls++;
            if (timeout) throw new SocketTimeoutException(PRIVATE + KEY);
            return new OutputStream() {
                @Override public void write(int value) { output.write(value); }
                @Override public void write(byte[] bytes, int offset, int length) { output.write(bytes, offset, length); }
                @Override public void close() { outputClosed=true; }
            };
        }
        @Override public int getResponseCode() {
            check(!getInstanceFollowRedirects(), "request cannot follow a credential-bearing redirect"); return code;
        }
        @Override public InputStream getInputStream() {
            inputCalls++;
            return new ByteArrayInputStream(response) { @Override public void close() { inputClosed=true; } };
        }
        @Override public InputStream getErrorStream() {
            errorReads++; throw new AssertionError("Provider error text must not be read");
        }
    }
}
