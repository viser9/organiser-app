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
import com.viser.organiser.ui.Chip
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
    var cat by remember(current?.id) { mutableStateOf(current?.category ?: "Others") }
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
                        Text("${rupees(t.amount)} added to $c", style = T.sans(15, 700))
                        Text("${rupees(left)} left this month" + if (pending.isNotEmpty()) " · ${pending.size} more" else "", style = T.sans(13, 400, color = C.Muted))
                    }
                    OutlineButton("Undo", height = 36.dp) {
                        scope.launch { r.updateTxn(done.first) }
                        first = done.first.id; confirmed = null
                    }
                }
                current == null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Nothing to confirm", style = T.serif(24, 600))
                    Text("Bank SMS for SBI, HDFC and slice show up here as they arrive.", style = T.sans(14, 400, color = C.Muted))
                    PrimaryButton("Done", height = 48.dp) { nav.pop() }
                }
                else -> PaymentCard(current, cat, onCat = { cat = it },
                    onConfirm = {
                        val t = current
                        scope.launch { r.confirm(t, cat) }
                        confirmed = t to cat
                        first = null
                    },
                    onLater = {
                        val idx = pending.indexOf(current)
                        first = pending.getOrNull(idx + 1)?.id ?: run { nav.pop(); null }
                    },
                    onIgnore = { scope.launch { r.ignore(current) }; first = null },
                )
            }
            if (pending.size > 1 && confirmed == null) {
                Text(
                    "Confirm all ${pending.size} as suggested",
                    style = T.sans(13, 600, color = C.Muted),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable {
                        val all = pending.toList()
                        scope.launch { all.forEach { r.confirm(it, it.category) } }
                    }.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PaymentCard(t: Txn, cat: String, onCat: (String) -> Unit, onConfirm: () -> Unit, onLater: () -> Unit, onIgnore: () -> Unit) {
    val income = t.kind == TxnKind.INCOME
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(C.Ink), contentAlignment = Alignment.Center) {
                Icon(Ic.Card, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(if (income) "Money received${if (t.merchant.isNotBlank() && t.merchant != "Money received") " from ${t.merchant}" else ""}?" else "Did you pay ${t.merchant}?", style = T.sans(15, 700))
                Text("${t.mode} · ${t.bank} SMS · ${dateShort(t.occurredAt)} ${timeHm(t.occurredAt)}", style = T.sans(12, 400, color = C.Muted))
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, C.Line, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("AMOUNT", style = T.sans(11, 600, 0.16.em, C.Muted))
                BigAmount(t.amount, 36)
            }
            Text(
                "From bank SMS" + if (t.accountLast4.isNotEmpty()) "\na/c ••${t.accountLast4}" else "",
                style = T.sans(12, 500, color = C.Good).copy(lineHeight = 17.sp),
                textAlign = TextAlign.End,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("Category")
            val cats = (if (income) Categories.income else Categories.expense).let { list -> listOf(cat) + list.filter { it != cat } }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.forEach { c -> Chip(c, c == cat, height = 36.dp) { onCat(c) } }
            }
            if (t.merchant.isNotBlank()) Text("Suggested from ${t.merchant}; your choice is remembered", style = T.sans(12, 400, color = C.Muted))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(if (income) "Confirm income" else "Confirm expense", height = 52.dp, onClick = onConfirm)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("Later", Modifier.weight(1f), onClick = onLater)
                OutlineButton("Not a payment", Modifier.weight(1f), onClick = onIgnore)
            }
        }
    }
}

@Suppress("unused")
private val unusedRole = Role.Button
