package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Themes picked on the TV (Customize > Themes, this TV). The repo's themes ship inside the APK (assets/themes/<name>:
 * config.json, screenshot.jpg, wallpaper.jpg, wallpapers/). Applying one writes the same files the theme tools push
 * into Config.dir and a new theme_revision, so Home treats it like a push. The owner's apps stay: keys that name apps
 * (dock_apps, hidden_apps, ...) are left out. "Current" puts back what was there before the first switch.
 */
final class ThemeLibrary {
    static final String ACTIVE_KEY = "active_theme";           // "" = the owner's own setup
    private static final String SNAPSHOT_KEY = "theme_snapshot_taken";
    private static final String ASSETS = "themes";
    /** Keys that name the owner's apps or would hide the Customize button: never taken from a library theme. */
    private static final List<String> KEEP_OWN = Arrays.asList("dock_apps", "hidden_apps", "app_keys",
            "top_bar_apps", "input_key_keycodes", "live_tv_package", "launch_actions", "app_banners", "top_menu",
            "greeting_name");
    private static final String[] THEME_FILES = {Config.FILE_NAME, Config.WALLPAPER_NAME};

    private ThemeLibrary() {
    }

    static List<String> names(Context ctx) {
        try {
            String[] list = ctx.getAssets().list(ASSETS);
            List<String> out = new ArrayList<>();
            if (list != null) for (String n : list) out.add(n);
            java.util.Collections.sort(out);
            return out;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /** "glass-gallery" -> "Glass Gallery", "istanbul_v2" -> "Istanbul V2". */
    static String label(String name) {
        StringBuilder b = new StringBuilder();
        for (String w : name.split("[-_ ]+")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            if (w.equalsIgnoreCase("tv")) { b.append("TV"); continue; }   // "apple-tv" -> "Apple TV"
            b.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return b.toString();
    }

    /** The theme's screenshot, decoded small for its card. */
    static Bitmap preview(Context ctx, String name, int widthPx) {
        String path = ASSETS + "/" + name + "/screenshot.jpg";
        try (InputStream in = ctx.getAssets().open(path)) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2;
            try (InputStream in2 = ctx.getAssets().open(path)) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                return BitmapFactory.decodeStream(in2, null, opts);
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static String active(Context ctx) {
        return prefs(ctx).getString(ACTIVE_KEY, "");
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    /** Applies a library theme, or with name "" puts back the owner's own setup. Runs off the main thread. */
    static boolean apply(Context ctx, String name) {
        File dir = Config.dir(ctx);
        if (dir == null) return false;
        // The new theme's button bar (button_bar) shows, not a bar picked earlier for another look.
        HeaderPlacement.prefs(ctx).edit().remove(HeaderPlacement.BUTTON_BAR).apply();
        File saved = new File(ctx.getFilesDir(), "theme_saved");
        try {
            if (!prefs(ctx).getBoolean(SNAPSHOT_KEY, false)) {
                // The first switch keeps a copy of what is there now, for "Current".
                deleteTree(saved);
                copyThemeFiles(dir, saved);
                prefs(ctx).edit().putBoolean(SNAPSHOT_KEY, true).commit();
            }
            clearTheme(dir);
            if (name.isEmpty()) {
                copyThemeFiles(saved, dir);
            } else {
                AssetManager assets = ctx.getAssets();
                String base = ASSETS + "/" + name;
                writeConfig(assets, base + "/" + Config.FILE_NAME, new File(dir, Config.FILE_NAME));
                copyAsset(assets, base + "/" + Config.WALLPAPER_NAME, new File(dir, Config.WALLPAPER_NAME));
                String[] walls = assets.list(base + "/wallpapers");
                if (walls != null && walls.length > 0) {
                    File wd = new File(dir, "wallpapers");
                    wd.mkdirs();
                    for (String w : walls) copyAsset(assets, base + "/wallpapers/" + w, new File(wd, w));
                }
            }
            // A new revision: Home reloads the theme, as after a push from the theme tools.
            try (OutputStream out = new FileOutputStream(new File(dir, "theme_revision"))) {
                out.write(("tv-" + System.currentTimeMillis()).getBytes(StandardCharsets.US_ASCII));
            }
            prefs(ctx).edit().putString(ACTIVE_KEY, name).commit();
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static void writeConfig(AssetManager assets, String path, File out) throws IOException {
        String text;
        try (InputStream in = assets.open(path)) {
            text = new String(readAll(in), StandardCharsets.UTF_8);
        }
        try {
            JSONObject json = new JSONObject(text);
            for (String k : KEEP_OWN) json.remove(k);
            text = json.toString(2);
        } catch (org.json.JSONException e) {
            return;   // an unreadable theme config: leave none rather than a broken one
        }
        try (OutputStream o = new FileOutputStream(out)) {
            o.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copyAsset(AssetManager assets, String path, File out) {
        try (InputStream in = assets.open(path); OutputStream o = new FileOutputStream(out)) {
            copy(in, o);
        } catch (IOException ignored) {
            // the theme has no such file
        }
    }

    private static void clearTheme(File dir) {
        for (String f : THEME_FILES) new File(dir, f).delete();
        deleteTree(new File(dir, "wallpapers"));
    }

    private static void copyThemeFiles(File from, File to) throws IOException {
        to.mkdirs();
        for (String f : THEME_FILES) {
            File src = new File(from, f);
            if (src.isFile()) copyFile(src, new File(to, f));
        }
        File walls = new File(from, "wallpapers");
        File[] list = walls.listFiles();
        if (list != null && list.length > 0) {
            File wd = new File(to, "wallpapers");
            wd.mkdirs();
            for (File w : list) if (w.isFile()) copyFile(w, new File(wd, w.getName()));
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
            copy(in, out);
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        copy(in, b);
        return b.toByteArray();
    }
}
