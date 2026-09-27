package com.viser.organiser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.viser.organiser.Nav
import com.viser.organiser.Screen
import com.viser.organiser.data.Item
import com.viser.organiser.ui.BottomNav
import com.viser.organiser.ui.GoalStatus
import com.viser.organiser.ui.GoalView
import com.viser.organiser.ui.Ic
import com.viser.organiser.ui.CircleIconButton
import com.viser.organiser.ui.Kicker
import com.viser.organiser.ui.Tab
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
import com.viser.organiser.util.monthLong
import com.viser.organiser.util.rupeeParts
import com.viser.organiser.util.rupees
import com.viser.organiser.util.startMillis
import com.viser.organiser.util.timeHm
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(nav: Nav) {
    val r = repo()
    val scope = rememberCoroutineScope()
    val ym = remember { YearMonth.now() }
    val txns by monthTxns(ym)
    val contribs by contributions()
    val goals by goals()
    val pending by pendingTxns()
    val todos by remember { r.db.items().todos() }.state(emptyList())
    val sum = summarize(txns, contribs, ym)

    val endOfToday = LocalDate.now().plusDays(1).startMillis()
    val today = todos.filter { !it.done && (it.remindAt ?: Long.MAX_VALUE) < endOfToday }
        .sortedBy { it.remindAt }
    val goalViews = goals.filter { !it.purchased }.map { goalView(it, contribs) }
    val featured = goalViews.firstOrNull { it.status == GoalStatus.BEHIND } ?: goalViews.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // top bar
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .border(1.dp, C.Line, RoundedCornerShape(20.dp))
                        .clickable(role = Role.Button) { nav.push(Screen.Review()) }
                        .padding(start = 6.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(C.Ink), contentAlignment = Alignment.Center) {
                        Icon(Ic.Inbox, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Text(if (pending.isEmpty()) "All confirmed" else "${pending.size} to confirm", style = T.sans(13, 600))
                }
                Spacer(Modifier.weight(1f))
                CircleIconButton(Ic.Search, "Search") { nav.replace(Screen.Saved(focusSearch = true)) }
                Spacer(Modifier.width(10.dp))
                CircleIconButton(Ic.Sliders, "Settings") { nav.push(Screen.Settings) }
            }

            // setup nudge until SMS + notifications are allowed
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val needsSetup = !notificationsOk(ctx) || !smsOk(ctx)
            if (needsSetup) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .border(1.dp, C.Line, RoundedCornerShape(16.dp))
                        .clickable { nav.push(Screen.Settings) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Ic.Shield, null, tint = C.Ink, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Finish setup", style = T.sans(14, 700))
                        Text("Allow SMS and notifications so expenses and reminders work", style = T.sans(12, 400, color = C.Muted))
                    }
                    Icon(Ic.ChevronRight, null, tint = C.Faint, modifier = Modifier.size(18.dp))
                }
            }

            // left to spend
            Column(Modifier.fillMaxWidth().clickable { nav.replace(Screen.Money) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Kicker("Left to spend · ${monthLong(ym)}")
                BigAmount(sum.remaining, 44)
            }

            // income / spent / set aside
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, C.Line, RoundedCornerShape(16.dp)),
            ) {
                StatCell("Income", rupees(sum.income), Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(C.LineSoft))
                StatCell("Spent", rupees(sum.spent), Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(C.LineSoft))
                StatCell("Set aside", rupees(sum.setAside), Modifier.weight(1f))
            }

            // capture bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(C.Ink)
                    .clickable(role = Role.Button) { nav.push(Screen.Capture()) }
                    .padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Add a to-do, expense or link…", style = T.sans(15, 400, color = C.OnDarkMuted), modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                    Icon(Ic.Plus, null, tint = C.Ink, modifier = Modifier.size(18.dp))
                }
            }

            // today
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Kicker(if (today.isEmpty()) "Today · all clear" else "Today · ${today.size} left", modifier = Modifier.weight(1f))
                    Text("All to-dos", style = T.sans(13, 600), modifier = Modifier.clickable { nav.tab(Tab.TODOS) })
                }
                if (today.isEmpty()) {
                    Text("Nothing due today. Tap + to add a to-do.", style = T.sans(14, 500, color = C.Muted), modifier = Modifier.padding(vertical = 10.dp))
                }
                today.take(4).forEachIndexed { i, t ->
                    TodayRow(t, last = i == minOf(today.size, 4) - 1,
                        onToggle = { scope.launch { r.setDone(t.id, true) } },
                        onOpen = { nav.push(Screen.ItemDetail(t.id)) })
                }
            }

            // goal
            if (featured != null) {
                GoalCard(featured, prefix = "Goal · ", onClick = { nav.replace(Screen.Money) })
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(C.plumBrush)
                        .clickable { nav.replace(Screen.Money) }
                        .padding(20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Kicker("Savings goals", color = C.GoalPlumText)
                        Text("Start a goal — a camera, a trip, a fund.", style = T.serif(20, 600, color = Color.White))
                    }
                }
            }
        }
        BottomNav(Tab.HOME, onTab = nav::tab, onCapture = { nav.push(Screen.Capture()) })
    }
}

@Composable
fun BigAmount(paise: Long, size: Int, color: Color = C.Ink, decColor: Color = C.Muted) {
    val (main, dec) = rupeeParts(paise)
    Text(
        buildAnnotatedString {
            append(main)
            withStyle(SpanStyle(fontWeight = FontWeight(500), color = decColor)) { append(dec) }
        },
        style = T.serif(size, 600, (-0.02).em, color),
    )
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = T.sans(11, 500, color = C.Muted))
        Text(value, style = T.serif(18, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TodayRow(t: Item, last: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val late = (t.remindAt ?: Long.MAX_VALUE) < System.currentTimeMillis()
    Column {
        Row(Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, if (late) C.Late else C.Faint, CircleShape)
                    .clickable(role = Role.Checkbox, onClickLabel = "Mark done", onClick = onToggle),
            )
            Text(t.title, style = T.sans(15, 500), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val time = t.remindAt?.let { timeHm(it) } ?: ""
            Text(if (late) "$time · late" else time, style = T.sans(12, if (late) 600 else 500, color = if (late) C.Late else C.Muted))
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line))
    }
}

/** The plum goal card from the designs (also used on Money). */
@Composable
fun GoalCard(v: GoalView, prefix: String = "", onClick: (() -> Unit)? = null, onAdd: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x471E0818), spotColor = Color(0x471E0818))
            .clip(RoundedCornerShape(18.dp))
            .background(C.plumBrush)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text((prefix + v.goal.name).uppercase(), style = T.sans(11, 600, 0.16.em, C.GoalPlumText), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildAnnotatedString {
                        append(rupees(v.saved))
                        withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight(500), color = C.GoalPlumText)) { append(" / " + rupees(v.goal.target)) }
                    },
                    style = T.serif(26, 600, color = Color.White),
                )
            }
            Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).padding(horizontal = 10.dp, vertical = 5.dp)) {
                Text(v.status.label, style = T.sans(11, 700, 0.06.em, C.Plum))
            }
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(v.pct / 100f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
        }
        if (onAdd == null) {
            Row(Modifier.fillMaxWidth()) {
                val by = v.goal.targetDate?.let { " · by ${dateLong(it)}" } ?: ""
                Text("${v.pct}%$by", style = T.sans(12, 500, color = C.GoalPlumText), modifier = Modifier.weight(1f))
                Text(goalNeedLine(v), style = T.sans(12, 500, color = C.GoalPlumText))
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(goalDetailLines(v), style = T.sans(12, 500, color = C.GoalPlumText).copy(lineHeight = 17.sp), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(Color.White)
                        .clickable(role = Role.Button, onClick = onAdd).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Add", style = T.sans(13, 700)) }
            }
        }
    }
}

fun goalNeedLine(v: GoalView): String = when {
    v.status == GoalStatus.HIT -> "Target hit"
    v.needPerMonth != null -> "Need ${rupees(v.needPerMonth)}/mo"
    else -> "${rupees(v.goal.target - v.saved)} to go"
}

fun goalDetailLines(v: GoalView): String {
    if (v.status == GoalStatus.HIT) return "Target hit — time to buy it."
    val first = if (v.needPerMonth != null && v.goal.targetDate != null)
        "Need ${rupees(v.needPerMonth)}/mo to hit ${com.viser.organiser.util.dateShort(v.goal.targetDate)}"
    else "${rupees(v.goal.target - v.saved)} to go"
    val second = when {
        v.pacePerMonth > 0 && v.reachInMonths != null -> "At ${rupees(v.pacePerMonth)}/mo you reach it in ${v.reachInMonths} month${if (v.reachInMonths == 1) "" else "s"}"
        else -> "Add money to start tracking pace"
    }
    return "$first\n$second"
}
