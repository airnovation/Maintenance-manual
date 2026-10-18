package android.widget;
import android.content.Context;
import android.view.View;
public class TextView extends View {
    public static int defaultTextColor;
    private CharSequence text = "";
    private int textColor = defaultTextColor;
    private android.content.res.ColorStateList colors;
    private final android.graphics.drawable.Drawable[] drawables=new android.graphics.drawable.Drawable[4];
    public float textSize; public int drawablePadding;
    public void setCompoundDrawablePadding(int value){drawablePadding=value;}
    public android.graphics.drawable.Drawable[] getCompoundDrawablesRelative(){return drawables;}
    public void setCompoundDrawablesRelativeWithIntrinsicBounds(android.graphics.drawable.Drawable left,android.graphics.drawable.Drawable top,android.graphics.drawable.Drawable right,android.graphics.drawable.Drawable bottom){drawables[0]=left;drawables[1]=top;drawables[2]=right;drawables[3]=bottom;for(android.graphics.drawable.Drawable d:drawables)if(d!=null)d.setBounds(0,0,d.getIntrinsicWidth(),d.getIntrinsicHeight());refreshDrawableState();}
    @Override protected void refreshDrawableState(){super.refreshDrawableState();if(drawables!=null)for(android.graphics.drawable.Drawable d:drawables)if(d!=null)d.setState(getDrawableState());}
    public void setTextColor(android.content.res.ColorStateList value){colors=value;textColorChanges++;}

    public int textColorChanges, maxLines;
    public Object movement, ellipsize;
    public void setMaxLines(int value) { maxLines=value; }
    public void setMovementMethod(Object value) { movement=value; }
    public void setEllipsize(Object value) { ellipsize=value; }

    public TextView(Context context) { super(context); }
    public void setTextSize(float size) { textSize=size; }
    public void setTextIsSelectable(boolean value) { }
    public void setTextColor(int color) { textColor = color; textColorChanges++; }
    public int getCurrentTextColor() { return colors==null?textColor:colors.getColorForState(getDrawableState(),colors.getDefaultColor()); }
    public void setText(CharSequence value) { text = value; }
    public CharSequence getText() { return text; }
}
