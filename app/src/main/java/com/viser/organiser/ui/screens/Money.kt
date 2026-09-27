package com.viser.organiser.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import com.viser.organiser.Nav
import com.viser.organiser.Screen
import com.viser.organiser.data.Categories
import com.viser.organiser.data.Goal
import com.viser.organiser.data.Txn
import com.viser.organiser.data.TxnKind
import com.viser.organiser.data.TxnStatus
import com.viser.organiser.ui.BottomNav
import com.viser.organiser.ui.BottomPanel
import com.viser.organiser.ui.Chip
import com.viser.organiser.ui.EmptyState
import com.viser.organiser.ui.GoalStatus
import com.viser.organiser.ui.GoalView
import com.viser.organiser.ui.Ic
import com.viser.organiser.ui.Kicker
import com.viser.organiser.ui.LightField
import com.viser.organiser.ui.SegTab
import com.viser.organiser.ui.Tab
import com.viser.organiser.ui.TxnDraft
import com.viser.organiser.ui.TxnEditor
import com.viser.organiser.ui.contributions
import com.viser.organiser.ui.goalView
import com.viser.organiser.ui.goals
import com.viser.organiser.ui.monthTxns
import com.viser.organiser.ui.pendingTxns
import com.viser.organiser.ui.repo
import com.viser.organiser.ui.state
import com.viser.organiser.ui.summarize
import com.viser.organiser.ui.theme.C
import com.viser.organiser.ui.theme.T
import com.viser.organiser.util.dateLong
import com.viser.organiser.util.dateShort
import com.viser.organiser.util.monthLabel
import com.viser.organiser.util.parseRupees
import com.viser.organiser.util.rupees
import com.viser.organiser.util.startMillis
import com.viser.organiser.util.timeHm
import com.viser.organiser.util.toLdt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar

private enum class MView(val label: String) { OVERVIEW("OVERVIEW"), SPENDS("SPENDS"), INCOME("INCOME"), REPORTS("REPORTS") }

@Composable
fun MoneyScreen(nav: Nav) {
    val r = repo()
    val scope = rememberCoroutineScope()
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var view by rememberSaveable { mutableStateOf(MView.OVERVIEW) }
    var monthMenu by remember { mutableStateOf(false) }
    var editTxn by remember { mutableStateOf<Txn?>(null) }
    var newGoal by remember { mutableStateOf(false) }
    var addTo by remember { mutableStateOf<GoalView?>(null) }

    val txns by monthTxns(ym)
    val contribs by contributions()
    val goals by goals()
    val pending by pendingTxns()
    val sum = summarize(txns, contribs, ym)
    val confirmed = txns.filter { it.status == TxnStatus.CONFIRMED }
    val expenses = confirmed.filter { it.kind == TxnKind.EXPENSE }
    val incomes = confirmed.filter { it.kind == TxnKind.INCOME }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Money", style = T.serif(32, 600, (-0.01).em), modifier = Modifier.weight(1f))
                Box {
                    Row(
                        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color.White)
                            .border(1.dp, C.Line, RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button) { monthMenu = true }
                            .padding(start = 16.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(monthLabel(ym), style = T.sans(13, 600))
                        Icon(Ic.ChevronDown, null, tint = C.Ink, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }, containerColor = Color.White) {
                        (0 until 12).map { YearMonth.now().minusMonths(it.toLong()) }.forEach { m ->
                            DropdownMenuItem(text = { Text(monthLabel(m), style = T.sans(14, if (m == ym) 700 else 500)) }, onClick = { ym = m; monthMenu = false })
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Kicker(if (ym == YearMonth.now()) "Spent this month" else "Spent in ${monthLabel(ym)}")
                BigAmount(sum.spent, 40)
                Text(
                    "${rupees(sum.remaining)} left of ${rupees(sum.income)} income" + if (sum.setAside != 0L) " · ${rupees(sum.setAside)} set aside" else "",
                    style = T.sans(13, 500, color = C.Muted),
                )
            }

            if (pending.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(C.Ink)
                        .clickable(role = Role.Button) { nav.push(Screen.Review()) }
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Ic.Inbox, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("${pending.size} SMS payment${if (pending.size == 1) "" else "s"} to confirm", style = T.sans(14, 600, color = Color.White), modifier = Modifier.weight(1f))
                    Box(Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text("Review", style = T.sans(13, 700))
                    }
                }
            }

            when (view) {
                MView.OVERVIEW -> {
                    GoalsSection(goals.filter { !it.purchased }.map { goalView(it, contribs) }, onNew = { newGoal = true }, onAdd = { addTo = it })
                    SectionHeader("Recent", if (confirmed.size > 5) "See all" else null) { view = MView.SPENDS }
                    if (confirmed.isEmpty()) Text("No confirmed entries this month yet.", style = T.sans(14, 500, color = C.Muted))
                    TxnList(confirmed.take(5)) { editTxn = it }
                }
                MView.SPENDS -> {
                    CategoryTotals(expenses)
                    SectionHeader("All spends · ${expenses.size}", null) {}
                    if (expenses.isEmpty()) EmptyState("No spends", "Confirmed expenses show up here.")
                    TxnList(expenses) { editTxn = it }
                }
                MView.INCOME -> {
                    SectionHeader("Income · ${rupees(sum.income)}", null) {}
                    if (incomes.isEmpty()) EmptyState("No income yet", "Salary SMS land here, or add it with + → Expense → Income.")
                    TxnList(incomes) { editTxn = it }
                }
                MView.REPORTS -> Reports(ym, expenses)
            }
        }

        BottomPanel {
            MView.values().forEach { v -> SegTab(v.label, view == v, Modifier.weight(1f)) { view = v } }
        }
        BottomNav(Tab.MONEY, onTab = nav::tab, onCapture = { nav.push(Screen.Capture(type = "expense")) })
    }

    editTxn?.let { t ->
        TxnDialog(t, onDismiss = { editTxn = null },
            onSave = { u -> scope.launch { if (u.status == TxnStatus.CONFIRMED && u.category != t.category) r.confirm(u) else r.updateTxn(u) }; editTxn = null },
            onDelete = { scope.launch { r.deleteTxn(t) }; editTxn = null })
    }
    if (newGoal) GoalDialog(onDismiss = { newGoal = false }) { g -> scope.launch { r.saveGoal(g) }; newGoal = false }
    addTo?.let { v ->
        ContributionDialog(v, onDismiss = { addTo = null },
            onSave = { amt -> scope.launch { r.contribute(v.goal.id, amt) }; addTo = null },
            onPurchased = { scope.launch { r.saveGoal(v.goal.copy(purchased = true)) }; addTo = null },
            onDelete = { scope.launch { r.saveGoal(v.goal.copy(deletedAt = System.currentTimeMillis())) }; addTo = null })
    }
}

@Composable
private fun SectionHeader(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Kicker(title, modifier = Modifier.weight(1f))
        if (action != null) Text(action, style = T.sans(13, 600), modifier = Modifier.clickable(onClick = onAction))
    }
}

@Composable
private fun GoalsSection(views: List<GoalView>, onNew: () -> Unit, onAdd: (GoalView) -> Unit) {
    var front by remember { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Kicker("Goals · ${views.size}", modifier = Modifier.weight(1f))
            Text("+ New goal", style = T.sans(13, 600), modifier = Modifier.clickable(onClick = onNew))
        }
        if (views.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.plumBrush).clickable(onClick = onNew).padding(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Kicker("No goals yet", color = C.GoalPlumText)
                    Text("Create one: name, target and a date.", style = T.serif(20, 600, color = Color.White))
                }
            }
            return@Column
        }
        val f = front.coerceIn(0, views.size - 1)
        val order = listOf(f) + views.indices.filter { it != f }
        val backs = order.drop(1).take(2).reversed() // furthest first
        Column {
            backs.forEachIndexed { i, idx ->
                val v = views[idx]
                val palette = if (i == backs.size - 1) C.goalPalette[2] else C.goalPalette[1]
                val inset = if (backs.size == 2 && i == 0) 20.dp else 10.dp
                Row(
                    Modifier
                        .padding(horizontal = inset)
                        .offset(y = (-12 * i).dp)
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .background(palette.first)
                        .clickable { front = idx }
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(v.goal.name.uppercase(), style = T.sans(12, 600, 0.12.em, palette.second), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val tail = if (v.status == GoalStatus.ON_TRACK) "On track" else if (v.status == GoalStatus.HIT) "Target hit" else "${v.pct}%"
                    Text("${rupees(v.saved)} · $tail", style = T.sans(12, 600, color = palette.second))
                }
            }
            Box(Modifier.offset(y = (-12 * backs.size).dp).zIndex(1f)) {
                GoalCard(views[f], onAdd = { onAdd(views[f]) })
            }
        }
    }
}

@Composable
private fun TxnList(list: List<Txn>, onClick: (Txn) -> Unit) {
    Column {
        list.forEachIndexed { i, t ->
            TxnRow(t, onClick = { onClick(t) })
            if (i < list.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
        }
    }
}

private fun catColors(cat: String, kind: String): Pair<Color, Color> = when {
    kind == TxnKind.INCOME -> Color(0xFFDCEFE5) to Color(0xFF1F5C40)
    cat in listOf("Food delivery", "Dining out", "Groceries") -> Color(0xFFFBE3D6) to Color(0xFF9A3B12)
    cat in listOf("Transport", "Fuel", "Travel") -> Color(0xFFE0E7FB) to Color(0xFF1E3A8A)
    cat in listOf("Shopping", "Gifts") -> Color(0xFFEDE5FA) to Color(0xFF4B2F94)
    cat in listOf("Bills & utilities", "Rent", "Subscriptions") -> Color(0xFFF6EDCF) to Color(0xFF7A5A00)
    else -> Color(0xFFEDEBE6) to Color(0xFF45433F)
}

@Composable
fun TxnRow(t: Txn, onClick: () -> Unit) {
    val (bg, fg) = catColors(t.category, t.kind)
    Row(
        Modifier.fillMaxWidth().height(56.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
            Text(Categories.initials(t.category), style = T.sans(13, 700, color = fg))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(t.merchant.ifBlank { t.category }, style = T.sans(15, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val extra = listOfNotNull(
                t.note.takeIf { it.isNotBlank() },
                if (t.isSplit) "split with " + t.people.joinToString(", ") else null,
            ).joinToString(" · ")
            Text(
                if (extra.isNotEmpty()) "${t.category} · $extra" else "${t.category} · ${if (t.source == "sms") "SMS" else t.mode} · ${dateShort(t.occurredAt)}",
                style = T.sans(12, 400, color = C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val income = t.kind == TxnKind.INCOME
        Column(horizontalAlignment = Alignment.End) {
            Text((if (income) "+" else "−") + rupees(t.effective), style = T.serif(17, 600, color = if (income) C.Good else C.Ink))
            if (t.isSplit) Text("of ${rupees(t.amount)}", style = T.sans(11, 500, color = C.Muted))
        }
    }
}

@Composable
private fun CategoryTotals(expenses: List<Txn>) {
    val totals = expenses.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.effective } }.entries.sortedByDescending { it.value }
    val max = totals.firstOrNull()?.value ?: 1L
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("By category", null) {}
        totals.forEach { (cat, amt) -> Bar(cat, amt, max) }
    }
}

@Composable
private fun Bar(label: String, amt: Long, max: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = T.sans(13, 600), modifier = Modifier.weight(1f))
            Text(rupees(amt), style = T.sans(13, 600))
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.Chip)) {
            Box(Modifier.fillMaxWidth((amt.toFloat() / max.coerceAtLeast(1)).coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(C.Ink))
        }
    }
}

@Composable
private fun Reports(ym: YearMonth, expenses: List<Txn>) {
    val r = repo()
    val start = ym.minusMonths(5)
    val flow = remember(ym) { r.db.txns().confirmedSince(start.atDay(1).startMillis()) }
    val six by flow.state(emptyList())
    val months = (0..5).map { start.plusMonths(it.toLong()) }
    val perMonth = months.map { m ->
        m to six.filter { it.kind == TxnKind.EXPENSE && YearMonth.from(it.occurredAt.toLdt()) == m }.sumOf { it.effective }
    }
    val maxM = perMonth.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader("Month on month", null) {}
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            perMonth.forEach { (m, v) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (v > 0) shortK(v) else "", style = T.sans(10, 600, color = C.Muted))
                    Box(
                        Modifier.fillMaxWidth().height((100f * v / maxM).coerceAtLeast(2f).dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (m == ym) C.Ink else C.Sheet),
                    )
                    Text(monthLabel(m).take(3), style = T.sans(11, if (m == ym) 700 else 500, color = if (m == ym) C.Ink else C.Muted))
                }
            }
        }
        CategoryTotals(expenses)
        SectionHeader("Top merchants", null) {}
        val top = expenses.filter { it.merchant.isNotBlank() }.groupBy { it.merchant }
            .mapValues { e -> e.value.sumOf { it.effective } to e.value.size }.entries.sortedByDescending { it.value.first }.take(6)
        if (top.isEmpty()) Text("Nothing yet.", style = T.sans(14, 500, color = C.Muted))
        top.forEach { (m, p) ->
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m, style = T.sans(14, 600), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${p.second}× · ", style = T.sans(13, 500, color = C.Muted))
                Text(rupees(p.first), style = T.serif(16, 600))
            }
        }
    }
}

private fun shortK(paise: Long): String {
    val r = paise / 100
    return when {
        r >= 100000 -> "%.1fL".format(r / 100000.0)
        r >= 1000 -> "%.1fk".format(r / 1000.0)
        else -> r.toString()
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
private fun TxnDialog(t: Txn, onDismiss: () -> Unit, onSave: (Txn) -> Unit, onDelete: () -> Unit) {
    val r = repo()
    var amount by remember { mutableStateOf((t.amount / 100.0).let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }) }
    var merchant by remember { mutableStateOf(t.merchant) }
    var draft by remember { mutableStateOf(TxnDraft.from(t)) }
    val splits by remember { r.db.txns().recentSplits() }.state(emptyList())
    val knownPeople = remember(splits) { splits.flatMap { it.split('|') }.filter { it.isNotBlank() }.distinct() }
    val paise = parseRupees(amount) ?: t.amount
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Ground,
        title = { Text("Edit entry", style = T.serif(22, 600)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LightField(amount, { amount = it }, "Amount", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                LightField(merchant, { merchant = it }, "Merchant / from")
                Text("${dateLong(t.occurredAt)} · ${timeHm(t.occurredAt)} · ${if (t.source == "sms") "${t.bank} SMS" else t.mode}", style = T.sans(12, 500, color = C.Muted))
                TxnEditor(paise, draft, knownPeople, compactCategories = false) { draft = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(draft.apply(t.copy(amount = paise, merchant = merchant.trim())))
            }) { Text("Save", style = T.sans(14, 700)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", style = T.sans(14, 600, color = C.Late)) }
                TextButton(onClick = onDismiss) { Text("Cancel", style = T.sans(14, 600, color = C.Muted)) }
            }
        },
    )
}

@Composable
private fun GoalDialog(onDismiss: () -> Unit, onSave: (Goal) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Ground,
        title = { Text("New goal", style = T.serif(22, 600)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LightField(name, { name = it }, "What for? e.g. Camera")
                LightField(target, { target = it }, "Target amount (₹)", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)
                        .border(1.dp, C.Line, RoundedCornerShape(24.dp))
                        .clickable {
                            val c = Calendar.getInstance()
                            DatePickerDialog(ctx, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
                                .apply { datePicker.minDate = System.currentTimeMillis() }.show()
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Ic.Calendar, null, tint = C.Muted, modifier = Modifier.size(18.dp))
                    Text(date?.let { "By " + dateLong(it.startMillis()) } ?: "Target date (optional)", style = T.sans(15, 400, color = if (date == null) C.Faint else C.Ink))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = parseRupees(target)
                if (name.isNotBlank() && p != null && p > 0) onSave(Goal(name = name.trim(), target = p, targetDate = date?.startMillis()))
            }) { Text("Create", style = T.sans(14, 700)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = T.sans(14, 600, color = C.Muted)) } },
    )
}

@Composable
private fun ContributionDialog(v: GoalView, onDismiss: () -> Unit, onSave: (Long) -> Unit, onPurchased: () -> Unit, onDelete: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var withdraw by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Ground,
        title = { Text(v.goal.name, style = T.serif(22, 600)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${rupees(v.saved)} of ${rupees(v.goal.target)} saved · ${v.pct}%", style = T.sans(13, 500, color = C.Muted))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Set aside", !withdraw, height = 34.dp) { withdraw = false }
                    Chip("Withdraw", withdraw, height = 34.dp) { withdraw = true }
                }
                LightField(amount, { amount = it }, "Amount (₹)", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (v.status == GoalStatus.HIT) Text("Mark purchased", style = T.sans(13, 700), modifier = Modifier.clickable(onClick = onPurchased))
                    Text("Delete goal", style = T.sans(13, 600, color = C.Late), modifier = Modifier.clickable(onClick = onDelete))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = parseRupees(amount)
                if (p != null && p > 0) onSave(if (withdraw) -p else p)
            }) { Text("Save", style = T.sans(14, 700)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = T.sans(14, 600, color = C.Muted)) } },
    )
}
