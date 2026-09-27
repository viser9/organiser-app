package com.viser.organiser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.viser.organiser.data.Contribution
import com.viser.organiser.data.Goal
import com.viser.organiser.data.Repo
import com.viser.organiser.data.Section
import com.viser.organiser.data.Txn
import com.viser.organiser.data.TxnKind
import com.viser.organiser.data.TxnStatus
import com.viser.organiser.data.now
import com.viser.organiser.util.endMillis
import com.viser.organiser.util.startMillis
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun repo(): Repo = Repo.get(LocalContext.current)

@Composable
fun <T> Flow<T>.state(initial: T): State<T> = collectAsState(initial)

data class MonthSummary(val income: Long, val spent: Long, val setAside: Long) {
    val remaining: Long get() = income - spent - setAside
}

fun summarize(txns: List<Txn>, contributions: List<Contribution>, ym: YearMonth): MonthSummary {
    val from = ym.startMillis(); val to = ym.endMillis()
    val confirmed = txns.filter { it.status == TxnStatus.CONFIRMED && it.deletedAt == null && it.occurredAt in from until to }
    val income = confirmed.filter { it.kind == TxnKind.INCOME }.sumOf { it.effective }
    val spent = confirmed.filter { it.kind == TxnKind.EXPENSE }.sumOf { it.effective }
    val aside = contributions.filter { it.deletedAt == null && it.at in from until to }.sumOf { it.amount }
    return MonthSummary(income, spent, aside)
}

@Composable
fun monthTxns(ym: YearMonth): State<List<Txn>> {
    val r = repo()
    val flow = remember(ym) { r.db.txns().between(ym.startMillis(), ym.endMillis()) }
    return flow.state(emptyList())
}

@Composable
fun contributions(): State<List<Contribution>> {
    val r = repo()
    return remember { r.db.goals().contributions() }.state(emptyList())
}

@Composable
fun goals(): State<List<Goal>> {
    val r = repo()
    return remember { r.db.goals().observe() }.state(emptyList())
}

@Composable
fun pendingTxns(): State<List<Txn>> {
    val r = repo()
    return remember { r.db.txns().pending() }.state(emptyList())
}

@Composable
fun sections(): State<List<Section>> {
    val r = repo()
    return remember { r.db.sections().observe() }.state(emptyList())
}

fun List<Section>.colorOf(name: String): Color =
    firstOrNull { it.name == name }?.let { Color(it.color) } ?: Color(0xFF8A8781)

enum class GoalStatus(val label: String) { NEW("NEW"), ON_TRACK("ON TRACK"), BEHIND("BEHIND"), HIT("TARGET HIT") }

data class GoalView(
    val goal: Goal,
    val saved: Long,
    val pct: Int,
    val monthsLeft: Int?,
    val needPerMonth: Long?,
    val pacePerMonth: Long,
    val reachInMonths: Int?,
    val status: GoalStatus,
)

private const val MONTH_MS = 30.44 * 24 * 3600 * 1000

fun goalView(g: Goal, contributions: List<Contribution>): GoalView {
    val saved = contributions.filter { it.goalId == g.id && it.deletedAt == null }.sumOf { it.amount }
    val left = max(0L, g.target - saved)
    val pct = if (g.target > 0) ((saved * 100) / g.target).toInt().coerceIn(0, 100) else 0
    val t = now()
    val monthsLeft = g.targetDate?.let { max(1, ceil((it - t) / MONTH_MS).toInt()) }
    val need = monthsLeft?.let { left / it }
    val since = max(1.0, (t - g.createdAt) / MONTH_MS)
    val pace = (saved / since).toLong()
    val reach = if (left == 0L) 0 else if (pace > 0) ceil(left.toDouble() / pace).toInt() else null
    val status = when {
        saved >= g.target && g.target > 0 -> GoalStatus.HIT
        saved <= 0 -> GoalStatus.NEW
        need == null -> GoalStatus.ON_TRACK
        pace >= need -> GoalStatus.ON_TRACK
        else -> GoalStatus.BEHIND
    }
    return GoalView(g, saved, pct, monthsLeft, need, pace, reach, status)
}
