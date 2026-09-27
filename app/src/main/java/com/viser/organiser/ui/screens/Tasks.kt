package com.viser.organiser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.viser.organiser.Nav
import com.viser.organiser.Screen
import com.viser.organiser.data.Item
import com.viser.organiser.data.Repeat
import com.viser.organiser.ui.BottomNav
import com.viser.organiser.ui.BottomPanel
import com.viser.organiser.ui.CircleIconButton
import com.viser.organiser.ui.EmptyState
import com.viser.organiser.ui.Ic
import com.viser.organiser.ui.Kicker
import com.viser.organiser.ui.SegTab
import com.viser.organiser.ui.Tab
import com.viser.organiser.ui.repo
import com.viser.organiser.ui.sections
import com.viser.organiser.ui.state
import com.viser.organiser.ui.theme.C
import com.viser.organiser.ui.theme.T
import com.viser.organiser.util.dateShort
import com.viser.organiser.util.friendlyWhen
import com.viser.organiser.util.startMillis
import com.viser.organiser.util.timeHm
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TView(val label: String) { TODAY("TODAY"), NEXT("NEXT"), LATE("LATE"), DONE("DONE") }

private fun viewOf(i: Item, startToday: Long, endToday: Long): TView {
    val at = i.remindAt
    return when {
        i.done -> TView.DONE
        at == null -> TView.NEXT
        at < startToday -> TView.LATE
        at < endToday -> TView.TODAY
        else -> TView.NEXT
    }
}

@Composable
fun TasksScreen(nav: Nav) {
    val r = repo()
    val scope = rememberCoroutineScope()
    val todos by remember { r.db.items().todos() }.state(emptyList())
    val secs by sections()
    var view by rememberSaveable { mutableStateOf(TView.TODAY) }
    var label by rememberSaveable { mutableStateOf<String?>(null) }
    var highOnly by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val s0 = today.startMillis(); val s1 = today.plusDays(1).startMillis()
    val filtered = todos.filter { (label == null || it.sectionList.contains(label)) && (!highOnly || it.priority == 2) }
    val byView = filtered.groupBy { viewOf(it, s0, s1) }
    val rows = (byView[view] ?: emptyList()).let { list ->
        if (view == TView.DONE) list.sortedByDescending { it.doneAt ?: it.updatedAt } else list
    }
    val nowMs = System.currentTimeMillis()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("To-dos", style = T.serif(32, 600, (-0.01).em), modifier = Modifier.weight(1f))
                Box {
                    CircleIconButton(Ic.Sliders, "Filter by label or priority") { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Color.White) {
                        DropdownMenuItem(text = { Text(if (highOnly) "✓ High priority only" else "High priority only", style = T.sans(14, 600)) }, onClick = { highOnly = !highOnly; menu = false })
                        DropdownMenuItem(text = { Text(if (label == null) "✓ All labels" else "All labels", style = T.sans(14, 600)) }, onClick = { label = null; menu = false })
                        secs.forEach { s ->
                            DropdownMenuItem(text = { Text((if (label == s.name) "✓ " else "") + s.name, style = T.sans(14, 500)) }, onClick = { label = s.name; menu = false })
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val kicker = when (view) {
                    TView.TODAY -> today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH))
                    TView.NEXT -> "Coming up"
                    TView.LATE -> "Past due"
                    TView.DONE -> "Completed"
                }
                Kicker(kicker + (label?.let { " · $it" } ?: "") + (if (highOnly) " · high" else ""))
                val n = rows.size
                val word = if (view == TView.DONE) "done" else if (n == 1) "task left" else "tasks left"
                Text(
                    buildAnnotatedString {
                        append(n.toString())
                        withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight(500), color = C.Muted)) { append(" $word") }
                    },
                    style = T.serif(44, 600, (-0.02).em).copy(lineHeight = 44.sp),
                )
            }

            if (rows.isEmpty()) {
                EmptyState(if (view == TView.DONE) "Nothing done yet" else "All clear", if (view == TView.DONE) "Finished to-dos show up here." else "Tap + to add a to-do.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(rows, key = { it.id }) { t ->
                        TodoCard(t, late = !t.done && (t.remindAt ?: Long.MAX_VALUE) < nowMs,
                            onToggle = { r.scope.launch { r.setDone(t.id, !t.done) } },
                            onOpen = { nav.push(Screen.ItemDetail(t.id)) })
                    }
                }
            }
        }
        BottomPanel {
            TView.values().forEach { v ->
                val c = byView[v]?.size ?: 0
                SegTab("${v.label} $c", view == v, Modifier.weight(1f)) { view = v }
            }
        }
        BottomNav(Tab.TODOS, onTab = nav::tab, onCapture = { nav.push(Screen.Capture(type = "todo")) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodoCard(t: Item, late: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, C.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (t.done) C.Ink else Color.Transparent)
                .border(1.5.dp, if (t.done) C.Ink else if (late) C.Late else C.Faint, CircleShape)
                .clickable(role = Role.Checkbox, onClickLabel = if (t.done) "Mark not done" else "Mark done", onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (t.done) Icon(Ic.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                t.title,
                style = T.sans(15, 600, color = if (t.done) C.Muted else C.Ink).copy(
                    textDecoration = if (t.done) TextDecoration.LineThrough else null, lineHeight = 20.sp,
                ),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val at = t.remindAt
                val whenText = when {
                    t.done -> "Done " + dateShort(t.doneAt ?: t.updatedAt)
                    at == null -> "No reminder"
                    late && at >= LocalDate.now().startMillis() -> timeHm(at) + " · late"
                    else -> friendlyWhen(at)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Ic.Bell, null, tint = if (late) C.Late else C.Muted, modifier = Modifier.size(14.dp))
                    Text(whenText, style = T.sans(12, 600, color = if (late) C.Late else C.Muted))
                }
                if (t.repeat.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Ic.Repeat, null, tint = C.Muted, modifier = Modifier.size(14.dp))
                        Text(Repeat.label(t.repeat), style = T.sans(12, 500, color = C.Muted))
                    }
                }
                if (t.sectionList.isNotEmpty()) Text(t.sectionList.joinToString(" · "), style = T.sans(12, 500, color = C.Muted))
            }
        }
        if (t.priority == 2 && !t.done) {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Ink).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("HIGH", style = T.sans(10, 700, 0.1.em, Color.White))
            }
        }
    }
}
