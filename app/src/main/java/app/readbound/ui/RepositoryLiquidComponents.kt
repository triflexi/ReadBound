package app.readbound.ui

/*
 * Copyright 2025 Kyant
 * SPDX-License-Identifier: Apache-2.0
 *
 * Adapted from AndroidLiquidGlass-kmp's catalog components (Apache-2.0):
 * LiquidButton, LiquidSlider, LiquidToggle, LiquidBottomTabs and LiquidBottomTab.
 * The public wrappers in LiquidGlass.kt keep ReadBound's Material-compatible API.
 */

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

@Composable
internal fun RepositoryLiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) {
    val resolvedBackdrop = backdrop ?: emptyBackdrop()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = .62f, stiffness = 420f),
        label = "liquid button press",
    )
    Row(
        modifier
            .drawBackdrop(
                backdrop = resolvedBackdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                highlight = { Highlight.Default.copy(alpha = .35f + .45f * pressProgress) },
                shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = .08f)) },
                innerShadow = { InnerShadow(radius = 5.dp * pressProgress, alpha = pressProgress) },
                layerBlock = {
                    val scale = lerp(1f, 1f + 4.dp.toPx() / size.height, pressProgress)
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = .75f))
                    }
                    if (surfaceColor.isSpecified) drawRect(surfaceColor)
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = if (backdrop == null) LocalIndication.current else null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .height(48.dp)
            .padding(horizontal = 16.dp)
            .alpha(if (enabled) 1f else .45f),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun RepositoryLiquidToggle(
    selected: Boolean,
    onSelect: ((Boolean) -> Unit)?,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val resolvedBackdrop = backdrop ?: emptyBackdrop()
    val trackBackdrop = rememberLayerBackdrop()
    val isLight = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > .5f
    val accent = if (isLight) Color(0xFF34C759) else Color(0xFF30D158)
    val track = if (isLight) Color(0xFF787878).copy(alpha = .20f) else Color(0xFF787880).copy(alpha = .36f)
    val fraction by animateFloatAsState(if (selected) 1f else 0f, spring(.75f, 500f), label = "liquid toggle")
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    Box(
        modifier
            .size(64.dp, 48.dp)
            .toggleable(
                value = selected,
                enabled = onSelect != null,
                role = Role.Switch,
                onValueChange = { onSelect?.invoke(it) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .background(androidx.compose.ui.graphics.lerp(track, accent, fraction))
                .size(64.dp, 28.dp),
        )
        Box(
            Modifier
                .graphicsLayer {
                    val start = 2.dp.toPx()
                    val travel = 20.dp.toPx()
                    translationX = if (isLtr) start + travel * fraction else -(start + travel * fraction)
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        resolvedBackdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop -> drawBackdrop() },
                    ),
                    shape = { Capsule() },
                    effects = { blur(8.dp.toPx()); lens(5.dp.toPx(), 10.dp.toPx(), chromaticAberration = true) },
                    highlight = { Highlight.Ambient },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = .08f)) },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = .94f)) },
                )
                .size(40.dp, 24.dp),
        )
    }
}

@Composable
internal fun RepositoryLiquidSliderThumb(
    backdrop: Backdrop?,
    trackBackdrop: Backdrop,
    pressed: Boolean,
) {
    val pressProgress by animateFloatAsState(if (pressed) 1f else 0f, spring(.65f, 480f), label = "liquid slider thumb")
    Box(
        Modifier
            .graphicsLayer {
                scaleX = lerp(1f, 1.5f, pressProgress)
                scaleY = lerp(1f, 1.5f, pressProgress)
            }
            .drawBackdrop(
                backdrop = rememberCombinedBackdrop(
                    backdrop ?: emptyBackdrop(),
                    rememberBackdrop(trackBackdrop) { drawBackdrop -> drawBackdrop() },
                ),
                shape = { Capsule() },
                effects = {
                    blur(8.dp.toPx() * (1f - pressProgress))
                    lens(10.dp.toPx() * pressProgress, 14.dp.toPx() * pressProgress, chromaticAberration = true)
                },
                highlight = { Highlight.Ambient.copy(alpha = .45f + .55f * pressProgress) },
                shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = .08f)) },
                innerShadow = { InnerShadow(radius = 4.dp * pressProgress, alpha = pressProgress) },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - .12f * pressProgress)) },
            )
            .size(40.dp, 24.dp),
    )
}

@Composable
internal fun RepositoryLiquidBottomTabs(
    selectedTabIndex: Int,
    backdrop: Backdrop?,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val resolvedBackdrop = backdrop ?: emptyBackdrop()
    val selected by animateFloatAsState(selectedTabIndex.toFloat(), spring(.75f, 420f), label = "liquid tab")
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isLight = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > .5f
    val container = if (isLight) Color(0xFFFAFAFA).copy(alpha = .58f) else Color(0xFF121212).copy(alpha = .62f)
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val tabWidth = (constraints.maxWidth - with(LocalDensity.current) { 8.dp.roundToPx() }).toFloat() / tabsCount
        Row(
            Modifier
                .layerBackdrop(tabsBackdrop)
                .drawBackdrop(
                    backdrop = resolvedBackdrop,
                    shape = { Capsule() },
                    effects = { vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx()) },
                    highlight = { Highlight.Default.copy(alpha = .65f) },
                    shadow = { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = .10f)) },
                    onDrawSurface = { drawRect(container) },
                )
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = if (isLtr) selected * tabWidth else size.width - (selected + 1f) * tabWidth
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(resolvedBackdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = { lens(10.dp.toPx(), 14.dp.toPx(), chromaticAberration = true) },
                    highlight = { Highlight.Default },
                    shadow = { Shadow(radius = 5.dp, color = Color.Black.copy(alpha = .10f)) },
                    innerShadow = { InnerShadow(radius = 5.dp, alpha = .35f) },
                    onDrawSurface = { drawRect(if (isLight) Color.Black.copy(alpha = .08f) else Color.White.copy(alpha = .12f)) },
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount)
                .clearAndSetSemantics { },
        )
    }
}

@Composable
internal fun RowScope.RepositoryLiquidBottomTab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(Capsule())
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
