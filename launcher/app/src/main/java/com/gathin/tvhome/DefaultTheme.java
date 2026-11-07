package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * The Apple TV theme is this version's default look: on the very first start, when no theme was pushed or picked
 * yet (no config.json), Home applies the bundled "apple-tv" theme once. A theme chosen later, or one pushed with
 * tools/theme, is never overwritten.
 */
final class DefaultTheme {
    static final String NAME = "apple-tv";
    private static final String DONE = "default_theme_done";

    private DefaultTheme() {
    }

    static void applyOnce(Context ctx) {
        SharedPreferences p = HeaderPlacement.prefs(ctx);
        if (p.getBoolean(DONE, false)) return;
        File dir = Config.dir(ctx);
        if (dir == null) return;   // storage not mounted yet: try again next start
        boolean hasTheme = new File(dir, Config.FILE_NAME).isFile();
        if (hasTheme || ThemeLibrary.apply(ctx, NAME)) p.edit().putBoolean(DONE, true).apply();
    }
}
