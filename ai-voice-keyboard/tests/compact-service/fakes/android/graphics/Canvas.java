package android.graphics;
public class Canvas {
 public int roundRects,arcs,lines,color;
 public int save(){return 1;}public void restore(){}public void translate(float x,float y){}public void scale(float x,float y){}
 public void drawRoundRect(float l,float t,float r,float b,float rx,float ry,Paint paint){roundRects++;color=paint.color;}
 public void drawArc(float l,float t,float r,float b,float start,float sweep,boolean center,Paint paint){arcs++;color=paint.color;}
 public void drawLine(float x,float y,float a,float b,Paint paint){lines++;color=paint.color;}
}
