package com.srikads.codewall.data

import android.service.notification.NotificationListenerService

/**
 * Exists only so Android lets CodeWall read the active media session
 * (title/artist) for the "now playing" field. Notifications themselves are ignored.
 */
class MediaListenerService : NotificationListenerService()
