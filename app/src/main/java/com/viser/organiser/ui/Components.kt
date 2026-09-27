package com.viser.organiser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.viser.organiser.ui.theme.C
import com.viser.organiser.ui.theme.T

enum class Tab { HOME, SAVED, TODOS, MONEY }

@Composable
fun BottomNav(current: Tab?, onTab: (Tab) -> Unit, onCapture: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(C.Nav)
            .navigationBarsPadding()
            .height(78.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, start = 6.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Top,
        ) {
            NavItem(Ic.Home, "HOME", current == Tab.HOME) { onTab(Tab.HOME) }
            NavItem(Ic.Bookmark, "SAVED", current == Tab.SAVED) { onTab(Tab.SAVED) }
            Box(
                Modifier
                    .offset(y = (-34).dp)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(C.Nav)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1C1C1C))
                    .border(1.dp, C.DarkLine2, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Capture something new") { onCapture() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.Plus, contentDescription = "Capture something new", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            NavItem(Ic.CheckCircle, "TO-DOS", current == Tab.TODOS) { onTab(Tab.TODOS) }
            NavItem(Ic.Wallet, "MONEY", current == Tab.MONEY) { onTab(Tab.MONEY) }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) Color.White else C.NavIdle
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Text(label, style = T.sans(10, if (active) 700 else 600, 0.12.em, color))
    }
}

/** Light bottom panel that sits above the nav (tabs / filters row). */
@Composable
fun BottomPanel(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
            .shadow(20.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp), ambientColor = Color(0x22151515), spotColor = Color(0x22151515))
            .background(Color.White, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun SegTab(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) C.Ink else C.Chip)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = T.sans(11, 700, 0.08.em, if (active) Color.White else C.Ink), maxLines = 1)
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, label: String, size: Dp = 44.dp, dark: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (dark) Color.Transparent else Color.White)
            .border(1.dp, if (dark) C.DarkLine2 else C.Line, CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (dark) Color.White else C.Ink, modifier = Modifier.size(if (size > 40.dp) 20.dp else 18.dp))
    }
}

@Composable
fun Kicker(text: String, color: Color = C.Muted, modifier: Modifier = Modifier) =
    Text(text.uppercase(), style = T.kicker(color), modifier = modifier)

/** Selectable chip used for categories, sections, reminder options. */
@Composable
fun Chip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
    dot: Color? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(if (selected) C.Ink else Color.White)
            .border(1.dp, if (selected) C.Ink else C.Line, RoundedCornerShape(height / 2))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(text, style = T.sans(13, 600, color = if (selected) Color.White else C.Ink), maxLines = 1)
    }
}

@Composable
fun DashedChip(text: String, dark: Boolean = false, height: Dp = 38.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .border(1.dp, if (dark) C.Dashed else C.OnDarkFaint, RoundedCornerShape(height / 2))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = T.sans(if (dark) 12 else 13, 600, color = if (dark) Color(0xFFE8E6E1) else C.Ink))
    }
}

@Composable
fun LabelPill(name: String, color: Color) {
    Row(
        Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(C.Chip).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(name, style = T.sans(12, 500))
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 56.dp, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(if (enabled) C.Ink else C.Faint)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = T.sans(15, 700, color = Color.White))
    }
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, height: Dp = 44.dp, onClick: () -> Unit) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(Color.White)
            .border(1.dp, C.Sheet, RoundedCornerShape(height / 2))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = T.sans(14, 600))
    }
}

/** A settings-style row: icon + label on the left, value + chevron on the right. */
@Composable
fun ValueRow(icon: ImageVector?, label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = C.Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = T.sans(14, 600), modifier = Modifier.weight(1f))
        Text("$value ›", style = T.sans(14, 500, color = C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Divider(color: Color = C.Line) = Box(Modifier.fillMaxWidth().height(1.dp).background(color))

@Composable
fun SheetHandle() = Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.Sheet))

/** Dark-surface text input matching the capture screen. */
@Composable
fun DarkField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = T.sans(15, 400, color = Color.White),
    boxed: Boolean = true,
    singleLine: Boolean = true,
    minHeight: Dp = 44.dp,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
) {
    val shape: Shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = style,
        keyboardOptions = keyboard,
        cursorBrush = SolidColor(Color.White),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                (if (boxed) Modifier.background(C.DarkCard, shape).border(1.dp, C.DarkLine, shape).padding(horizontal = 14.dp) else Modifier)
                    .fillMaxWidth()
                    .then(if (boxed) Modifier.height(minHeight) else Modifier),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = C.Faint))
                inner()
            }
        },
    )
}

/** Light-surface text input (search, dialogs). */
@Composable
fun LightField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    leading: ImageVector? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = T.sans(15, 400),
        keyboardOptions = keyboard,
        cursorBrush = SolidColor(C.Ink),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (singleLine) Modifier.height(48.dp) else Modifier)
                    .background(Color.White, RoundedCornerShape(if (singleLine) 24.dp else 14.dp))
                    .border(1.dp, C.Line, RoundedCornerShape(if (singleLine) 24.dp else 14.dp))
                    .padding(horizontal = 16.dp, vertical = if (singleLine) 0.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leading != null) Icon(leading, null, tint = C.Muted, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = T.sans(15, 400, color = C.Faint))
                    inner()
                }
            }
        },
    )
}

@Composable
fun EmptyState(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = T.serif(22, 600))
        Text(sub, style = T.sans(13, 500, color = C.Muted))
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp), onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(BorderStroke(1.dp, C.Line), RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
    ) { content() }
}
