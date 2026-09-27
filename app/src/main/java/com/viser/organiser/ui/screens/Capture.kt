package com.viser.organiser.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.viser.organiser.Nav
import com.viser.organiser.Screen
import com.viser.organiser.data.Categories
import com.viser.organiser.data.Item
import com.viser.organiser.data.ItemType
import com.viser.organiser.data.Repeat
import com.viser.organiser.data.Txn
import com.viser.organiser.data.TxnKind
import com.viser.organiser.data.TxnStatus
import com.viser.organiser.reminders.TimeParser
import com.viser.organiser.ui.Chip
import com.viser.organiser.ui.CircleIconButton
import com.viser.organiser.ui.DarkField
import com.viser.organiser.ui.DashedChip
import com.viser.organiser.ui.Divider
import com.viser.organiser.ui.Ic
import com.viser.organiser.ui.Kicker
import com.viser.organiser.ui.PrimaryButton
import com.viser.organiser.ui.SheetHandle
import com.viser.organiser.ui.Tab
import com.viser.organiser.ui.ValueRow
import com.viser.organiser.ui.colorOf
import com.viser.organiser.ui.repo
import com.viser.organiser.ui.sections
import com.viser.organiser.ui.theme.C
import com.viser.organiser.ui.theme.T
import com.viser.organiser.util.Shared
import com.viser.organiser.util.dateShort
import com.viser.organiser.util.domainOf
import com.viser.organiser.util.findUrl
import com.viser.organiser.util.friendlyWhen
import com.viser.organiser.util.isMapsUrl
import com.viser.organiser.util.parseLatLng
import com.viser.organiser.util.parseRupees
import com.viser.organiser.util.placeNameFromUrl
import com.viser.organiser.util.rupees
import com.viser.organiser.util.startMillis
import com.viser.organiser.util.toMillis
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.Calendar

// ------------------------------------------------------------------ reminder presets

data class RemindOption(val key: String, val label: String, val sub: String, val at: Long?)

fun reminderPresets(now: LocalDateTime = LocalDateTime.now()): List<RemindOption> {
    val today = now.toLocalDate()
    val hour = now.plusHours(1).withSecond(0).withNano(0).let { it.withMinute((it.minute / 5) * 5) }
    val eveToday = today.atTime(19, 0)
    val eve = if (now.isBefore(eveToday.minusMinutes(30))) eveToday else eveToday.plusDays(1)
    val tmrw = today.plusDays(1).atTime(9, 0)
    var sat = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).atTime(10, 0)
    if (!sat.isAfter(now)) sat = sat.plusWeeks(1)
    return listOf(
        RemindOption("hour", "In 1 hour", friendlyWhen(hour.toMillis()), hour.toMillis()),
        RemindOption("eve", if (eve.toLocalDate() == today) "This evening" else "Tomorrow evening", friendlyWhen(eve.toMillis()), eve.toMillis()),
        RemindOption("tmrw", "Tomorrow 9 am", friendlyWhen(tmrw.toMillis()), tmrw.toMillis()),
        RemindOption("wknd", "Weekend", friendlyWhen(sat.toMillis()), sat.toMillis()),
    )
}

/** Android date then time picker; returns epoch millis. */
fun pickDateTime(ctx: Context, initial: Long? = null, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { if (initial != null) timeInMillis = initial }
    DatePickerDialog(ctx, { _, y, m, d ->
        TimePickerDialog(ctx, { _, h, min ->
            onPicked(LocalDateTime.of(y, m + 1, d, h, min).toMillis())
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
        datePicker.minDate = System.currentTimeMillis() - 1000
    }.show()
}

// ------------------------------------------------------------------ screen

private val TYPES = listOf("todo" to "To-do", "expense" to "Expense", "link" to "Link", "note" to "Note", "place" to "Place")

@Composable
fun CaptureScreen(nav: Nav, initialType: String?, shared: Shared?) {
    var type by rememberSaveable { mutableStateOf(initialType) }

    Column(Modifier.fillMaxSize().background(C.Nav).statusBarsPadding().imePadding()) {
        val t = type
        if (t == null) {
            Chooser(onClose = { nav.pop() }) { type = it }
        } else {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp).fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircleIconButton(Ic.ChevronLeft, "Back to choices", dark = true) { if (initialType == null) type = null else nav.pop() }
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(C.DarkSeg).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TYPES.forEach { (k, l) ->
                        val on = k == t
                        Box(
                            Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp))
                                .background(if (on) Color.White else Color.Transparent)
                                .clickable(role = Role.Tab) { type = k },
                            contentAlignment = Alignment.Center,
                        ) { Text(l, style = T.sans(12, 700, color = if (on) C.Ink else C.OnDarkMuted), maxLines = 1) }
                    }
                }
            }
            val sh = shared?.takeIf { it.kind == t }
            when (t) {
                "todo" -> TodoForm(nav, sh)
                "expense" -> ExpenseForm(nav)
                "link" -> LinkForm(nav, sh)
                "note" -> NoteForm(nav, sh ?: shared?.takeIf { it.kind != "link" && it.kind != "place" })
                "place" -> PlaceForm(nav, sh)
            }
        }
    }
}

@Composable
private fun Chooser(onClose: () -> Unit, onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(Modifier.height(44.dp)) { CircleIconButton(Ic.Close, "Close", dark = true, onClick = onClose) }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("New entry", color = C.OnDarkFaint)
            Text("What do you want to save?", style = T.serif(38, 500, color = Color.White).copy(lineHeight = 42.sp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceRow(Ic.CheckCircle, null, "To-do", "A task, with a reminder") { onPick("todo") }
            ChoiceRow(null, "₹", "Expense", "Cash or anything the SMS missed") { onPick("expense") }
            ChoiceRow(Ic.Link, null, "Link", "A web page to read or buy from later") { onPick("link") }
            ChoiceRow(Ic.Note, null, "Note", "A size, a hint, an idea, a list") { onPick("note") }
            ChoiceRow(Ic.Place, null, "Place", "A Google Maps spot to visit") { onPick("place") }
        }
    }
}

@Composable
private fun ChoiceRow(icon: ImageVector?, glyph: String?, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(20.dp)).background(C.DarkCard)
            .border(1.dp, C.DarkLine, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, tint = C.Ink, modifier = Modifier.size(22.dp))
            else Text(glyph.orEmpty(), style = T.serif(24, 600))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = T.sans(17, 700, color = Color.White))
            Text(sub, style = T.sans(13, 400, color = C.OnDarkMuted))
        }
        Icon(Ic.ChevronRight, null, tint = C.Faint, modifier = Modifier.size(18.dp))
    }
}

/** The light bottom sheet under the dark input area. */
@Composable
private fun ColumnScope.Sheet(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(C.Ground)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetHandle() }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionPicker(selected: Set<String>, onToggle: (String) -> Unit) {
    val r = repo()
    val scope = rememberCoroutineScope()
    val secs by sections()
    var adding by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Kicker("Save to section")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            secs.forEach { s -> Chip(s.name, s.name in selected, dot = if (s.name in selected) null else secs.colorOf(s.name)) { onToggle(s.name) } }
            DashedChip("+ New section") { adding = true }
        }
        Text("Pick more than one if it fits both", style = T.sans(12, 400, color = C.Muted))
    }
    if (adding) NameDialog("New section", "e.g. Books, Gifts", onDismiss = { adding = false }) { n ->
        r.scope.launch { r.addSection(n) }; onToggle(n); adding = false
    }
}

/** Compact reminder row for links, notes and places. */
@Composable
private fun ReminderRow(label: String, at: Long?, onChange: (Long?) -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        ValueRow(Ic.Bell, label, at?.let { friendlyWhen(it) } ?: "Off") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Color.White) {
            DropdownMenuItem(text = { Text("Off", style = T.sans(14, 500)) }, onClick = { onChange(null); open = false })
            reminderPresets().forEach { o ->
                DropdownMenuItem(text = { Text("${o.label} · ${o.sub}", style = T.sans(14, 500)) }, onClick = { onChange(o.at); open = false })
            }
            DropdownMenuItem(text = { Text("Pick date & time…", style = T.sans(14, 600)) }, onClick = {
                open = false; pickDateTime(ctx, at) { onChange(it) }
            })
        }
    }
}

// ------------------------------------------------------------------ to-do

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TodoForm(nav: Nav, shared: Shared?) {
    val r = repo()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val secs by sections()
    var text by rememberSaveable { mutableStateOf(shared?.let { listOf(it.title, it.body).filter { s -> s.isNotBlank() }.joinToString(" ") }.orEmpty()) }
    var labels by remember { mutableStateOf(setOf<String>()) }
    var priority by rememberSaveable { mutableStateOf(1) }
    var repeat by rememberSaveable { mutableStateOf(Repeat.NONE) }
    var pick by rememberSaveable { mutableStateOf("auto") }
    var custom by rememberSaveable { mutableStateOf<Long?>(null) }
    var labelMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val parsed = remember(text) { TimeParser.parse(text) }
    val presets = remember { reminderPresets() }
    val key = if (pick == "auto") (if (parsed != null) "parsed" else "none") else pick
    val at: Long? = when (key) {
        "parsed" -> parsed?.at?.toMillis()
        "custom" -> custom
        "none" -> null
        else -> presets.firstOrNull { it.key == key }?.at
    }

    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Kicker("To-do", color = C.OnDarkFaint)
        DarkField(text, { text = it }, "Call bank tomorrow 11am", Modifier.focusRequester(focus),
            style = T.serif(30, 500, color = Color.White).copy(lineHeight = 36.sp), boxed = false, singleLine = false)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEach { l ->
                Row(
                    Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { labels = labels - l }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(secs.colorOf(l)))
                    Text(l, style = T.sans(12, 600))
                }
            }
            Box {
                DashedChip("+ Label", dark = true, height = 32.dp) { labelMenu = true }
                DropdownMenu(expanded = labelMenu, onDismissRequest = { labelMenu = false }, containerColor = Color.White) {
                    secs.forEach { s ->
                        DropdownMenuItem(text = { Text((if (s.name in labels) "✓ " else "") + s.name, style = T.sans(14, 500)) },
                            onClick = { labels = if (s.name in labels) labels - s.name else labels + s.name; labelMenu = false })
                    }
                }
            }
            DashedChip("Priority: " + listOf("Low", "Normal", "High")[priority], dark = true, height = 32.dp) { priority = (priority + 1) % 3 }
            DashedChip("Repeat: " + Repeat.label(repeat), dark = true, height = 32.dp) {
                repeat = Repeat.all[(Repeat.all.indexOf(repeat) + 1) % Repeat.all.size]
            }
        }
    }
    run {
        Sheet {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("When should I remind you?", style = T.serif(24, 600))
                Text(if (parsed != null) "Found “${parsed.phrase}” in your text" else "Pick one, or type a time like “tomorrow 11am”", style = T.sans(13, 400, color = C.Muted))
            }
            val opts = buildList {
                if (parsed != null) add(RemindOption("parsed", friendlyWhen(parsed.at.toMillis()), "From your text", parsed.at.toMillis()))
                addAll(presets)
                add(RemindOption("custom", if (custom != null) friendlyWhen(custom!!) else "Pick date & time", if (custom != null) "Custom" else "Calendar", custom))
                add(RemindOption("none", "No reminder", "Just save it", null))
            }
            opts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { o ->
                        val on = o.key == key
                        Column(
                            Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (on) C.Ink else Color.White)
                                .border(1.dp, if (on) C.Ink else C.Line, RoundedCornerShape(14.dp))
                                .clickable(role = Role.RadioButton) {
                                    if (o.key == "custom") pickDateTime(ctx, custom) { custom = it; pick = "custom" } else pick = o.key
                                }
                                .padding(horizontal = 14.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(o.label, style = T.sans(14, 600, color = if (on) Color.White else C.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(o.sub, style = T.sans(12, 500, color = (if (on) Color.White else C.Ink).copy(alpha = 0.75f)), maxLines = 1)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            val cta = when {
                at == null -> "Save to-do"
                else -> "Save · remind ${friendlyWhen(at).replaceFirstChar { it.lowercase() }}"
            }
            PrimaryButton(cta, enabled = text.isNotBlank()) {
                r.scope.launch {
                    r.saveItem(Item(type = ItemType.TODO, title = text.trim(), sections = Item.encodeSections(labels), priority = priority,
                        remindAt = at, repeat = if (at != null) repeat else Repeat.NONE))
                }
                nav.tab(Tab.TODOS)
            }
        }
    }
}

// ------------------------------------------------------------------ expense

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ExpenseForm(nav: Nav) {
    val r = repo()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var flow by rememberSaveable { mutableStateOf(TxnKind.EXPENSE) }
    var amount by rememberSaveable { mutableStateOf("") }
    var what by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("Dining out") }
    var mode by rememberSaveable { mutableStateOf("UPI") }
    var date by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Kicker("Amount", color = C.OnDarkFaint, modifier = Modifier.weight(1f))
            Row(Modifier.clip(RoundedCornerShape(16.dp)).background(C.DarkSeg).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(TxnKind.EXPENSE to "Expense", TxnKind.INCOME to "Income").forEach { (k, l) ->
                    val on = flow == k
                    Box(
                        Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(if (on) Color.White else Color.Transparent)
                            .clickable(role = Role.RadioButton) { flow = k; cat = if (k == TxnKind.INCOME) "Salary" else "Dining out" }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(l, style = T.sans(12, 700, color = if (on) C.Ink else C.OnDarkMuted)) }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("₹", style = T.serif(36, 500, color = C.OnDarkFaint), modifier = Modifier.padding(bottom = 8.dp))
            DarkField(amount, { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' } }, "0",
                Modifier.focusRequester(focus), style = T.serif(56, 600, (-0.02).em, Color.White), boxed = false,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        DarkField(what, { what = it }, if (flow == TxnKind.INCOME) "From? e.g. Salary, client" else "What was it for?")
    }
    run {
        Sheet {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Kicker("Category")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (if (flow == TxnKind.INCOME) Categories.income else Categories.expense).forEach { c -> Chip(c, c == cat) { cat = c } }
                }
            }
            if (flow == TxnKind.EXPENSE) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kicker("Paid with")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("UPI", "Card", "Cash").forEach { m ->
                            val on = m == mode
                            Box(
                                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) C.Ink else Color.White)
                                    .border(1.dp, if (on) C.Ink else C.Line, RoundedCornerShape(12.dp)).clickable(role = Role.RadioButton) { mode = m },
                                contentAlignment = Alignment.Center,
                            ) { Text(m, style = T.sans(14, 600, color = if (on) Color.White else C.Ink)) }
                        }
                    }
                }
            }
            Column {
                Divider()
                val isToday = date >= LocalDate.now().startMillis()
                ValueRow(null, "Date", if (isToday) "Today, ${dateShort(date)}" else dateShort(date)) {
                    val c = Calendar.getInstance().apply { timeInMillis = date }
                    DatePickerDialog(ctx, { _, y, m, d ->
                        date = LocalDate.of(y, m + 1, d).atTime(LocalTime.now()).toMillis()
                    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
                        datePicker.maxDate = System.currentTimeMillis()
                    }.show()
                }
                Divider()
            }
            Spacer(Modifier.height(4.dp))
            val paise = parseRupees(amount)
            PrimaryButton(
                (if (flow == TxnKind.INCOME) "Save income" else "Save expense") + (paise?.let { " · " + rupees(it) } ?: ""),
                enabled = paise != null && paise > 0,
            ) {
                val p = paise ?: return@PrimaryButton
                r.scope.launch {
                    r.addManualTxn(Txn(kind = flow, amount = p, category = cat, merchant = what.trim(), mode = if (flow == TxnKind.INCOME) "Bank" else mode,
                        source = "manual", occurredAt = date, status = TxnStatus.CONFIRMED))
                }
                nav.tab(Tab.MONEY)
            }
        }
    }
}

// ------------------------------------------------------------------ link

@Composable
private fun ColumnScope.LinkForm(nav: Nav, shared: Shared?) {
    val r = repo()
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf(shared?.url.orEmpty()) }
    var title by rememberSaveable { mutableStateOf(shared?.title.orEmpty()) }
    var details by rememberSaveable { mutableStateOf(shared?.body.orEmpty()) }
    var secs by remember { mutableStateOf(setOf("Links")) }
    var remind by rememberSaveable { mutableStateOf<Long?>(null) }
    val clean = findUrl(url) ?: url.trim()
    val domain = domainOf(clean)

    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Kicker("Paste a link", color = C.OnDarkFaint)
        DarkField(url, { url = it }, "https://…", minHeight = 52.dp, keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
        if (domain.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (isMapsUrl(clean)) Ic.Place else Ic.Link, null, tint = C.OnDarkMuted, modifier = Modifier.size(14.dp))
                Text((if (isMapsUrl(clean)) "Maps link · switch to Place for a place card" else "Web link · $domain"), style = T.sans(13, 400, color = C.OnDarkMuted))
            }
        }
        DarkField(title, { title = it }, "Add a title (optional)")
        DarkField(details, { details = it }, "Details — why you saved it, price, what to check…", singleLine = false, minHeight = 44.dp,
            style = T.sans(15, 400, color = Color.White).copy(lineHeight = 21.sp))
    }
    run {
        Sheet {
            SectionPicker(secs) { s -> secs = if (s in secs) secs - s else secs + s }
            Column { Divider(); ReminderRow("Remind me to open it", remind) { remind = it }; Divider() }
            Spacer(Modifier.height(4.dp))
            PrimaryButton("Save link", enabled = clean.isNotBlank()) {
                r.scope.launch {
                    r.saveItem(Item(type = ItemType.LINK, title = title.trim(), body = details.trim(), url = clean, domain = domain,
                        sections = Item.encodeSections(secs), remindAt = remind))
                }
                nav.tab(Tab.SAVED)
            }
        }
    }
}

// ------------------------------------------------------------------ note

@Composable
private fun ColumnScope.NoteForm(nav: Nav, shared: Shared?) {
    val r = repo()
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf(shared?.title.orEmpty()) }
    var body by rememberSaveable { mutableStateOf(shared?.body.orEmpty()) }
    var secs by remember { mutableStateOf(setOf<String>()) }
    var checklist by rememberSaveable { mutableStateOf(false) }
    var pinned by rememberSaveable { mutableStateOf(false) }
    var remind by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Kicker("Note", color = C.OnDarkFaint)
        DarkField(title, { title = it }, "Title (optional)", style = T.serif(30, 500, color = Color.White), boxed = false)
        DarkField(body, { body = it }, if (checklist) "One item per line" else "Write anything",
            style = T.sans(16, 400, color = Color(0xFFE8E6E1)).copy(lineHeight = 24.sp), boxed = false, singleLine = false)
    }
    run {
        Sheet {
            SectionPicker(secs) { s -> secs = if (s in secs) secs - s else secs + s }
            Column {
                Divider()
                ValueRow(Ic.Checklist, "Make it a checklist", if (checklist) "On" else "Off") { checklist = !checklist }
                Divider()
                ValueRow(Ic.Pin, "Pin to top", if (pinned) "On" else "Off") { pinned = !pinned }
                Divider()
                ReminderRow("Add a reminder", remind) { remind = it }
                Divider()
            }
            Spacer(Modifier.height(4.dp))
            PrimaryButton("Save note", enabled = title.isNotBlank() || body.isNotBlank()) {
                r.scope.launch {
                    r.saveItem(Item(type = ItemType.NOTE, title = title.trim(), body = body.trim(), sections = Item.encodeSections(secs),
                        checklist = checklist, pinned = pinned, remindAt = remind))
                }
                nav.tab(Tab.SAVED)
            }
        }
    }
}

// ------------------------------------------------------------------ place

private val STATUSES = listOf("", "Want to try", "Been there", "Favourite")

@Composable
private fun ColumnScope.PlaceForm(nav: Nav, shared: Shared?) {
    val r = repo()
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf(shared?.url.orEmpty()) }
    var name by rememberSaveable { mutableStateOf(shared?.title.orEmpty()) }
    var area by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("Want to try") }
    var secs by remember { mutableStateOf(setOf("Food", "Places")) }
    var remind by rememberSaveable { mutableStateOf<Long?>(null) }
    var editArea by remember { mutableStateOf(false) }
    val clean = findUrl(url) ?: url.trim()
    val ll = remember(clean) { parseLatLng(clean) }
    LaunchedEffect(clean) { if (name.isBlank()) placeNameFromUrl(clean)?.let { name = it } }

    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Kicker("Google Maps link", color = C.OnDarkFaint)
        DarkField(url, { url = it }, "Share a place from Maps, or paste its link", minHeight = 48.dp, keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
        if (clean.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Ic.Place, null, tint = C.OnDarkMuted, modifier = Modifier.size(14.dp))
                Text(
                    if (ll != null) "Place found · %.4f, %.4f".format(ll.first, ll.second) else "Saved as a Maps link · opens in Google Maps",
                    style = T.sans(13, 400, color = C.OnDarkMuted),
                )
            }
        }
        DarkField(name, { name = it }, "Place name")
    }
    run {
        Sheet {
            SectionPicker(secs) { s -> secs = if (s in secs) secs - s else secs + s }
            Column {
                Divider()
                ValueRow(Ic.Tag, "Area", area.ifBlank { "Add" }) { editArea = true }
                Divider()
                ValueRow(Ic.Tag, "Status", status.ifBlank { "None" }) { status = STATUSES[(STATUSES.indexOf(status) + 1) % STATUSES.size] }
                Divider()
                ReminderRow("Remind me to go", remind) { remind = it }
                Divider()
            }
            Spacer(Modifier.height(4.dp))
            PrimaryButton("Save place", enabled = name.isNotBlank() || clean.isNotBlank()) {
                r.scope.launch {
                    r.saveItem(Item(type = ItemType.PLACE, title = name.trim().ifBlank { "Saved place" }, url = clean, domain = domainOf(clean),
                        lat = ll?.first, lng = ll?.second, area = area.trim(), status = status, body = shared?.body.orEmpty(),
                        sections = Item.encodeSections(secs), remindAt = remind))
                }
                nav.tab(Tab.SAVED)
            }
        }
    }
    if (editArea) NameDialog("Area", "e.g. Indiranagar", initial = area, onDismiss = { editArea = false }) { area = it; editArea = false }
}

