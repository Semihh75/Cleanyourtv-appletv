package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;

/**
 * Soft Apple-TV-like navigation sounds on Home (this TV): a short tick when focus moves after a remote key, and a
 * lower tock on OK. The sounds are original, synthesized for this launcher (res/raw/ui_tick.wav, ui_select.wav).
 * Android's own click sounds are switched off on the TV (settings: system sound_effects_enabled 0), so nothing
 * doubles. Switch: ui_sounds in config.xml.
 */
final class UiSounds {
    private static final float VOLUME = 0.12f;
    /** A focus change counts as a remote move only this soon after a key, not when Home restores focus itself. */
    private static final long KEY_WINDOW_MS = 400;

    private final SoundPool pool;
    private final int tick;
    private final int select;
    private long lastKeyAt;

    UiSounds(Context context) {
        pool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        tick = pool.load(context, R.raw.ui_tick, 1);
        select = pool.load(context, R.raw.ui_select, 1);
    }

    void onKey() {
        lastKeyAt = SystemClock.uptimeMillis();
    }

    void onFocusMoved() {
        if (SystemClock.uptimeMillis() - lastKeyAt > KEY_WINDOW_MS) return;
        pool.play(tick, VOLUME, VOLUME, 1, 0, 1f);
    }

    void onSelect() {
        pool.play(select, VOLUME, VOLUME, 1, 0, 1f);
    }

    void release() {
        pool.release();
    }
}
