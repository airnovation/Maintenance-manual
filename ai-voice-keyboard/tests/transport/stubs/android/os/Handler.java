package android.os;

import java.util.concurrent.ConcurrentLinkedQueue;

/** Deliberately queued callbacks: tests decide exactly when the UI thread runs. */
public final class Handler {
    private static final ConcurrentLinkedQueue<Runnable> MAIN = new ConcurrentLinkedQueue<>();
    public Handler(Looper looper) { }
    public boolean post(Runnable task) { MAIN.add(task); return true; }
    public static int pendingCount() { return MAIN.size(); }
    public static void drain() {
        Runnable task;
        while ((task = MAIN.poll()) != null) task.run();
    }
}
