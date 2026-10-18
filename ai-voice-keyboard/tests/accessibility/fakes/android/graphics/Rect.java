package android.graphics;
public final class Rect {
    public int left, top, right, bottom;
    public Rect() { }
    public Rect(int l, int t, int r, int b) { set(l, t, r, b); }
    public Rect(Rect other) { set(other.left, other.top, other.right, other.bottom); }
    public void set(int l, int t, int r, int b) { left=l; top=t; right=r; bottom=b; }
    public int width() { return right-left; }
    public int height() { return bottom-top; }
    public boolean isEmpty() { return left >= right || top >= bottom; }
    public boolean contains(Rect r) { return !isEmpty() && left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom; }
    public static boolean intersects(Rect a, Rect b) { return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top; }
    public boolean intersect(Rect r) {
        if (!intersects(this, r)) return false;
        set(Math.max(left,r.left), Math.max(top,r.top), Math.min(right,r.right), Math.min(bottom,r.bottom));
        return true;
    }
    public String toShortString() { return "["+left+","+top+"]["+right+","+bottom+"]"; }
}
