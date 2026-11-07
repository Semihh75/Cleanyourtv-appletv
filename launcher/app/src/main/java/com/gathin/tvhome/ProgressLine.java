package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** A thin rounded progress line, Apple Music style: a faint track and a white fill. */
public final class ProgressLine extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float fraction;

    public ProgressLine(Context context, AttributeSet attrs) {
        super(context, attrs);
        track.setColor(0x33FFFFFF);
        fill.setColor(0xE6FFFFFF);
    }

    void setFraction(float f) {
        f = Math.max(0f, Math.min(1f, f));
        if (Math.abs(f - fraction) < 0.0005f) return;
        fraction = f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = getHeight() / 2f;
        rect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(rect, r, r, track);
        if (fraction > 0f) {
            rect.set(0, 0, Math.max(getHeight(), getWidth() * fraction), getHeight());
            canvas.drawRoundRect(rect, r, r, fill);
        }
    }
}
