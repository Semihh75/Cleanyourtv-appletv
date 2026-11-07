package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;

/**
 * Home widgets (this TV), in the style of tvOS / iOS widgets: Weather (CITY below, from Open-Meteo every 30 minutes, the
 * last forecast kept for offline starts) and Date (weekday in red, the day large, month and week number). Each can be
 * shown or hidden, placed anywhere (presets or the arrow-key mover) and drawn Clean (text on the wallpaper) or on
 * Glass, all in Customize > Widgets.
 */
final class Widgets {
    static final String[] IDS = {"weather", "date"};
    static final float[][] PRESETS = {{0f, 0f}, {0.5f, 0f}, {0f, 0.22f}};
    // The town comes from launcher/local.properties (weather.city, weather.lat, weather.lon), Cupertino if not set.
    private final String CITY;
    private final String forecastUrl;
    private static final String FORECAST_QUERY = "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min"
            + "&timezone=auto&forecast_days=1";
    private static final long REFRESH_MS = 30 * 60_000L;

    // ---- settings ------------------------------------------------------------------------------------------------

    static SharedPreferences prefs(Activity a) {
        return HeaderPlacement.prefs(a);
    }

    static boolean shown(Activity a, String id) {
        return prefs(a).getBoolean("widget_" + id + "_show", !"date".equals(id));   // Date off by default
    }

    static String style(Activity a, String id) {
        return prefs(a).getString("widget_" + id + "_style", "clean");
    }

    static float x(Activity a, String id) {
        return prefs(a).getFloat("widget_" + id + "_x", 0f);
    }

    static float y(Activity a, String id) {
        return prefs(a).getFloat("widget_" + id + "_y", "date".equals(id) ? 0.22f : 0f);
    }

    static String keyX(String id) {
        return "widget_" + id + "_x";
    }

    static String keyY(String id) {
        return "widget_" + id + "_y";
    }

    // ---- views ---------------------------------------------------------------------------------------------------

    private final Activity activity;
    private final float dp;
    private final Typeface semibold;
    private final Handler main = new Handler(Looper.getMainLooper());
    final View weather, date;
    private ImageView wIcon;
    private TextView wTemp, wLine, wRange;
    private TextView dDay, dNum, dMonth;
    private float[] spotWeather, spotDate;

    Widgets(Activity activity, ViewGroup content) {
        this.activity = activity;
        dp = activity.getResources().getDisplayMetrics().density;
        CITY = activity.getString(R.string.weather_city);
        forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + activity.getString(R.string.weather_lat)
                + "&longitude=" + activity.getString(R.string.weather_lon) + FORECAST_QUERY;
        semibold = ResourcesCompat.getFont(activity, R.font.inter_semibold);
        weather = buildWeather();
        date = buildDate();
        for (View v : new View[]{weather, date}) {
            content.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        }
        spotWeather = new float[]{x(activity, "weather"), y(activity, "weather")};
        spotDate = new float[]{x(activity, "date"), y(activity, "date")};
        applyStyle(weather, style(activity, "weather"));
        applyStyle(date, style(activity, "date"));
        weather.setVisibility(shown(activity, "weather") ? View.VISIBLE : View.GONE);
        date.setVisibility(shown(activity, "date") ? View.VISIBLE : View.GONE);
        showCachedWeather();
        updateDate();
    }

    View view(String id) {
        return "date".equals(id) ? date : weather;
    }

    float[] spot(String id) {
        return "date".equals(id) ? spotDate : spotWeather;
    }

    void setSpot(String id, float sx, float sy) {
        float[] s = spot(id);
        s[0] = Math.max(0f, Math.min(1f, sx));
        s[1] = Math.max(0f, Math.min(1f, sy));
    }

    /** Faded out while the apps below the dock are shown, like Now Playing. */
    void setSuppressed(boolean s) {
        for (View v : new View[]{weather, date}) {
            if (v.getVisibility() == View.VISIBLE) v.animate().alpha(s ? 0f : 1f).setDuration(180).start();
        }
    }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(activity);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(semibold);
        // A soft shadow keeps white text readable on a light wallpaper (Clean style).
        t.setShadowLayer(8 * dp, 0, 1 * dp, 0x66000000);
        return t;
    }

    private View buildWeather() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        wIcon = new ImageView(activity);
        wIcon.setImageResource(R.drawable.ic_w_cloud);
        top.addView(wIcon, new LinearLayout.LayoutParams(Math.round(30 * dp), Math.round(30 * dp)));
        wTemp = text(34, Color.WHITE, true);
        wTemp.setText("--°");
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart(Math.round(10 * dp));
        top.addView(wTemp, tlp);
        box.addView(top);
        wLine = text(15, 0xF2FFFFFF, true);
        wLine.setText(CITY);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        l1.topMargin = Math.round(6 * dp);
        box.addView(wLine, l1);
        wRange = text(13, 0xB3FFFFFF, false);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        l2.topMargin = Math.round(2 * dp);
        box.addView(wRange, l2);
        return box;
    }

    private View buildDate() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        dDay = text(13, 0xFFFF453A, true);   // Apple's calendar red
        dDay.setLetterSpacing(0.08f);
        box.addView(dDay);
        dNum = text(40, Color.WHITE, true);
        LinearLayout.LayoutParams n = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        n.topMargin = Math.round(2 * dp);
        box.addView(dNum, n);
        dMonth = text(13, 0xB3FFFFFF, false);
        LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        m.topMargin = Math.round(2 * dp);
        box.addView(dMonth, m);
        return box;
    }

    /** Clean: no background. Glass: a small frosted card like the button bar. */
    private void applyStyle(View v, String style) {
        if ("glass".equals(style)) {
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x33FFFFFF, 0x1AFFFFFF});
            g.setCornerRadius(22 * dp);
            g.setStroke(Math.round(dp), 0x38FFFFFF);
            v.setBackground(g);
            int p = Math.round(16 * dp);
            v.setPadding(Math.round(20 * dp), p, Math.round(22 * dp), p);
        } else {
            v.setBackground(null);
            v.setPadding(0, 0, 0, 0);
        }
    }

    // ---- content -------------------------------------------------------------------------------------------------

    void updateDate() {
        Calendar c = Calendar.getInstance();
        Locale l = Locale.getDefault();
        dDay.setText(c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, l).toUpperCase(l));
        dNum.setText(String.valueOf(c.get(Calendar.DAY_OF_MONTH)));
        Calendar iso = Calendar.getInstance();
        iso.setMinimalDaysInFirstWeek(4);
        iso.setFirstDayOfWeek(Calendar.MONDAY);
        dMonth.setText(c.getDisplayName(Calendar.MONTH, Calendar.LONG, l) + " · Week " + iso.get(Calendar.WEEK_OF_YEAR));
    }

    private final Runnable refresh = this::fetchWeather;

    void start() {
        updateDate();
        main.removeCallbacks(refresh);
        if (weather.getVisibility() != View.VISIBLE) return;
        long age = System.currentTimeMillis() - prefs(activity).getLong("widget_weather_at", 0);
        if (age >= REFRESH_MS) fetchWeather();
        else main.postDelayed(refresh, REFRESH_MS - age);
    }

    void stop() {
        main.removeCallbacks(refresh);
    }

    private void fetchWeather() {
        main.removeCallbacks(refresh);
        main.postDelayed(refresh, REFRESH_MS);
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(forecastUrl).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", "TvHome-launcher");
                if (c.getResponseCode() != 200) return;
                String body;
                try (InputStream in = c.getInputStream()) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int r;
                    while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
                    body = new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
                new JSONObject(body);   // reject anything that is not JSON
                prefs(activity).edit().putString("widget_weather_json", body)
                        .putLong("widget_weather_at", System.currentTimeMillis()).apply();
                main.post(this::showCachedWeather);
            } catch (Exception ignored) {
                // offline or the service is down: the last forecast stays
            }
        }, "weather").start();
    }

    private void showCachedWeather() {
        String body = prefs(activity).getString("widget_weather_json", null);
        if (body == null) {
            wRange.setText("");
            return;
        }
        try {
            JSONObject j = new JSONObject(body);
            JSONObject cur = j.getJSONObject("current");
            JSONObject daily = j.getJSONObject("daily");
            int code = cur.getInt("weather_code");
            boolean day = cur.optInt("is_day", 1) == 1;
            wTemp.setText(Math.round(cur.getDouble("temperature_2m")) + "°");
            wIcon.setImageResource(icon(code, day));
            wLine.setText(CITY + " · " + condition(code));
            wRange.setText("H:" + Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)) + "°  L:"
                    + Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)) + "°");
        } catch (Exception ignored) {
        }
    }

    /** WMO weather codes. */
    private static String condition(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly Cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow Showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }

    private static int icon(int code, boolean day) {
        if (code <= 1) return day ? R.drawable.ic_sun : R.drawable.ic_moon;
        if (code <= 3) return R.drawable.ic_w_cloud;
        if (code == 45 || code == 48) return R.drawable.ic_w_fog;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.ic_w_snow;
        if (code >= 95) return R.drawable.ic_w_thunder;
        return R.drawable.ic_w_rain;
    }
}
