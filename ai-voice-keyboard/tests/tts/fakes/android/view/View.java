package android.view;
import android.content.Context;
public class View {
    public static final int VISIBLE = 0, GONE = 8;
    public static int defaultBackgroundColor;
    private OnClickListener click;
    public int visibility;
    public View parent;
    public int backgroundColor = defaultBackgroundColor, backgroundColorChanges;
    public interface OnClickListener { void onClick(View view); }
    public View(Context context) { }
    public void setVisibility(int value) { visibility = value; }
    public int getVisibility() { return visibility; }
    public boolean isShown() { return visibility == VISIBLE && (parent == null || parent.isShown()); }
    public void setBackgroundColor(int color) { backgroundColor = color; backgroundColorChanges++; }
    public void setOnClickListener(OnClickListener listener) { click = listener; }
    public boolean performClick() { if (click == null) return false; click.onClick(this); return true; }
}
