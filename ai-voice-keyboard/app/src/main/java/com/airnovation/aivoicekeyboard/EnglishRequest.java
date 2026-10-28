package com.airnovation.aivoicekeyboard;

import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One cancellable EN request. Cancellation cannot recall bytes already uploaded. */
public final class EnglishRequest {
    public enum Provider { GEMINI, OPENAI }
    public enum Outcome { PENDING, SUCCESS, FAILED, CANCELLED, BUSY }

    /** Content-free, monotonic timings. No message, key, URL or exception is retained. */
    public static final class Timing {
        public final Provider provider;
        public final Outcome outcome;
        public final int attempts;
        public final long queueMillis, requestMillis, callbackWaitMillis, totalMillis;
        private Timing(Provider provider, Outcome outcome, int attempts, long queueMillis,
                long requestMillis, long callbackWaitMillis, long totalMillis) {
            this.provider = provider; this.outcome = outcome; this.attempts = attempts;
            this.queueMillis = queueMillis; this.requestMillis = requestMillis;
            this.callbackWaitMillis = callbackWaitMillis; this.totalMillis = totalMillis;
        }
    }

    static final String BUSY_ERROR = "The previous AI request is still closing. Please try again shortly.";
    private static final ExecutorService CLEANUP = newExecutor("en-translation-close");
    private static volatile Timing lastTiming;
    private final Provider provider;
    private final long created = System.nanoTime();
    private long started = -1, attemptStarted = -1, requestNanos, workFinished = -1, finished = -1;
    private int attempts;
    private Outcome outcome = Outcome.PENDING;
    private boolean cancelled, delivered;
    private FutureTask<Void> future;
    private HttpURLConnection connection;

    EnglishRequest(Provider provider) { this.provider = provider; }

    /** Two bounded workers, zero backlog: one cancelled socket cannot queue the next EN tap. */
    static ExecutorService newExecutor() { return newExecutor("en-translation"); }

    private static ExecutorService newExecutor(String threadName) {
        return new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    void execute(ExecutorService executor, Runnable work, Runnable rejected) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            synchronized (this) {
                if (cancelled) return null;
                started = System.nanoTime();
            }
            work.run();
            return null;
        });
        synchronized (this) {
            future = task;
            if (cancelled) { task.cancel(true); return; }
        }
        try { executor.execute(task); }
        catch (RejectedExecutionException ignored) {
            task.cancel(false);
            finishWork(Outcome.BUSY);
            rejected.run();
        }
    }

    /** Idempotent; suppresses callbacks and requests I/O closure without blocking the IME thread.
     * Socket implementations may ignore interruption/closure; workers remain bounded.
     */
    public void cancel() {
        FutureTask<Void> task;
        HttpURLConnection active;
        synchronized (this) {
            if (cancelled || delivered) return;
            cancelled = true;
            task = future; future = null;
            active = connection; connection = null;
            finishAttempt();
            outcome = Outcome.CANCELLED;
            finished = System.nanoTime();
            lastTiming = snapshot(finished);
        }
        if (task != null) task.cancel(true);
        if (active != null) {
            try { CLEANUP.execute(() -> disconnect(active)); }
            catch (RejectedExecutionException ignored) {
                // Two closes are already stuck: do not queue, block the UI or spawn more threads.
                // The interrupted worker still owns its finally/detach and finite socket timeouts.
            }
        }
    }

    synchronized void check() throws Cancelled {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new Cancelled();
    }

    void attach(HttpURLConnection active) throws Cancelled {
        synchronized (this) {
            if (!cancelled && !Thread.currentThread().isInterrupted()) {
                connection = active; attemptStarted = System.nanoTime(); attempts++;
                return;
            }
        }
        disconnect(active);
        throw new Cancelled();
    }

    void detach(HttpURLConnection active) {
        synchronized (this) {
            if (connection == active) { connection = null; finishAttempt(); }
        }
        disconnect(active);
    }

    synchronized void finishWork(Outcome result) {
        future = null;
        if (!cancelled) { outcome = result; workFinished = System.nanoTime(); }
    }

    void deliver(Runnable callback) {
        synchronized (this) {
            if (cancelled || delivered) return;
            delivered = true;
            finished = System.nanoTime();
            lastTiming = snapshot(finished);
        }
        callback.run();
    }

    /** In-memory only; intended for tests/debugger diagnostics, not translation-quality claims. */
    public synchronized Timing timing() { return snapshot(finished < 0 ? System.nanoTime() : finished); }
    public static Timing lastTiming() { return lastTiming; }

    private Timing snapshot(long now) {
        long request = requestNanos + (attemptStarted < 0 ? 0 : now - attemptStarted);
        return new Timing(provider, outcome, attempts, millis((started < 0 ? now : started) - created),
                millis(request), millis(workFinished < 0 ? 0 : now - workFinished), millis(now - created));
    }
    private void finishAttempt() {
        if (attemptStarted >= 0) { requestNanos += System.nanoTime() - attemptStarted; attemptStarted = -1; }
    }
    private static long millis(long nanos) { return Math.max(0, TimeUnit.NANOSECONDS.toMillis(nanos)); }
    private static void disconnect(HttpURLConnection active) {
        if (active != null) try { active.disconnect(); } catch (RuntimeException ignored) { }
    }
    static final class Cancelled extends Exception { }
}
