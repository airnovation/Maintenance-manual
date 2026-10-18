package android.graphics;
public class Paint {
 public static final int ANTI_ALIAS_FLAG=1;public enum Style {FILL,STROKE}public enum Cap {ROUND}
 public int color,alpha=255;public Style style;
 public Paint(int flags){}public void setColor(int value){color=value;}public void setStyle(Style value){style=value;}
 public void setStrokeWidth(float value){}public void setStrokeCap(Cap value){}public void setAlpha(int value){alpha=value;}public void setColorFilter(ColorFilter value){}
}
