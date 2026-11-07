package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

/**
 * Control Center's backlight tile (this TV): a sun, "Backlight", the level in percent and a wide bar, all drawn
 * here. Left and Right change the level in 5% steps (held, they keep going); frosted when idle, white when focused,
 * like the other tiles. The level goes to the listener, which sets the screen brightness (the panel backlight here).
 */
final class BacklightSlider extends View {
    interface Listener {
        void onLevel(int percent);
    }

    /** Step mode (Volume): each Left or Right press is one step for the listener; the level is set from outside. */
    interface StepListener {
        void onStep(int direction);
    }

    private StepListener stepListener;

    void setStepListener(StepListener l) {
        stepListener = l;
    }

    private final float dp;
    private final Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private Drawable sun;
    private String labelText;
    private boolean light;
    private int level = 50;
    private Listener listener;

    public BacklightSlider(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        dp = ctx.getResources().getDisplayMetrics().density;
        sun = ctx.getDrawable(R.drawable.ic_sun).mutate();
        label.setTextSize(12 * ctx.getResources().getDisplayMetrics().scaledDensity);
        label.setTypeface(ResourcesCompat.getFont(ctx, R.font.inter_semibold));
        value.setTextSize(11 * ctx.getResources().getDisplayMetrics().scaledDensity);
        value.setTextAlign(Paint.Align.RIGHT);
        value.setTypeface(label.getTypeface());
        setFocusable(true);
        labelText = ctx.getString(R.string.cc_backlight);
        setContentDescription(labelText);
    }

    /** The same tile for another level (Control Center's Volume). */
    void setIconAndLabel(int iconRes, String text) {
        sun = getContext().getDrawable(iconRes).mutate();
        labelText = text;
        setContentDescription(text);
        invalidate();
    }

    /** Light appearance: dark ink, a grey wash when idle. */
    void setLight(boolean l) {
        light = l;
        invalidate();
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setLevel(int percent) {
        level = Math.max(0, Math.min(100, percent));
        invalidate();
    }

    int level() {
        return level;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int step = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 5 : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -5 : 0;
        if (step == 0) return super.onKeyDown(keyCode, event);
        if (stepListener != null) {
            stepListener.onStep(step > 0 ? 1 : -1);
            return true;
        }
        int next = Math.max(0, Math.min(100, level + step));
        if (next != level) {
            level = next;
            invalidate();
            if (listener != null) listener.onLevel(level);
        }
        return true;   // Left and Right stay on the slider, also at its ends
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gainFocus, direction, previous);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        boolean focused = isFocused();
        int ink = focused || light ? 0xFF111111 : 0xFFFFFFFF;
        float w = getWidth(), h = getHeight();
        rect.set(0, 0, w, h);
        tile.setColor(focused ? (light ? 0xFFFFFFFF : 0xF5FFFFFF) : (light ? 0x1F000000 : 0x26FFFFFF));
        c.drawRoundRect(rect, 16 * dp, 16 * dp, tile);

        float pad = 12 * dp;
        int icon = Math.round(15 * dp);
        int top = Math.round(8 * dp);
        sun.setTint(ink);
        sun.setBounds(Math.round(pad), top, Math.round(pad) + icon, top + icon);
        sun.draw(c);
        label.setColor(ink);
        float baseline = top + icon * 0.78f;
        c.drawText(labelText, pad + icon + 8 * dp, baseline, label);
        value.setColor(focused || light ? 0xB3111111 : 0xB3FFFFFF);
        c.drawText(level + "%", w - pad, baseline, value);

        // The bar: a thick rounded track, filled to the level.
        float barTop = h - 15 * dp, barH = 6 * dp;
        rect.set(pad, barTop, w - pad, barTop + barH);
        track.setColor(focused || light ? 0x26000000 : 0x33FFFFFF);
        c.drawRoundRect(rect, barH / 2, barH / 2, track);
        float end = pad + (w - 2 * pad) * level / 100f;
        if (end - pad >= barH) {
            rect.set(pad, barTop, end, barTop + barH);
            fill.setColor(focused || light ? 0xFF111111 : 0xFFFFFFFF);
            c.drawRoundRect(rect, barH / 2, barH / 2, fill);
        }
    }
}
