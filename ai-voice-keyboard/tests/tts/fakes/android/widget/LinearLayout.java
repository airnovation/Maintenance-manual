package android.widget;
import android.content.Context;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
public class LinearLayout extends View {
    public static final int VERTICAL = 1, HORIZONTAL = 0;
    public void setOrientation(int orientation) { }
    public void removeAllViews() { for (View child : children) child.parent = null; children.clear(); }
    public final List<View> children = new ArrayList<>();
    public LinearLayout(Context context) { super(context); }
    public void addView(View child) { children.add(child); child.parent = this; }
    public void addView(View child, LayoutParams params) { addView(child); }
    public static class LayoutParams {
        public LayoutParams(int width, int height) { }
        public LayoutParams(int width, int height, float weight) { }
    }
}
