package android.os;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
public class Handler {
    private record Task(Handler owner, Runnable action) { }
    private static final ArrayDeque<Task> posted = new ArrayDeque<>();
    private static final List<Task> delayed = new ArrayList<>();
    public Handler(Looper looper) { }
    public boolean post(Runnable action) { synchronized (posted) { posted.add(new Task(this, action)); } return true; }
    public boolean postDelayed(Runnable action, long milliseconds) { delayed.add(new Task(this, action)); return true; }
    public void removeCallbacks(Runnable action) {
        synchronized (posted) { posted.removeIf(t -> t.owner == this && t.action == action); }
        delayed.removeIf(t -> t.owner == this && t.action == action);
    }
    public static void drain() {
        for (int i = 0; i < 100; i++) {
            Task next;
            synchronized (posted) { next = posted.poll(); }
            if (next == null) return;
            next.action.run();
        }
        throw new AssertionError("Unbounded immediate callbacks");
    }
    public static void tick() {
        List<Task> snapshot = new ArrayList<>(delayed); delayed.clear();
        for (Task task : snapshot) task.action.run();
        drain();
    }
    public static int pending() { return posted.size() + delayed.size(); }
    public static void reset() { posted.clear(); delayed.clear(); }
}
