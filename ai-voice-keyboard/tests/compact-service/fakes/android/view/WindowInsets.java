package android.view;
public class WindowInsets {
public static class Type { public static int navigationBars() { return 1; } }
public static class Insets { public int bottom; }
public Insets getInsets(int type) { return new Insets(); }
public int getSystemWindowInsetBottom() { return 0; }
}
