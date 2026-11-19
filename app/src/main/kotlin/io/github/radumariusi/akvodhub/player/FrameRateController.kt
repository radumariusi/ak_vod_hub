package io.github.radumariusi.akvodhub.player

import android.app.Activity
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import io.github.radumariusi.akvodhub.parser.player.FrameRateMatch

/**
 * Solicită un mod de aceeași rezoluție, potrivit cu fps-ul conținutului. Sistemul/TV-ul poate refuza.
 *
 * Modul ales rămâne fix pe toată redarea: pauza, bufferingul, schimbarea vitezei sau o sursă
 * încă neîncărcată nu îl schimbă (fiecare comutare înseamnă 1–3 s de ecran negru pe TV).
 * Se recalculează doar când conținutul are alt fps (alt episod/altă sursă) și se restaurează
 * modul inițial doar când opțiunea e oprită sau ieși din player ([enabled] = false).
 */
class FrameRateController(private val activity: Activity, private val player: ExoPlayer) : Player.Listener {
    private val original = activity.window.attributes.preferredDisplayModeId

    /** Ultimul fps cunoscut al conținutului; null = încă necunoscut (rămâne modul curent). */
    private var contentFps: Float? = null

    var enabled = false
        set(value) {
            field = value
            if (!value) contentFps = null
            update()
        }

    @Suppress("DEPRECATION")
    fun update() {
        runCatching {
            val target =
                if (enabled) {
                    player.videoFormat
                        ?.frameRate
                        ?.takeIf { it.isFinite() && it > 0f }
                        ?.let { contentFps = it }
                    contentFps?.let(::modeFor) ?: activity.window.attributes.preferredDisplayModeId
                } else {
                    original
                }
            if (activity.window.attributes.preferredDisplayModeId != target) {
                activity.window.attributes = activity.window.attributes.apply { preferredDisplayModeId = target }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun modeFor(fps: Float): Int {
        val display = activity.windowManager.defaultDisplay
        val current = display.mode
        val modes =
            display.supportedModes.filter {
                it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
            }
        return FrameRateMatch.choose(fps, modes.map { it.refreshRate })?.let { modes[it].modeId } ?: original
    }

    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) = update()

    override fun onRenderedFirstFrame() = update()

    override fun onVideoSizeChanged(videoSize: VideoSize) = update()
}
