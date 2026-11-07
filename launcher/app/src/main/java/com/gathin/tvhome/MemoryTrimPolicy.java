package com.gathin.tvhome;

import android.content.ComponentCallbacks2;

/** Memory that affects the picture is released only when Home is not on screen. */
final class MemoryTrimPolicy {
    private MemoryTrimPolicy() {
    }

    static boolean shouldRelease(int level, boolean homeStarted) {
        return shouldRelease(level, homeStarted, false);
    }

    /**
     * keepTiles (config keep_tiles_in_memory, this TV): hold the tiles through ordinary background trims, which
     * arrive each time another app takes the screen and made Home rebuild every tile and banner on the way back
     * (slow first seconds after SmartTube). Only a critical level still releases them.
     */
    static boolean shouldRelease(int level, boolean homeStarted, boolean keepTiles) {
        if (homeStarted) return false;
        if (keepTiles) {
            return level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                    || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL;
        }
        return level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
                || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL;
    }
}
