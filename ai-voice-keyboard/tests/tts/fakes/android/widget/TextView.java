package android.widget;
import android.content.Context;
import android.view.View;
public class TextView extends View {
    private CharSequence text = "";
    public TextView(Context context) { super(context); }
    public void setTextSize(float size) { }
    public void setTextIsSelectable(boolean value) { }
    public void setText(CharSequence value) { text = value; }
    public CharSequence getText() { return text; }
}
