package android.widget;
import android.content.Context;
import android.view.View;
public class TextView extends View {
    public static int defaultTextColor;
    private CharSequence text = "";
    private int textColor = defaultTextColor;
    public int textColorChanges, maxLines;
    public Object movement, ellipsize;
    public void setMaxLines(int value) { maxLines=value; }
    public void setMovementMethod(Object value) { movement=value; }
    public void setEllipsize(Object value) { ellipsize=value; }

    public TextView(Context context) { super(context); }
    public void setTextSize(float size) { }
    public void setTextIsSelectable(boolean value) { }
    public void setTextColor(int color) { textColor = color; textColorChanges++; }
    public int getCurrentTextColor() { return textColor; }
    public void setText(CharSequence value) { text = value; }
    public CharSequence getText() { return text; }
}
