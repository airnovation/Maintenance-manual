package com.airnovation.aivoicekeyboard;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Button;

/** Local, presentation-only colors and shapes. No editor, audio, API or settings access. */
final class KeyboardAppearance {
    enum Role { MIC, STOP, NEUTRAL }
    private static final int[][] STATES = {
            {-android.R.attr.state_enabled}, {android.R.attr.state_pressed},
            {android.R.attr.state_focused}, {}};

    static final class Colors {
        final int fill, pressed, disabled, text, disabledText;
        Colors(int fill, int pressed, int disabled, int text, int disabledText) {
            this.fill = fill; this.pressed = pressed; this.disabled = disabled;
            this.text = text; this.disabledText = disabledText;
        }
    }
    static final class Palette {
        final int surface, statusText;
        final Colors mic, stop, neutral;
        Palette(int surface, int statusText, Colors mic, Colors stop, Colors neutral) {
            this.surface = surface; this.statusText = statusText;
            this.mic = mic; this.stop = stop; this.neutral = neutral;
        }
        Colors colors(Role role) { return role == Role.MIC ? mic : role == Role.STOP ? stop : neutral; }
    }
    static final Palette LIGHT = new Palette(0xffeef0f3, 0xff3e4857,
            new Colors(0xff1e5fb6, 0xff174a91, 0xffd3ddeb, 0xffffffff, 0xff526175),
            new Colors(0xffaf473d, 0xff89362f, 0xffe8dad7, 0xffffffff, 0xff71544f),
            new Colors(0xffd8dee7, 0xffc7d0dd, 0xffe1e5eb, 0xff263445, 0xff5c6674));
    static final Palette DARK = new Palette(0xff1c2026, 0xffdce3ed,
            new Colors(0xff80b5ff, 0xffa7ccff, 0xff303e50, 0xff10243c, 0xffa0adbf),
            new Colors(0xffffa194, 0xffffc0b6, 0xff4a3534, 0xff411c18, 0xffc3a6a1),
            new Colors(0xff363e49, 0xff485360, 0xff2b323c, 0xffe4e9f1, 0xff9ca7b6));

    private KeyboardAppearance() { }
    static Palette forConfiguration(Configuration configuration) {
        return (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                ? DARK : LIGHT;
    }
    static void style(Button button, Palette palette, Role role, float density) {
        if (button == null) return;
        Colors colors = palette.colors(role);
        StateListDrawable background = new StateListDrawable();
        int[] fills = {colors.disabled, colors.pressed, colors.pressed, colors.fill};
        for (int i = 0; i < STATES.length; i++) {
            GradientDrawable shape = new GradientDrawable();
            shape.setColor(fills[i]);
            shape.setCornerRadius(12 * density);
            // Internal insets give every key the same visual gap without shrinking its
            // full button touch target or adding any row margin/keyboard height.
            background.addState(STATES[i], new InsetDrawable(shape,
                    Math.round(3 * density), Math.round(6 * density),
                    Math.round(3 * density), Math.round(6 * density)));
        }
        int left = button.getPaddingLeft(), top = button.getPaddingTop();
        int right = button.getPaddingRight(), bottom = button.getPaddingBottom();
        button.setBackgroundTintList(null);
        button.setBackground(background);
        button.setPadding(left, top, right, bottom); // Preserve theme touch/text geometry.
        button.setStateListAnimator(null);
        button.setElevation(0);
        ColorStateList text = new ColorStateList(STATES,
                new int[]{colors.disabledText, colors.text, colors.text, colors.text});
        button.setTextColor(text);
        if (role != Role.NEUTRAL) {
            Drawable icon = new VoiceIcon(role == Role.STOP, text, Math.round(18 * density));
            button.setCompoundDrawablePadding(Math.round(6 * density));
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
        }
    }

    /** Exact fixed-copy shortening only; provider bodies or arbitrary exception text never enter here. */
    static String statusCopy(String safe) {
        if (safe.equals("Paste and save your provider API key in Settings first."))
            return "请先在设置中粘贴并保存服务商API密钥。";
        if (safe.equals("API key format looks wrong. Paste only the key, without spaces, line breaks, quotes or error text."))
            return "API密钥格式有误。\n仅粘贴密钥，勿含空白、换行、引号或错误文字。";
        for (int code : new int[]{401, 403}) {
            if (safe.equals("API access was refused (HTTP " + code
                    + "). Check the key and provider permissions in Settings."))
                return "API访问被拒绝（HTTP " + code + "）。\n请在设置检查密钥及服务商权限。";
        }
        if (safe.equals("API quota or rate limit reached (HTTP 429). Check billing/quota or try later."))
            return "额度或速率限制（HTTP 429）。\n请检查账单/额度，或稍后重试。";
        if (safe.equals("A secure connection could not be established. Check your network and device time."))
            return "安全连接失败。请检查网络及设备时间。";
        if (safe.equals("The configured AI models are unavailable. Check provider access in Settings."))
            return "当前AI模型不可用。请在设置检查服务商访问权限。";
        return safe;
    }

    /** Monochrome microphone / stop mark. Text labels stay present; meaning never relies on color. */
    private static final class VoiceIcon extends Drawable {
        private final boolean stop;
        private final ColorStateList colors;
        private final int size;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        VoiceIcon(boolean stop, ColorStateList colors, int size) {
            this.stop = stop; this.colors = colors; this.size = size;
        }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) { invalidateSelf(); return true; }
        @Override public void draw(Canvas canvas) {
            paint.setColor(colors.getColorForState(getState(), colors.getDefaultColor()));
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            paint.setStyle(Paint.Style.FILL);
            if (stop) canvas.drawRoundRect(5, 5, 19, 19, 2, 2, paint);
            else {
                canvas.drawRoundRect(8, 2, 16, 14, 4, 4, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawArc(5, 7, 19, 19, 0, 180, false, paint);
                canvas.drawLine(12, 19, 12, 22, paint);
                canvas.drawLine(8, 22, 16, 22, paint);
            }
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
