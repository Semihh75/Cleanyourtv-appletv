package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Now Playing "Shelf" (this TV): the album art across the whole top of Home like an Apple TV top shelf. The art is
 * blurred cheaply by shrinking it to a few dozen pixels and drawing that back up with filtering, darkened a little,
 * shaded on the left where the title sits, and darkened towards the dock, so there is no edge or frame.
 */
final class ShelfBackdrop extends View {
    /** The backdrop covers the whole screen while music plays (one calm background, not the music at the top and the
     *  focused app's colour around the dock); from FADE_FROM down it darkens, so the dock and the rows read well. */
    private static final float HEIGHT = 1f, FADE_FROM = 0.50f;

    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint();
    private final Paint side = new Paint();
    private final Paint mask = new Paint();
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private Bitmap blurred;

    ShelfBackdrop(Context ctx) {
        super(ctx);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        shade.setColor(0x59000000);
        setAlpha(0f);
        setVisibility(GONE);
    }

    /** The artwork to show, or null to clear it. */
    void setArt(Bitmap art) {
        if (blurred != null) blurred.recycle();
        blurred = art == null ? null : blur(art);
        invalidate();
    }

    /** Shrunk to 64 px wide, then three box-blur passes: drawn back up with filtering it is a smooth blur with no
     *  blocks (shrinking alone left a visible grid when stretched over the screen). */
    private static Bitmap blur(Bitmap art) {
        int w = Math.max(1, art.getWidth()), h = Math.max(1, art.getHeight());
        int sw = 64, sh = Math.max(1, 64 * h / w);
        Bitmap mid = Bitmap.createScaledBitmap(art, 256, Math.max(1, 256 * h / w), true);
        Bitmap small = Bitmap.createScaledBitmap(mid, sw, sh, true).copy(Bitmap.Config.ARGB_8888, true);
        mid.recycle();
        int[] px = new int[sw * sh];
        small.getPixels(px, 0, sw, 0, 0, sw, sh);
        for (int pass = 0; pass < 3; pass++) {
            px = boxBlur(px, sw, sh, 4, true);
            px = boxBlur(px, sw, sh, 4, false);
        }
        small.setPixels(px, 0, sw, 0, 0, sw, sh);
        return small;
    }

    /** One direction of a box blur with clamped edges. */
    private static int[] boxBlur(int[] in, int w, int h, int r, boolean horizontal) {
        int[] out = new int[in.length];
        int lines = horizontal ? h : w, len = horizontal ? w : h, n = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < len; i++) {
                int rs = 0, gs = 0, bs = 0;
                for (int k = -r; k <= r; k++) {
                    int j = Math.max(0, Math.min(len - 1, i + k));
                    int p = in[horizontal ? line * w + j : j * w + line];
                    rs += (p >> 16) & 0xFF; gs += (p >> 8) & 0xFF; bs += p & 0xFF;
                }
                out[horizontal ? line * w + i : i * w + line] = 0xFF000000 | (rs / n) << 16 | (gs / n) << 8 | (bs / n);
            }
        }
        return out;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float bottom = h * HEIGHT;
        mask.setShader(new LinearGradient(0, bottom * (FADE_FROM / HEIGHT), 0, bottom,
                0x00000000, 0xB3000000, Shader.TileMode.CLAMP));
        side.setShader(new LinearGradient(0, 0, w * 0.6f, 0, 0x8C000000, 0x00000000, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (blurred == null) return;
        float w = getWidth(), h = getHeight() * HEIGHT;
        // centerCrop of the small image into the top band
        float scale = Math.max(w / blurred.getWidth(), h / blurred.getHeight());
        float bw = blurred.getWidth() * scale, bh = blurred.getHeight() * scale;
        dst.set((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f);
        src.set(0, 0, blurred.getWidth(), blurred.getHeight());
        c.clipRect(0, 0, w, h);
        c.drawBitmap(blurred, src, dst, image);
        c.drawRect(0, 0, w, h, shade);
        c.drawRect(0, 0, w, h, side);
        c.drawRect(0, 0, w, h, mask);   // darker towards the dock
    }
}
