package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * Frosted glass for panels (this TV): the TV has no window blur (dumpsys window: mBlurEnabled=false), so the screen
 * behind is copied small with PixelCopy, box-blurred, and drawn as the panel's background, cut to where the panel
 * sits, under a light glass tint and rim like the button bar's. The panel then matches the theme behind it.
 */
final class FrostedGlass {
    /** The screen is copied at 1/SCALE of its size; drawn back up with filtering it is already soft. */
    private static final int SCALE = 8;

    interface Ready {
        void onReady(Bitmap blurred);   // null when the copy failed: the panel uses its plain tint
    }

    private FrostedGlass() {
    }

    /** Copies and blurs the activity's window, then calls back on the main thread. */
    static void capture(Activity activity, Ready ready) {
        View decor = activity.getWindow().getDecorView();
        int w = Math.max(1, decor.getWidth() / SCALE), h = Math.max(1, decor.getHeight() / SCALE);
        Handler main = new Handler(Looper.getMainLooper());
        try {
            Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            PixelCopy.request(activity.getWindow(), small, result -> {
                if (result != PixelCopy.SUCCESS) {
                    ready.onReady(null);
                    return;
                }
                blur(small, 3, 3);
                ready.onReady(small);
            }, main);
        } catch (Exception e) {
            main.post(() -> ready.onReady(null));
        }
    }

    /** A box blur, both directions, a few passes (close to a gaussian). */
    static void blur(Bitmap b, int r, int passes) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int p = 0; p < passes; p++) {
            px = pass(px, w, h, r, true);
            px = pass(px, w, h, r, false);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static int[] pass(int[] in, int w, int h, int r, boolean horizontal) {
        int[] out = new int[in.length];
        int lines = horizontal ? h : w, len = horizontal ? w : h, n = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                int rs = 0, gs = 0, bs = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(len - 1, i + k));
                    int p = in[horizontal ? line * w + j : j * w + line];
                    rs += (p >> 16) & 0xFF;
                    gs += (p >> 8) & 0xFF;
                    bs += p & 0xFF;
                }
                out[horizontal ? line * w + i : i * w + line] = 0xFF000000 | (rs / n) << 16 | (gs / n) << 8 | (bs / n);
            }
        }
        return out;
    }

    /** The panel background: the blurred screen behind it (or none), a glass tint, a rim, rounded corners. */
    static final class PanelDrawable extends Drawable {
        private final Bitmap blurred;
        private final float radius;
        private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final RectF box = new RectF();
        private final Rect src = new Rect();
        /** Where the panel is on the screen, in screen pixels. */
        private int screenX, screenY;

        PanelDrawable(Bitmap blurred, float radiusPx, float density) {
            this.blurred = blurred;
            this.radius = radiusPx;
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(density);
            rim.setColor(0x40FFFFFF);
            // Without the blurred copy, a darker tint keeps the text readable.
            shade.setColor(blurred != null ? 0x33000000 : 0xCC1B1C21);
        }

        /** Light appearance: milky white glass with a brighter rim. */
        void setLight(boolean light) {
            if (!light) return;
            this.light = true;
            rim.setColor(0x80FFFFFF);
            shade.setColor(blurred != null ? 0x99EEEEF2 : 0xE6EEEEF2);
            onBoundsChange(getBounds());
            invalidateSelf();
        }

        private boolean light;

        void setScreenPosition(int x, int y) {
            if (x == screenX && y == screenY) return;   // called every frame: redraw only on a move
            screenX = x;
            screenY = y;
            invalidateSelf();
        }

        @Override
        protected void onBoundsChange(@NonNull Rect b) {
            super.onBoundsChange(b);
            box.set(b);
            clip.reset();
            clip.addRoundRect(box, radius, radius, Path.Direction.CW);
            // Light from the top like the button bar: white 20% fading to 8%.
            tint.setShader(new LinearGradient(0, b.top, 0, b.bottom, light ? 0x66FFFFFF : 0x33FFFFFF,
                    light ? 0x26FFFFFF : 0x14FFFFFF, Shader.TileMode.CLAMP));
        }

        @Override
        public void draw(@NonNull Canvas c) {
            Rect b = getBounds();
            int save = c.save();
            c.clipPath(clip);
            if (blurred != null) {
                src.set(screenX / SCALE, screenY / SCALE, (screenX + b.width()) / SCALE, (screenY + b.height()) / SCALE);
                c.drawBitmap(blurred, src, b, image);
            }
            c.drawRect(b, shade);
            c.drawRect(b, tint);
            c.restoreToCount(save);
            box.set(b);
            box.inset(rim.getStrokeWidth() / 2, rim.getStrokeWidth() / 2);
            c.drawRoundRect(box, radius, radius, rim);
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
}
