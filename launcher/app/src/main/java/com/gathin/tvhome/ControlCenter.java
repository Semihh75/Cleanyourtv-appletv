package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Control Center (this TV): a tvOS-style panel that drops down under the button bar. Power opens Android's power
 * menu; Wi-Fi shows the network and opens Wi-Fi settings (Android 14 does not let an app switch Wi-Fi itself);
 * the sleep timer turns the TV off after 15 to 120 minutes; the backlight slider sets the screen brightness, which
 * drives the panel backlight on this TV; and Customize and Settings tiles open those screens (Inputs stays in the bar).
 */
final class ControlCenter {
    private static final String PREFS = "control_center";
    private static final String SLEEP_AT = "sleep_at";
    private static final int[] SLEEP_STEPS = {0, 15, 30, 60, 90, 120};
    // 0% is the dimmest level that still lights the panel: at 3 of 255 this TV switched the backlight off and the
    // screen went black (2026-11-07).
    private static final int MIN_BRIGHTNESS = 25, MAX_BRIGHTNESS = 255;

    private ControlCenter() {
    }

    static void show(Activity activity, Runnable openCustomize) {
        show(activity, openCustomize, true);
    }

    /**
     * On Home. takeFocus false (the bar's button): the panel opens while the button keeps the focus; Down from the
     * button goes in, Up from the top row goes back out, OK on the button closes it.
     */
    static void show(Activity activity, Runnable openCustomize, boolean takeFocus) {
        // Frosted glass: the screen behind is copied and blurred first, then the panel opens on it.
        FrostedGlass.capture(activity, blurred -> {
            Dialog d = open(activity, openCustomize, blurred, false);
            if (d == null) return;
            if (takeFocus && d.getWindow() != null) {
                d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);   // focus on Backlight
            } else {
                pillMode = true;
                blockFocus(openSheet);   // nothing in the panel looks selected while the button has the focus
            }
        });
    }

    private static Dialog openDialog;
    private static View openSheet;
    /** The open panel was opened from the bar's button and gives the focus back to it (see show). */
    private static boolean pillMode;

    static boolean isOpen() {
        return openDialog != null && openDialog.isShowing();
    }

    /** Down from the bar's button: into the panel, on its first item. */
    static void enterPanel() {
        if (!isOpen() || openDialog.getWindow() == null) return;
        openDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        View sheet = openSheet;
        ((android.view.ViewGroup) sheet).setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        sheet.post(() -> {
            View first = android.view.FocusFinder.getInstance()
                    .findNextFocus((android.view.ViewGroup) sheet, null, View.FOCUS_DOWN);
            if (first != null) first.requestFocus();
        });
    }

    /** Up from the top row in pill mode: the focus goes back to the bar's button, the panel stays open. */
    private static void leavePanel() {
        if (openDialog != null && openDialog.getWindow() != null) {
            blockFocus(openSheet);
            openDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }

    /** No item in the panel holds (or shows) the focus. */
    static void blockFocus(View sheet) {
        if (!(sheet instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup g = (android.view.ViewGroup) sheet;
        View f = g.findFocus();
        if (f != null) f.clearFocus();
        g.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        View f2 = g.findFocus();
        if (f2 != null) f2.clearFocus();
        g.invalidate();
    }

    /** Over another app (the remote's Control Center button): the screen comes from the remote helper. */
    static void showOver(Activity activity, Runnable onClosed) {
        Runnable customize = () -> activity.startActivity(new Intent(activity, LauncherSettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        InputKeyService.blurredScreen(blurred -> {
            if (activity.isFinishing()) return;
            Dialog d = open(activity, customize, blurred, true);
            if (d == null) {
                onClosed.run();
                return;
            }
            closedHook = onClosed;
        });
    }

    /** Folds the open panel away; null while none is open (the remote's button then opens one). */
    private static Runnable closeOpen;

    /** The remote's Control Center button pressed again: close the panel. False when none is open. */
    static boolean closeIfOpen() {
        Runnable c = closeOpen;
        if (c == null) return false;
        c.run();
        return true;
    }

    /** Run once when the panel shown by showOver closes. */
    private static Runnable closedHook;

    private static Dialog open(Activity activity, Runnable openCustomize, android.graphics.Bitmap blurred,
                               boolean overApp) {
        if (activity.isFinishing()) return null;
        Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(R.layout.control_center);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.TOP | Gravity.END);
            WindowManager.LayoutParams lp = w.getAttributes();
            float dp = activity.getResources().getDisplayMetrics().density;
            // On Home under the button bar, its right edge in line with the bar's; over an app (no bar) docked in
            // the top-right corner, as on Apple TV.
            lp.x = Math.round((overApp ? 32 : 44) * dp);
            lp.y = Math.round((overApp ? 28 : 80) * dp);
            lp.dimAmount = 0f;   // no darkening behind the panel (this TV, on request)
            lp.windowAnimations = 0;   // the panel animates itself (slide in from the corner)
            w.setAttributes(lp);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            if (!overApp) w.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);   // see show(..., takeFocus)
        }

        View power = d.findViewById(R.id.ccPower);
        power.setOnClickListener(v -> {
            d.dismiss();
            if (!InputKeyService.powerMenu()) {
                Toast.makeText(activity, "Power menu needs the remote helper (accessibility service)",
                        Toast.LENGTH_LONG).show();
            }
        });

        TextView wifiState = d.findViewById(R.id.ccWifiState);
        wifiState.setText(wifiName(activity));
        d.findViewById(R.id.ccWifi).setOnClickListener(v -> {
            d.dismiss();
            RemoteKeys.action(activity, "wifi");
        });

        TextView sleepState = d.findViewById(R.id.ccSleepState);
        sleepState.setText(sleepLabel(activity));
        d.findViewById(R.id.ccSleep).setOnClickListener(v -> {
            cycleSleep(activity);
            sleepState.setText(sleepLabel(activity));
        });

        BacklightSlider slider = d.findViewById(R.id.ccBacklight);
        slider.setLevel(backlightPercent(activity));
        slider.setListener(percent -> {
            if (!setBacklight(activity, percent)) {
                Toast.makeText(activity, R.string.cc_no_access, Toast.LENGTH_LONG).show();
            }
        });

        // Volume: the media stream, in 5% steps (the TV's own volume bar shows too).
        android.media.AudioManager audio = activity.getSystemService(android.media.AudioManager.class);
        BacklightSlider volume = d.findViewById(R.id.ccVolume);
        volume.setIconAndLabel(R.drawable.ic_sound, activity.getString(R.string.cc_volume));
        if (audio != null) {
            int max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            volume.setLevel(Math.round(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100f / max));
            // One tick per press, the way the remote's volume buttons do it: "set to X" is ignored here when the
            // sound goes to a soundbar over HDMI ARC, and 5% jumps were too big. The level follows the change live.
            volume.setStepListener(direction -> audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                    direction > 0 ? android.media.AudioManager.ADJUST_RAISE : android.media.AudioManager.ADJUST_LOWER, 0));
        }

        // Audio: where the sound goes now; OK opens the TV's sound settings to change it (Android does not let an
        // app pick the output).
        TextView audioState = d.findViewById(R.id.ccAudioState);
        audioState.setText(audioOutput(activity, audio));
        d.findViewById(R.id.ccAudio).setOnClickListener(v -> {
            d.dismiss();
            KeepPlaying.aroundSettings(activity);
            try {
                activity.startActivity(SystemSettings.intent(activity, Settings.ACTION_SOUND_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                RemoteKeys.action(activity, "settings");
            }
        });

        // Night Shift: on or off; the hours and the level are in Customize > Display.
        TextView nightState = d.findViewById(R.id.ccNightState);
        Runnable nightLabel = () -> nightState.setText(!NightShift.enabled(activity) ? activity.getString(R.string.cc_off)
                : NightShift.isNight(activity) ? activity.getString(R.string.cc_on_now) : NightShift.window(activity));
        nightLabel.run();
        d.findViewById(R.id.ccNight).setOnClickListener(v -> {
            NightShift.setEnabled(activity, !NightShift.enabled(activity));
            nightLabel.run();
            slider.setLevel(backlightPercent(activity));
        });

        MusicRow music = new MusicRow(activity, d);

        // Live: volume changed with the remote, or brightness by Night Shift, moves the sliders while the panel is
        // open. Volume comes as a broadcast; both are also in Settings.System, watched as a fallback.
        android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable refresh = () -> {
            if (audio != null) {
                int max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                volume.setLevel(Math.round(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100f / max));
            }
            slider.setLevel(backlightPercent(activity));
        };
        android.content.BroadcastReceiver volumeChanged = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                ui.post(refresh);
            }
        };
        android.content.IntentFilter vf = new android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        vf.addAction("android.media.STREAM_MUTE_CHANGED_ACTION");
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(volumeChanged, vf, Context.RECEIVER_EXPORTED);
        } else {
            activity.registerReceiver(volumeChanged, vf);
        }
        android.database.ContentObserver systemChanged = new android.database.ContentObserver(ui) {
            @Override
            public void onChange(boolean selfChange) {
                refresh.run();
            }
        };
        activity.getContentResolver().registerContentObserver(Settings.System.CONTENT_URI, true, systemChanged);
        d.setOnDismissListener(x -> {
            music.stop();
            try {
                activity.unregisterReceiver(volumeChanged);
            } catch (IllegalArgumentException ignored) {
            }
            activity.getContentResolver().unregisterContentObserver(systemChanged);
            closeOpen = null;
            if (openDialog == d) {
                openDialog = null;
                openSheet = null;
                pillMode = false;
            }
            Runnable closed = closedHook;
            closedHook = null;
            if (closed != null) closed.run();
        });

        d.findViewById(R.id.ccCustomize).setOnClickListener(v -> {
            d.dismiss();
            openCustomize.run();
        });
        d.findViewById(R.id.ccSettings).setOnClickListener(v -> {
            d.dismiss();
            RemoteKeys.action(activity, "settings");
        });

        d.show();
        // Drops down from the button bar (top-right) on a soft spring, like a menu out of the pill; Back folds it
        // back up before it closes. The same on Home and over an app.
        View sheet = ((android.view.ViewGroup) d.findViewById(android.R.id.content)).getChildAt(0);
        float px = activity.getResources().getDisplayMetrics().density;
        sheet.setPivotX(300 * px);
        sheet.setPivotY(0f);
        sheet.setAlpha(0f);
        sheet.setTranslationY(-36 * px);
        sheet.setScaleX(0.96f);
        sheet.setScaleY(0.90f);
        sheet.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(380)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.8f)).start();
        Runnable foldAway = () -> sheet.animate().alpha(0f).translationY(-28 * px).scaleX(0.97f).scaleY(0.92f)
                .setDuration(170).setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f))
                .withEndAction(d::dismiss).start();
        closeOpen = foldAway;
        openDialog = d;
        openSheet = sheet;
        pillMode = false;
        d.setOnKeyListener((dialog, keyCode, event) -> {
            // Up from the top row goes back to the button it came from: the panel folds away (a toggle for the
            // bar's button, whose focus the panel holds while open).
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                View f = d.getCurrentFocus();
                if (f == null || android.view.FocusFinder.getInstance()
                        .findNextFocus((android.view.ViewGroup) sheet, f, View.FOCUS_UP) != null) return false;
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    if (pillMode && openDialog == d) leavePanel();
                    else foldAway.run();
                }
                return true;
            }
            if (keyCode != android.view.KeyEvent.KEYCODE_BACK) return false;
            if (event.getAction() == android.view.KeyEvent.ACTION_UP && !event.isCanceled()) foldAway.run();
            return true;
        });
        // The panel's glass: the blurred screen behind it, cut to where it sits, under a light tint and rim.
        View panel = ((android.view.ViewGroup) d.findViewById(android.R.id.content)).getChildAt(0);
        float density = activity.getResources().getDisplayMetrics().density;
        FrostedGlass.PanelDrawable glass = new FrostedGlass.PanelDrawable(blurred, 22 * density, density);
        panel.setBackground(glass);
        if (Appearance.isLight(activity)) {
            glass.setLight(true);
            Appearance.inkAll(panel);
            int[] tiles = {R.id.ccPower, R.id.ccWifi, R.id.ccSleep, R.id.ccAudio, R.id.ccNight, R.id.ccCustomize,
                    R.id.ccSettings};
            for (int id : tiles) d.findViewById(id).setBackground(Appearance.lightTile(16 * density, false));
            int[] round = {R.id.ccPrev, R.id.ccPlay, R.id.ccNext};
            for (int id : round) d.findViewById(id).setBackground(Appearance.lightTile(0, true));
            android.graphics.drawable.GradientDrawable strip = new android.graphics.drawable.GradientDrawable();
            strip.setColor(0x1F000000);
            strip.setCornerRadius(16 * density);
            d.findViewById(R.id.ccMusic).setBackground(strip);
        }
        // Every frame, so the blur stays put behind the panel while it slides in and out.
        panel.getViewTreeObserver().addOnPreDrawListener(() -> {
            int[] at = new int[2];
            panel.getLocationOnScreen(at);
            glass.setScreenPosition(at[0], at[1]);
            return true;
        });
        // The backlight is what this panel is mostly for at night: start there.
        slider.requestFocus();
        return d;
    }

    // ---- Audio ----------------------------------------------------------------------------------------------

    /** Where the sound goes: a connected Bluetooth device or soundbar wins over the TV's own speakers. */
    private static String audioOutput(Context ctx, android.media.AudioManager audio) {
        if (audio == null) return "";
        String arc = null, wired = null;
        for (android.media.AudioDeviceInfo dev : audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (dev.getType()) {
                case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                    CharSequence name = dev.getProductName();
                    return name != null && name.length() > 0 ? name.toString() : "Bluetooth";
                case android.media.AudioDeviceInfo.TYPE_HDMI_ARC:
                case android.media.AudioDeviceInfo.TYPE_HDMI_EARC:
                    arc = ctx.getString(R.string.cc_arc);
                    break;
                case android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case android.media.AudioDeviceInfo.TYPE_USB_HEADSET:
                    wired = ctx.getString(R.string.cc_headphones);
                    break;
                default:
                    break;
            }
        }
        return wired != null ? wired : arc != null ? arc : ctx.getString(R.string.cc_speakers);
    }

    /** Control Center's Now Playing row: the playing (or paused) app's track, with previous, play/pause, next. */
    private static final class MusicRow {
        private final View row;
        private final android.widget.ImageView art, play;
        private final TextView title, artist;
        private android.media.session.MediaController controller;
        private final android.media.session.MediaController.Callback callback =
                new android.media.session.MediaController.Callback() {
                    @Override
                    public void onPlaybackStateChanged(android.media.session.PlaybackState state) {
                        bind();
                    }

                    @Override
                    public void onMetadataChanged(android.media.MediaMetadata metadata) {
                        bind();
                    }
                };

        MusicRow(Activity activity, Dialog d) {
            row = d.findViewById(R.id.ccMusic);
            art = d.findViewById(R.id.ccMusicArt);
            art.setClipToOutline(true);
            play = d.findViewById(R.id.ccPlay);
            title = d.findViewById(R.id.ccMusicTitle);
            artist = d.findViewById(R.id.ccMusicArtist);
            try {
                android.media.session.MediaSessionManager msm =
                        activity.getSystemService(android.media.session.MediaSessionManager.class);
                java.util.List<android.media.session.MediaController> list = msm.getActiveSessions(
                        new android.content.ComponentName(activity, NowPlayingListener.class));
                for (android.media.session.MediaController c : list) {
                    android.media.session.PlaybackState s = c.getPlaybackState();
                    if (s != null && s.getState() == android.media.session.PlaybackState.STATE_PLAYING) {
                        controller = c;
                        break;
                    }
                }
                if (controller == null) {
                    for (android.media.session.MediaController c : list) {
                        if (c.getMetadata() != null && c.getPlaybackState() != null) {
                            controller = c;
                            break;
                        }
                    }
                }
            } catch (SecurityException noAccess) {
                controller = null;
            }
            if (controller == null) return;
            controller.registerCallback(callback, new android.os.Handler(android.os.Looper.getMainLooper()));
            d.findViewById(R.id.ccPrev).setOnClickListener(v -> controller.getTransportControls().skipToPrevious());
            d.findViewById(R.id.ccNext).setOnClickListener(v -> controller.getTransportControls().skipToNext());
            play.setOnClickListener(v -> {
                android.media.session.PlaybackState s = controller.getPlaybackState();
                if (s != null && s.getState() == android.media.session.PlaybackState.STATE_PLAYING) {
                    controller.getTransportControls().pause();
                } else {
                    controller.getTransportControls().play();
                }
            });
            bind();
        }

        private void bind() {
            if (controller == null) return;
            android.media.MediaMetadata m = controller.getMetadata();
            if (m == null) {
                row.setVisibility(View.GONE);
                return;
            }
            row.setVisibility(View.VISIBLE);
            CharSequence t = m.getText(android.media.MediaMetadata.METADATA_KEY_TITLE);
            CharSequence a = m.getText(android.media.MediaMetadata.METADATA_KEY_ARTIST);
            title.setText(t != null ? t : "");
            artist.setText(a != null ? a : "");
            android.graphics.Bitmap bmp = m.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (bmp == null) bmp = m.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART);
            art.setImageBitmap(bmp);
            android.media.session.PlaybackState s = controller.getPlaybackState();
            boolean playing = s != null && s.getState() == android.media.session.PlaybackState.STATE_PLAYING;
            play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        }

        void stop() {
            if (controller != null) controller.unregisterCallback(callback);
            controller = null;
        }
    }

    // ---- Wi-Fi ----------------------------------------------------------------------------------------------

    private static String wifiName(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) return ctx.getString(R.string.cc_off);
            WifiInfo info = wm.getConnectionInfo();
            String ssid = info != null ? info.getSSID() : null;
            if (info == null || info.getNetworkId() == -1) return ctx.getString(R.string.cc_wifi_off);
            if (ssid != null) {
                ssid = ssid.replace("\"", "").trim();
                if (!ssid.isEmpty() && !ssid.contains("unknown")) return ssid;
            }
            return "On";
        } catch (Exception e) {
            return "";
        }
    }

    // ---- Backlight ------------------------------------------------------------------------------------------

    static int backlightPercent(Context ctx) {
        int b = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, MAX_BRIGHTNESS);
        int p = Math.round((b - MIN_BRIGHTNESS) * 100f / (MAX_BRIGHTNESS - MIN_BRIGHTNESS));
        return Math.max(0, Math.min(100, Math.round(p / 5f) * 5));
    }

    /** Sets the screen brightness (the panel backlight here); false without the "modify system settings" grant. */
    static boolean setBacklight(Context ctx, int percent) {
        if (!Settings.System.canWrite(ctx)) return false;
        int b = MIN_BRIGHTNESS + Math.round((MAX_BRIGHTNESS - MIN_BRIGHTNESS) * percent / 100f);
        try {
            Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            // Remembered, so BacklightGuard can put it back when the TV's picture service overwrites it.
            BacklightGuard.remember(ctx, b);
            return Settings.System.putInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, b);
        } catch (SecurityException e) {
            return false;
        }
    }

    // ---- Sleep timer ----------------------------------------------------------------------------------------

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Minutes left, rounded up, or 0 when no timer runs. */
    private static int minutesLeft(Context ctx) {
        long at = prefs(ctx).getLong(SLEEP_AT, 0);
        long left = at - System.currentTimeMillis();
        return left <= 0 ? 0 : (int) ((left + 59_999) / 60_000);
    }

    private static String sleepLabel(Context ctx) {
        int left = minutesLeft(ctx);
        return left == 0 ? ctx.getString(R.string.cc_off) : ctx.getString(R.string.cc_minutes, left);
    }

    /** OK steps through Off, 15, 30, 60, 90 and 120 minutes; from a running timer it goes to the next longer step. */
    private static void cycleSleep(Context ctx) {
        int left = minutesLeft(ctx);
        int next = 0;
        for (int step : SLEEP_STEPS) {
            if (step > left) { next = step; break; }
        }
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, new Intent(ctx, SleepReceiver.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        if (next == 0) {
            if (am != null) am.cancel(pi);
            prefs(ctx).edit().remove(SLEEP_AT).apply();
            return;
        }
        long at = System.currentTimeMillis() + next * 60_000L;
        prefs(ctx).edit().putLong(SLEEP_AT, at).apply();
        // Within a minute of the time is close enough for a sleep timer, and needs no exact-alarm permission.
        if (am != null) am.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000L, pi);
    }

    /** The sleep timer ran out: the TV goes to sleep through the remote helper. */
    public static final class SleepReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            prefs(ctx).edit().remove(SLEEP_AT).apply();
            if (!InputKeyService.sleepNow()) InputKeyService.powerMenu();
        }
    }
}
