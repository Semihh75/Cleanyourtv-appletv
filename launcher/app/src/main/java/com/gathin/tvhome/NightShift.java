package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * Night Shift (this TV): in the evening the backlight goes down to a chosen level, in the morning back to what it was.
 * Set in Customize > Display and switched on or off from Control Center. A level changed by hand during the night is
 * kept until morning. Also tells Appearance "auto" whether it is night. Alarms (inexact, within a minute) mark the
 * start and the end; Home checks again whenever it comes to the front, so a missed alarm is caught up.
 */
final class NightShift {
    static final String ENABLED = "night_shift";
    static final String FROM = "night_from";      // hour 0-23
    static final String UNTIL = "night_until";    // hour 0-23
    static final String LEVEL = "night_level";    // percent
    private static final String APPLIED = "night_applied";
    private static final String DAY_LEVEL = "night_day_level";

    static final int[] FROM_HOURS = {21, 22, 23};
    static final int[] UNTIL_HOURS = {6, 7, 8};
    static final int[] LEVELS = {20, 30, 40, 50};

    private NightShift() {
    }

    static SharedPreferences prefs(Context ctx) {
        return HeaderPlacement.prefs(ctx);
    }

    static boolean enabled(Context ctx) {
        return prefs(ctx).getBoolean(ENABLED, false);
    }

    static int from(Context ctx) {
        return prefs(ctx).getInt(FROM, 22);
    }

    static int until(Context ctx) {
        return prefs(ctx).getInt(UNTIL, 7);
    }

    static int level(Context ctx) {
        return prefs(ctx).getInt(LEVEL, 30);
    }

    /** Whether the clock is in the night window (from FROM until UNTIL, across midnight). */
    static boolean isNight(Context ctx) {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int f = from(ctx), u = until(ctx);
        return f > u ? (h >= f || h < u) : (h >= f && h < u);
    }

    static String window(Context ctx) {
        return String.format(java.util.Locale.ROOT, "%02d–%02d", from(ctx), until(ctx));
    }

    static void setEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(ENABLED, on).apply();
        update(ctx);
    }

    /** Brings the backlight in line with the schedule and sets the next alarm. */
    static void update(Context ctx) {
        SharedPreferences p = prefs(ctx);
        boolean applied = p.getBoolean(APPLIED, false);
        boolean night = enabled(ctx) && isNight(ctx);
        if (night && !applied) {
            int day = ControlCenter.backlightPercent(ctx);
            if (ControlCenter.setBacklight(ctx, Math.min(day, level(ctx)))) {
                p.edit().putBoolean(APPLIED, true).putInt(DAY_LEVEL, day).apply();
            }
        } else if (!night && applied) {
            ControlCenter.setBacklight(ctx, p.getInt(DAY_LEVEL, 100));
            p.edit().putBoolean(APPLIED, false).apply();
        }
        schedule(ctx);
    }

    /** The next start or end of the night window. */
    private static void schedule(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, new Intent(ctx, Receiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        if (!enabled(ctx) && !prefs(ctx).getBoolean(APPLIED, false)
                && !"auto".equals(Appearance.mode(ctx))) return;
        Calendar next = Calendar.getInstance();
        int hour = isNight(ctx) ? until(ctx) : from(ctx);
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 5);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.DAY_OF_MONTH, 1);
        am.setWindow(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), 60_000L, pi);
    }

    /** Start or end of the night: backlight, and Home's look when Appearance is "auto". */
    public static final class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            update(ctx);
            if ("auto".equals(Appearance.mode(ctx))) {
                ctx.sendBroadcast(new Intent(Appearance.ACTION_CHANGED).setPackage(ctx.getPackageName()));
            }
        }
    }
}
