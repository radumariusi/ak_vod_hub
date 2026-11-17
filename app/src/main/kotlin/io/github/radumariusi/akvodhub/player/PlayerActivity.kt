package io.github.radumariusi.akvodhub.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.github.radumariusi.akvodhub.ui.player.PlayerScreen
import io.github.radumariusi.akvodhub.ui.theme.AkVodHubTheme

/** Ecranul de redare (separat de hub, ca playerul să fie eliberat imediat ce ieși). */
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.start(request(intent))
        setContent {
            AkVodHubTheme {
                PlayerScreen(viewModel, onExit = { finish() })
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onStop()
    }

    companion object {
        private const val TITLE = "titleId"
        private const val ENTRY = "entryId"
        private const val SEASON = "season"
        private const val EPISODE = "episode"
        private const val RESUME = "resume"

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

        private fun request(intent: Intent) =
            PlayRequest(
                titleId = intent.getLongExtra(TITLE, -1L),
                entryId = intent.getLongExtra(ENTRY, -1L).takeIf { it >= 0 },
                season = intent.getIntExtra(SEASON, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                episode = intent.getIntExtra(EPISODE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                resume = intent.getBooleanExtra(RESUME, false),
            )
    }
}
