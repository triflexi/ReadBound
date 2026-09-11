package app.readbound.ui

/*
 * Copyright 2025 Kyant
 * SPDX-License-Identifier: Apache-2.0
 *
 * Adapted from AndroidLiquidGlass-kmp's catalog components (Apache-2.0):
 * LiquidButton, LiquidSlider, LiquidToggle, LiquidBottomTabs and LiquidBottomTab.
 * Material-compatible wrappers live in LiquidGlass.kt.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sign
import kotlin.math.tanh

@Composable
internal fun RepositoryLiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val resolvedBackdrop = backdrop ?: emptyBackdrop()
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { RepositoryInteractiveHighlight(animationScope) }

    Row(
        modifier
            .drawBackdrop(
                backdrop = resolvedBackdrop,
                shape = { Capsule() },
                effects = { vibrancy(); blur(2.dp.toPx()); lens(12.dp.toPx(), 24.dp.toPx()) },
                layerBlock = if (enabled) {
                    {
                        val width = size.width
                        val height = size.height
                        val progress = interactiveHighlight.pressProgress
                        val baseScale = lerp(1f, 1f + 4.dp.toPx() / height, progress)
                        val maxOffset = size.minDimension
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
                        translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)
                        val dragScale = 4.dp.toPx() / height
                        val angle = atan2(offset.y, offset.x)
                        scaleX = baseScale + dragScale * abs(cos(angle) * offset.x / size.maxDimension) * (width / height).fastCoerceAtMost(1f)
                        scaleY = baseScale + dragScale * abs(sin(angle) * offset.y / size.maxDimension) * (height / width).fastCoerceAtMost(1f)
                    }
                } else null,
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) drawRect(surfaceColor)
                },
            )
            .clickable(
                interactionSource = null,
                indication = if (enabled) null else LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (enabled) Modifier.then(interactiveHighlight.modifier).then(interactiveHighlight.gestureModifier) else Modifier)
            .height(48.dp)
            .padding(contentPadding)
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
    val isLight = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > .5f
    val accent = if (isLight) Color(0xFF34C759) else Color(0xFF30D158)
    val track = if (isLight) Color(0xFF787878).copy(alpha = .20f) else Color(0xFF787880).copy(alpha = .36f)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    val currentSelected = androidx.compose.runtime.rememberUpdatedState(selected)
    val currentOnSelect = androidx.compose.runtime.rememberUpdatedState(onSelect)
    var fraction by remember { mutableFloatStateOf(if (selected) 1f else 0f) }
    val damped = remember(animationScope) {
        RepositoryDampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                val callback = currentOnSelect.value ?: return@RepositoryDampedDragAnimation
                if (didDrag) {
                    fraction = if (targetValue >= .5f) 1f else 0f
                    didDrag = false
                } else {
                    fraction = if (currentSelected.value) 0f else 1f
                }
                callback(fraction == 1f)
            },
            onDrag = { _, amount ->
                if (currentOnSelect.value == null) return@RepositoryDampedDragAnimation
                if (!didDrag) didDrag = amount.x != 0f
                val delta = amount.x / dragWidth
                fraction = if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f) else (fraction - delta).fastCoerceIn(0f, 1f)
            },
        )
    }
    LaunchedEffect(damped) {
        snapshotFlow { fraction }.collectLatest { damped.updateValue(it) }
    }
    LaunchedEffect(selected) {
        val target = if (selected) 1f else 0f
        if (target != fraction) {
            fraction = target
            damped.animateToValue(target)
        }
    }

    val trackBackdrop = rememberLayerBackdrop()
    Box(
        modifier
            .size(64.dp, 48.dp)
            .then(if (onSelect != null) damped.modifier else Modifier)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(selected)
                if (onSelect != null) onClick { onSelect(!selected); true }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .align(Alignment.Center)
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .background(androidx.compose.ui.graphics.lerp(track, accent, damped.value))
                .size(64.dp, 28.dp),
        )
        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2.dp.toPx()
                    translationX = if (isLtr) lerp(padding, padding + dragWidth, damped.value) else lerp(-padding, -(padding + dragWidth), damped.value)
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        resolvedBackdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = damped.pressProgress
                            scale(lerp(2f / 3f, .75f, progress), lerp(0f, .75f, progress)) { drawBackdrop() }
                        },
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = damped.pressProgress
                        blur(8.dp.toPx() * (1f - progress))
                        lens(5.dp.toPx() * progress, 10.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        val progress = damped.pressProgress
                        Highlight.Ambient.copy(width = Highlight.Ambient.width / 1.5f, blurRadius = Highlight.Ambient.blurRadius / 1.5f, alpha = progress)
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = .05f)) },
                    innerShadow = { InnerShadow(radius = 4.dp * damped.pressProgress, alpha = damped.pressProgress) },
                    layerBlock = {
                        scaleX = damped.scaleX
                        scaleY = damped.scaleY
                        val velocity = damped.velocity / 50f
                        scaleX /= 1f - (velocity * .75f).fastCoerceIn(-.2f, .2f)
                        scaleY *= 1f - (velocity * .25f).fastCoerceIn(-.2f, .2f)
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - damped.pressProgress)) },
                )
                .size(40.dp, 24.dp),
        )
    }
}

@Composable
internal fun RepositoryLiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val resolvedBackdrop = backdrop ?: emptyBackdrop()
    val isLight = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > .5f
    val accent = if (isLight) Color(0xFF0088FF) else Color(0xFF0091FF)
    val track = if (isLight) Color(0xFF787878).copy(alpha = .20f) else Color(0xFF787880).copy(alpha = .36f)
    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                setProgress { requested ->
                    val target = requested.coerceIn(valueRange)
                    onValueChange(target)
                    onValueChangeFinished?.invoke()
                    true
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val currentOnValueChange = androidx.compose.runtime.rememberUpdatedState(onValueChange)
        val currentOnValueChangeFinished = androidx.compose.runtime.rememberUpdatedState(onValueChangeFinished)
        var didDrag by remember { mutableStateOf(false) }
        var downPositionX by remember { mutableFloatStateOf(0f) }
        val damped = remember(animationScope, valueRange, visibilityThreshold) {
            RepositoryDampedDragAnimation(
                animationScope = animationScope,
                initialValue = value,
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = { position -> didDrag = false; downPositionX = position.x },
                onDragStopped = {
                    if (didDrag) {
                        currentOnValueChange.value(targetValue)
                    } else {
                        val fraction = (downPositionX / trackWidth).fastCoerceIn(0f, 1f)
                        val target = if (isLtr) {
                            lerp(valueRange.start, valueRange.endInclusive, fraction)
                        } else {
                            lerp(valueRange.endInclusive, valueRange.start, fraction)
                        }
                        currentOnValueChange.value(target)
                        animateToValue(target)
                    }
                    currentOnValueChangeFinished.value?.invoke()
                    didDrag = false
                },
                onDrag = { _, amount ->
                    if (!didDrag) didDrag = amount.x != 0f
                    val delta = (valueRange.endInclusive - valueRange.start) * (amount.x / trackWidth)
                    currentOnValueChange.value(if (isLtr) (targetValue + delta).coerceIn(valueRange) else (targetValue - delta).coerceIn(valueRange))
                },
            )
        }
        LaunchedEffect(value) {
            if (damped.targetValue != value) damped.updateValue(value)
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(track)
                    .height(6.dp)
                    .fillMaxWidth(),
            )
            Box(
                Modifier
                    .clip(Capsule())
                    .background(accent)
                    .height(6.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * damped.progress).fastRoundToInt()
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    },
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX = (-size.width / 2f + trackWidth * damped.progress)
                        .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                .then(damped.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        resolvedBackdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = damped.pressProgress
                            scale(lerp(2f / 3f, 1f, progress), lerp(0f, 1f, progress)) { drawBackdrop() }
                        },
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = damped.pressProgress
                        blur(8.dp.toPx() * (1f - progress))
                        lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        val progress = damped.pressProgress
                        Highlight.Ambient.copy(width = Highlight.Ambient.width / 1.5f, blurRadius = Highlight.Ambient.blurRadius / 1.5f, alpha = progress)
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = .05f)) },
                    innerShadow = { InnerShadow(radius = 4.dp * damped.pressProgress, alpha = damped.pressProgress) },
                    layerBlock = {
                        scaleX = damped.scaleX
                        scaleY = damped.scaleY
                        val velocity = damped.velocity / 10f
                        scaleX /= 1f - (velocity * .75f).fastCoerceIn(-.2f, .2f)
                        scaleY *= 1f - (velocity * .25f).fastCoerceIn(-.2f, .2f)
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - damped.pressProgress)) },
                )
                .size(40.dp, 24.dp),
        )
        Box(Modifier.fillMaxSize().then(damped.modifier))
    }
}

private val LocalLiquidTabScale = staticCompositionLocalOf { { 1f } }

@Composable
internal fun RepositoryLiquidBottomTabs(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop?,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val resolvedBackdrop = backdrop ?: emptyBackdrop()
    val isLight = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > .5f
    val accent = if (isLight) Color(0xFF0088FF) else Color(0xFF0091FF)
    val container = if (isLight) Color(0xFFFAFAFA).copy(alpha = .40f) else Color(0xFF121212).copy(alpha = .40f)
    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabsCount }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex) }
        val damped = remember(animationScope, tabsCount) {
            RepositoryDampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = .001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    currentIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    animateToValue(currentIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, .5f)) }
                },
                onDrag = { _, amount ->
                    updateValue((targetValue + amount.x / tabWidth * if (isLtr) 1f else -1f).fastCoerceIn(0f, (tabsCount - 1).toFloat()))
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + amount.x) }
                },
            )
        }
        LaunchedEffect(selectedTabIndex) { currentIndex = selectedTabIndex }
        LaunchedEffect(damped) {
            snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
                damped.animateToValue(index.toFloat())
                onTabSelected(index)
            }
        }
        val interactiveHighlight = remember(animationScope) {
            RepositoryInteractiveHighlight(animationScope) { size, _ ->
                Offset(
                    if (isLtr) (damped.value + .5f) * tabWidth + panelOffset else size.width - (damped.value + .5f) * tabWidth + panelOffset,
                    size.height / 2f,
                )
            }
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = resolvedBackdrop,
                    shape = { Capsule() },
                    effects = { vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx()) },
                    layerBlock = {
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, damped.pressProgress)
                        scaleX = scale; scaleY = scale
                    },
                    onDrawSurface = { drawRect(container) },
                )
                .then(interactiveHighlight.modifier)
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        CompositionLocalProvider(LocalLiquidTabScale provides { lerp(1f, 1.2f, damped.pressProgress) }) {
            Row(
                Modifier
                    .clearAndSetSemantics { }
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = resolvedBackdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy(); blur(8.dp.toPx())
                            lens(24.dp.toPx() * damped.pressProgress, 24.dp.toPx() * damped.pressProgress)
                        },
                        highlight = { Highlight.Default.copy(alpha = damped.pressProgress) },
                        onDrawSurface = { drawRect(container) },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = if (isLtr) damped.value * tabWidth + panelOffset else size.width - (damped.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(damped.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(resolvedBackdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = { lens(10.dp.toPx() * damped.pressProgress, 14.dp.toPx() * damped.pressProgress, chromaticAberration = true) },
                    highlight = { Highlight.Default.copy(alpha = damped.pressProgress) },
                    shadow = { Shadow(alpha = damped.pressProgress) },
                    innerShadow = { InnerShadow(radius = 8.dp * damped.pressProgress, alpha = damped.pressProgress) },
                    layerBlock = {
                        scaleX = damped.scaleX; scaleY = damped.scaleY
                        val velocity = damped.velocity / 10f
                        scaleX /= 1f - (velocity * .75f).fastCoerceIn(-.2f, .2f)
                        scaleY *= 1f - (velocity * .25f).fastCoerceIn(-.2f, .2f)
                    },
                    onDrawSurface = {
                        drawRect(if (isLight) Color.Black.copy(alpha = .10f) else Color.White.copy(alpha = .10f), alpha = 1f - damped.pressProgress)
                        drawRect(Color.Black.copy(alpha = .03f * damped.pressProgress))
                    },
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount),
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
    val scale = LocalLiquidTabScale.current
    Column(
        modifier
            .clip(Capsule())
            .selectable(selected = selected, interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer { scaleX = scale(); scaleY = scale() },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
