package io.github.radumariusi.akvodhub.player

import android.app.Activity
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import io.github.radumariusi.akvodhub.parser.player.FrameRateMatch

/** Demande un mod de aceeași rezoluție. Sistemul/TV-ul poate refuza; modul inițial se restaurează. */
class FrameRateController(private val activity: Activity, private val player: ExoPlayer) : Player.Listener {
    private val original = activity.window.attributes.preferredDisplayModeId
    var enabled = false
        set(value) {
            field = value
            update()
        }

    @Suppress("DEPRECATION")
    fun update() {
        runCatching {
            val display = activity.windowManager.defaultDisplay
            val current = display.mode
            val modes = display.supportedModes.filter {
                it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
            }
            val fps = (player.videoFormat?.frameRate ?: -1f) * player.playbackParameters.speed
            val index = if (enabled && player.isPlaying) FrameRateMatch.choose(fps, modes.map { it.refreshRate }) else null
            val target = index?.let { modes[it].modeId } ?: original
            if (activity.window.attributes.preferredDisplayModeId != target) {
                activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = target }
            }
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) = update()
    override fun onIsPlayingChanged(isPlaying: Boolean) = update()
    override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) = update()
}
