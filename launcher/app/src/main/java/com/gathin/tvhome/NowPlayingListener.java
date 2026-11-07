package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so Android lets Home read which media is playing (MediaSessionManager.getActiveSessions needs an
 * enabled notification listener). It reads no notifications. Granted over ADB:
 * cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener
 */
public class NowPlayingListener extends NotificationListenerService {
}
