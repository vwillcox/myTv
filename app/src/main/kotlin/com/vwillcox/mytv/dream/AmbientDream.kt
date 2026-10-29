package com.vwillcox.mytv.dream

import android.service.dreams.DreamService
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil3.compose.AsyncImage
import com.vwillcox.mytv.app
import com.vwillcox.mytv.ui.Type
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * myTV Ambient: artwork from the library of whoever is watching, from AllMedia, drifting slowly, with the time and
 * what each picture is from. Nothing from services they don't own.
 */
class AmbientDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AmbientDream)
            setViewTreeSavedStateRegistryOwner(this@AmbientDream)
            setContent { Ambient() }
        })
    }

    override fun onDreamingStarted() { super.onDreamingStarted(); registry.currentState = Lifecycle.State.RESUMED }
    override fun onDreamingStopped() { registry.currentState = Lifecycle.State.CREATED; super.onDreamingStopped() }
    override fun onDetachedFromWindow() { registry.currentState = Lifecycle.State.DESTROYED; super.onDetachedFromWindow() }
}

private data class Slide(val image: String, val title: String)

@Composable
private fun Ambient() {
    val app = androidx.compose.ui.platform.LocalContext.current.app
    val slides by produceState(emptyList<Slide>()) {
        value = runCatching { app.feed.backdrops().mapNotNull { c -> c.backdrop?.let { Slide(it, c.title) } } }.getOrDefault(emptyList())
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(slides) { while (slides.isNotEmpty()) { delay(20_000); index = (index + 1) % slides.size } }
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1000) } }
    val drift = rememberInfiniteTransition(label = "drift")
    val zoom by drift.animateFloat(1.04f, 1.14f, infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(slides.getOrNull(index), animationSpec = tween(2500), label = "slide") { slide ->
            if (slide != null) AsyncImage(slide.image, slide.title, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom }, contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000))))
        // Moves a little each minute so nothing stays put on the panel.
        val shift = ((now / 60_000) % 5).toInt() * 6
        Column(Modifier.align(Alignment.BottomStart).padding(start = (56 + shift).dp, bottom = (44 + shift).dp)) {
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)), style = Type.heroTitle.copy(fontSize = Type.heroTitle.fontSize * 1.6f), color = Color.White)
            slides.getOrNull(index)?.let { Text(it.title, style = Type.rowSmall, color = Color(0xCCFFFFFF)) }
        }
    }
}
