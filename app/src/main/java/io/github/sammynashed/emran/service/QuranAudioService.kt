package io.github.sammynashed.emran.service

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.sammynashed.emran.data.audio.QuranAudio

/**
 * Plays downloaded recitations: one ayah per playlist item, gapless, with the lock-screen and
 * notification controls a media session brings (and without which HyperOS mutes background audio).
 * Which ayah is sounding is published through [QuranAudio.state], so the reader can mark it.
 */
class QuranAudioService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // The decoded audio also flows past RecitationLevels, which turns it into the player
        // cover's five pillar heights.
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean) =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(androidx.media3.exoplayer.audio.TeeAudioProcessor(io.github.sammynashed.emran.data.audio.RecitationLevels)))
                    .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // headphones out: pause, don't blast the room
            .build()
        player.addListener(object : Player.Listener {
            var lastSurah = -1
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val surah = mediaItem?.mediaId?.let(QuranAudio::parse)?.surah ?: -1
                // Repeat the surah: running on into the next surah goes back to this one's start.
                if (QuranAudio.repeat.value == QuranAudio.Repeat.SURAH && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                    lastSurah > 0 && surah != lastSurah
                ) {
                    startOf(player, lastSurah)?.let { player.seekTo(it, 0); return }
                }
                lastSurah = surah
                publish(player)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) io.github.sammynashed.emran.data.audio.RecitationLevels.rest()
                publish(player)
            }
            override fun onPlaybackStateChanged(state: Int) {
                // The queue ran out on a repeated surah: start it again.
                if (state == Player.STATE_ENDED && QuranAudio.repeat.value == QuranAudio.Repeat.SURAH && lastSurah > 0) {
                    startOf(player, lastSurah)?.let { player.seekTo(it, 0); player.play(); return }
                }
                if (state == Player.STATE_ENDED || state == Player.STATE_IDLE) QuranAudio.state.value = null
                else publish(player)
            }
        })
        session = MediaSession.Builder(this, player).build()
    }

    /** Playlist index of [surah]'s first item. */
    private fun startOf(player: Player, surah: Int): Int? =
        (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId.let(QuranAudio::parse)?.surah == surah }

    private fun publish(player: Player) {
        val now = player.currentMediaItem?.mediaId?.let(QuranAudio::parse)
        QuranAudio.state.value = now?.copy(playing = player.isPlaying)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    /** Swiped away from recents while paused: nothing to keep alive. */
    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        QuranAudio.state.value = null
        super.onDestroy()
    }
}
