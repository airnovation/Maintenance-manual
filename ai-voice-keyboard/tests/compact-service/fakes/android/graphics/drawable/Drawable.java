package android.graphics.drawable;
import android.graphics.*;
public class Drawable {
 private int[] state={};private final Rect bounds=new Rect();
 public void draw(Canvas canvas){}public int getIntrinsicWidth(){return -1;}public int getIntrinsicHeight(){return -1;}
 public boolean isStateful(){return false;}protected boolean onStateChange(int[] value){return false;}public void invalidateSelf(){}
 public int[] getState(){return state;}public boolean setState(int[] value){state=value;return onStateChange(value);}
 public Rect getBounds(){return bounds;}public void setBounds(int l,int t,int r,int b){bounds.left=l;bounds.top=t;bounds.right=r;bounds.bottom=b;}
 public void setAlpha(int value){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return 0;}public Drawable getCurrent(){return this;}
}
