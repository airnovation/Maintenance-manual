package android.graphics.drawable;
import java.util.*;import android.content.res.ColorStateList;
public class StateListDrawable extends Drawable {
 private final List<int[]> states=new ArrayList<>();private final List<Drawable> drawables=new ArrayList<>();
 public void addState(int[] state,Drawable drawable){states.add(state);drawables.add(drawable);}
 @Override public Drawable getCurrent(){for(int i=0;i<states.size();i++)if(ColorStateList.matches(states.get(i),getState()))return drawables.get(i);return null;}
}
