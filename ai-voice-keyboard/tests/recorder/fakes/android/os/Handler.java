package android.os;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministic main-loop queue using Android's callback-identity removal semantics. */
public final class Handler {
    private static final List<Pending> QUEUE = new ArrayList<>();
    private static long sequence;
    private record Pending(Handler owner, Runnable runnable, long due, long order) { }

    public Handler(Looper looper) {
        if (looper != Looper.getMainLooper()) throw new AssertionError("Expected main looper");
    }
    public boolean postDelayed(Runnable runnable, long delay) {
        QUEUE.add(new Pending(this, runnable, SystemClock.elapsedRealtime() + delay, sequence++));
        return true;
    }
    public void removeCallbacks(Runnable runnable) {
        QUEUE.removeIf(pending -> pending.owner() == this && pending.runnable() == runnable);
    }
    public static int pendingCount() { return QUEUE.size(); }
    public static void reset() { QUEUE.clear(); sequence = 0; SystemClock.setTime(0); }
    public static void advanceBy(long milliseconds) {
        if (milliseconds < 0) throw new AssertionError("Cannot rewind time");
        long target = SystemClock.elapsedRealtime() + milliseconds;
        while (true) {
            Pending next = QUEUE.stream().filter(pending -> pending.due() <= target)
                    .min(Comparator.comparingLong(Pending::due).thenComparingLong(Pending::order))
                    .orElse(null);
            if (next == null) break;
            QUEUE.remove(next);
            SystemClock.setTime(next.due());
            next.runnable().run();
        }
        SystemClock.setTime(target);
    }
}
