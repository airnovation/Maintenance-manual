package android.os;

/** Virtual monotonic clock; tests never sleep. */
public final class SystemClock {
    private static long now;
    public static long elapsedRealtime() { return now; }
    public static void setTime(long value) { now = value; }
}
