package io.github.radumariusi.akvodhub.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.radumariusi.akvodhub.parser.player.PlaybackActivityState
import io.github.radumariusi.akvodhub.ui.player.PlayerScreen
import io.github.radumariusi.akvodhub.ui.theme.AkVodHubTheme

/** Ecranul de redare (separat de hub, ca playerul să fie eliberat imediat ce ieși). */
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    private var frameRateController: FrameRateController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Pe telefon: ecran complet (barele sistemului apar doar la glisare). Pe TV nu există bare, deci nu schimbă nimic.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        val launchRequest = request(intent)
        viewModel.start(launchRequest)
        val frameRate = FrameRateController(this, viewModel.player)
        frameRateController = frameRate
        viewModel.player.addListener(frameRate)
        setContent {
            val prefs by viewModel.prefs.collectAsState()
            DisposableEffect(prefs.matchFrameRate) {
                frameRate.enabled = prefs.matchFrameRate
                onDispose { frameRate.enabled = false }
            }
            AkVodHubTheme(prefs.accentColor) {
                PlayerScreen(viewModel, onExit = { finish() }, openTrackMenu = launchRequest.openTrackMenu)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PlaybackActivityState.active = true
    }

    override fun onStop() {
        PlaybackActivityState.active = false
        super.onStop()
        viewModel.onStop()
        frameRateController?.enabled = false
    }

    override fun onResume() {
        super.onResume()
        frameRateController?.enabled = viewModel.prefs.value.matchFrameRate
    }

    override fun onDestroy() {
        frameRateController?.let { viewModel.player.removeListener(it) }
        super.onDestroy()
    }

    companion object {
        private const val TITLE = "titleId"
        private const val ENTRY = "entryId"
        private const val SEASON = "season"
        private const val EPISODE = "episode"
        private const val RESUME = "resume"
        private const val TRACK_MENU = "openTrackMenu"

        fun intent(
            context: Context,
            request: PlayRequest,
        ): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(TITLE, request.titleId)
                .putExtra(ENTRY, request.entryId ?: -1L)
                .putExtra(SEASON, request.season ?: Int.MIN_VALUE)
                .putExtra(EPISODE, request.episode ?: Int.MIN_VALUE)
                .putExtra(RESUME, request.resume)
                .putExtra(TRACK_MENU, request.openTrackMenu)

        private fun request(intent: Intent) =
            PlayRequest(
                titleId = intent.getLongExtra(TITLE, -1L),
                entryId = intent.getLongExtra(ENTRY, -1L).takeIf { it >= 0 },
                season = intent.getIntExtra(SEASON, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                episode = intent.getIntExtra(EPISODE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                resume = intent.getBooleanExtra(RESUME, false),
                openTrackMenu = intent.getBooleanExtra(TRACK_MENU, false),
            )
    }
}
