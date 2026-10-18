package android.view;
import android.content.Context;
public class View {
    public static final int VISIBLE = 0, GONE = 8;
    public static int defaultBackgroundColor;
    public static final int IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS = 8;
    public boolean saveEnabled = true;
    public int importantForAutofill;
    public boolean focusable=true, focusableInTouch=true, verticalScrollBar;
    public int padLeft,padTop,padRight,padBottom;
    public int getPaddingLeft(){return padLeft;} public int getPaddingTop(){return padTop;}
    public int getPaddingRight(){return padRight;} public int getPaddingBottom(){return padBottom;}
    public int minimumHeight; public int getMinimumHeight(){return minimumHeight;}
    public CharSequence contentDescription;
    public void setContentDescription(CharSequence value){contentDescription=value;}
    public CharSequence getContentDescription(){return contentDescription;}
    public float elevation; public void setElevation(float value){elevation=value;}
    public void setStateListAnimator(Object value){}
    private android.graphics.drawable.Drawable background;
    public void setBackground(android.graphics.drawable.Drawable value){
        background=value;refreshDrawableState();
        // Android background replacement may change View padding. Model that side effect
        // so production's save/restore contract is tested rather than silently assumed.
        if(value!=null && value.getCurrent() instanceof android.graphics.drawable.InsetDrawable inset)
            setPadding(inset.left,inset.top,inset.right,inset.bottom);
    }
    public android.graphics.drawable.Drawable getBackground(){return background;}
    public boolean pressed,focused;
    public void setPressed(boolean value){pressed=value;refreshDrawableState();}
    public void setFocused(boolean value){focused=value;refreshDrawableState();}
    public int[] getDrawableState(){java.util.List<Integer> s=new java.util.ArrayList<>();if(enabled)s.add(android.R.attr.state_enabled);if(pressed)s.add(android.R.attr.state_pressed);if(focused)s.add(android.R.attr.state_focused);return s.stream().mapToInt(Integer::intValue).toArray();}
    protected void refreshDrawableState(){if(background!=null)background.setState(getDrawableState());}

    public void setPadding(int l,int t,int r,int b) { padLeft=l;padTop=t;padRight=r;padBottom=b; }
    public void setFocusable(boolean value) { focusable=value; }
    public void setFocusableInTouchMode(boolean value) { focusableInTouch=value; }
    public void setVerticalScrollBarEnabled(boolean value) { verticalScrollBar=value; }
    public void scrollTo(int x,int y) { }
    public interface OnApplyWindowInsetsListener { WindowInsets apply(View view,WindowInsets insets); }
    public OnApplyWindowInsetsListener insetsListener;
    public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener listener) { insetsListener=listener; }

    public void setSaveEnabled(boolean value) { saveEnabled = value; }
    public void setImportantForAutofill(int value) { importantForAutofill = value; }
    private OnClickListener click;
    private boolean enabled = true;
    public void setEnabled(boolean value) { enabled = value; refreshDrawableState(); }
    public boolean isEnabled() { return enabled; }
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
    public boolean performClick() { if (!enabled || click == null) return false; click.onClick(this); return true; }
}
