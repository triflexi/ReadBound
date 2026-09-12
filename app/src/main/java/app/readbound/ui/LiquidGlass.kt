package app.readbound.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.readbound.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

// Only the background is recorded here. Recording a glass surface into the
// backdrop it reads would create a recursive graphics-layer dependency.
internal val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun GlassBackground(content: @Composable () -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            drawRect(colors.background)
            drawRect(Brush.radialGradient(
                listOf(colors.primary.copy(alpha = if (dark) .14f else .13f), Color.Transparent),
                center = Offset(size.width * .95f, size.height * .12f), radius = size.width * 1.1f,
            ))
            drawRect(Brush.radialGradient(
                listOf(Color(0xFF67CDBB).copy(alpha = if (dark) .09f else .12f), Color.Transparent),
                center = Offset(0f, size.height * .68f), radius = size.width * 1.2f,
            ))
        }
        CompositionLocalProvider(
            LocalGlassBackdrop provides backdrop,
            LocalContentColor provides colors.onBackground,
            content = content,
        )
    }
}

/** A shared material: blurred backdrop, restrained refraction, tint and edge light. */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = MaterialTheme.colorScheme.surface,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    elevated: Boolean = false,
): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val supportsEffects = Build.VERSION.SDK_INT >= 31 && !LocalInspectionMode.current
    val colored = tint.alpha > 0f && tint != MaterialTheme.colorScheme.surface &&
        tint != MaterialTheme.colorScheme.surfaceVariant && tint != MaterialTheme.colorScheme.primaryContainer
    val opacity = when {
        colored -> .98f
        !supportsEffects || backdrop == null -> if (dark) .96f else .94f
        // Reader chrome is laid over dense text. Keep it translucent, but never
        // transparent enough for glyphs below it to bleed into control labels.
        elevated -> if (dark) .90f else .86f
        else -> if (dark) .86f else .76f
    }
    val base = if (tint.alpha == 0f) MaterialTheme.colorScheme.surface else tint
    val sheen = Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (dark) .10f else .34f),
        Color.White.copy(alpha = .02f),
        base.copy(alpha = .10f),
    ))
    val edge = Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (dark) .28f else .88f),
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = .26f),
        Color.White.copy(alpha = if (dark) .10f else .52f),
    ))
    return this
        .shadow(if (elevated) 12.dp else 2.dp, shape, clip = false,
            ambientColor = Color(0xFF172C48).copy(alpha = .12f),
            spotColor = Color(0xFF172C48).copy(alpha = .12f))
        .then(if (backdrop != null && supportsEffects) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(if (elevated) 10.dp.toPx() else 5.dp.toPx())
                    if (elevated) lens(12.dp.toPx(), 20.dp.toPx())
                },
                highlight = null,
                shadow = null,
                onDrawSurface = { drawRect(base.copy(alpha = opacity)); drawRect(sheen) },
            )
        } else Modifier.background(base.copy(alpha = opacity), shape).background(sheen, shape))
        .border(1.dp, edge, shape)
        .clip(shape)
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = glassContentColorFor(color),
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .965f else 1f, spring(dampingRatio = .62f, stiffness = 420f), label = "glass press")
    val animatedColor by animateColorAsState(color, tween(360), label = "glass surface color")
    val animatedContentColor by animateColorAsState(contentColor, tween(360), label = "glass content color")
    val glass = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .liquidGlass(shape, animatedColor, backdrop, shadowElevation > 0.dp)
        .then(if (border != null) Modifier.border(border, shape) else Modifier)
    if (onClick != null) {
        Surface(onClick = onClick, enabled = enabled, modifier = glass, shape = shape,
            color = Color.Transparent, contentColor = animatedContentColor, interactionSource = interaction, content = content)
    } else {
        Surface(modifier = glass, shape = shape, color = Color.Transparent,
            contentColor = animatedContentColor, content = content)
    }
}

@Composable
private fun glassContentColorFor(color: Color): Color {
    val scheme = MaterialTheme.colorScheme
    fun Color.matches(other: Color) =
        kotlin.math.abs(red - other.red) < .002f &&
            kotlin.math.abs(green - other.green) < .002f &&
            kotlin.math.abs(blue - other.blue) < .002f
    return when {
        color.matches(scheme.primary) -> scheme.onPrimary
        color.matches(scheme.primaryContainer) -> scheme.onPrimaryContainer
        color.matches(scheme.error) -> scheme.onError
        color.matches(scheme.errorContainer) -> scheme.onErrorContainer
        color.matches(scheme.inverseSurface) -> scheme.inverseOnSurface
        color.matches(scheme.surfaceVariant) -> scheme.onSurfaceVariant
        color.matches(scheme.background) -> scheme.onBackground
        else -> scheme.onSurface
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
    ),
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val targetContainer = if (enabled) colors.containerColor else colors.disabledContainerColor
    val targetForeground = if (enabled) colors.contentColor else colors.disabledContentColor.copy(alpha = colors.disabledContentColor.alpha.coerceAtLeast(.72f))
    val container by animateColorAsState(targetContainer, tween(280), label = "glass button surface")
    val foreground by animateColorAsState(targetForeground, tween(280), label = "glass button content")
    val backdrop = LocalGlassBackdrop.current
    val prominent = container.copy(alpha = 1f) == MaterialTheme.colorScheme.primary.copy(alpha = 1f)
    val surfaceAlpha = when {
        backdrop == null -> .94f
        prominent -> .82f
        else -> .68f
    }
    CompositionLocalProvider(LocalContentColor provides foreground) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            RepositoryLiquidButton(
                onClick = onClick,
                backdrop = backdrop,
                modifier = modifier.defaultMinSize(minHeight = 48.dp).semantics { role = Role.Button },
                enabled = enabled,
                shape = shape,
                tint = if (prominent) container.copy(alpha = .72f) else Color.Unspecified,
                surfaceColor = container.copy(alpha = surfaceAlpha),
                contentPadding = contentPadding,
            ) { content() }
        }
    }
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) = GlassButton(onClick, modifier, enabled, shape = shape, colors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    contentColor = MaterialTheme.colorScheme.onSurface,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
), contentPadding = contentPadding, content = content)

@Composable
fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val targetColor = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f)
    val foreground by animateColorAsState(targetColor, tween(280), label = "glass text button content")
    CompositionLocalProvider(LocalContentColor provides foreground) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            RepositoryLiquidButton(
                onClick = onClick,
                backdrop = LocalGlassBackdrop.current,
                modifier = modifier,
                enabled = enabled,
                shape = shape,
                surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .10f),
                contentPadding = contentPadding,
                content = content,
            )
        }
    }
}

/** Compact repository-backed button used by toolbar, FAB and sheet actions. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = glassContentColorFor(containerColor),
    elevated: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val backdrop = LocalGlassBackdrop.current
    val foreground by animateColorAsState(
        if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
        tween(280),
        label = "glass icon content",
    )
    CompositionLocalProvider(LocalContentColor provides foreground) {
        RepositoryLiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .then(if (elevated) Modifier.shadow(10.dp, shape, clip = false) else Modifier),
            enabled = enabled,
            shape = shape,
            surfaceColor = containerColor.copy(alpha = if (backdrop == null) .94f else .68f),
            contentPadding = PaddingValues(0.dp),
        ) { content() }
    }
}

/** A consistent dropdown/selector affordance with the same spring and drag response as demo buttons. */
@Composable
fun GlassDropdownButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    expanded: Boolean = false,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, spring(dampingRatio = .7f, stiffness = 500f), label = "selector arrow")
    GlassOutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f), maxLines = 1)
        ReaderIcon(
            R.raw.icon_chevron,
            null,
            Modifier.size(18.dp).rotate(rotation),
            ColorFilter.tint(LocalContentColor.current),
        )
    }
}

/** Non-interactive glass pill for metadata such as note tags. */
@Composable
fun GlassLabelChip(label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier.heightIn(min = 32.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            ProvideTextStyle(MaterialTheme.typography.labelSmall, content = label)
        }
    }
}

/** Checkbox semantics with a small animated glass selection indicator. */
@Composable
fun GlassCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 1.16f else 1f, spring(dampingRatio = .58f, stiffness = 520f), label = "glass checkbox press")
    val container by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        tween(260),
        label = "glass checkbox surface",
    )
    val foreground by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimaryContainer else Color.Transparent,
        tween(220),
        label = "glass checkbox mark",
    )
    Box(
        modifier
            .size(48.dp)
            .toggleable(
                value = checked,
                enabled = onCheckedChange != null,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = null,
                onValueChange = { onCheckedChange?.invoke(it) },
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .liquidGlass(RoundedCornerShape(10.dp), tint = container),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text("✓", color = foreground, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tint by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        label = "glass selection",
    )
    GlassSurface(modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = selected },
        shape = RoundedCornerShape(50), color = tint,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick, enabled = enabled,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .32f)) else null,
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge, content = label)
        }
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    shape: Shape = RoundedCornerShape(20.dp),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    TextField(value, onValueChange, modifier.liquidGlass(shape), label = label,
        placeholder = placeholder, leadingIcon = leadingIcon, trailingIcon = trailingIcon,
        singleLine = singleLine, minLines = minLines, maxLines = maxLines,
        shape = shape, visualTransformation = visualTransformation,
        colors = colors.copy(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
    )
}

@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    RepositoryLiquidToggle(checked, onCheckedChange, LocalGlassBackdrop.current, modifier)
}

@Composable
fun GlassSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return
    RepositoryLiquidBottomTabs(
        selectedTabIndex = selectedIndex.coerceIn(labels.indices),
        onTabSelected = onSelected,
        backdrop = LocalGlassBackdrop.current,
        tabsCount = labels.size,
        modifier = modifier,
    ) {
        labels.forEachIndexed { index, label ->
            RepositoryLiquidBottomTab(
                selected = index == selectedIndex,
                onClick = { onSelected(index) },
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    RepositoryLiquidSlider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished, valueRange = valueRange,
        visibilityThreshold = .001f,
        backdrop = LocalGlassBackdrop.current,
        modifier = modifier.fillMaxWidth(),
    )
}

// Sheets/dialogs own a different window: a screen-coordinate backdrop must not
// be sampled there. Dense frosted glass keeps text readable without a stale capture.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    CompositionLocalProvider(LocalGlassBackdrop provides null) {
        ModalBottomSheet(onDismissRequest, modifier,
            sheetState = sheetState, shape = shape, containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 0.dp,
            dragHandle = null,
            scrimColor = Color(0xFF101928).copy(alpha = .28f),
        ) {
            // Draw inside the anchored sheet. An outer draw modifier paints at
            // the unshifted origin while Material moves the sheet for gestures.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(containerColor, shape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .34f), shape)
                    .clip(shape),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    BottomSheetDefaults.DragHandle()
                }
                content()
            }
        }
    }
}

@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    CompositionLocalProvider(LocalGlassBackdrop provides null) {
        AlertDialog(onDismissRequest, confirmButton,
            modifier = Modifier.liquidGlass(RoundedCornerShape(32.dp), backdrop = null),
            dismissButton = dismissButton, title = title, text = text,
            shape = RoundedCornerShape(32.dp), containerColor = Color.Transparent, tonalElevation = 0.dp)
    }
}
