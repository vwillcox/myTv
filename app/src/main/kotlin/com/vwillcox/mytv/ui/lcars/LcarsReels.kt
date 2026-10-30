package com.vwillcox.mytv.ui.lcars

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vwillcox.mytv.R
import com.vwillcox.mytv.ui.LocalSounds
import com.vwillcox.mytv.ui.isSelect

/** The films hidden in the frame, each behind a block that only the upright's own blocks lead to. */
internal enum class Reel(val video: Int, val loop: Boolean) {
    /** The Enterprise-D at warp, the Omarchy LCARS-TNG theme's wallpaper: behind the bottom elbow, 77-0994. */
    WARP(R.raw.warp, loop = true),
    /** For Sir Patrick Stewart, 1940–2026: behind the LCARS block at the very top. */
    TRIBUTE(R.raw.tribute, loop = false),
}

/** Which film is showing, and whether the blocks that lead to them may take focus. */
@Stable
internal class Reels {
    var showing by mutableStateOf<Reel?>(null)
    var navFocused by mutableStateOf(false)
    var topFocused by mutableStateOf(false)
    val top = FocusRequester()
    val aft = FocusRequester()

    /** Only from the upright, so Up from the screen's top row and Down from its last don't stumble on them. */
    val reachable get() = navFocused || topFocused || showing != null

    fun from(reel: Reel) = if (reel == Reel.TRIBUTE) top else aft
}

/**
 * A film over the whole screen. It comes up out of black once its first frame is ready, then loops or holds on its
 * last frame, and goes with Back or OK. It stops while the screen is out of sight.
 */
@Composable
internal fun ReelPlayer(reel: Reel, onClose: () -> Unit) {
    val context = LocalContext.current
    val sounds = LocalSounds.current
    val player = remember(reel) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/${reel.video}"))
            repeatMode = if (reel.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    var ready by remember(reel) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { ready = true }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    LaunchedEffect(player, lifecycle) { player.playWhenReady = lifecycle.isAtLeast(Lifecycle.State.STARTED) }
    val veil by animateFloatAsState(if (ready) 0f else 1f, tween(700), label = "veil")

    fun close() { sounds.close(); onClose() }
    BackHandler { close() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // An OK only counts if it went down here, not the one that opened the film.
    var pressed by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(focus)
            .onKeyEvent { e ->
                when {
                    e.isSelect() -> {
                        if (e.type == KeyEventType.KeyDown) pressed = true else if (e.type == KeyEventType.KeyUp && pressed) close()
                        true
                    }
                    // Nothing underneath moves while it's up.
                    e.key in Arrows -> true
                    else -> false
                }
            }
            .focusable(),
    ) {
        AndroidView({ SurfaceView(it).also(player::setVideoSurfaceView) }, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = veil }.background(Color.Black))
    }
}

private val Arrows = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)
