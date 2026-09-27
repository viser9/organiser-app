package com.viser.organiser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.viser.organiser.Nav
import com.viser.organiser.data.Item
import com.viser.organiser.data.ItemType
import com.viser.organiser.data.Repeat
import com.viser.organiser.reminders.ReminderScheduler
import com.viser.organiser.ui.Chip
import com.viser.organiser.ui.CircleIconButton
import com.viser.organiser.ui.DashedChip
import com.viser.organiser.ui.Divider
import com.viser.organiser.ui.Ic
import com.viser.organiser.ui.Kicker
import com.viser.organiser.ui.OutlineButton
import com.viser.organiser.ui.PrimaryButton
import com.viser.organiser.ui.ValueRow
import com.viser.organiser.ui.colorOf
import com.viser.organiser.ui.repo
import com.viser.organiser.ui.sections
import com.viser.organiser.ui.state
import com.viser.organiser.ui.theme.C
import com.viser.organiser.ui.theme.T
import com.viser.organiser.util.dateLong
import com.viser.organiser.util.friendlyWhen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DONE_MARK = "✓ "

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemScreen(nav: Nav, id: String) {
    val r = repo()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val stored by remember(id) { r.db.items().observe(id) }.state(null)
    val secs by sections()
    var draft by remember(id) { mutableStateOf<Item?>(null) }
    var remindMenu by remember { mutableStateOf(false) }
    var addSec by remember { mutableStateOf(false) }
    var editArea by remember { mutableStateOf(false) }

    LaunchedEffect(stored) { if (draft == null && stored != null) draft = stored }
    // Autosave edits after a short pause.
    LaunchedEffect(draft) {
        val d = draft ?: return@LaunchedEffect
        val s = stored ?: return@LaunchedEffect
        if (d.copy(updatedAt = 0) != s.copy(updatedAt = 0)) { delay(350); r.saveItem(d) }
    }

    // Leaving the screen (back, or opening something else) saves any edit the debounce hadn't saved yet.
    val deleting = remember { booleanArrayOf(false) }
    val latestDraft = androidx.compose.runtime.rememberUpdatedState(draft)
    val latestStored = androidx.compose.runtime.rememberUpdatedState(stored)
    androidx.compose.runtime.DisposableEffect(id) {
        onDispose {
            val d = latestDraft.value
            val s = latestStored.value
            if (!deleting[0] && d != null && s != null && s.deletedAt == null && d.copy(updatedAt = 0) != s.copy(updatedAt = 0)) {
                r.scope.launch { r.db.items().get(d.id)?.takeIf { it.deletedAt == null }?.let { r.saveItem(d) } }
            }
        }
    }

    val item = draft
    Column(Modifier.fillMaxSize().background(C.Ground).statusBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Ic.ChevronLeft, "Back") { nav.pop() }
            Spacer(Modifier.width(12.dp))
            Kicker(
                when (item?.type) { ItemType.TODO -> "To-do"; ItemType.LINK -> "Link"; ItemType.PLACE -> "Place"; else -> "Note" } +
                    (item?.let { " · saved " + dateLong(it.createdAt) } ?: ""),
                modifier = Modifier.weight(1f),
            )
            if (item != null && item.type != ItemType.TODO) {
                CircleIconButton(Ic.Pin, if (item.pinned) "Unpin" else "Pin to top") { draft = item.copy(pinned = !item.pinned) }
                Spacer(Modifier.width(10.dp))
            }
            CircleIconButton(Ic.Trash, "Delete") {
                deleting[0] = true
                draft = null
                r.scope.launch { r.softDelete(id) }
                nav.pop()
            }
        }
        if (item == null) {
            Text(if (stored == null) "" else "Loading…", modifier = Modifier.padding(20.dp))
            return@Column
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (item.pinned) Text("PINNED", style = T.kicker())
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (item.type == ItemType.TODO) {
                    Box(
                        Modifier.padding(top = 8.dp).size(28.dp).clip(CircleShape)
                            .background(if (item.done) C.Ink else Color.Transparent)
                            .border(1.5.dp, if (item.done) C.Ink else C.Faint, CircleShape)
                            .clickable(role = Role.Checkbox) {
                                val at = item.remindAt
                                draft = if (!item.done && item.repeat.isNotEmpty() && at != null) {
                                    item.copy(remindAt = ReminderScheduler.nextOccurrence(at, item.repeat))
                                } else {
                                    item.copy(done = !item.done, doneAt = if (!item.done) System.currentTimeMillis() else null)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { if (item.done) Icon(Ic.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                }
                BasicTextField(
                    value = item.title,
                    onValueChange = { draft = item.copy(title = it) },
                    textStyle = T.serif(30, 500).copy(lineHeight = 36.sp, textDecoration = if (item.done) TextDecoration.LineThrough else null),
                    cursorBrush = SolidColor(C.Ink),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box { if (item.title.isEmpty()) Text("Title", style = T.serif(30, 500, color = C.Faint)); inner() }
                    },
                )
            }

            // type-specific actions
            when (item.type) {
                ItemType.LINK -> {
                    UrlField(item.url, "Link") { u ->
                        val clean = com.viser.organiser.util.findUrl(u) ?: u.trim()
                        draft = item.copy(url = u.trim(), domain = com.viser.organiser.util.domainOf(clean))
                    }
                    PrimaryButton("Open ${item.domain.ifBlank { "link" }}", height = 48.dp, enabled = item.url.isNotBlank()) { openUrl(ctx, item.url) }
                }
                ItemType.PLACE -> {
                    UrlField(item.url, "Google Maps link") { u ->
                        val ll = com.viser.organiser.util.parseLatLng(u.trim())
                        draft = item.copy(url = u.trim(), domain = com.viser.organiser.util.domainOf(u.trim()),
                            lat = ll?.first ?: item.lat, lng = ll?.second ?: item.lng)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineButton("Open in Maps", Modifier.weight(1f), height = 48.dp) { openMaps(ctx, item, navigate = false) }
                        Box(
                            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp)).background(C.Ink).clickable { openMaps(ctx, item, navigate = true) },
                            contentAlignment = Alignment.Center,
                        ) { Text("Navigate", style = T.sans(14, 600, color = Color.White)) }
                    }
                    if (item.lat != null) Text("%.5f, %.5f".format(item.lat, item.lng), style = T.sans(12, 400, color = C.Muted))
                }
            }

            // body / checklist
            if (item.type != ItemType.TODO || item.body.isNotBlank()) {
                if (item.checklist) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val lines = item.body.lines()
                        lines.forEachIndexed { idx, line ->
                            if (line.isBlank()) return@forEachIndexed
                            val done = line.startsWith(DONE_MARK)
                            Row(
                                Modifier.fillMaxWidth().height(44.dp).clickable {
                                    val newLines = lines.toMutableList()
                                    newLines[idx] = if (done) line.removePrefix(DONE_MARK) else DONE_MARK + line
                                    draft = item.copy(body = newLines.joinToString("\n"))
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (done) C.Ink else Color.White)
                                        .border(1.5.dp, if (done) C.Ink else C.Faint, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center,
                                ) { if (done) Icon(Ic.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                                Text(line.removePrefix(DONE_MARK), style = T.sans(15, 500, color = if (done) C.Muted else C.Ink).copy(textDecoration = if (done) TextDecoration.LineThrough else null))
                            }
                        }
                    }
                    Kicker("Edit list (one item per line)")
                }
                BasicTextField(
                    value = item.body,
                    onValueChange = { draft = item.copy(body = it) },
                    textStyle = T.sans(16, 400).copy(lineHeight = 24.sp),
                    cursorBrush = SolidColor(C.Ink),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                        .border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(14.dp),
                    decorationBox = { inner -> Box { if (item.body.isEmpty()) Text("Add details…", style = T.sans(16, 400, color = C.Faint)); inner() } },
                )
            }

            // sections
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kicker(if (item.type == ItemType.TODO) "Labels" else "Sections")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    secs.forEach { s ->
                        val on = s.name in item.sectionList
                        Chip(s.name, on, height = 34.dp, dot = if (on) null else secs.colorOf(s.name)) {
                            val list = item.sectionList.toMutableSet().apply { if (on) remove(s.name) else add(s.name) }
                            draft = item.copy(sections = Item.encodeSections(list))
                        }
                    }
                    DashedChip("+ New", height = 34.dp) { addSec = true }
                }
            }

            Column {
                Divider()
                Box {
                    ValueRow(Ic.Bell, "Reminder", item.remindAt?.let { friendlyWhen(it) } ?: "Off") { remindMenu = true }
                    DropdownMenu(expanded = remindMenu, onDismissRequest = { remindMenu = false }, containerColor = Color.White) {
                        DropdownMenuItem(text = { Text("Off", style = T.sans(14, 500)) }, onClick = { draft = item.copy(remindAt = null); remindMenu = false })
                        reminderPresets().forEach { o ->
                            DropdownMenuItem(text = { Text("${o.label} · ${o.sub}", style = T.sans(14, 500)) },
                                onClick = { draft = item.copy(remindAt = o.at, done = false); remindMenu = false })
                        }
                        DropdownMenuItem(text = { Text("Pick date & time…", style = T.sans(14, 600)) }, onClick = {
                            remindMenu = false
                            pickDateTime(ctx, item.remindAt) { t -> draft = (draft ?: item).copy(remindAt = t, done = false) }
                        })
                    }
                }
                Divider()
                if (item.type == ItemType.TODO) {
                    ValueRow(Ic.Repeat, "Repeat", Repeat.label(item.repeat)) {
                        draft = item.copy(repeat = Repeat.all[(Repeat.all.indexOf(item.repeat) + 1) % Repeat.all.size])
                    }
                    Divider()
                    ValueRow(null, "Priority", listOf("Low", "Normal", "High")[item.priority.coerceIn(0, 2)]) {
                        draft = item.copy(priority = (item.priority + 1) % 3)
                    }
                    Divider()
                }
                if (item.type == ItemType.NOTE) {
                    ValueRow(Ic.Checklist, "Checklist", if (item.checklist) "On" else "Off") { draft = item.copy(checklist = !item.checklist) }
                    Divider()
                }
                if (item.type == ItemType.PLACE) {
                    ValueRow(Ic.Tag, "Area", item.area.ifBlank { "Add" }) { editArea = true }
                    Divider()
                    val st = listOf("", "Want to try", "Been there", "Favourite")
                    ValueRow(Ic.Tag, "Status", item.status.ifBlank { "None" }) { draft = item.copy(status = st[(st.indexOf(item.status) + 1) % st.size]) }
                    Divider()
                }
            }

            if (item.type != ItemType.TODO) {
                OutlineButton("Make it a to-do", Modifier.fillMaxWidth(), height = 48.dp) {
                    val verb = when (item.type) { ItemType.PLACE -> "Visit "; ItemType.LINK -> "Read "; else -> "" }
                    r.scope.launch {
                        r.saveItem(Item(type = ItemType.TODO, title = verb + item.title.ifBlank { item.domain }, sections = item.sections, remindAt = item.remindAt))
                    }
                    nav.pop()
                }
            }
        }
    }

    if (addSec) NameDialog("New section", "e.g. Books", onDismiss = { addSec = false }) { n ->
        r.scope.launch { r.addSection(n) }
        draft?.let { d -> draft = d.copy(sections = Item.encodeSections(d.sectionList + n)) }
        addSec = false
    }
    if (editArea) {
        val d = draft
        NameDialog("Area", "e.g. Indiranagar", initial = d?.area.orEmpty(), onDismiss = { editArea = false }) { a ->
            draft = d?.copy(area = a); editArea = false
        }
    }
}

@Composable
private fun UrlField(value: String, label: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Kicker(label)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = false,
            maxLines = 4,
            textStyle = T.sans(14, 400, color = C.Ink),
            cursorBrush = SolidColor(C.Ink),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                .border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(14.dp),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text("https://…", style = T.sans(14, 400, color = C.Faint)); inner() } },
        )
    }
}
