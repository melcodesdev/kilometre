package dev.melcodes.kilometre.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import dev.melcodes.kilometre.R

// Shared building blocks for the Settings screens. These were private helpers
// inside SettingsScreen.kt until the screen was split into a category hub plus
// one screen per category (Profile / App / Map & replay / About); they are now
// `internal` so every category screen in this package reuses the same widgets
// instead of each redefining its own. Pure presentation — no AppContainer or
// navigation here; the screens own state and wire these together.

// A single selectable language row inside the language chooser: a radio button
// plus the endonym label, the whole row tappable.
@Composable
internal fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

// A titled group of settings rows rendered as one rounded surfaceContainer
// card, with a muted uppercase header floating above it. Grouping rows into a
// tonal card (instead of a flat list with hairline dividers) is what gives the
// screen its visual structure: each card reads as a single unit, and the calm
// onSurfaceVariant header avoids the louder primary-coloured headers we had
// before. content is a ColumnScope so callers stack rows directly inside.
@Composable
internal fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 6.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(content = content)
        }
    }
}

// A subtle divider between rows inside a SettingsGroup card. Inset from the
// left so it doesn't touch the card edge, and uses outlineVariant (fainter
// than outline) so it reads as a hairline separator, not a hard line.
@Composable
internal fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

// Per-row accent colours for the leading icon badges. These are decorative —
// they give each row a distinct identity (the WeatherMaster-style look the
// author asked for) and are deliberately NOT theme colorScheme roles, so they
// stay constant across light/dark. The badge background is the same hue at low
// alpha, which reads correctly on both surfaces.
internal val AccentBlue = Color(0xFF4C8DF5)
internal val AccentTeal = Color(0xFF1FB6A6)
internal val AccentAmber = Color(0xFFE0A02E)
internal val AccentPurple = Color(0xFF9B7BE8)
internal val AccentOrange = Color(0xFFE07A4B)
internal val AccentGreen = Color(0xFF54B45A)
internal val AccentPink = Color(0xFFE269A6)
internal val AccentSlate = Color(0xFF8290A4)
internal val AccentRed = Color(0xFFE05B5B)

// The round, tinted icon badge that leads every settings row. The icon takes
// the full accent colour; the disc behind it is the same hue at 18% alpha.
@Composable
internal fun IconBadge(icon: ImageVector, accent: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
    }
}

// Standard settings row: leading icon badge, a title, an optional muted
// subtitle (usually the current value), and an optional trailing affordance.
// onClick is optional — rows that only display information (e.g. the version
// row) pass null and render non-clickable, with no ripple or false affordance.
// A row can carry EITHER a trailingIcon (a chevron pointing to a sub-screen or
// dialog) OR a trailingContent slot (an inline control such as a Switch); the
// slot takes precedence so a toggle row reads as on/off with no false "opens
// elsewhere" chevron.
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String? = null,
    trailingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, accent)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (onInfo != null) InfoButton(onInfo)
            }
            if (subtitle != null) {
                // No maxLines cap: the subtitle is a description (a save-folder
                // path, a hint about what a toggle does) and a description has
                // to show all of itself, not a truncated head or tail. Long
                // text wraps onto as many lines as it needs.
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(8.dp))
            trailingContent()
        } else if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp),
            )
        }
    }
}

// Header for the inline-control rows (theme, map style, gradient): the same
// leading badge + title as a SettingsRow, but with no value/trailing because
// the control itself sits directly below it inside the same card row.
@Composable
internal fun ControlHeader(
    icon: ImageVector,
    accent: Color,
    title: String,
    onInfo: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, accent)
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (onInfo != null) InfoButton(onInfo)
    }
}

// A small leading-aligned ⓘ button used next to a setting's title to open a
// short explanation dialog, so the row itself stays uncluttered.
@Composable
internal fun InfoButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = stringResource(R.string.cd_info),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

// Generic segmented-button setting row: a header with an inline single-choice
// segmented row beneath. `options` carries (value, already-resolved label)
// pairs. Used for theme, map style, replay length/speed, chart smoothing — any
// small fixed set of mutually-exclusive choices.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SegmentedSettingRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    options: List<Pair<T, String>>,
    current: T,
    onSet: (T) -> Unit,
    enabled: Boolean = true,
    onInfo: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        ControlHeader(icon, accent, title, onInfo)
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (value, label) ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    selected = current == value,
                    enabled = enabled,
                    onClick = { onSet(value) },
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

// App theme selection: three-segment segmented button for System / Light /
// Dark. Tapping a segment persists immediately via the container setter.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeRow(icon: ImageVector, accent: Color, current: String, onSet: (String) -> Unit) {
    val options = listOf(
        "system" to R.string.settings_theme_system,
        "light" to R.string.settings_theme_light,
        "dark" to R.string.settings_theme_dark,
    )
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        ControlHeader(icon, accent, stringResource(R.string.settings_theme))
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (key, labelRes) ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    selected = current == key,
                    onClick = { onSet(key) },
                ) {
                    Text(stringResource(labelRes))
                }
            }
        }
    }
}

// Map tile style selection: four-segment row for the four OpenFreeMap
// hosted styles. Liberty is the default and was previously hardcoded.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapStyleRow(icon: ImageVector, accent: Color, current: String, onSet: (String) -> Unit) {
    val options = listOf(
        "liberty" to R.string.settings_map_liberty,
        "bright" to R.string.settings_map_bright,
        "positron" to R.string.settings_map_positron,
        "fiord" to R.string.settings_map_fiord,
    )
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        ControlHeader(icon, accent, stringResource(R.string.settings_map_style))
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (key, labelRes) ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    selected = current == key,
                    onClick = { onSet(key) },
                ) {
                    Text(stringResource(labelRes))
                }
            }
        }
    }
}

// A setting row whose trailing control is a round colour swatch; tapping the
// row opens the colour picker for that slot.
@Composable
internal fun ColorSettingRow(
    icon: ImageVector,
    title: String,
    hex: String,
    onClick: () -> Unit,
) {
    // The icon badge takes the chosen colour itself, so the row previews the
    // colour in both the leading badge and the trailing swatch.
    val color = remember(hex) { hexToComposeColor(hex) }
    SettingsRow(
        icon = icon,
        accent = color,
        title = title,
        onClick = onClick,
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        },
    )
}

// Route gradient row. Shows a gradient preview bar between two tappable
// colour swatches (one for the start colour, one for the end). Tapping a
// swatch opens the colour-picker bottom sheet for that slot.
@Composable
internal fun GradientRow(
    icon: ImageVector,
    accent: Color,
    startHex: String,
    endHex: String,
    onTapStart: () -> Unit,
    onTapEnd: () -> Unit,
) {
    val startColor = remember(startHex) { hexToComposeColor(startHex) }
    val endColor = remember(endHex) { hexToComposeColor(endHex) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        ControlHeader(icon, accent, stringResource(R.string.settings_gradient))
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorSwatch(color = startColor, onClick = onTapStart)
            Spacer(Modifier.width(8.dp))
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
            ) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(colors = listOf(startColor, endColor)),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
            Spacer(Modifier.width(8.dp))
            ColorSwatch(color = endColor, onClick = onTapEnd)
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_gradient_start),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp),
            )
            Spacer(Modifier.width(8.dp))
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_gradient_end),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp),
            )
        }
    }
}

// A round tappable colour swatch used in GradientRow.
@Composable
internal fun ColorSwatch(color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
    )
}

// HSV colour wheel bottom sheet (skydoves/colorpicker-compose, Apache-2.0).
// The wheel initialises at the current saved colour; the brightness slider
// below adjusts the V channel. Tapping Save commits the selection.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColorPickerSheet(
    title: String,
    currentHex: String,
    sheetState: SheetState,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val controller = rememberColorPickerController()
    // pickedHex is the canonical AARRGGBB value the wheel reports; it's what we save.
    var pickedHex by remember { mutableStateOf(currentHex) }
    // hexField is the visible RRGGBB text. Gradients are opaque, so we hide the
    // alpha byte from the user and re-add FF when parsing their input back.
    var hexField by remember { mutableStateOf(currentHex.takeLast(6)) }
    val initialColor = remember(currentHex) { hexToComposeColor(currentHex) }

    // Snap the wheel to the saved colour once the composable enters composition.
    LaunchedEffect(initialColor) {
        controller.selectByColor(initialColor, fromUser = false)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Scroll + imePadding so the focused hex field lifts above the
                // soft keyboard instead of being covered by it. The text field's
                // built-in bring-into-view scrolls it into the shrunk viewport.
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            HsvColorPicker(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                controller = controller,
                // fromUser distinguishes a real wheel/slider drag from our own
                // programmatic selectByColor below; only mirror genuine drags into
                // the text field, otherwise typing fights the cursor.
                onColorChanged = { envelope ->
                    pickedHex = envelope.hexCode
                    if (envelope.fromUser) hexField = envelope.hexCode.takeLast(6)
                },
            )
            Spacer(Modifier.height(16.dp))
            BrightnessSlider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                controller = controller,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = hexField,
                onValueChange = { input ->
                    // Keep only hex digits, cap at the 6 RRGGBB chars.
                    val cleaned = input.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                    hexField = cleaned
                    // A full triplet drives the wheel; selectByColor reports back
                    // through onColorChanged (fromUser = false) and updates pickedHex.
                    if (cleaned.length == 6) {
                        controller.selectByColor(hexToComposeColor("FF$cleaned"), fromUser = false)
                    }
                },
                label = { Text(stringResource(R.string.settings_color_picker_hex)) },
                prefix = { Text("#") },
                singleLine = true,
                isError = hexField.length < 6,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSelect(pickedHex) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        }
    }
}

// Single text-field dialog used for both the name and the goal edits.
// keyboardType lets the goal field use a numeric keyboard.
@Composable
internal fun EditTextDialog(
    title: String,
    initial: String,
    singleLine: Boolean,
    keyboardType: KeyboardType,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = singleLine,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onSave(text.trim()) },
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

// Parse an AARRGGBB hex string (e.g. "FF493F59") to a Compose Color.
internal fun hexToComposeColor(hex: String): Color {
    val argb = android.graphics.Color.parseColor("#$hex")
    return Color(
        red = (argb shr 16) and 0xFF,
        green = (argb shr 8) and 0xFF,
        blue = argb and 0xFF,
        alpha = (argb ushr 24) and 0xFF,
    )
}
