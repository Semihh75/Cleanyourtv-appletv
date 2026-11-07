package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

/**
 * The glass on a focused tile (this TV, Advanced effects): a soft light along the top edge, like the curved glass of
 * a tvOS app icon, and one diagonal glint that sweeps across the tile when it gets focus. Drawn as the card's
 * foreground, so it is clipped to the tile's rounded corners. Idle tiles draw nothing.
 */
final class GlassSheen extends Drawable {
    private final Paint top = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix glintMatrix = new Matrix();
    private LinearGradient glintShader;
    /** 0 idle, 1 fully lit. */
    private float lit;
    /** Where the glint is, 0 (off the left) to 1 (off the right); negative means no glint. */
    private float sweep = -1f;
    private ValueAnimator litAnim, sweepAnim;

    void setFocused(boolean focused) {
        if (litAnim != null) litAnim.cancel();
        litAnim = ValueAnimator.ofFloat(lit, focused ? 1f : 0f);
        litAnim.setDuration(focused ? 260 : 180);
        litAnim.addUpdateListener(a -> {
            lit = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        litAnim.start();
        if (sweepAnim != null) sweepAnim.cancel();
        if (focused) {
            sweepAnim = ValueAnimator.ofFloat(0f, 1f);
            sweepAnim.setStartDelay(90);
            sweepAnim.setDuration(720);
            sweepAnim.setInterpolator(new DecelerateInterpolator(1.4f));
            sweepAnim.addUpdateListener(a -> {
                sweep = (float) a.getAnimatedValue();
                invalidateSelf();
            });
            sweepAnim.start();
        } else {
            sweep = -1f;
        }
    }

    @Override
    protected void onBoundsChange(@NonNull Rect b) {
        super.onBoundsChange(b);
        // The top light: white fading out by 45% of the height.
        top.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.45f,
                0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        // The glint: a narrow diagonal band, moved across by a matrix.
        float w = b.width() * 0.35f;
        glintShader = new LinearGradient(0, 0, w, w * 0.6f,
                new int[]{0x00FFFFFF, 0x47FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        glint.setShader(glintShader);
    }

    @Override
    public void draw(@NonNull Canvas c) {
        if (lit <= 0.01f) return;
        Rect b = getBounds();
        top.setAlpha(Math.round(255 * lit));
        c.drawRect(b, top);
        if (sweep >= 0f && sweep < 1f && glintShader != null) {
            float w = b.width() * 0.35f;
            float x = b.left - w + (b.width() + 2 * w) * sweep;
            glintMatrix.setTranslate(x, b.top - w * 0.3f * (1 - sweep));
            glintShader.setLocalMatrix(glintMatrix);
            glint.setAlpha(Math.round(255 * lit * (1f - Math.abs(sweep - 0.5f))));
            c.drawRect(b, glint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
