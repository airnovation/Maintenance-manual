package android.graphics.drawable;
public class InsetDrawable extends Drawable {
 private final Drawable drawable;public final int left,top,right,bottom;
 public InsetDrawable(Drawable drawable,int l,int t,int r,int b){this.drawable=drawable;left=l;top=t;right=r;bottom=b;}
 public Drawable getDrawable(){return drawable;}
}
