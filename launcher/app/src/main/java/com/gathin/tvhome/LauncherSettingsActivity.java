package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    // Home's header (HeaderPlacement): position presets plus "Custom...", clock style, size and date.
    private final TextView[] clockPos = new TextView[HeaderPlacement.CLOCK_PRESETS.length];
    private final TextView[] buttonsPos = new TextView[HeaderPlacement.BUTTON_PRESETS.length];
    private TextView clockPosCustom;
    private TextView buttonsPosCustom;
    private static final String[] STYLES = {"white", "transparent", "glass"};
    private final TextView[] styleChoices = new TextView[STYLES.length];
    private static final String[] SIZES = {"s", "m", "l"};
    private final TextView[] sizeChoices = new TextView[SIZES.length];
    private TextView dateShow;
    private TextView dateHide;
    // Now Playing.
    private TextView npShow, npHide, npPosCustom;
    private final TextView[] npPos = new TextView[HeaderPlacement.MEDIA_PRESETS.length];
    private static final String[] NP_STYLES = {"glass", "color", "solid", "minimal"};
    private static final String[] NP_LAYOUTS = {"shelf", "card", "compact", "tile", "cover"};
    private final TextView[] npLayouts = new TextView[NP_LAYOUTS.length];
    private TextView npProgShow, npProgHide, npSrcShow, npSrcHide;
    private static final String[] BARS = {"separate", "pill", "pill_time"};
    private final TextView[] bars = new TextView[BARS.length];
    private final TextView[] npStyles = new TextView[NP_STYLES.length];
    private final TextView[] npSizes = new TextView[SIZES.length];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockPosIds = {R.id.clockPos0, R.id.clockPos1, R.id.clockPos2, R.id.clockPos3};
        for (int i = 0; i < clockPosIds.length; i++) {
            final float[] spot = HeaderPlacement.CLOCK_PRESETS[i];
            clockPos[i] = findViewById(clockPosIds[i]);
            clockPos[i].setOnClickListener(v -> putSpot(HeaderPlacement.CLOCK_X, HeaderPlacement.CLOCK_Y, spot));
        }
        int[] buttonsPosIds = {R.id.buttonsPos0, R.id.buttonsPos1, R.id.buttonsPos2};
        for (int i = 0; i < buttonsPosIds.length; i++) {
            final float[] spot = HeaderPlacement.BUTTON_PRESETS[i];
            buttonsPos[i] = findViewById(buttonsPosIds[i]);
            buttonsPos[i].setOnClickListener(v -> putSpot(HeaderPlacement.BUTTONS_X, HeaderPlacement.BUTTONS_Y, spot));
        }
        clockPosCustom = findViewById(R.id.clockPosCustom);
        buttonsPosCustom = findViewById(R.id.buttonsPosCustom);
        clockPosCustom.setOnClickListener(v -> placeOnHome("clock"));
        buttonsPosCustom.setOnClickListener(v -> placeOnHome("buttons"));
        int[] barIds = {R.id.barSeparate, R.id.barPill, R.id.barPillTime};
        for (int i = 0; i < barIds.length; i++) {
            final String value = BARS[i];
            bars[i] = findViewById(barIds[i]);
            bars[i].setOnClickListener(v -> putString(HeaderPlacement.BUTTON_BAR, value));
        }
        int[] styleIds = {R.id.styleWhite, R.id.styleTransparent, R.id.styleGlass};
        for (int i = 0; i < styleIds.length; i++) {
            final String value = STYLES[i];
            styleChoices[i] = findViewById(styleIds[i]);
            styleChoices[i].setOnClickListener(v -> putString(HeaderPlacement.CLOCK_STYLE, value));
        }
        int[] sizeIds = {R.id.sizeS, R.id.sizeM, R.id.sizeL};
        for (int i = 0; i < sizeIds.length; i++) {
            final String value = SIZES[i];
            sizeChoices[i] = findViewById(sizeIds[i]);
            sizeChoices[i].setOnClickListener(v -> putString(HeaderPlacement.CLOCK_SIZE, value));
        }
        dateShow = findViewById(R.id.dateShow);
        dateHide = findViewById(R.id.dateHide);
        dateShow.setOnClickListener(v -> { choices.edit().putBoolean(HeaderPlacement.SHOW_DATE, true).apply(); changed(); refresh(); });
        dateHide.setOnClickListener(v -> { choices.edit().putBoolean(HeaderPlacement.SHOW_DATE, false).apply(); changed(); refresh(); });
        npShow = findViewById(R.id.npShow);
        npHide = findViewById(R.id.npHide);
        npShow.setOnClickListener(v -> { choices.edit().putBoolean(HeaderPlacement.MEDIA_SHOW, true).apply(); changed(); refresh(); });
        npHide.setOnClickListener(v -> { choices.edit().putBoolean(HeaderPlacement.MEDIA_SHOW, false).apply(); changed(); refresh(); });
        int[] npPosIds = {R.id.npPos0, R.id.npPos1, R.id.npPos2};
        for (int i = 0; i < npPosIds.length; i++) {
            final float[] spot = HeaderPlacement.MEDIA_PRESETS[i];
            npPos[i] = findViewById(npPosIds[i]);
            npPos[i].setOnClickListener(v -> putSpot(HeaderPlacement.MEDIA_X, HeaderPlacement.MEDIA_Y, spot));
        }
        npPosCustom = findViewById(R.id.npPosCustom);
        npPosCustom.setOnClickListener(v -> placeOnHome("media"));
        int[] npLayoutIds = {R.id.npLayoutShelf, R.id.npLayoutCard, R.id.npLayoutCompact, R.id.npLayoutTile, R.id.npLayoutCover};
        for (int i = 0; i < npLayoutIds.length; i++) {
            final String value = NP_LAYOUTS[i];
            npLayouts[i] = findViewById(npLayoutIds[i]);
            npLayouts[i].setOnClickListener(v -> putString(HeaderPlacement.MEDIA_LAYOUT, value));
        }
        npProgShow = findViewById(R.id.npProgShow);
        npProgHide = findViewById(R.id.npProgHide);
        npSrcShow = findViewById(R.id.npSrcShow);
        npSrcHide = findViewById(R.id.npSrcHide);
        npProgShow.setOnClickListener(v -> { choices.edit().putBoolean(HeaderPlacement.MEDIA_PROGRESS, true).apply(); changed(); refresh(); });
        npProgHide.setOnClickListener(v -> { choices.edit().putBoolean(HeaderPlacement.MEDIA_PROGRESS, false).apply(); changed(); refresh(); });
        npSrcShow.setOnClickListener(v -> { choices.edit().putBoolean(HeaderPlacement.MEDIA_SOURCE, true).apply(); changed(); refresh(); });
        npSrcHide.setOnClickListener(v -> { choices.edit().putBoolean(HeaderPlacement.MEDIA_SOURCE, false).apply(); changed(); refresh(); });
        int[] npStyleIds = {R.id.npGlass, R.id.npColor, R.id.npSolid, R.id.npMinimal};
        for (int i = 0; i < npStyleIds.length; i++) {
            final String value = NP_STYLES[i];
            npStyles[i] = findViewById(npStyleIds[i]);
            npStyles[i].setOnClickListener(v -> putString(HeaderPlacement.MEDIA_STYLE, value));
        }
        int[] npSizeIds = {R.id.npSizeS, R.id.npSizeM, R.id.npSizeL};
        for (int i = 0; i < npSizeIds.length; i++) {
            final String value = SIZES[i];
            npSizes[i] = findViewById(npSizeIds[i]);
            npSizes[i].setOnClickListener(v -> putString(HeaderPlacement.MEDIA_SIZE, value));
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
            reverseRow(clockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY).commit();
            SharedPreferences.Editor header = choices.edit();
            for (String key : HeaderPlacement.KEYS) header.remove(key);
            header.commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            clockFormat = current.clockFormat;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        buildThemeRow();
        refresh();
        setupCategories();
    }

    // ---- Widgets (this TV): show, position and style per widget -------------------------------------------------

    private void bindWidget(String id, int showId, int hideId, int[] posIds, int customId, int cleanId, int glassId) {
        SharedPreferences p = HeaderPlacement.prefs(this);
        TextView show = findViewById(showId), hide = findViewById(hideId), custom = findViewById(customId);
        TextView clean = findViewById(cleanId), glass = findViewById(glassId);
        TextView[] pos = new TextView[posIds.length];
        for (int i = 0; i < posIds.length; i++) pos[i] = findViewById(posIds[i]);
        Runnable sync = () -> {
            boolean on = Widgets.shown(this, id);
            show.setSelected(on);
            hide.setSelected(!on);
            float x = p.getFloat(Widgets.keyX(id), 0f), y = p.getFloat(Widgets.keyY(id), "date".equals(id) ? 0.22f : 0f);
            int preset = HeaderPlacement.presetIndex(Widgets.PRESETS, x, y);
            for (int i = 0; i < pos.length; i++) pos[i].setSelected(preset == i);
            custom.setSelected(preset < 0);
            boolean isGlass = "glass".equals(p.getString("widget_" + id + "_style", "clean"));
            glass.setSelected(isGlass);
            clean.setSelected(!isGlass);
        };
        show.setOnClickListener(v -> { p.edit().putBoolean("widget_" + id + "_show", true).apply(); changed(); sync.run(); });
        hide.setOnClickListener(v -> { p.edit().putBoolean("widget_" + id + "_show", false).apply(); changed(); sync.run(); });
        for (int i = 0; i < pos.length; i++) {
            final float[] spot = Widgets.PRESETS[i];
            pos[i].setOnClickListener(v -> {
                p.edit().putFloat(Widgets.keyX(id), spot[0]).putFloat(Widgets.keyY(id), spot[1]).apply();
                changed();
                sync.run();
            });
        }
        custom.setOnClickListener(v -> placeOnHome(id));
        clean.setOnClickListener(v -> { p.edit().putString("widget_" + id + "_style", "clean").apply(); changed(); sync.run(); });
        glass.setOnClickListener(v -> { p.edit().putString("widget_" + id + "_style", "glass").apply(); changed(); sync.run(); });
        sync.run();
    }

    // ---- Display (this TV): appearance and Night Shift ----------------------------------------------------------

    private void bindDisplay() {
        SharedPreferences p = HeaderPlacement.prefs(this);
        TextView[] modes = {findViewById(R.id.appDark), findViewById(R.id.appLight), findViewById(R.id.appAuto)};
        Runnable showModes = () -> {
            String m = Appearance.mode(this);
            for (int i = 0; i < modes.length; i++) modes[i].setSelected(Appearance.MODES[i].equals(m));
        };
        for (int i = 0; i < modes.length; i++) {
            final String value = Appearance.MODES[i];
            modes[i].setOnClickListener(v -> {
                p.edit().putString(Appearance.KEY, value).apply();
                NightShift.update(this);   // Auto needs the day and night alarms
                changed();
                showModes.run();
            });
        }
        showModes.run();

        TextView off = findViewById(R.id.nightOff), on = findViewById(R.id.nightOn);
        Runnable showOn = () -> {
            boolean e = NightShift.enabled(this);
            on.setSelected(e);
            off.setSelected(!e);
        };
        off.setOnClickListener(v -> { NightShift.setEnabled(this, false); showOn.run(); });
        on.setOnClickListener(v -> { NightShift.setEnabled(this, true); showOn.run(); });
        showOn.run();

        bindHours(new int[]{R.id.nightFrom21, R.id.nightFrom22, R.id.nightFrom23}, NightShift.FROM_HOURS,
                NightShift.FROM, NightShift.from(this));
        bindHours(new int[]{R.id.nightUntil6, R.id.nightUntil7, R.id.nightUntil8}, NightShift.UNTIL_HOURS,
                NightShift.UNTIL, NightShift.until(this));
        bindHours(new int[]{R.id.nightLevel20, R.id.nightLevel30, R.id.nightLevel40, R.id.nightLevel50},
                NightShift.LEVELS, NightShift.LEVEL, NightShift.level(this));
    }

    /** A row of segments that each store one number under key. */
    private void bindHours(int[] ids, int[] values, String key, int current) {
        TextView[] views = new TextView[ids.length];
        for (int i = 0; i < ids.length; i++) views[i] = findViewById(ids[i]);
        Runnable show = () -> {
            int now = HeaderPlacement.prefs(this).getInt(key, current);
            for (int i = 0; i < views.length; i++) views[i].setSelected(values[i] == now);
        };
        for (int i = 0; i < views.length; i++) {
            final int value = values[i];
            views[i].setOnClickListener(v -> {
                HeaderPlacement.prefs(this).edit().putInt(key, value).apply();
                NightShift.update(this);
                show.run();
            });
        }
        show.run();
    }

    // ---- Categories (this TV): a list on the left, one section of settings on the right --------------------------

    /** The category shown last; kept while the app runs, so coming back (from the on-screen mover) lands there. */
    private static int lastCategory;
    private View[] categories;
    private View[] sections;
    private View categoryList;

    private void setupCategories() {
        bindDisplay();
        bindWidget("weather", R.id.wWeatherShow, R.id.wWeatherHide,
                new int[]{R.id.wWeatherPos0, R.id.wWeatherPos1, R.id.wWeatherPos2}, R.id.wWeatherPosCustom,
                R.id.wWeatherClean, R.id.wWeatherGlass);
        bindWidget("date", R.id.wDateShow, R.id.wDateHide,
                new int[]{R.id.wDatePos0, R.id.wDatePos1, R.id.wDatePos2}, R.id.wDatePosCustom,
                R.id.wDateClean, R.id.wDateGlass);
        int[] catIds = {R.id.catThemes, R.id.catClock, R.id.catNowPlaying, R.id.catWidgets, R.id.catApps,
                R.id.catMotion, R.id.catDisplay, R.id.catGeneral};
        int[] secIds = {R.id.secThemes, R.id.secClock, R.id.secNowPlaying, R.id.secWidgets, R.id.secApps,
                R.id.secMotion, R.id.secDisplay, R.id.secGeneral};
        categoryList = findViewById(R.id.categoryList);
        categories = new View[catIds.length];
        sections = new View[secIds.length];
        for (int i = 0; i < catIds.length; i++) {
            final int index = i;
            categories[i] = findViewById(catIds[i]);
            sections[i] = findViewById(secIds[i]);
            // Moving through the list shows that category's settings at once, as tvOS Settings does.
            categories[i].setOnFocusChangeListener((v, has) -> {
                if (has) showCategory(index);
            });
            categories[i].setOnClickListener(v -> enterSection());
        }
        if (lastCategory < 0 || lastCategory >= categories.length) lastCategory = 0;
        showCategory(lastCategory);
        categories[lastCategory].requestFocus();
    }

    private void showCategory(int index) {
        lastCategory = index;
        for (int k = 0; k < sections.length; k++) {
            sections[k].setVisibility(k == index ? View.VISIBLE : View.GONE);
            categories[k].setActivated(k == index);
        }
        findViewById(R.id.sectionScroll).scrollTo(0, 0);
    }

    /** From the list into the shown section: its first setting. */
    private void enterSection() {
        sections[lastCategory].requestFocus(View.FOCUS_DOWN);
    }

    private static boolean isInside(View view, View group) {
        for (View v = view; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v == group) return true;
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && categories != null) {
            View focus = getCurrentFocus();
            int key = event.getKeyCode();
            boolean inList = focus != null && isInside(focus, categoryList);
            if (inList) {
                if (key == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    enterSection();
                    return true;
                }
                // The list does not wrap or leave at its ends.
                if (key == android.view.KeyEvent.KEYCODE_DPAD_UP && focus == categories[0]) return true;
                if (key == android.view.KeyEvent.KEYCODE_DPAD_DOWN && focus == categories[categories.length - 1]) {
                    return true;
                }
            } else if (focus != null && isInside(focus, sections[lastCategory])) {
                int dir = key == android.view.KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT
                        : key == android.view.KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP
                        : key == android.view.KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN : 0;
                if (dir != 0 && android.view.FocusFinder.getInstance()
                        .findNextFocus((ViewGroup) sections[lastCategory], focus, dir) == null) {
                    // Left at a section's edge goes back to its category; Up and Down stay in the section, so
                    // focus never lands on another category by accident.
                    if (dir == View.FOCUS_LEFT) categories[lastCategory].requestFocus();
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // ---- Themes (ThemeLibrary): preview cards, "Current" first --------------------------------------------------

    private final java.util.Map<String, View> themeChecks = new java.util.HashMap<>();
    private boolean applyingTheme;

    private void buildThemeRow() {
        LinearLayout row = findViewById(R.id.themeRow);
        float d = getResources().getDisplayMetrics().density;
        int w = Math.round(224 * d), h = Math.round(126 * d);
        List<String> names = new ArrayList<>();
        // This TV: only the bundled theme (Apple TV) is offered; the "Current" card and the other themes were
        // removed on request (2026-11-07). They are still in the repo's themes folder.
        names.addAll(ThemeLibrary.names(this));
        java.util.Map<String, android.widget.ImageView> images = new java.util.HashMap<>();
        for (String name : names) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);
            card.setClickable(true);
            card.setStateListAnimator(android.animation.AnimatorInflater.loadStateListAnimator(this, R.animator.settings_focus));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMarginEnd(Math.round(20 * d));
            row.addView(card, clp);

            android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
            frame.setDuplicateParentStateEnabled(true);
            card.addView(frame, new LinearLayout.LayoutParams(w, h));
            android.widget.ImageView img = new android.widget.ImageView(this);
            img.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            img.setBackgroundResource(name.isEmpty() ? R.drawable.theme_current_bg : R.drawable.np_art_bg);
            img.setClipToOutline(true);
            frame.addView(img, new android.widget.FrameLayout.LayoutParams(w, h));
            if (name.isEmpty()) {
                TextView inner = new TextView(this);
                inner.setText(R.string.theme_current_inner);
                inner.setTextColor(0xB3FFFFFF);
                inner.setTextSize(15f);
                inner.setGravity(android.view.Gravity.CENTER);
                frame.addView(inner, new android.widget.FrameLayout.LayoutParams(w, h));
            }
            View border = new View(this);
            border.setDuplicateParentStateEnabled(true);
            border.setBackgroundResource(R.drawable.theme_card_frame);
            frame.addView(border, new android.widget.FrameLayout.LayoutParams(w, h));
            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextColor(0xFF111214);
            check.setTextSize(15f);
            check.setGravity(android.view.Gravity.CENTER);
            check.setBackgroundResource(R.drawable.theme_check_bg);
            int c = Math.round(28 * d);
            android.widget.FrameLayout.LayoutParams chk = new android.widget.FrameLayout.LayoutParams(c, c,
                    android.view.Gravity.TOP | android.view.Gravity.END);
            chk.setMargins(0, Math.round(8 * d), Math.round(8 * d), 0);
            frame.addView(check, chk);
            themeChecks.put(name, check);

            TextView label = new TextView(this);
            label.setText(name.isEmpty() ? getString(R.string.theme_current) : ThemeLibrary.label(name));
            label.setTextColor(0xE6FFFFFF);
            label.setTextSize(16f);
            label.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            label.setSingleLine(true);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = Math.round(10 * d);
            card.addView(label, llp);

            card.setOnClickListener(v -> applyTheme(name));
            if (!name.isEmpty()) images.put(name, img);
        }
        updateThemeChecks();
        // Previews off the main thread: 26 screenshots decoded small.
        new Thread(() -> {
            for (java.util.Map.Entry<String, android.widget.ImageView> e : images.entrySet()) {
                android.graphics.Bitmap b = ThemeLibrary.preview(this, e.getKey(), w);
                if (b != null) runOnUiThread(() -> e.getValue().setImageBitmap(b));
            }
        }, "ThemePreviews").start();
    }

    private void updateThemeChecks() {
        String active = ThemeLibrary.active(this);
        for (java.util.Map.Entry<String, View> e : themeChecks.entrySet()) {
            e.getValue().setVisibility(e.getKey().equals(active) ? View.VISIBLE : View.GONE);
        }
    }

    private void applyTheme(String name) {
        if (applyingTheme || name.equals(ThemeLibrary.active(this))) return;
        applyingTheme = true;
        new Thread(() -> {
            boolean ok = ThemeLibrary.apply(this, name);
            runOnUiThread(() -> {
                applyingTheme = false;
                if (!ok) {
                    Toast.makeText(this, R.string.theme_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                changed();
                // A theme resets the on-TV layout choices, as a push does; show the values Home will use.
                Config current = Config.load(this);
                columns = current.columns;
                rowsBelowDock = current.rowsBelowDock;
                dockOpacity = current.dockOpacity;
                clockFormat = current.clockFormat;
                animations = current.animations;
                advancedEffects = current.animations && current.advancedEffects;
                updateThemeChecks();
                refresh();
                Toast.makeText(this, getString(R.string.theme_applied,
                        name.isEmpty() ? getString(R.string.theme_current) : ThemeLibrary.label(name)), Toast.LENGTH_SHORT).show();
            });
        }, "ApplyTheme").start();
    }

    private void putSpot(String keyX, String keyY, float[] spot) {
        choices.edit().putFloat(keyX, spot[0]).putFloat(keyY, spot[1]).apply();
        changed();
        refresh();
    }

    private void putString(String key, String value) {
        choices.edit().putString(key, value).apply();
        changed();
        refresh();
    }

    /** "Custom...": back to Home, which shows the item with a dashed outline to move with the arrows. */
    private void placeOnHome(String what) {
        choices.edit().putString(HeaderPlacement.PLACE_PENDING, what).apply();
        changed();
        finish();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        int clockPreset = HeaderPlacement.presetIndex(HeaderPlacement.CLOCK_PRESETS,
                HeaderPlacement.clockX(this), HeaderPlacement.clockY(this));
        for (int i = 0; i < clockPos.length; i++) clockPos[i].setSelected(clockPreset == i);
        clockPosCustom.setSelected(clockPreset < 0);
        int buttonsPreset = HeaderPlacement.presetIndex(HeaderPlacement.BUTTON_PRESETS,
                HeaderPlacement.buttonsX(this), HeaderPlacement.buttonsY(this));
        for (int i = 0; i < buttonsPos.length; i++) buttonsPos[i].setSelected(buttonsPreset == i);
        buttonsPosCustom.setSelected(buttonsPreset < 0);
        String bar = HeaderPlacement.buttonBar(this, Config.load(this).buttonBar);
        for (int i = 0; i < bars.length; i++) bars[i].setSelected(BARS[i].equals(bar));
        String style = HeaderPlacement.clockStyle(this);
        for (int i = 0; i < styleChoices.length; i++) styleChoices[i].setSelected(STYLES[i].equals(style));
        String size = HeaderPlacement.clockSize(this);
        for (int i = 0; i < sizeChoices.length; i++) sizeChoices[i].setSelected(SIZES[i].equals(size));
        boolean date = HeaderPlacement.showDate(this);
        dateShow.setSelected(date);
        dateHide.setSelected(!date);
        boolean np = HeaderPlacement.mediaShow(this);
        npShow.setSelected(np);
        npHide.setSelected(!np);
        int npPreset = HeaderPlacement.presetIndex(HeaderPlacement.MEDIA_PRESETS,
                HeaderPlacement.mediaX(this), HeaderPlacement.mediaY(this));
        for (int i = 0; i < npPos.length; i++) npPos[i].setSelected(npPreset == i);
        npPosCustom.setSelected(npPreset < 0);
        String npStyle = HeaderPlacement.mediaStyle(this);
        for (int i = 0; i < npStyles.length; i++) npStyles[i].setSelected(NP_STYLES[i].equals(npStyle));
        String npLayout = HeaderPlacement.mediaLayout(this);
        for (int i = 0; i < npLayouts.length; i++) npLayouts[i].setSelected(NP_LAYOUTS[i].equals(npLayout));
        boolean npProg = HeaderPlacement.mediaProgress(this);
        npProgShow.setSelected(npProg);
        npProgHide.setSelected(!npProg);
        boolean npSrc = HeaderPlacement.mediaSource(this);
        npSrcShow.setSelected(npSrc);
        npSrcHide.setSelected(!npSrc);
        String npSize = HeaderPlacement.mediaSize(this);
        for (int i = 0; i < npSizes.length; i++) npSizes[i].setSelected(SIZES[i].equals(npSize));
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY)
                || hasHeaderChoices();
    }

    private boolean hasHeaderChoices() {
        for (String key : HeaderPlacement.KEYS) if (choices.contains(key)) return true;
        return false;
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
