package com.vwillcox.mytv.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vwillcox.mytv.app
import com.vwillcox.mytv.data.MediaCard
import com.vwillcox.mytv.data.RowStyle
import com.vwillcox.mytv.data.TvApp
import kotlinx.coroutines.delay

/** Search: the library and installed apps here, or everything through Google TV's own search. */
@Composable
fun SearchScreen(ui: HomeUi, all: List<TvApp>, actions: RowActions) {
    val app = LocalContext.current.app
    val tones = LocalTones.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it }
    }
    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        searching = true
        results = runCatching { app.feed.search(query.trim()) }.getOrDefault(emptyList())
        searching = false
    }
    val apps = if (query.isBlank()) emptyList() else all.filter { it.label.contains(query.trim(), ignoreCase = true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = TopBarHeight + 20.dp, bottom = 60.dp)) {
        Row(Modifier.padding(horizontal = Size.gutter), verticalAlignment = Alignment.CenterVertically) {
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            LocalSkin.current.SearchFace(focused, "Films, shows and apps".takeIf { query.isEmpty() }) {
                BasicTextField(
                    query, { query = it }, singleLine = true, interactionSource = interaction,
                    textStyle = Type.rowSmall.copy(color = tones.text), cursorBrush = SolidColor(tones.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth().focusRequester(ui.entry).focusProperties { up = ui.selectedTab },
                )
            }
            Spacer(Modifier.width(14.dp))
            RoundButton(Icons.Rounded.Mic, "Speak") {
                runCatching {
                    voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                }
            }
            Spacer(Modifier.width(10.dp))
            RoundButton(Icons.Rounded.Public, "Search everywhere") { if (query.isBlank()) app.system.assistant() else app.system.search(query.trim()) }
        }
        Text(
            when {
                query.isBlank() -> "Type, or press the microphone. The globe asks Google TV, which searches every service."
                searching -> "Searching…"
                results.isEmpty() && apps.isEmpty() -> "Nothing in your library or apps. The globe asks Google TV instead."
                else -> ""
            },
            style = Type.itemSub, color = tones.faint, modifier = Modifier.padding(start = Size.gutter + 4.dp, top = 12.dp),
        )
        if (apps.isNotEmpty()) {
            Text("Apps", style = Type.rowSmall, color = tones.dim, modifier = Modifier.padding(start = Size.gutter, top = 20.dp, bottom = 8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = Size.gutter - Size.appInset, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(Size.appGap)) {
                items(apps, key = { it.pkg }) { a -> AppTile(a, banner = false, moving = false, onLongClick = { actions.appMenu(a) }) { actions.launch(a) } }
            }
        }
        if (results.isNotEmpty()) {
            Text("In your library", style = Type.rowSmall, color = tones.dim, modifier = Modifier.padding(start = Size.gutter, top = 16.dp, bottom = 8.dp))
            CardRow(results, RowStyle.WIDE, focusedRow = true, actions = actions)
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LocalSkin.current.ButtonFace(icon, label, focused, Modifier.tvClickable(interaction, onClick = onClick))
}

@Composable
internal fun GoogleSearchFace(focused: Boolean, placeholder: String?, field: @Composable () -> Unit) {
    val tones = LocalTones.current
    Row(
        Modifier.width(520.dp).height(52.dp).clip(CircleShape).background(tones.surface)
            .border(2.dp, if (focused) Color.White else Color.Transparent, CircleShape).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = tones.dim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (placeholder != null) Text(placeholder, style = Type.rowSmall, color = tones.faint)
            field()
        }
    }
}

@Composable
internal fun GoogleButtonFace(icon: ImageVector, label: String, focused: Boolean, control: Modifier) {
    val tones = LocalTones.current
    Box(
        Modifier.size(52.dp).then(control).clip(CircleShape).background(if (focused) Color.White else tones.surface),
        Alignment.Center,
    ) { Icon(icon, label, tint = if (focused) tones.onAccent else tones.text, modifier = Modifier.size(24.dp)) }
}
