package android.view;
import android.content.Context;
public class View {
    public static final int VISIBLE = 0, GONE = 8;
    private OnClickListener click;
    public int visibility;
    public interface OnClickListener { void onClick(View view); }
    public View(Context context) { }
    public void setVisibility(int value) { visibility = value; }
    public void setOnClickListener(OnClickListener listener) { click = listener; }
    public boolean performClick() { if (click == null) return false; click.onClick(this); return true; }
}
