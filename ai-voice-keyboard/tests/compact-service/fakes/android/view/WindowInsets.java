package android.view;
public class WindowInsets {
public int bottom;
public static class Type { public static int navigationBars() { return 1; } }
public static class Insets { public int bottom; }
public Insets getInsets(int type) { Insets insets=new Insets();insets.bottom=bottom;return insets; }
public int getSystemWindowInsetBottom() { return bottom; }
}
