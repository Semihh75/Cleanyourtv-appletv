package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Home's header as the owner arranges it in Customize (this TV): where the clock and the top buttons sit, as a spot on
 * the screen (x and y from 0 to 1 inside a safe margin, so a preset or a hand-placed spot stays put when the time gets
 * wider), the clock's style and size, and whether the date shows.
 */
final class HeaderPlacement {
    static final String CLOCK_X = "clock_x";
    static final String CLOCK_Y = "clock_y";
    static final String BUTTONS_X = "buttons_x";
    static final String BUTTONS_Y = "buttons_y";
    static final String CLOCK_STYLE = "clock_style";      // white, transparent, glass
    static final String CLOCK_SIZE = "clock_size";        // s, m, l
    static final String SHOW_DATE = "show_date";
    /** Set by Customize's "Custom..." choice: "clock" or "buttons". Home starts the on-screen mover and clears it. */
    static final String PLACE_PENDING = "place_pending";

    // Now Playing card.
    static final String MEDIA_SHOW = "media_show";
    static final String MEDIA_X = "media_x";
    static final String MEDIA_Y = "media_y";
    static final String MEDIA_STYLE = "media_style";      // glass, solid, minimal
    static final String MEDIA_SIZE = "media_size";        // s, m, l
    static final String MEDIA_LAYOUT = "media_layout";    // shelf (default), card, compact, tile, cover
    static final String MEDIA_PROGRESS = "media_progress";
    static final String MEDIA_SOURCE = "media_source";
    /** Top buttons: "separate", "pill" or "pill_time"; unset follows the theme (button_bar). */
    static final String BUTTON_BAR = "button_bar";

    static final String[] KEYS = {CLOCK_X, CLOCK_Y, BUTTONS_X, BUTTONS_Y, CLOCK_STYLE, CLOCK_SIZE, SHOW_DATE,
            MEDIA_SHOW, MEDIA_X, MEDIA_Y, MEDIA_STYLE, MEDIA_SIZE, MEDIA_LAYOUT, MEDIA_PROGRESS, MEDIA_SOURCE, BUTTON_BAR,
            "clock_position", "buttons_higher"};   // the last two: an earlier version's keys, cleared with the rest

    /** Now Playing presets: left, center and right, in the open band between the clock and the dock. */
    static final float[][] MEDIA_PRESETS = {{0f, 0.42f}, {0.5f, 0.42f}, {1f, 0.42f}};

    static boolean mediaShow(Context ctx) {
        return prefs(ctx).getBoolean(MEDIA_SHOW, true);
    }

    static float mediaX(Context ctx) {
        return prefs(ctx).getFloat(MEDIA_X, 1f);
    }

    static float mediaY(Context ctx) {
        return prefs(ctx).getFloat(MEDIA_Y, 0.42f);
    }

    static String mediaStyle(Context ctx) {
        return prefs(ctx).getString(MEDIA_STYLE, "glass");
    }

    static String mediaSize(Context ctx) {
        return prefs(ctx).getString(MEDIA_SIZE, "m");
    }

    static String mediaLayout(Context ctx) {
        return prefs(ctx).getString(MEDIA_LAYOUT, "shelf");
    }

    static boolean mediaProgress(Context ctx) {
        return prefs(ctx).getBoolean(MEDIA_PROGRESS, true);
    }

    static boolean mediaSource(Context ctx) {
        return prefs(ctx).getBoolean(MEDIA_SOURCE, true);
    }

    /** The chosen button bar, or the theme's when none was chosen. */
    static String buttonBar(Context ctx, String themeDefault) {
        String v = prefs(ctx).getString(BUTTON_BAR, null);
        if (v == null) v = themeDefault;
        return "pill".equals(v) || "pill_time".equals(v) ? v : "separate";
    }

    /** Clock presets: left, center, right at the top, and the middle of the screen. */
    static final float[][] CLOCK_PRESETS = {{0f, 0f}, {0.5f, 0f}, {1f, 0f}, {0.5f, 0.32f}};
    /** Top button presets: left, center, right. */
    static final float[][] BUTTON_PRESETS = {{0f, 0f}, {0.5f, 0f}, {1f, 0f}};

    private HeaderPlacement() {
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    static float clockX(Context ctx) {
        return prefs(ctx).getFloat(CLOCK_X, ctx.getResources().getBoolean(R.bool.clock_center) ? 0.5f : 0f);
    }

    static float clockY(Context ctx) {
        return prefs(ctx).getFloat(CLOCK_Y, 0f);
    }

    static float buttonsX(Context ctx) {
        return prefs(ctx).getFloat(BUTTONS_X, 1f);
    }

    static float buttonsY(Context ctx) {
        return prefs(ctx).getFloat(BUTTONS_Y, 0f);
    }

    static String clockStyle(Context ctx) {
        return prefs(ctx).getString(CLOCK_STYLE, "transparent");
    }

    static String clockSize(Context ctx) {
        return prefs(ctx).getString(CLOCK_SIZE, "m");
    }

    static boolean showDate(Context ctx) {
        return prefs(ctx).getBoolean(SHOW_DATE, true);
    }

    /** The preset at (x, y), or -1 when the spot was placed by hand. */
    static int presetIndex(float[][] presets, float x, float y) {
        for (int i = 0; i < presets.length; i++) {
            if (Math.abs(presets[i][0] - x) < 0.001f && Math.abs(presets[i][1] - y) < 0.001f) return i;
        }
        return -1;
    }
}
