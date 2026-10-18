package android.widget;
import android.content.Context;
public class CheckBox extends Button {
    private boolean checked;
    public CheckBox(Context context) { super(context); }
    public void setChecked(boolean value) { checked = value; }
    public boolean isChecked() { return checked; }
    @Override public boolean performClick() { checked = !checked; super.performClick(); return true; }
}
