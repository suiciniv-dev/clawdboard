package dev.clawdboard

import android.service.notification.NotificationListenerService

class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        repo.music.poke()
    }

    override fun onListenerDisconnected() {
        repo.music.poke()
    }
}
