package com.vatsyahub.workout.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vatsyahub.workout.domain.dropIndex
import com.vatsyahub.workout.domain.orderNumber
import com.vatsyahub.workout.domain.shiftForDrag
import java.time.LocalDate

@Composable
fun Page(scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val base = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 20.dp)
    if (scroll) {
        Column(base.verticalScroll(rememberScrollState()).padding(bottom = 28.dp), content = content)
    } else {
        Column(base.padding(bottom = 12.dp), content = content)
    }
}

@Composable
fun ListPage(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp),
    ) {
        content()
    }
}

@Composable
fun Door(icon: ImageVector, label: String, detail: String = "", onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun StartCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .heightIn(min = 124.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun OrderBadge(number: Int) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun DragGrip(onStart: () -> Unit, onDrag: (Float) -> Unit, onEnd: () -> Unit) {
    val start = rememberUpdatedState(onStart)
    val drag = rememberUpdatedState(onDrag)
    val end = rememberUpdatedState(onEnd)
    Box(
        Modifier
            .size(48.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var total = 0f
                    start.value()
                    down.consume()
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.position - change.previousPosition
                            change.consume()
                            total += delta.y
                            drag.value(total)
                        }
                    } finally {
                        end.value()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.DragIndicator, contentDescription = "Reorder", tint = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun <T> ReorderColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onMove: (Int, Int) -> Unit,
    row: @Composable (index: Int, item: T, number: Int, moving: Boolean, handle: @Composable () -> Unit) -> Unit,
) {
    var from by remember { mutableIntStateOf(-1) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val session = remember { floatArrayOf(-1f, 0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    val spans = items.map { heights[keyOf(it)]?.toFloat() ?: 0f }
    val target = if (from in items.indices) dropIndex(from, items.size, dragY, spans) else -1
    val draggedSpan = spans.getOrElse(from) { 0f }
    val elevation = with(LocalDensity.current) { 10.dp.toPx() }
    items.forEachIndexed { index, item ->
        key(keyOf(item)) {
            val moving = index == from
            val shift = if (from >= 0) shiftForDrag(index, from, target, draggedSpan) else 0f
            val number = if (from >= 0) orderNumber(index, from, target) else index + 1
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (moving) 1f else 0f)
                    .onSizeChanged { heights[keyOf(item)] = it.height }
                    .graphicsLayer {
                        translationY = if (moving) dragY else shift
                        shadowElevation = if (moving) elevation else 0f
                        shape = RoundedCornerShape(16.dp)
                        scaleX = if (moving) 1.02f else 1f
                        scaleY = if (moving) 1.02f else 1f
                    }
                    .then(
                        if (moving) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                        } else {
                            Modifier
                        },
                    ),
            ) {
                row(index, item, number, moving) {
                    DragGrip(
                        onStart = {
                            session[0] = index.toFloat()
                            session[1] = 0f
                            from = index
                            dragY = 0f
                        },
                        onDrag = {
                            session[1] = it
                            dragY = it
                        },
                        onEnd = {
                            val origin = session[0].toInt()
                            val landing = if (origin in items.indices) {
                                val measured = items.map { heights[keyOf(it)]?.toFloat() ?: 0f }
                                dropIndex(origin, items.size, session[1], measured)
                            } else {
                                origin
                            }
                            session[0] = -1f
                            session[1] = 0f
                            from = -1
                            dragY = 0f
                            if (origin >= 0) onMove(origin, landing)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun WorkoutCard(
    title: String,
    detail: String,
    favourite: Boolean = false,
    onUnfavourite: (() -> Unit)? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon, Modifier.padding(start = 14.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(start = if (icon == null) 16.dp else 12.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (favourite && onUnfavourite == null) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Favourite",
                        modifier = Modifier.padding(start = 8.dp).size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
        if (onUnfavourite != null) {
            IconButton(onClick = onUnfavourite, modifier = Modifier.padding(end = 4.dp)) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Remove favourite",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun FavouriteMark(on: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (on) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (on) "Remove favourite" else "Favourite",
            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun SetTableHeader(showWeight: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Set",
            modifier = Modifier.weight(0.7f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            "Reps",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (showWeight) {
            Text(
                "Weight",
                modifier = Modifier.weight(1.1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun QuietButton(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

@Composable
fun ChoiceChip(label: String, on: Boolean, onClick: () -> Unit) {
    val background = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Text(
        label,
        color = foreground,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .padding(end = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
fun ActionPage(
    action: String,
    onAction: () -> Unit,
    showAction: Boolean = true,
    scrollEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState(), enabled = scrollEnabled)
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
            content = content,
        )
        if (showAction) {
            PrimaryButton(action, Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp), onAction)
        }
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun InsetCard(
    title: String,
    detail: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun BoxField(
    value: String,
    placeholder: String = "",
    onCommit: (String) -> Unit,
) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = if (focused) text else value,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
        decorationBox = { inner ->
            Box {
                if (!focused && value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                inner()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    text = value
                    focused = true
                } else if (focused) {
                    focused = false
                    onCommit(text)
                }
            },
    )
}

@Composable
fun FormPage(onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp),
            content = content,
        )
        PrimaryButton("Save", Modifier.padding(bottom = 4.dp), onSave)
    }
}

@Composable
fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

@Composable
fun Field(
    label: String,
    value: String,
    number: Boolean = false,
    onDone: (() -> Unit)? = null,
    onValue: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        if (label.isNotBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onBackground),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun ToggleRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (on) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(22.dp))
        } else {
            Spacer(Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun Word(
    text: String,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    onClick: () -> Unit,
) {
    Text(
        text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
fun Line(text: String, style: TextStyle = MaterialTheme.typography.titleLarge) {
    Text(text, style = style, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun Editable(
    shown: String,
    raw: String,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    number: Boolean = false,
    onCommit: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(raw) }
    if (!editing) {
        Text(
            shown,
            style = style,
            modifier = Modifier
                .clickable {
                    text = raw
                    editing = true
                }
                .padding(vertical = 8.dp),
        )
        return
    }
    val focus = remember { FocusRequester() }
    var armed by remember { mutableStateOf(false) }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = style.copy(color = MaterialTheme.colorScheme.onBackground),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = {
            editing = false
            onCommit(text)
        }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged {
                if (armed && !it.isFocused) {
                    editing = false
                    onCommit(text)
                }
            }
            .padding(vertical = 8.dp),
    )
    LaunchedEffect(Unit) {
        focus.requestFocus()
        armed = true
    }
}

@Composable
fun pickDate(current: LocalDate, onPick: (LocalDate) -> Unit): () -> Unit {
    val context = LocalContext.current
    return {
        DatePickerDialog(
            context,
            { _, year, month, day -> onPick(LocalDate.of(year, month + 1, day)) },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth,
        ).show()
    }
}
