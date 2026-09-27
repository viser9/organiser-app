package com.viser.organiser.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.viser.organiser.Nav
import com.viser.organiser.data.Categories
import com.viser.organiser.data.Txn
import com.viser.organiser.data.TxnKind
import com.viser.organiser.ui.TxnDraft
import com.viser.organiser.ui.TxnEditor
import com.viser.organiser.ui.state
import com.viser.organiser.ui.Ic
import com.viser.organiser.ui.Kicker
import com.viser.organiser.ui.OutlineButton
import com.viser.organiser.ui.PrimaryButton
import com.viser.organiser.ui.contributions
import com.viser.organiser.ui.monthTxns
import com.viser.organiser.ui.pendingTxns
import com.viser.organiser.ui.repo
import com.viser.organiser.ui.summarize
import com.viser.organiser.ui.theme.C
import com.viser.organiser.ui.theme.T
import com.viser.organiser.util.dateShort
import com.viser.organiser.util.rupees
import com.viser.organiser.util.timeHm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * "Did you just pay …?" — confirm SMS-detected payments one at a time (E-7),
 * styled after the payment-detected popup.
 */
@Composable
fun ReviewScreen(nav: Nav, startId: String?) {
    val r = repo()
    val scope = rememberCoroutineScope()
    val pending by pendingTxns()
    val txns by monthTxns(YearMonth.now())
    val contribs by contributions()
    var confirmed by remember { mutableStateOf<Pair<Txn, String>?>(null) }
    var first by remember { mutableStateOf(startId) }

    val current = pending.firstOrNull { it.id == first } ?: pending.firstOrNull()
    var draft by remember(current?.id) { mutableStateOf(current?.let { TxnDraft.from(it) }) }
    var typedAmount by remember(current?.id) { mutableStateOf("") }
    val splits by remember { r.db.txns().recentSplits() }.state(emptyList())
    val knownPeople = remember(splits) { splits.flatMap { it.split('|') }.filter { it.isNotBlank() }.distinct() }
    val left = summarize(txns, contribs, YearMonth.now()).remaining

    LaunchedEffect(confirmed) {
        if (confirmed != null) { delay(2500); confirmed = null }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFDAD8D3))) {
        // backdrop: faint hint of the app underneath
        Column(Modifier.fillMaxSize().padding(top = 300.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(Color(0xFFC4C1BA)))
            Box(Modifier.height(16.dp).padding(horizontal = 100.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFC4C1BA)))
            Text(if (pending.isEmpty()) "ALL CAUGHT UP" else "${pending.size} TO CONFIRM", style = T.sans(12, 600, 0.14.em, Color(0xFF6B6862)))
        }
        Box(Modifier.fillMaxSize().background(Color(0x73121212)).clickable(enabled = pending.isEmpty() && confirmed == null) { nav.pop() })

        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 16.dp)
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .clip(RoundedCornerShape(24.dp))
                .background(C.Ground)
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val done = confirmed
            when {
                done != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(C.Ink), contentAlignment = Alignment.Center) {
                        Icon(Ic.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val (t, c) = done
                        Text("${rupees(t.effective)} added to $c" + if (t.isSplit) " · split" else "", style = T.sans(15, 700))
                        Text("${rupees(left)} left this month" + if (pending.isNotEmpty()) " · ${pending.size} more" else "", style = T.sans(13, 400, color = C.Muted))
                    }
                    OutlineButton("Undo", height = 36.dp) {
                        r.scope.launch { r.updateTxn(done.first.copy(status = com.viser.organiser.data.TxnStatus.PENDING)) }
                        first = done.first.id; confirmed = null
                    }
                }
                current == null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Nothing to confirm", style = T.serif(24, 600))
                    Text("Bank SMS for SBI, HDFC and slice show up here as they arrive.", style = T.sans(14, 400, color = C.Muted))
                    PrimaryButton("Done", height = 48.dp) { nav.pop() }
                }
                else -> PaymentCard(current, draft ?: TxnDraft.from(current), knownPeople, onDraft = { draft = it },
                    typedAmount = typedAmount, onTypedAmount = { typedAmount = it },
                    onConfirm = {
                        val base = if (current.amount == 0L) current.copy(amount = com.viser.organiser.util.parseRupees(typedAmount) ?: 0L) else current
                        val t = (draft ?: TxnDraft.from(current)).apply(base)
                        r.scope.launch { r.confirm(t) }
                        confirmed = t to t.category
                        first = null
                    },
                    onLater = {
                        val idx = pending.indexOf(current)
                        first = pending.getOrNull(idx + 1)?.id ?: run { nav.pop(); null }
                    },
                    onIgnore = { r.scope.launch { r.ignore(current) }; first = null },
                )
            }
            if (pending.size > 1 && confirmed == null) {
                Text("${pending.size - 1} more after this", style = T.sans(12, 500, color = C.Muted), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PaymentCard(
    t: Txn, d: TxnDraft, knownPeople: List<String>, onDraft: (TxnDraft) -> Unit,
    typedAmount: String, onTypedAmount: (String) -> Unit,
    onConfirm: () -> Unit, onLater: () -> Unit, onIgnore: () -> Unit,
) {
    val needsAmount = t.amount == 0L
    val amount = if (needsAmount) com.viser.organiser.util.parseRupees(typedAmount) ?: 0L else t.amount
    val smsSaysIncome = t.kind == TxnKind.INCOME
    val income = d.kind == TxnKind.INCOME
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(C.Ink), contentAlignment = Alignment.Center) {
                Icon(Ic.Card, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                val who = t.merchant.takeIf { it.isNotBlank() && it != "Money received" && !it.endsWith(" payment") }
                Text(
                    if (smsSaysIncome) "Money received${who?.let { " from $it" } ?: ""}" else "Payment${who?.let { " to $it" } ?: ""}",
                    style = T.sans(15, 700),
                )
                Text(
                    if (t.source == "popup") "${t.note.ifBlank { "In-app payment" }} · spotted in the app · ${dateShort(t.occurredAt)} ${timeHm(t.occurredAt)}"
                    else "${t.mode} · ${t.bank} SMS · ${dateShort(t.occurredAt)} ${timeHm(t.occurredAt)}",
                    style = T.sans(12, 400, color = C.Muted),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, C.Line, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("AMOUNT", style = T.sans(11, 600, 0.16.em, C.Muted))
                if (needsAmount) {
                    com.viser.organiser.ui.LightField(
                        typedAmount, { v -> onTypedAmount(v.filter { it.isDigit() || it == '.' }) }, "Enter amount (₹)",
                        keyboard = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    )
                } else BigAmount(t.amount, 36)
            }
            if (!needsAmount) Text(
                "From bank SMS" + if (t.accountLast4.isNotEmpty()) "\na/c ••${t.accountLast4}" else "",
                style = T.sans(12, 500, color = C.Good).copy(lineHeight = 17.sp),
                textAlign = TextAlign.End,
            )
        }
        if (needsAmount) Text("No bank SMS yet — type the amount, or tap Later and it fills in when the SMS arrives.", style = T.sans(12, 400, color = C.Muted))
        TxnEditor(amount, d, knownPeople, compactCategories = true, onChange = onDraft)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val share = if (d.split && d.people.isNotEmpty() && amount > 0) " · your ${rupees(d.myShare(amount))}" else ""
            PrimaryButton((if (income) "Confirm income" else "Confirm spend") + share, height = 52.dp, enabled = amount > 0, onClick = onConfirm)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("Later", Modifier.weight(1f), onClick = onLater)
                OutlineButton("Not a payment", Modifier.weight(1f), onClick = onIgnore)
            }
        }
    }
}
