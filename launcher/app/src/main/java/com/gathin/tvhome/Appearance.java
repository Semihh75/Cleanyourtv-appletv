package com.gathin.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Dark or Light appearance (this TV, Customize > Display), like tvOS. It changes the glass surfaces: the button bar,
 * Control Center and the bar's pop-up menus. Dark is the look so far; Light is milky white glass with dark text;
 * Auto is Light by day and Dark during the Night Shift hours.
 */
final class Appearance {
    static final String KEY = "appearance";   // dark, light, auto
    static final String ACTION_CHANGED = "com.gathin.tvhome.APPEARANCE_CHANGED";
    static final String[] MODES = {"dark", "light", "auto"};
    static final int INK = 0xFF1C1C1E;

    private Appearance() {
    }

    static String mode(Context ctx) {
        return HeaderPlacement.prefs(ctx).getString(KEY, "dark");
    }

    static boolean isLight(Context ctx) {
        String m = mode(ctx);
        if ("light".equals(m)) return true;
        return "auto".equals(m) && !NightShift.isNight(ctx);
    }

    /** Dark text and icons everywhere under this view, for a light glass surface. */
    static void inkAll(View v) {
        if (v instanceof BacklightSlider) {
            ((BacklightSlider) v).setLight(true);
        } else if (v instanceof TextView) {
            TextView t = (TextView) v;
            t.setTextColor(INK);
            t.setCompoundDrawableTintList(ColorStateList.valueOf(INK));
        } else if (v instanceof ImageView && ((ImageView) v).getImageTintList() != null) {
            ((ImageView) v).setImageTintList(ColorStateList.valueOf(INK));
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) inkAll(g.getChildAt(i));
        }
    }

    /** A Control Center tile background for Light: a faint grey wash, white when focused. */
    static StateListDrawable lightTile(float radiusPx, boolean oval) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, shape(0xFFFFFFFF, radiusPx, oval));
        s.addState(new int[]{}, shape(0x1F000000, radiusPx, oval));
        return s;
    }

    private static GradientDrawable shape(int color, float radius, boolean oval) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        if (oval) g.setShape(GradientDrawable.OVAL);
        else g.setCornerRadius(radius);
        return g;
    }
}
