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
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Real EN clients and lifecycle handle; fake HTTPS/Android/fixture DOM only. */
public final class EnglishTransportTest {
    private static final String KEY = "SYNTHETIC-ONLY-NOT-A-REAL-KEY";
    private static final String PRIVATE = "SYNTHETIC-PRIVATE-TEXT";
    private static final Network NETWORK = new Network();
    private static int checks, cases, fixtures;
    private enum Route { GEMINI, OPENAI }
    private static final ThreadPoolExecutor[] POOLS = {
        executor(GeminiEnglish.class, "EXECUTOR"), executor(OpenAiEnglish.class, "EXECUTOR")
    };

    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                if (!"https".equals(protocol)) throw new AssertionError("Real network blocked");
                return NETWORK.open(url);
            }
        });
        try {
            testCancelBeforeStart(); testCancelBeforeAttach(); testTimingShape(); testBoundedNonblockingCleanup();
            for (Route route : Route.values()) {
                testSuccess(route); testPendingCallbackCancelled(route);
                testCancelledSocketDoesNotQueueNext(route); testBoundedSaturation(route);
                testTimeout(route); testInvalidKey(route); testIncomplete(route);
                testNon404DoesNotFallback(route);
            }
            test404Fallback(); test404Exhaustion();
            System.out.println("PASS " + cases + " EN transport cases / " + checks + " checks (offline only)");
        } finally {
            for (Connection connection : NETWORK.opened) { connection.release.countDown(); connection.closeRelease.countDown(); }
            executor(EnglishRequest.class, "CLEANUP").shutdownNow();
            for (ExecutorService pool : POOLS) pool.shutdownNow();
            for (ExecutorService pool : POOLS)
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) throw new AssertionError("worker did not close");
        }
    }

    private static void testCancelBeforeStart() {
        cases++;
        ManualExecutor executor = new ManualExecutor();
        EnglishRequest request = new EnglishRequest(EnglishRequest.Provider.GEMINI);
        int[] work = {0}, rejected = {0};
        request.execute(executor, () -> work[0]++, () -> rejected[0]++);
        request.cancel(); request.cancel(); executor.runAll();
        check(work[0] == 0 && rejected[0] == 0, "cancelled queued work never starts");
        check(request.timing().outcome == EnglishRequest.Outcome.CANCELLED, "queued cancel timing");
        check(request.timing().attempts == 0, "queued cancel has no request");
    }
    private static void testCancelBeforeAttach() throws Exception {
        cases++;
        EnglishRequest request = new EnglishRequest(EnglishRequest.Provider.OPENAI);
        Connection connection = new Connection(200, "unused");
        request.cancel();
        try { request.attach(connection); throw new AssertionError("cancelled attach accepted"); }
        catch (EnglishRequest.Cancelled expected) { }
        check(connection.disconnects == 1, "late connection immediately disconnected");
        int[] delivered = {0}; request.deliver(() -> delivered[0]++);
        check(delivered[0] == 0, "cancelled request cannot deliver");
    }
    private static void testTimingShape() {
        cases++;
        for (Field field : EnglishRequest.Timing.class.getDeclaredFields()) {
            Class<?> type = field.getType();
            check(type == long.class || type == int.class || type == EnglishRequest.Provider.class
                    || type == EnglishRequest.Outcome.class, "timing can contain only enums/numbers");
        }
    }
    private static void testBoundedNonblockingCleanup() throws Exception {
        cases++;
        ThreadPoolExecutor cleanup = executor(EnglishRequest.class, "CLEANUP");
        await(() -> cleanup.getActiveCount() == 0, "cleanup initially idle");
        List<Connection> blocked = new ArrayList<>();
        List<EnglishRequest> requests = new ArrayList<>();
        try {
            for (int i = 0; i < 3; i++) {
                Connection connection = new Connection(200, "unused"); connection.blockDisconnect = true;
                EnglishRequest request = new EnglishRequest(EnglishRequest.Provider.OPENAI);
                request.attach(connection); blocked.add(connection); requests.add(request);
                CountDownLatch returned = new CountDownLatch(1);
                Thread cancel = new Thread(() -> { request.cancel(); returned.countDown(); }); cancel.setDaemon(true); cancel.start();
                check(returned.await(5, TimeUnit.SECONDS), "cancel returns even with blocked disconnect");
                if (i < 2) check(connection.closeEntered.await(5, TimeUnit.SECONDS), "bounded async close started");
            }
            check(cleanup.getPoolSize() == 2 && cleanup.getQueue().isEmpty(), "cleanup has two workers and zero backlog");
            check(blocked.get(2).disconnects == 0, "saturated cleanup does not close on caller thread");
        } finally {
            for (Connection connection : blocked) connection.closeRelease.countDown();
        }
        await(() -> cleanup.getActiveCount() == 0, "cleanup workers released");
        // The normal worker finally path remains able to close a request rejected by cleanup.
        requests.get(2).detach(blocked.get(2));
        check(blocked.get(2).disconnects > 0, "worker finally still owns deferred cleanup");
    }
    private static void testSuccess(Route route) throws Exception {
        begin(); Connection connection = NETWORK.add(200, success(route, "Synthetic complete output"));
        Result result = new Result(); EnglishRequest request = call(route, KEY, result);
        finish(result);
        check(result.calls == 1 && result.error == null && "Synthetic complete output".equals(result.text), "one complete result");
        check(!connection.getInstanceFollowRedirects(), "credential redirects disabled");
        check(connection.getConnectTimeout() == (route == Route.GEMINI ? 15000 : 20000)
                && connection.getReadTimeout() == 40000, "timeouts preserved");
        check(connection.output.toString(StandardCharsets.UTF_8).contains(EnglishRewritePolicy.INSTRUCTION), "shared policy used");
        check(connection.output.toString(StandardCharsets.UTF_8).contains(PRIVATE), "explicit input preserved");
        check(!connection.output.toString(StandardCharsets.UTF_8).contains(KEY), "key absent from body");
        check(route != Route.OPENAI || connection.output.toString(StandardCharsets.UTF_8).contains("store=false"), "OpenAI storage disabled");
        assertTiming(request, EnglishRequest.Outcome.SUCCESS, 1);
        request.cancel();
        check(request.timing().outcome == EnglishRequest.Outcome.SUCCESS, "reset after delivered success does not relabel it cancelled");
        verify(1);
    }
    private static void testPendingCallbackCancelled(Route route) throws Exception {
        begin(); NETWORK.add(200, success(route, "Do not insert this"));
        Result result = new Result(); EnglishRequest request = call(route, KEY, result);
        await(() -> Handler.pendingCount() == 1, "completed response posted to main");
        request.cancel(); request.cancel(); Handler.drain(); idle();
        check(result.calls == 0, "cancel after post suppresses callback");
        assertTiming(request, EnglishRequest.Outcome.CANCELLED, 1); verify(1);
    }
    private static void testCancelledSocketDoesNotQueueNext(Route route) throws Exception {
        begin();
        // Deliberately hostile fake: it ignores interruption and disconnect until explicitly released.
        Connection old = NETWORK.add(route == Route.GEMINI ? 404 : 200, success(route, "Stale output")); old.blocked = true;
        Result stale = new Result(); EnglishRequest abandoned = call(route, KEY, stale);
        check(old.entered.await(5, TimeUnit.SECONDS), "old request is waiting for response");
        old.blockDisconnect = true;
        abandoned.cancel();
        check(old.closeEntered.await(5, TimeUnit.SECONDS), "cancel schedules disconnect off the caller thread");
        check(old.closeRelease.getCount() == 1, "disconnect still blocked after cancel returned");
        await(() -> old.interrupted, "reset interrupts worker");
        NETWORK.add(200, success(route, "New output"));
        Result current = new Result(); EnglishRequest next = call(route, KEY, current);
        await(() -> Handler.pendingCount() > 0, "new response arrives while old socket remains blocked");
        Handler.drain();
        check(current.calls == 1 && "New output".equals(current.text) && current.error == null, "new EN does not wait behind abandoned socket");
        check(old.release.getCount() == 1, "old socket is still blocked when new EN completes");
        old.closeRelease.countDown(); old.release.countDown(); idle(); Handler.drain();
        check(stale.calls == 0, "old result never delivered");
        assertTiming(abandoned, EnglishRequest.Outcome.CANCELLED, 1);
        assertTiming(next, EnglishRequest.Outcome.SUCCESS, 1);
        verify(2); // For Gemini, a cancelled late 404 cannot start a fallback request.
    }
    private static void testBoundedSaturation(Route route) throws Exception {
        begin();
        Connection first = NETWORK.add(200, success(route, "Old one")); first.blocked = true;
        EnglishRequest a = call(route, KEY, new Result());
        check(first.entered.await(5, TimeUnit.SECONDS), "first blocked"); a.cancel();
        Connection second = NETWORK.add(200, success(route, "Old two")); second.blocked = true;
        EnglishRequest b = call(route, KEY, new Result());
        check(second.entered.await(5, TimeUnit.SECONDS), "second blocked"); b.cancel();
        Result rejected = new Result(); EnglishRequest third = call(route, KEY, rejected);
        Handler.drain();
        check(rejected.calls == 1 && rejected.text == null && EnglishRequest.BUSY_ERROR.equals(rejected.error), "bounded overload returns fixed retry message, not backlog");
        assertTiming(third, EnglishRequest.Outcome.BUSY, 0);
        check(NETWORK.opened.size() == 2 && pool(route).getQueue().isEmpty(), "no extra request or queued payload");
        first.release.countDown(); second.release.countDown(); idle(); Handler.drain();
        NETWORK.add(200, success(route, "Recovered"));
        Result recovered = new Result(); call(route, KEY, recovered); finish(recovered);
        check(recovered.calls == 1 && recovered.error == null && "Recovered".equals(recovered.text), "fresh EN works after cancelled sockets close");
        verify(3);
    }
    private static void testTimeout(Route route) throws Exception {
        begin(); Connection connection = NETWORK.add(200, "unused"); connection.timeout = true;
        Result result = new Result(); EnglishRequest request = call(route, KEY, result); finish(result);
        check(result.calls == 1 && result.text == null && result.error.contains("timed out"), "timeout reported safely");
        safe(result.error); assertTiming(request, EnglishRequest.Outcome.FAILED, 1); verify(1);
    }
    private static void testInvalidKey(Route route) throws Exception {
        begin(); Result result = new Result(); EnglishRequest request = call(route, KEY + "\n", result); finish(result);
        check(result.calls == 1 && result.text == null && result.error != null, "invalid key rejected locally");
        safe(result.error); assertTiming(request, EnglishRequest.Outcome.FAILED, 0); verify(0);
    }
    private static void testIncomplete(Route route) throws Exception {
        begin(); NETWORK.add(200, fixture(route == Route.GEMINI ? gemini("MAX_TOKENS", "Cut off") : openai("incomplete", "Cut off")));
        Result result = new Result(); EnglishRequest request = call(route, KEY, result); finish(result);
        check(result.calls == 1 && result.text == null && result.error != null, "incomplete response rejected");
        safe(result.error); assertTiming(request, EnglishRequest.Outcome.FAILED, 1); verify(1);
    }
    private static void testNon404DoesNotFallback(Route route) throws Exception {
        begin(); NETWORK.add(429, PRIVATE + KEY);
        Result result = new Result(); call(route, KEY, result); finish(result);
        check(result.error.contains("HTTP 429") && result.text == null, "non-404 failure returned without fallback"); safe(result.error); verify(1);
    }
    private static void test404Fallback() throws Exception {
        begin(); NETWORK.add(404, PRIVATE + KEY); NETWORK.add(200, success(Route.GEMINI, "Fallback output"));
        Result result = new Result(); EnglishRequest request = call(Route.GEMINI, KEY, result); finish(result);
        check("Fallback output".equals(result.text) && result.error == null, "404 falls back once");
        check(NETWORK.opened.get(0).getURL().toString().contains("gemini-3.5-flash-lite")
                && NETWORK.opened.get(1).getURL().toString().contains("gemini-3.8-flash"), "existing model order preserved");
        assertTiming(request, EnglishRequest.Outcome.SUCCESS, 2); verify(2);
    }
    private static void test404Exhaustion() throws Exception {
        begin(); for (int i = 0; i < 3; i++) NETWORK.add(404, PRIVATE);
        Result result = new Result(); EnglishRequest request = call(Route.GEMINI, KEY, result); finish(result);
        check(result.text == null && result.error.contains("unavailable"), "all existing fallback models exhausted");
        check(NETWORK.opened.get(2).getURL().toString().contains("gemini-3.6-flash"), "third model unchanged");
        assertTiming(request, EnglishRequest.Outcome.FAILED, 3); verify(3);
    }

    private static EnglishRequest call(Route route, String key, Result result) {
        return route == Route.GEMINI ? GeminiEnglish.rewrite(key, PRIVATE, result::done)
                : OpenAiEnglish.rewrite(key, PRIVATE, result::done);
    }
    private static void begin() throws Exception {
        idle(); Handler.drain(); NETWORK.reset(); JSONObject.clearFixtures(); cases++;
    }
    private static void finish(Result result) throws Exception {
        await(() -> { Handler.drain(); return result.calls > 0; }, "callback delivered");
        idle(); Handler.drain();
    }
    private static void idle() throws Exception {
        await(() -> POOLS[0].getActiveCount() == 0 && POOLS[1].getActiveCount() == 0, "workers idle");
    }
    private static void await(BooleanSupplier condition, String label) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) throw new AssertionError(label);
            Thread.yield();
        }
    }
    private static void assertTiming(EnglishRequest request, EnglishRequest.Outcome expected, int attempts) {
        EnglishRequest.Timing timing = request.timing();
        check(timing.outcome == expected && timing.attempts == attempts, "fixed outcome and attempt count");
        check(timing.queueMillis >= 0 && timing.requestMillis >= 0 && timing.callbackWaitMillis >= 0
                && timing.totalMillis >= timing.queueMillis && timing.totalMillis >= timing.requestMillis
                && timing.totalMillis >= timing.callbackWaitMillis, "monotonic bounded timing fields");
    }
    private static void verify(int requests) {
        check(NETWORK.unexpected == null, "no unexpected HTTP access");
        check(NETWORK.opened.size() == requests && NETWORK.plans.isEmpty(), "exact fake request count");
        for (Connection connection : NETWORK.opened) {
            check(connection.disconnects > 0, "connection closed");
            check(connection.errorReads == 0, "no provider error-body reads");
        }
    }
    private static void safe(String error) { check(!error.contains(KEY) && !error.contains(PRIVATE), "no key/private exception leakage"); }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
    private static ThreadPoolExecutor pool(Route route) { return POOLS[route == Route.GEMINI ? 0 : 1]; }
    private static ThreadPoolExecutor executor(Class<?> type, String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return (ThreadPoolExecutor) field.get(null); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private static String success(Route route, String text) { return fixture(route == Route.GEMINI ? gemini("STOP", text) : openai("completed", text)); }
    private static String fixture(JSONObject value) {
        String body = "opaque-en-fixture-" + (++fixtures); JSONObject.registerFixture(body, value); return body;
    }
    private static JSONObject gemini(String reason, String text) {
        return new JSONObject().put("candidates", new JSONArray().put(new JSONObject().put("finishReason", reason)
                .put("content", new JSONObject().put("parts", new JSONArray()
                        .put(new JSONObject().put("thought", true).put("text", "Never insert hidden thought"))
                        .put(new JSONObject().put("text", text))))));
    }
    private static JSONObject openai(String status, String text) {
        return new JSONObject().put("status", status).put("output", new JSONArray().put(new JSONObject().put("type", "message")
                .put("content", new JSONArray().put(new JSONObject().put("type", "output_text").put("text", text)))));
    }
    private static final class Result {
        int calls; String text, error;
        void done(String text, String error) { this.text = text; this.error = error; calls++; }
    }
    private static final class Network {
        final Queue<Connection> plans = new ConcurrentLinkedQueue<>();
        final List<Connection> opened = new CopyOnWriteArrayList<>();
        volatile AssertionError unexpected;
        Connection add(int code, String body) throws Exception { Connection c = new Connection(code, body); plans.add(c); return c; }
        Connection open(URL url) {
            Connection connection = plans.poll();
            if (connection == null) { unexpected = new AssertionError("Unexpected network; no real connection allowed"); throw unexpected; }
            connection.target(url); opened.add(connection); return connection;
        }
        void reset() {
            if (unexpected != null) throw unexpected;
            check(plans.isEmpty(), "no unused network fixture"); opened.clear();
        }
    }
    private static final class Connection extends HttpURLConnection {
        final int code; final byte[] body;
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final CountDownLatch closeEntered = new CountDownLatch(1), closeRelease = new CountDownLatch(1);
        volatile boolean blocked, timeout, interrupted, blockDisconnect;
        volatile int disconnects, errorReads;
        Connection(int code, String body) throws Exception { super(new URL("https://synthetic.invalid/")); this.code = code; this.body = body.getBytes(StandardCharsets.UTF_8); }
        void target(URL target) { url = target; }
        @Override public void connect() { throw new AssertionError("Unexpected explicit connect"); }
        @Override public boolean usingProxy() { return false; }
        @Override public void disconnect() {
            disconnects++; closeEntered.countDown();
            if (blockDisconnect) {
                boolean released = false;
                while (!released) try { closeRelease.await(); released = true; }
                catch (InterruptedException ignored) { }
            }
        }
        @Override public OutputStream getOutputStream() throws IOException {
            if (timeout) throw new SocketTimeoutException(KEY + PRIVATE);
            return output;
        }
        @Override public int getResponseCode() {
            entered.countDown();
            if (blocked) {
                boolean released = false;
                while (!released) try { release.await(); released = true; }
                catch (InterruptedException ignored) { interrupted = true; }
            }
            return code;
        }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(body); }
        @Override public InputStream getErrorStream() { errorReads++; throw new AssertionError("Error body must remain private"); }
    }
    private static final class ManualExecutor extends AbstractExecutorService {
        final List<Runnable> queued = new ArrayList<>();
        public void execute(Runnable task) { queued.add(task); }
        void runAll() { for (Runnable task : queued) task.run(); queued.clear(); }
        public void shutdown() { }
        public List<Runnable> shutdownNow() { return new ArrayList<>(); }
        public boolean isShutdown() { return false; }
        public boolean isTerminated() { return false; }
        public boolean awaitTermination(long value, TimeUnit unit) { return true; }
    }
}
