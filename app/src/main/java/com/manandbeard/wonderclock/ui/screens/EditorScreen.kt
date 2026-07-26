package com.manandbeard.wonderclock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.manandbeard.wonderclock.data.ClockConfig
import com.manandbeard.wonderclock.render.RenderEnv
import com.manandbeard.wonderclock.ui.components.ClockPreview
import com.manandbeard.wonderclock.ui.components.checkerboard
import com.manandbeard.wonderclock.ui.components.rememberLiveEnv

/** Aspect ratios matching common home-screen widget footprints. */
internal data class PreviewShape(val label: String, val ratio: Float)

private val PREVIEW_SHAPES = listOf(
    PreviewShape("4×2", 250f / 110f),
    PreviewShape("2×2", 1f),
    PreviewShape("4×4", 1f),
    PreviewShape("5×2", 320f / 110f),
    PreviewShape("4×1", 250f / 55f),
)

private enum class Backdrop(val label: String) {
    WALLPAPER("Wallpaper"),
    DARK("Dark"),
    LIGHT("Light"),
    CHECKER("None"),
}

/**
 * The settings surface for one widget.
 *
 * The panel at the top is the real renderer at a real widget aspect ratio, so
 * every control below shows its effect immediately and honestly — there is no
 * separate preview implementation that could drift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    initial: ClockConfig,
    title: String,
    onSave: (ClockConfig) -> Unit,
    onCancel: () -> Unit,
) {
    var config by remember { mutableStateOf(initial) }
    var shapeIndex by remember { mutableStateOf(0) }
    var backdrop by remember { mutableStateOf(Backdrop.WALLPAPER) }
    val openSections = remember { mutableStateMapOf("style" to true) }
    val env = rememberLiveEnv(1_000L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
                actions = { TextButton(onClick = { onSave(config) }) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            PreviewPanel(
                config = config,
                env = env,
                shape = PREVIEW_SHAPES[shapeIndex],
                backdrop = backdrop,
                onShapeIndex = { shapeIndex = it },
                onBackdrop = { backdrop = it },
                shapes = PREVIEW_SHAPES,
                selectedIndex = shapeIndex,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                editorSections(
                    config = config,
                    onChange = { transform -> config = transform(config) },
                    isOpen = { key -> openSections[key] == true },
                    onToggle = { key -> openSections[key] = openSections[key] != true },
                )
            }
        }
    }
}

@Composable
private fun PreviewPanel(
    config: ClockConfig,
    env: RenderEnv,
    shape: PreviewShape,
    backdrop: Backdrop,
    shapes: List<PreviewShape>,
    selectedIndex: Int,
    onShapeIndex: (Int) -> Unit,
    onBackdrop: (Backdrop) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(shape.ratio.coerceIn(0.4f, 6f))
                .clip(RoundedCornerShape(24.dp))
                .then(backdropModifier(backdrop)),
            contentAlignment = Alignment.Center,
        ) {
            ClockPreview(config = config, env = env, modifier = Modifier.fillMaxSize())
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shapes.forEachIndexed { index, candidate ->
                PillButton(
                    text = candidate.label,
                    selected = index == selectedIndex,
                    onClick = { onShapeIndex(index) },
                )
            }
            Text(
                text = "|",
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Backdrop.entries.forEach { candidate ->
                PillButton(
                    text = candidate.label,
                    selected = candidate == backdrop,
                    onClick = { onBackdrop(candidate) },
                )
            }
        }
    }
}

@Composable
internal fun PillButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = foreground)
    }
}

private fun backdropModifier(backdrop: Backdrop): Modifier = when (backdrop) {
    Backdrop.WALLPAPER -> Modifier.background(
        Brush.linearGradient(
            listOf(Color(0xFF2B2251), Color(0xFF123043), Color(0xFF4A2338)),
        ),
    )
    Backdrop.DARK -> Modifier.background(Color(0xFF101014))
    Backdrop.LIGHT -> Modifier.background(Color(0xFFF2F2F5))
    Backdrop.CHECKER -> Modifier.checkerboard(12.dp)
}
