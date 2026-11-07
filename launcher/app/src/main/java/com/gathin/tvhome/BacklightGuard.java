package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

/**
 * Keeps the backlight where the owner put it (this TV). The MediaTek picture service (com.mediatek.tv.agent) writes
 * its own stored brightness (134 of 255, about 45%) back into screen_brightness on its own, e.g. while a video plays
 * (seen in `dumpsys settings`: last writer com.mediatek.tv.agent). Control Center and Night Shift remember the level
 * they set; when the setting changes to anything else, it is put back. Runs in the remote helper, which is always on,
 * so it also works over other apps. Limited to a few corrections a minute, so it can never fight in a loop.
 */
final class BacklightGuard {
    private static final String TAG = "BacklightGuard";
    private static final String KEY = "backlight_wanted";
    private static final int MAX_FIXES_PER_MINUTE = 6;

    private final Context ctx;
    private final ContentObserver observer;
    private long windowStart;
    private int fixes;

    private BacklightGuard(Context context) {
        ctx = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                main.postDelayed(BacklightGuard.this::check, 250);
            }
        };
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("backlight_guard", Context.MODE_PRIVATE);
    }

    /** The raw level (0-255) the owner chose. */
    static void remember(Context c, int raw) {
        prefs(c).edit().putInt(KEY, raw).apply();
    }

    static BacklightGuard start(Context c) {
        BacklightGuard g = new BacklightGuard(c);
        g.ctx.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, g.observer);
        g.check();   // also right after a reboot, when the agent sets its value at start-up
        return g;
    }

    void stop() {
        ctx.getContentResolver().unregisterContentObserver(observer);
    }

    private void check() {
        int wanted = prefs(ctx).getInt(KEY, -1);
        if (wanted < 0 || !Settings.System.canWrite(ctx)) return;
        int now = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, wanted);
        if (now == wanted) return;
        long t = SystemClock.elapsedRealtime();
        if (t - windowStart > 60_000) {
            windowStart = t;
            fixes = 0;
        }
        if (++fixes > MAX_FIXES_PER_MINUTE) return;
        Log.i(TAG, "brightness changed to " + now + " by someone else; back to " + wanted);
        Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, wanted);
    }
}
