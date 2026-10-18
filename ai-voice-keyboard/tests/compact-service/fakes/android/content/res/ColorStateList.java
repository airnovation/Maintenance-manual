package android.content.res;
public class ColorStateList {
 private final int[][] states; private final int[] colors;
 public ColorStateList(int[][] states,int[] colors){this.states=states;this.colors=colors;}
 public static ColorStateList valueOf(int value){return new ColorStateList(new int[][]{{}},new int[]{value});}
 public int getDefaultColor(){return colors[colors.length-1];}
 public int getColorForState(int[] state,int fallback){for(int i=0;i<states.length;i++)if(matches(states[i],state))return colors[i];return fallback;}
 public static boolean matches(int[] specification,int[] state){for(int item:specification){boolean found=false;for(int s:state)if(s==Math.abs(item))found=true;if((item>0)!=found)return false;}return true;}
}
