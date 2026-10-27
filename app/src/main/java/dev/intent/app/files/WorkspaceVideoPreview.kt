package dev.intent.app.files

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

@Composable
internal fun WorkspaceVideoPreview(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var error by remember(file) { mutableStateOf(false) }
    val player = remember(file) { ExoPlayer.Builder(context).build() }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlayerError(failure: PlaybackException) {
                error = true
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    Column(modifier.fillMaxSize()) {
        if (error) Text("Cannot play video. Use Open in another app.")
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxSize().testTag("file.video"),
            factory = { PlayerView(it).apply { this.player = player } },
            onRelease = { it.player = null }
        )
    }
}
