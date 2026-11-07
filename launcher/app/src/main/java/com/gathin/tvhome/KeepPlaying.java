package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;

/**
 * Music after Settings (this TV). Xiaomi's TV Settings pauses whatever plays on purpose, and takes the audio back
 * each time something else asks for it while Settings is on screen (MiAudioFocusManager, no setting to turn it off),
 * so music cannot play inside Settings. Instead: when the launcher opens Settings (Control Center, the remote's
 * Settings button) it notes the playing music; if that pauses right after, the music starts again as soon as Home is
 * back. Videos (SmartTube, YouTube) take the audio for good and are not touched: music still pauses for them.
 */
final class KeepPlaying {
    private static final long WATCH_MS = 5000, RESUME_WITHIN_MS = 30 * 60_000L;

    /** The session Settings paused, and when; null when there is nothing to resume. */
    private static MediaSession.Token pausedToken;
    private static long pausedAt;

    private KeepPlaying() {
    }

    private static List<MediaController> sessions(Context app) {
        MediaSessionManager msm = app.getSystemService(MediaSessionManager.class);
        return msm.getActiveSessions(new ComponentName(app, NowPlayingListener.class));
    }

    /** Call right before opening Settings. */
    static void aroundSettings(Context ctx) {
        Context app = ctx.getApplicationContext();
        MediaController playing = null;
        try {
            for (MediaController c : sessions(app)) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    playing = c;
                    break;
                }
            }
        } catch (SecurityException | NullPointerException e) {
            return;   // no notification access: nothing to watch
        }
        if (playing == null) return;
        final MediaController c = playing;
        Handler main = new Handler(Looper.getMainLooper());
        MediaController.Callback watch = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                if (state != null && state.getState() == PlaybackState.STATE_PAUSED) {
                    pausedToken = c.getSessionToken();
                    pausedAt = SystemClock.elapsedRealtime();
                    c.unregisterCallback(this);
                }
            }
        };
        c.registerCallback(watch, main);
        main.postDelayed(() -> c.unregisterCallback(watch), WATCH_MS);
    }

    /** Home is back: play the music Settings paused, if it is still paused and that was not long ago. */
    static void resumeIfPausedBySettings(Context ctx) {
        MediaSession.Token token = pausedToken;
        if (token == null) return;
        pausedToken = null;
        if (SystemClock.elapsedRealtime() - pausedAt > RESUME_WITHIN_MS) return;
        try {
            for (MediaController c : sessions(ctx.getApplicationContext())) {
                if (!token.equals(c.getSessionToken())) continue;
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PAUSED) c.getTransportControls().play();
                return;
            }
        } catch (SecurityException | NullPointerException ignored) {
        }
    }
}
