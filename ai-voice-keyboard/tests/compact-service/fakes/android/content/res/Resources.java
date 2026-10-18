package android.content.res;
import android.util.DisplayMetrics;
public class Resources {
    public Configuration configuration = new Configuration();
    public Configuration getConfiguration() { return configuration; }
    private final DisplayMetrics metrics = new DisplayMetrics();
    public DisplayMetrics getDisplayMetrics() { return metrics; }
}
