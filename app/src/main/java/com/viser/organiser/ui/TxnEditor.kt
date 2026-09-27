package com.viser.organiser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.viser.organiser.data.Categories
import com.viser.organiser.data.Item
import com.viser.organiser.data.Txn
import com.viser.organiser.data.TxnKind
import com.viser.organiser.ui.theme.C
import com.viser.organiser.ui.theme.T
import com.viser.organiser.util.parseRupees
import com.viser.organiser.util.rupees

/**
 * What the owner decides when confirming (or editing) a transaction:
 * spend or income → category → optional note → split? → with whom, and their own share.
 */
data class TxnDraft(
    val kind: String,
    val category: String,
    val note: String,
    val split: Boolean,
    val people: List<String>,
    /** Owner's share as typed; blank = split equally. */
    val shareText: String,
) {
    fun myShare(amount: Long): Long =
        parseRupees(shareText)?.coerceIn(0, amount) ?: (amount / (people.size + 1))

    fun apply(t: Txn): Txn {
        val splitNow = split && people.isNotEmpty()
        return t.copy(
            kind = kind,
            category = category,
            note = note.trim(),
            splitWith = if (splitNow) Item.encodeSections(people) else "",
            myShare = if (splitNow) myShare(t.amount) else null,
        )
    }

    companion object {
        fun from(t: Txn): TxnDraft {
            val kind = if (t.kind == TxnKind.INCOME) TxnKind.INCOME else TxnKind.EXPENSE
            val equal = if (t.isSplit) t.amount / (t.people.size + 1) else 0L
            val custom = t.myShare != null && t.myShare != equal
            return TxnDraft(
                kind = kind,
                category = t.category,
                note = t.note,
                split = t.isSplit,
                people = t.people,
                shareText = if (custom) (t.myShare!! / 100).toString() else "",
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TxnEditor(amount: Long, d: TxnDraft, knownPeople: List<String>, compactCategories: Boolean, onChange: (TxnDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. spend or income
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("This is")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TxnKind.EXPENSE to "Spend", TxnKind.INCOME to "Income").forEach { (k, label) ->
                    val on = d.kind == k
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (on) C.Ink else Color.White)
                            .border(1.dp, if (on) C.Ink else C.Line, RoundedCornerShape(12.dp))
                            .clickable(role = Role.RadioButton) {
                                if (!on) {
                                    val cats = if (k == TxnKind.INCOME) Categories.income else Categories.expense
                                    onChange(d.copy(kind = k, category = if (d.category in cats) d.category else cats.first()))
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = T.sans(14, 700, color = if (on) Color.White else C.Ink)) }
                }
            }
        }

        // 2. category
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("Category")
            val base = if (d.kind == TxnKind.INCOME) Categories.income else Categories.expense
            if (compactCategories) {
                val cats = listOf(d.category) + base.filter { it != d.category }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cats.forEach { c -> Chip(c, c == d.category, height = 36.dp) { onChange(d.copy(category = c)) } }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    base.forEach { c -> Chip(c, c == d.category, height = 34.dp) { onChange(d.copy(category = c)) } }
                }
            }
        }

        // 3. note
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("Note")
            LightField(d.note, { onChange(d.copy(note = it)) }, "Add a note (optional)",
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        }

        // 4. split
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Kicker("Split this?", modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("No", !d.split, height = 32.dp) { onChange(d.copy(split = false)) }
                    Chip("Yes", d.split, height = 32.dp) { onChange(d.copy(split = true)) }
                }
            }
            if (d.split) SplitPeople(amount, d, knownPeople, onChange)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitPeople(amount: Long, d: TxnDraft, knownPeople: List<String>, onChange: (TxnDraft) -> Unit) {
    var name by remember { mutableStateOf("") }
    fun add(n: String) {
        val clean = n.trim().replace("|", "")
        if (clean.isNotEmpty() && d.people.none { it.equals(clean, true) }) onChange(d.copy(people = d.people + clean))
        name = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Split with", style = T.sans(13, 600))
        if (d.people.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.people.forEach { p ->
                    Row(
                        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(C.Ink)
                            .clickable(onClickLabel = "Remove $p") { onChange(d.copy(people = d.people - p)) }
                            .padding(start = 14.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(p, style = T.sans(13, 600, color = Color.White))
                        Icon(Ic.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LightField(
                name, { name = it }, "Add a person's name", modifier = Modifier.weight(1f),
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                onDone = { add(name) },
            )
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).background(if (name.isBlank()) C.Chip else C.Ink)
                    .clickable(enabled = name.isNotBlank(), role = Role.Button, onClickLabel = "Add person") { add(name) },
                contentAlignment = Alignment.Center,
            ) { Icon(Ic.Plus, "Add person", tint = if (name.isBlank()) C.Faint else Color.White, modifier = Modifier.size(18.dp)) }
        }
        val suggestions = knownPeople.filter { k -> d.people.none { it.equals(k, true) } && (name.isBlank() || k.startsWith(name.trim(), true)) }.take(6)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach { s -> DashedChip("+ $s", height = 32.dp) { add(s) } }
            }
        }
        if (d.people.isNotEmpty()) {
            val mine = d.myShare(amount)
            val others = amount - mine
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
                    .border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Your share", style = T.sans(14, 600), modifier = Modifier.weight(1f))
                    LightField(
                        d.shareText, { v -> onChange(d.copy(shareText = v.filter { it.isDigit() || it == '.' })) },
                        rupees(amount / (d.people.size + 1)) + " (equal)",
                        modifier = Modifier.weight(1.2f),
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                val verb = if (d.kind == TxnKind.INCOME) "belongs to" else "to collect from"
                Text(
                    "${rupees(mine)} counts as yours · ${rupees(others)} $verb ${d.people.joinToString(", ")}",
                    style = T.sans(12, 500, color = C.Muted),
                )
            }
        }
    }
}
