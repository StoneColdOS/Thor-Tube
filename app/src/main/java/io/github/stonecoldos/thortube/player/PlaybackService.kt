package io.github.stonecoldos.thortube.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.stonecoldos.thortube.ui.MainActivity

/** What the background service needs from the app: the player, what's playing, and "next". */
object BackgroundPlayback {
    @Volatile var player: Player? = null
    @Volatile var metadata: MediaMetadata = MediaMetadata.EMPTY
    @Volatile var onNext: (() -> Unit)? = null
}

/**
 * Screen-off audio (Setup > Playback). Wraps the app's player in a media session so Android keeps
 * it running with the screen off, and shows play / pause / next in the notification shade and on
 * the lock screen. Only this app (and the system) may control it.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val base = BackgroundPlayback.player ?: return
        val player = object : ForwardingPlayer(base) {
            // The player itself plays bare streams; the title, channel and picture come from the app.
            override fun getMediaMetadata(): MediaMetadata = BackgroundPlayback.metadata
            override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).build()
            override fun isCommandAvailable(command: Int): Boolean =
                command == Player.COMMAND_SEEK_TO_NEXT || command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
                    super.isCommandAvailable(command)
            override fun seekToNext() { BackgroundPlayback.onNext?.invoke() }
            override fun seekToNextMediaItem() { BackgroundPlayback.onNext?.invoke() }
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(object : MediaSession.Callback {
                // Only this app and Android itself (notification, lock screen) get to control playback.
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                    if (controller.isTrusted) super.onConnect(session, controller)
                    else MediaSession.ConnectionResult.reject()
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // Swiping the app away from recents stops the sound too.
    override fun onTaskRemoved(rootIntent: Intent?) {
        session?.player?.pause()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
