package app.readbound.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    contentColor: Color = contentColorFor(color),
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, spring(), label = "glass press")
    val glass = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        .liquidGlass(shape, color, backdrop, shadowElevation > 0.dp)
        .then(if (border != null) Modifier.border(border, shape) else Modifier)
    if (onClick != null) {
        Surface(onClick = onClick, enabled = enabled, modifier = glass, shape = shape,
            color = Color.Transparent, contentColor = contentColor, interactionSource = interaction, content = content)
    } else {
        Surface(modifier = glass, shape = shape, color = Color.Transparent,
            contentColor = contentColor, content = content)
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val container = if (enabled) colors.containerColor else colors.disabledContainerColor
    val foreground = if (enabled) colors.contentColor else colors.disabledContentColor
    CompositionLocalProvider(LocalContentColor provides foreground) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            RepositoryLiquidButton(
                onClick = onClick,
                backdrop = LocalGlassBackdrop.current,
                modifier = modifier.defaultMinSize(minHeight = 48.dp).semantics { role = Role.Button },
                enabled = enabled,
                tint = if (container == MaterialTheme.colorScheme.primary) container else Color.Unspecified,
                surfaceColor = container.copy(alpha = if (container == MaterialTheme.colorScheme.surface) .34f else .88f),
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
    content: @Composable RowScope.() -> Unit,
) = GlassButton(onClick, modifier, enabled, colors = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.primary,
), content = content)

@Composable
fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            RepositoryLiquidButton(
                onClick = onClick,
                backdrop = LocalGlassBackdrop.current,
                modifier = modifier,
                enabled = enabled,
                surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .10f),
                contentPadding = contentPadding,
                content = content,
            )
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
