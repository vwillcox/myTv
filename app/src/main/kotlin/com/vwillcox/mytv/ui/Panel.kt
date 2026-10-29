package com.vwillcox.mytv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A Google TV style panel down the right of the screen. Focus starts on its first item and can't wander out to
 * the screen behind it; Back closes it (handled by the root).
 */
@Composable
fun SidePanel(title: String, subtitle: String? = null, key: Any, content: LazyListScope.() -> Unit) {
    val tones = LocalTones.current
    val first = remember(key) { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color(0x66000000)), Alignment.CenterEnd) {
        Column(
            Modifier.width(400.dp).fillMaxHeight().clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)).background(tones.surface)
                .focusProperties { onExit = { cancelFocusChange() } }.focusGroup()
                .padding(top = 36.dp),
        ) {
            Text(title, style = Type.panelTitle, color = tones.text, modifier = Modifier.padding(horizontal = 28.dp))
            subtitle?.let { Text(it, style = Type.itemSub, color = tones.dim, modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 6.dp)) }
            Spacer(Modifier.size(16.dp))
            val list = rememberLazyListState()
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f).focusRequester(first),
                state = list, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) { content() }
        }
    }
    LaunchedEffect(key) { delay(30); runCatching { first.requestFocus() } }
}

@Composable
fun PanelItem(
    title: String,
    sub: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable (focused: Boolean) -> Unit)? = null,
    chevron: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tones = LocalTones.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fg = if (focused) tones.onAccent else tones.text
    Row(
        Modifier.fillMaxWidth().tvClickable(interaction, onLongClick, onClick).clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White else Color.Transparent).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.item, color = fg)
            sub?.let { Text(it, style = Type.itemSub, color = if (focused) tones.onAccent.copy(alpha = 0.72f) else tones.dim) }
        }
        trailing?.invoke(focused)
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = fg.copy(alpha = 0.7f))
    }
}

@Composable
fun PanelToggle(title: String, sub: String? = null, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) =
    PanelItem(title, sub, icon, trailing = { focused -> Switch(checked, focused) }) { onChange(!checked) }

@Composable
fun PanelChoice(title: String, sub: String? = null, selected: Boolean, onClick: () -> Unit) {
    val tones = LocalTones.current
    PanelItem(title, sub, trailing = { focused ->
        Icon(
            if (selected) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked, null,
            tint = if (focused) tones.onAccent else if (selected) tones.accent else tones.dim,
        )
    }, onClick = onClick)
}

@Composable
fun PanelHeading(text: String) =
    Text(text, style = Type.itemSub, color = LocalTones.current.faint, modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 4.dp))

@Composable
fun PanelNote(text: String) =
    Text(text, style = Type.itemSub, color = LocalTones.current.dim, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))

@Composable
private fun Switch(on: Boolean, focused: Boolean) {
    val tones = LocalTones.current
    val track = when {
        on && focused -> tones.onAccent
        on -> tones.accent
        focused -> Color(0xFF9AA0A6)
        else -> Color(0xFF5F6368)
    }
    Box(Modifier.width(40.dp).size(width = 40.dp, height = 22.dp).clip(CircleShape).background(track).padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(if (on) (if (focused) Color.White else tones.onAccent) else Color(0xFFE8EAED)), Alignment.Center) {
            if (on) Icon(Icons.Rounded.Check, null, tint = if (focused) tones.onAccent else tones.accent, modifier = Modifier.size(12.dp))
        }
    }
}
