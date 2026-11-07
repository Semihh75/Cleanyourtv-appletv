package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.Collections;
import java.util.Map;

/**
 * Maps the remote's Input button to the system input switcher, from any app. Remotes such as the Chromecast voice
 * remote send KEYCODE_TV for it, others (KTC and Kalley Realtek Google TV remotes) KEYCODE_TV_INPUT; on Google TV
 * the stock launcher handles both, so with the stock launcher disabled the button does nothing. Buttons listed in
 * input_key_keycodes (config.xml or a theme) open the input switcher the same way, and app_keys start an app (a
 * YouTube button that only the stock launcher answered). Every other key passes through untouched.
 * Opt-in: INPUT_KEY_SERVICE=1 build-and-install.sh (or -InputKeyService on Windows) enables it.
 */
public class InputKeyService extends AccessibilityService {

    private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    private int[] inputKeycodes = new int[0];
    private Map<Integer, String> appKeys = Collections.emptyMap();
    private Map<Integer, String> appScancodes = Collections.emptyMap();
    private Map<Integer, String> holdAppKeys = Collections.emptyMap();
    private Map<Integer, String> holdAppScancodes = Collections.emptyMap();
    /** restore_key_services was on when the config was read: this helper is noted for ServiceRepair. */
    private boolean repairRecord;
    /** consume_volume_keys: Volume Up/Down go to VolumeKeys instead of on to the firmware. */
    private boolean consumeVolumeKeys;
    /** Stops key filtering while a game controller is connected; null unless gamepad_passthrough and key_filter. */
    private GamepadPassthrough gamepads;
    /** Reads the Xiaomi key log for app_keys while key_filter is off; null otherwise. */
    private LogKeys logKeys;
    /** The running helper, for Control Center: the power menu and the sleep timer go through it. */
    private static InputKeyService running;
    private BacklightGuard guard;

    /** Opens Android's power menu (Power off, Restart); false when the helper is not running. */
    static boolean powerMenu() {
        InputKeyService s = running;
        return s != null && s.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG);
    }

    /**
     * A small blurred copy of the screen (the app being watched) for Control Center's glass over another app; null
     * when the helper is off, Android is older than 11 or the app forbids screenshots (then the panel is plain).
     */
    static void blurredScreen(java.util.function.Consumer<android.graphics.Bitmap> done) {
        InputKeyService s = running;
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        if (s == null || android.os.Build.VERSION.SDK_INT < 30) {
            main.post(() -> done.accept(null));
            return;
        }
        try {
            s.takeScreenshot(android.view.Display.DEFAULT_DISPLAY, s.getMainExecutor(), new TakeScreenshotCallback() {
                @Override
                public void onSuccess(ScreenshotResult result) {
                    android.graphics.Bitmap out = null;
                    try {
                        android.graphics.Bitmap hw = android.graphics.Bitmap.wrapHardwareBuffer(
                                result.getHardwareBuffer(), result.getColorSpace());
                        if (hw != null) {
                            // The screenshot is 4K; FrostedGlass reads it at 1/8 of the 1080p layout size.
                            int w = Math.max(1, s.getResources().getDisplayMetrics().widthPixels / 8);
                            int h = Math.max(1, s.getResources().getDisplayMetrics().heightPixels / 8);
                            android.graphics.Bitmap soft = hw.copy(android.graphics.Bitmap.Config.ARGB_8888, false);
                            out = android.graphics.Bitmap.createScaledBitmap(soft, w, h, true)
                                    .copy(android.graphics.Bitmap.Config.ARGB_8888, true);
                            soft.recycle();
                            FrostedGlass.blur(out, 3, 3);
                        }
                        result.getHardwareBuffer().close();
                    } catch (Exception ignored) {
                        out = null;
                    }
                    done.accept(out);
                }

                @Override
                public void onFailure(int errorCode) {
                    done.accept(null);
                }
            });
        } catch (Exception e) {
            main.post(() -> done.accept(null));
        }
    }

    /** Puts the TV to sleep (the lock-screen action turns the screen off here); false without the helper. */
    static boolean sleepNow() {
        InputKeyService s = running;
        return s != null && android.os.Build.VERSION.SDK_INT >= 28 && s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
        guard = BacklightGuard.start(this);
        // From config.xml or a theme's config.json; theme.sh rebinds this service after a push.
        Config config = Config.load(this);
        // restore_key_services: note that this helper runs, so Home can put it back after a force stop.
        repairRecord = config.restoreKeyServices;
        if (repairRecord) ServiceRepair.connected(this, getClass());
        inputKeycodes = config.inputKeycodes;
        consumeVolumeKeys = config.consumeVolumeKeys;
        appKeys = RemoteKeys.parseAppKeys(config.appKeys);
        appScancodes = RemoteKeys.parseAppScancodes(config.appKeys);
        RemoteKeys.applyKeyFilter(this, config.keyFilter);
        // This TV: key filtering stops Android repeating a held volume key, so follow the Xiaomi key log and
        // repeat it here (LogKeys; needs READ_LOGS). Switch: log_volume_ramp in config.xml.
        if (config.keyFilter && getResources().getBoolean(R.bool.log_volume_ramp) && logKeys == null) {
            logKeys = new LogKeys(this);
            logKeys.start();
        }
        holdAppKeys = RemoteKeys.parseHoldAppKeys(config.appKeys);
        holdAppScancodes = RemoteKeys.parseHoldAppScancodes(config.appKeys);
        // gamepad_passthrough, off by default. Only with key_filter on: off, the service never filters keys.
        if (config.gamepadPassthrough && config.keyFilter && gamepads == null) {
            gamepads = new GamepadPassthrough(this);
            gamepads.start();
        }
    }

    private boolean isInputKey(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_TV || keyCode == KeyEvent.KEYCODE_TV_INPUT) return true;
        for (int code : inputKeycodes) if (keyCode == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (consumeVolumeKeys && VolumeKeys.handle(this, event)) return true;
        // A scancode entry is more specific than any keycode, so it goes first.
        if (RemoteKeys.handle(this, event, appScancodes.get(event.getScanCode()),
                holdAppScancodes.get(event.getScanCode()))) return true;
        if (RemoteKeys.handle(this, event, appKeys.get(event.getKeyCode()),
                holdAppKeys.get(event.getKeyCode()))) return true;
        if (!isInputKey(event.getKeyCode())) return false;
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            try {
                startActivity(new Intent(ACTION_VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
            }
        }
        return true;
    }

    /** Unbound by Android: the user turned the helper off, so Home doesn't put it back (ServiceRepair). */
    @Override
    public boolean onUnbind(Intent intent) {
        RemoteKeys.release(this);
        if (repairRecord) ServiceRepair.disconnected(this, getClass());
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        if (guard != null) guard.stop();
        if (logKeys != null) logKeys.stop();
        RemoteKeys.release(this);
        if (gamepads != null) gamepads.stop();
        if (consumeVolumeKeys) VolumeKeys.stop();
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
