package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Held-volume ramp for this TV (Xiaomi MiTV-MZTU0, Google TV 14). With InputKeyService filtering keys (needed
 * for the YouTube, star and Settings buttons), Android stops repeating a held key, and the volume keys are handled
 * by the window policy before the helper sees them, so consume_volume_keys has nothing to repeat. But the Xiaomi
 * window policy logs every volume key, once on press and once on release:
 * "MiTVWindowPolicyInjector: mKeyHandlerManager.handleAction24" (25 for Volume Down). This reader follows that log
 * (READ_LOGS, granted over ADB) and, while a volume key is held, steps the volume itself: the first step is the
 * firmware's own, repeats start after REPEAT_DELAY_MS. A ramp never runs longer than MAX_RAMP_MS, so a lost
 * release line cannot run the volume away.
 */
final class LogKeys {
    private static final Pattern LINE = Pattern.compile("handleAction(\\d+)\\b");
    private static final int KEY_VOLUME_UP = 24;
    private static final int KEY_VOLUME_DOWN = 25;
    private static final long REPEAT_DELAY_MS = 400;
    private static final long REPEAT_INTERVAL_MS = 80;
    private static final long MAX_RAMP_MS = 4000;

    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private Process process;

    /** The volume key currently held (24 or 25), or 0. Touched on the main thread only. */
    private int heldKey;
    private long heldSince;
    private final Runnable step = new Runnable() {
        @Override public void run() {
            if (heldKey == 0) return;
            if (SystemClock.uptimeMillis() - heldSince > MAX_RAMP_MS) { heldKey = 0; return; }
            audio.adjustVolume(heldKey == KEY_VOLUME_UP ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI);
            main.postDelayed(this, REPEAT_INTERVAL_MS);
        }
    };

    LogKeys(Context ctx) {
        audio = ctx.getSystemService(AudioManager.class);
    }

    void start() {
        if (running || audio == null) return;
        running = true;
        Thread t = new Thread(this::loop, "LogKeys");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        running = false;
        Process p = process;
        if (p != null) p.destroy();
        main.post(this::release);
    }

    /** Waits after a reader that ended within 30 s (Android's log-access prompt declined or not answered), so the
     *  prompt does not come back every few seconds: 30 s, 2 min, 10 min, then every 30 min. A reader that ran
     *  longer was working, so the next one starts after 2 s as before. */
    private static final long[] BACKOFF_MS = {2_000, 30_000, 120_000, 600_000, 1_800_000};
    /** Static: the accessibility helper can be restarted (and LogKeys with it), which must not reset the wait. */
    private static int failures;
    /** elapsedRealtime before which no new reader starts. */
    private static long nextTry;

    private void loop() {
        while (running) {
            long wait = nextTry - SystemClock.elapsedRealtime();
            if (wait > 0) { SystemClock.sleep(Math.min(wait, 1_000)); continue; }   // short naps, so stop() is noticed
            long began = SystemClock.elapsedRealtime();
            try {
                // -T 1: start at the newest line, so a restarted reader does not replay old presses.
                process = new ProcessBuilder("logcat", "-v", "brief", "-T", "1",
                        "-s", "MiTVWindowPolicyInjector:D").redirectErrorStream(true).start();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while (running && (line = in.readLine()) != null) {
                        Matcher m = LINE.matcher(line);
                        if (!m.find()) continue;
                        int code = Integer.parseInt(m.group(1));
                        main.post(() -> onKeyLine(code));
                    }
                }
            } catch (Exception ignored) {
            }
            if (!running) break;   // stopped on purpose: not a failure
            // logcat ended (killed, or no log access): try again.
            boolean quick = SystemClock.elapsedRealtime() - began < 30_000;
            failures = quick ? Math.min(failures + 1, BACKOFF_MS.length - 1) : 0;
            nextTry = SystemClock.elapsedRealtime() + BACKOFF_MS[failures];
        }
    }

    /** Lines alternate press, release for the same key; any other key ends a ramp. */
    private void onKeyLine(int code) {
        if (code != KEY_VOLUME_UP && code != KEY_VOLUME_DOWN) {
            release();
            return;
        }
        if (heldKey == code) {               // the release of the key being held
            release();
            return;
        }
        release();                           // a different volume key: start over
        heldKey = code;
        heldSince = SystemClock.uptimeMillis();
        main.postDelayed(step, REPEAT_DELAY_MS);
    }

    private void release() {
        heldKey = 0;
        main.removeCallbacks(step);
    }
}
