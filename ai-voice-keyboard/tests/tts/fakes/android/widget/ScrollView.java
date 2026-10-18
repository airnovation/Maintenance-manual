package android.widget;
import android.content.Context;
import android.view.View;
public class ScrollView extends View {
    public View child;
    public ScrollView(Context context) { super(context); }
    public void addView(View view) { child = view; view.parent = this; }
}
