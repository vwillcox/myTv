package com.vwillcox.mytv.ui.lcars

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vwillcox.mytv.data.BackgroundMode
import com.vwillcox.mytv.data.Look
import com.vwillcox.mytv.data.Tab
import com.vwillcox.mytv.ui.BackgroundLayer
import com.vwillcox.mytv.ui.HomeFrame
import com.vwillcox.mytv.ui.HomeUi
import com.vwillcox.mytv.ui.LocalSounds
import com.vwillcox.mytv.ui.Panel
import com.vwillcox.mytv.ui.Type
import com.vwillcox.mytv.ui.tvClickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** The upright down the left of both frames. */
private val Side = 128.dp
/** The arms along the frames, and the black slices cut between pieces. */
private val Arm = 12.dp
private val Slice = 4.dp
private val HeaderHeight = 72.dp
/** The main frame's corner blocks, top and bottom. */
private val TopElbow = 58.dp
private val BottomElbow = 40.dp
private val BottomArm = 8.dp
/** Between the upright and the screen it frames. */
private val Inset = 14.dp

private typealias Boot = Animatable<Float, AnimationVector1D>

/** Fades a part in as the frame powers up, from [from] to [to] of the way through. */
private fun Boot.part(from: Float, to: Float) = smoothstep(from, to, value)

/**
 * The frame of an LCARS panel: a header with its readouts above, and below it the main frame, whose upright carries
 * the navigation and whose arms enclose the screen on show. It powers up in sequence the first time it appears.
 */
@Composable
internal fun LcarsFrame(f: HomeFrame) {
    val sounds = LocalSounds.current
    val motion = LocalLcarsMotion.current
    val boot = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (boot.value < 1f) { sounds.boot(); boot.animateTo(1f, tween(1400, easing = LinearEasing)) }
    }
    val reels = remember { Reels() }
    LaunchedEffect(f.ui.resets, f.ui.panels.size) { reels.showing = null }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 22.dp, vertical = 14.dp)) {
            Column(Modifier.fillMaxSize()) {
                Header(f.look, boot, reels, Modifier.fillMaxWidth().height(HeaderHeight))
                Spacer(Modifier.height(6.dp))
                Main(f, boot, reels, Modifier.fillMaxWidth().weight(1f))
            }
        }
        reels.showing?.let { reel ->
            // Focus goes back to its block before the film goes, while the block may still take it.
            ReelPlayer(reel) { runCatching { reels.from(reel).requestFocus() }; reels.showing = null }
        }
    }
}

@Composable
private fun Header(look: Look, boot: Boot, reels: Reels, modifier: Modifier) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(60_000 - value % 60_000) }
    }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val side = Side.toPx(); val arm = Arm.toPx(); val slice = Slice.toPx()
            val block = 30.dp.toPx()
            val elbowTop = block + slice
            val elbowHeight = size.height - elbowTop
            val reach = side + 90.dp.toPx()
            val shown = boot.part(0.05f, 0.2f)
            drawRect(Lcars.BlueBell, size = Size(side, block), alpha = shown)
            translate(top = elbowTop) {
                drawPath(elbow(elbowHeight, side, arm, 30.dp.toPx(), 14.dp.toPx(), reach, bottom = true), Lcars.Orange, alpha = shown)
            }
            // The arm runs out from the elbow as it powers up.
            clipRect(right = reach + (size.width - reach) * boot.part(0.12f, 0.45f)) {
                bar(reach + slice, size.width, size.height - arm, arm, slice, listOf(
                    Piece(150.dp.toPx(), Lcars.Tangerine), Piece(0f, Lcars.Peach), Piece(46.dp.toPx(), Lcars.Lilac), Piece(30.dp.toPx(), Lcars.Orange),
                ))
            }
        }
        TopBlock(reels, Modifier.width(Side).height(30.dp), boot.part(0.15f, 0.3f))
        BlockLabel(code(look.brand + "deck"), Modifier.width(Side).height(HeaderHeight), boot.part(0.2f, 0.35f))
        Row(
            Modifier.fillMaxSize().padding(start = Side + 30.dp, bottom = Arm + 5.dp)
                .graphicsLayer { alpha = boot.part(0.5f, 0.75f) },
            verticalAlignment = Alignment.Bottom,
        ) {
            DataCascade(Modifier.width(236.dp).fillMaxHeight(), seed = 7)
            Spacer(Modifier.width(18.dp))
            Blinkers(Modifier.width(64.dp).height(30.dp).padding(bottom = 4.dp), seed = 3)
            Spacer(Modifier.weight(1f))
            // These two stand taller than the readouts, and rise above them rather than being squeezed.
            Column(Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true), horizontalAlignment = Alignment.End) {
                Text(look.brand.ifBlank { "myTV" }.uppercase(), style = Type.panelTitle.copy(fontSize = Type.panelTitle.fontSize * 1.25f), color = Lcars.Orange, maxLines = 1)
                Text("STARDATE ${stardate(now)}  ·  U.S.S. ENTERPRISE  ·  NCC-1701-D", style = LcarsText.readout, color = Lcars.Lilac, maxLines = 1)
            }
            Spacer(Modifier.width(18.dp))
            val clock = remember(now, look.clock24) { SimpleDateFormat(if (look.clock24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now)) }
            Text(clock, style = Type.clock, color = Lcars.Peach, modifier = Modifier.wrapContentHeight(Alignment.Bottom, unbounded = true).offset(y = 6.dp))
        }
    }
}

/** The number at the foot of an upright's block, in black, the way LCARS blocks are labelled. */
@Composable
private fun BlockLabel(text: String, modifier: Modifier, alpha: Float) {
    Box(modifier.graphicsLayer { this.alpha = alpha }, Alignment.BottomEnd) {
        Text(text, style = LcarsText.label, color = Color.Black, modifier = Modifier.padding(end = 8.dp, bottom = 2.dp))
    }
}

/**
 * The block at the very top, labelled LCARS. Up from Search reaches it, and OK on it plays the tribute. Like the
 * upright's blocks, it flashes and lights when focused.
 */
@Composable
private fun TopBlock(reels: Reels, modifier: Modifier, alpha: Float) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val flash by rememberFlash(focused)
    Box(
        modifier.focusRequester(reels.top)
            .focusProperties { canFocus = reels.reachable; left = FocusRequester.Cancel; right = FocusRequester.Cancel; up = FocusRequester.Cancel }
            .onFocusChanged { reels.topFocused = it.isFocused }
            .tvClickable(interaction) { reels.showing = Reel.TRIBUTE }
            .drawBehind { if (focused) drawRect(lerp(Lcars.BlueBell.dim(0.4f), Lcars.Lit, flash)) },
        Alignment.BottomEnd,
    ) {
        Text("LCARS", style = LcarsText.label, color = Color.Black, modifier = Modifier.graphicsLayer { this.alpha = alpha }.padding(end = 8.dp, bottom = 2.dp))
    }
}

/** The main frame's bottom elbow, in its own space: the upright's foot curving into the thin bottom arm. */
private fun DrawScope.aftElbow() = elbow(BottomElbow.toPx(), Side.toPx(), BottomArm.toPx(), 28.dp.toPx(), 12.dp.toPx(), (Side + AftReach).toPx(), bottom = true)

private val AftReach = 44.dp

/**
 * The bottom elbow, numbered 77-0994, below Settings at the upright's foot. OK on it plays the warp. Focused, the
 * whole elbow lights.
 */
@Composable
private fun Aft(entry: FocusRequester?, reels: Reels, alpha: Float) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val flash by rememberFlash(focused)
    Box(
        Modifier.fillMaxWidth().height(BottomElbow).focusRequester(reels.aft)
            .focusProperties { canFocus = reels.reachable; entry?.let { right = it } }
            .tvClickable(interaction) { reels.showing = Reel.WARP }
            .drawBehind { if (focused) drawPath(aftElbow(), lerp(Lcars.Tangerine.dim(0.4f), Lcars.Lit, flash)) },
        Alignment.BottomEnd,
    ) {
        Text(code("aft"), style = LcarsText.label, color = Color.Black, modifier = Modifier.graphicsLayer { this.alpha = alpha }.padding(end = 8.dp, bottom = 2.dp))
    }
}

@Composable
private fun Main(f: HomeFrame, boot: Boot, reels: Reels, modifier: Modifier) {
    val ui = f.ui
    val time = LocalLcarsTime.current
    val motion = LocalLcarsMotion.current
    val focus = LocalFocusManager.current
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val side = Side.toPx(); val slice = Slice.toPx()
            val shown = boot.part(0.05f, 0.2f)
            val grow = boot.part(0.12f, 0.45f)
            // The top elbow and its arm, with a light sweeping along part of it now and then.
            val topReach = side + 60.dp.toPx()
            drawPath(elbow(TopElbow.toPx(), side, Arm.toPx(), 34.dp.toPx(), 16.dp.toPx(), topReach, bottom = false), Lcars.Lilac, alpha = shown)
            clipRect(right = topReach + (size.width - topReach) * grow) {
                val spans = bar(topReach + slice, size.width, 0f, Arm.toPx(), slice, listOf(
                    Piece(96.dp.toPx(), Lcars.Orange), Piece(28.dp.toPx(), Lcars.Peach), Piece(0f, Lcars.BlueBell), Piece(70.dp.toPx(), Lcars.Orange), Piece(26.dp.toPx(), Lcars.Tangerine),
                ))
                if (motion) sweep(spans[2], 0f, Arm.toPx(), time.value, 5200L)
            }
            // The bottom elbow and its thinner arm.
            val bottomReach = side + AftReach.toPx()
            translate(top = size.height - BottomElbow.toPx()) { drawPath(aftElbow(), Lcars.Tangerine, alpha = shown) }
            clipRect(right = bottomReach + (size.width - bottomReach) * grow) {
                val y = size.height - BottomArm.toPx()
                val spans = bar(bottomReach + slice, size.width, y, BottomArm.toPx(), slice, listOf(
                    Piece(170.dp.toPx(), Lcars.BlueBell), Piece(0f, Lcars.Peach), Piece(54.dp.toPx(), Lcars.Lilac), Piece(24.dp.toPx(), Lcars.Orange),
                ))
                if (motion) sweep(spans[1], y, BottomArm.toPx(), time.value + 2600, 7800L)
            }
        }
        BlockLabel(code("main" + f.look.brand), Modifier.width(Side).height(TopElbow), boot.part(0.2f, 0.35f))
        SectionTitle(f, Modifier.align(Alignment.TopEnd).padding(end = 120.dp).offset(y = (-7).dp).graphicsLayer { alpha = boot.part(0.45f, 0.6f) })

        Box(Modifier.fillMaxSize().focusRequester(ui.main).focusRestorer().focusGroup()) {
            Nav(f, boot, reels, Modifier.width(Side).fillMaxHeight().padding(top = TopElbow + Slice))
            Box(
                Modifier.fillMaxSize().padding(start = Side + Inset, top = Arm + 8.dp, bottom = BottomArm + 8.dp).clipToBounds()
                    .graphicsLayer { alpha = boot.part(0.55f, 1f) }
                    // On Google TV the tabs are above everything, so Up from the top of a screen reaches them. Here
                    // they're beside it, so Up with nowhere left to go is taken there.
                    .onKeyEvent { e ->
                        if (e.key != Key.DirectionUp || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        focus.moveFocus(FocusDirection.Up) || runCatching { ui.selectedTab.requestFocus() }.isSuccess
                    },
            ) {
                if (f.look.background != BackgroundMode.DARK) BackgroundLayer(f.look, ui.focusedArt)
                f.content(Modifier)
            }
        }
        SourceTab(ui, boot, Modifier.align(Alignment.TopEnd).padding(top = Arm + Slice))
    }
}

/**
 * Where the spotlight's title comes from, in a block hanging under the end of the top arm, out of the readout's way.
 * It fades as the page scrolls past the spotlight, and isn't there on screens without one.
 */
@Composable
private fun SourceTab(ui: HomeUi, boot: Boot, modifier: Modifier) {
    val card = ui.spotlight.takeIf { ui.grid == null && !ui.searching && ui.tab != Tab.APPS }
    Crossfade(card, modifier.graphicsLayer { alpha = (1f - ui.scrolled) * boot.part(0.55f, 0.75f) }, tween(300), label = "source") { c ->
        if (c != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(code(c.key), style = LcarsText.readout, color = Lcars.Lilac, modifier = Modifier.padding(end = 8.dp))
            Box(Modifier.height(26.dp).background(pick(c.sourceLabel)).padding(start = 16.dp, end = 10.dp), Alignment.CenterEnd) {
                Text(c.sourceLabel.uppercase(), style = LcarsText.label, color = Color.Black, maxLines = 1, textAlign = TextAlign.End)
            }
        }
    }
}

/** A lighter stretch running along a bar piece [span] every [period], like data moving through it. */
private fun DrawScope.sweep(span: ClosedFloatingPointRange<Float>, y: Float, thickness: Float, time: Long, period: Long) {
    val length = 60.dp.toPx()
    val travel = (span.endInclusive - span.start) + length
    val at = span.start - length + travel * ((time % period) / (period * 0.6f))
    if (at > span.endInclusive) return
    clipRect(span.start, y, span.endInclusive, y + thickness) {
        drawRect(Lcars.Lit, Offset(at, y), Size(length, thickness), alpha = 0.85f)
    }
}

/** What's on show, set into the top arm in a gap of its own. */
@Composable
private fun SectionTitle(f: HomeFrame, modifier: Modifier) {
    val ui = f.ui
    val title = ui.grid?.title ?: if (ui.searching) "Search" else ui.tab.label
    Text(
        "  ${title.uppercase()}  ", style = Type.tab.copy(fontSize = Type.tab.fontSize * 1.1f), color = Lcars.Orange, maxLines = 1,
        modifier = modifier.background(Color.Black),
    )
}

/**
 * The upright's blocks: the tabs, a spare block, then who's watching and settings, with the bottom elbow at the
 * foot. Like Google TV's tabs, a tab opens as soon as it's focused. Coming in from the screen always lands on the
 * open tab, whichever block is nearest, so passing through doesn't change tab.
 */
@Composable
private fun Nav(f: HomeFrame, boot: Boot, reels: Reels, modifier: Modifier) {
    val ui = f.ui
    // Right goes back down into the screen, unless a genre's grid is up (it has its own way in).
    val entry = if (ui.grid == null) ui.entry else null
    Column(
        modifier
            .focusProperties {
                onEnter = { if (requestedFocusDirection != FocusDirection.Enter) runCatching { ui.selectedTab.requestFocus() } }
            }
            .onFocusChanged { f.onNavFocus(it.hasFocus); reels.navFocused = it.hasFocus }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(Slice),
    ) {
        var i = 0
        fun appear() = (i++).let { n -> Modifier.graphicsLayer { alpha = boot.part(0.15f + n * 0.05f, 0.25f + n * 0.05f) } }
        TabBlock("Search", Lcars.BlueBell, 38.dp, ui.searching, ui.tabs.getValue("search"), entry, appear()) { f.onTab(null) }
        for (t in Tab.entries) {
            TabBlock(t.label, TabColours.getValue(t), 38.dp, !ui.searching && ui.tab == t, ui.tabs.getValue(t.name), entry, appear()) { f.onTab(t) }
        }
        Spare(Modifier.weight(1f).then(appear()))
        NavBlock(f.userName ?: "Crew", Lcars.Lilac, 30.dp, entry, appear()) { ui.open(Panel.Page("account")) }
        NavBlock("Settings", Lcars.Mars, 30.dp, entry, appear()) { ui.open(Panel.Page("root")) }
        Aft(entry, reels, boot.part(0.25f, 0.4f))
    }
}

private val TabColours = mapOf(Tab.HOME to Lcars.Peach, Tab.MOVIES to Lcars.Tangerine, Tab.SHOWS to Lcars.Periwinkle, Tab.APPS to Lcars.Sunflower)

@Composable
private fun TabBlock(label: String, colour: Color, height: Dp, selected: Boolean, requester: FocusRequester, entry: FocusRequester?, modifier: Modifier, onFocus: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val sounds = LocalSounds.current
    LaunchedEffect(focused) {
        if (focused) { sounds.tab(); delay(160); onFocus() }
    }
    Block(label, colour, height, selected, focused, modifier.focusRequester(requester).focusProperties { entry?.let { right = it } }.tvClickable(interaction) { onFocus() })
}

@Composable
private fun NavBlock(label: String, colour: Color, height: Dp, entry: FocusRequester?, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Block(label, colour, height, false, focused, modifier.focusProperties { entry?.let { right = it } }.tvClickable(interaction, onClick = onClick))
}

/**
 * One block of the upright, its label in black at its foot. The open tab is lit orange with a tab of the same colour
 * reaching toward the screen; focus lights it white after a quick flash.
 */
@Composable
private fun Block(label: String, colour: Color, height: Dp, selected: Boolean, focused: Boolean, control: Modifier) {
    val flash by rememberFlash(focused)
    Box(
        Modifier.fillMaxWidth().height(height).then(control).drawBehind {
            val fill = when {
                focused -> lerp(colour.dim(0.4f), Lcars.Lit, flash)
                selected -> Lcars.Orange
                else -> colour
            }
            drawRect(fill)
            if (selected) drawRect(Lcars.Orange, Offset(size.width + 3.dp.toPx(), 0f), Size(5.dp.toPx(), size.height))
        },
        Alignment.BottomEnd,
    ) {
        Text(label.uppercase(), style = LcarsText.label, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 2.dp))
    }
}

/** The upright's spare block, which flickers now and then as if something's running through it. */
@Composable
private fun Spare(modifier: Modifier) {
    val time = LocalLcarsTime.current
    Box(
        modifier.fillMaxWidth().drawBehind {
            val t = time.value
            val lit = chance(11, t, 380) > 0.88f
            drawRect(if (lit) Lcars.Lit else Lcars.Violet)
            // A thin cut across it, where blocks of two sizes have been joined.
            drawRect(Color.Black, Offset(0f, size.height * 0.3f), Size(size.width, Slice.toPx() / 2))
        },
        Alignment.BottomEnd,
    ) { Text(code("spare"), style = LcarsText.label, color = Color.Black, modifier = Modifier.padding(end = 8.dp, bottom = 2.dp)) }
}
