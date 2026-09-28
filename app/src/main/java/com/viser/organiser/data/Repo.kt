package com.viser.organiser.data

import android.content.Context
import com.viser.organiser.reminders.Notifier
import com.viser.organiser.reminders.ReminderScheduler
import com.viser.organiser.sms.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Single entry point for reads and writes, so a sync layer can plug in later. */
class Repo(private val ctx: Context) {
    val db = AppDb.get(ctx)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ------------------------------------------------------------------ setup
    suspend fun seed() {
        if (db.sections().all().isEmpty()) {
            val names = listOf("Food", "Places", "Shop", "Links", "Personal", "Work", "Ideas", "Watch/Read", "Documents")
            db.sections().insertAll(names.mapIndexed { i, n -> Section(n, C_LABELS[i % C_LABELS.size], i) })
        }
    }

    suspend fun addSection(name: String) {
        val all = db.sections().all()
        if (all.any { it.name.equals(name, true) }) return
        db.sections().upsert(Section(name.trim(), C_LABELS[all.size % C_LABELS.size], all.size))
    }

    /** Moves a section to a new position (0 = front) and renumbers the rest. */
    suspend fun moveSection(name: String, to: Int) {
        val list = db.sections().all().toMutableList()
        val i = list.indexOfFirst { it.name == name }
        if (i < 0) return
        val s = list.removeAt(i)
        list.add(to.coerceIn(0, list.size), s)
        list.forEachIndexed { idx, sec -> db.sections().upsert(sec.copy(sort = idx)) }
    }

    /** Deletes a section; items keep everything else and simply lose that label. */
    suspend fun deleteSection(name: String) {
        db.sections().delete(name)
        val tag = "|$name|"
        db.items().all().filter { it.sections.contains(tag) }.forEach { item ->
            saveItem(item.copy(sections = Item.encodeSections(item.sectionList - name)))
        }
    }

    // ------------------------------------------------------------------ items
    suspend fun saveItem(item: Item) {
        val i = item.copy(updatedAt = now())
        db.items().upsert(i)
        ReminderScheduler.sync(ctx, i)
    }

    suspend fun setDone(id: String, done: Boolean) {
        val it = db.items().get(id) ?: return
        if (done && it.repeat.isNotEmpty() && it.remindAt != null) {
            // Repeating: completing moves it to the next occurrence.
            saveItem(it.copy(remindAt = ReminderScheduler.nextOccurrence(it.remindAt, it.repeat)))
        } else {
            saveItem(it.copy(done = done, doneAt = if (done) now() else null))
        }
        Notifier.cancel(ctx, id)
    }

    suspend fun softDelete(id: String) {
        val it = db.items().get(id) ?: return
        saveItem(it.copy(deletedAt = now(), remindAt = null))
        Notifier.cancel(ctx, id)
    }

    // ------------------------------------------------------------------ money
    suspend fun addManualTxn(t: Txn) {
        db.txns().upsert(t)
        if (t.merchant.isNotBlank() && t.kind == TxnKind.EXPENSE) learn(t.merchant, t.category)
    }

    suspend fun confirm(t: Txn, category: String = t.category) {
        db.txns().upsert(t.copy(category = category, status = TxnStatus.CONFIRMED, updatedAt = now()))
        if (t.merchant.isNotBlank() && t.kind == TxnKind.EXPENSE) learn(t.merchant, category)
        Notifier.cancelTxn(ctx, t.id)
    }

    suspend fun ignore(t: Txn) {
        db.txns().upsert(t.copy(status = TxnStatus.IGNORED, updatedAt = now()))
        Notifier.cancelTxn(ctx, t.id)
    }

    suspend fun updateTxn(t: Txn) = db.txns().upsert(t.copy(updatedAt = now()))

    suspend fun deleteTxn(t: Txn) = db.txns().upsert(t.copy(deletedAt = now(), updatedAt = now()))

    private suspend fun learn(merchant: String, category: String) {
        val key = SmsParser.merchantKey(merchant)
        if (key.isNotEmpty()) db.rules().upsert(MerchantRule(key, category))
    }

    suspend fun categoryFor(merchant: String, kind: String): String {
        val key = SmsParser.merchantKey(merchant)
        if (key.isNotEmpty() && kind == TxnKind.EXPENSE) db.rules().categoryFor(key)?.let { return it }
        return SmsParser.guessCategory(merchant, kind)
    }

    /**
     * Stores a bank SMS as a pending transaction. Returns the new Txn, or null if it was not a
     * transaction, was already imported, or duplicates one we already have (E-5).
     */
    suspend fun ingestSms(sender: String, body: String, time: Long, source: String = "sms", bankLabel: String? = null): Txn? {
        val hash = SmsParser.hash(sender, body, time)
        if (db.txns().hashCount(hash) > 0) return null
        val p = SmsParser.parse(sender, body)?.let { if (bankLabel != null) it.copy(bank = bankLabel) else it } ?: return null
        if (p.kind == TxnKind.TRANSFER) return null // card bill payments etc. are not spend

        if (p.upiRef.isNotEmpty() && db.txns().byRef(p.upiRef) != null) return null

        // A popup entry waiting for its amount (payment spotted in-app, SMS arriving now): fill it in.
        if (p.kind == TxnKind.EXPENSE) {
            val waiting = db.txns().recentPending(time - 15 * 60_000L)
                .firstOrNull { it.source == "popup" && it.amount == 0L && it.occurredAt <= time + 2 * 60_000L }
            if (waiting != null) {
                db.txns().upsert(waiting.copy(amount = p.amount, bank = p.bank, accountLast4 = p.accountLast4, upiRef = p.upiRef,
                    smsHash = hash, mode = p.mode, updatedAt = now()))
                return null // the popup / review screen already covers it
            }
        }
        val window = 10 * 60 * 1000L
        val near = db.txns().near(p.amount, p.kind, time - window, time + window)
        if (near.any { it.bank != p.bank || (it.accountLast4.isNotEmpty() && it.accountLast4 != p.accountLast4) }) return null

        val merchant = p.merchant.ifBlank { if (p.kind == TxnKind.INCOME) "Money received" else "${p.bank} payment" }
        val t = Txn(
            kind = p.kind,
            amount = p.amount,
            category = categoryFor(merchant, p.kind),
            merchant = merchant,
            mode = p.mode,
            source = source,
            bank = p.bank,
            accountLast4 = p.accountLast4,
            upiRef = p.upiRef,
            smsHash = hash,
            occurredAt = time,
            status = TxnStatus.PENDING,
        )
        return if (db.txns().insert(t) > 0) t else null
    }

    /**
     * A payment spotted by the "Did you just pay…?" detector. Reuses a bank SMS that already
     * arrived in the last few minutes; otherwise creates a pending entry with the amount still unknown
     * (the SMS fills it in when it lands — see [ingestSms]).
     */
    suspend fun popupTxn(merchant: String, payApp: String, at: Long): Txn {
        val recentSms = db.txns().recentPending(at - 5 * 60_000L)
            .firstOrNull { (it.source == "sms" || it.source == "notif") && it.kind == TxnKind.EXPENSE }
        if (recentSms != null) {
            val better = recentSms.copy(
                merchant = if (recentSms.merchant.endsWith(" payment") || recentSms.merchant.contains('@')) merchant else recentSms.merchant,
                note = recentSms.note.ifBlank { "via $payApp" },
                updatedAt = now(),
            )
            db.txns().upsert(better)
            Notifier.cancelTxn(ctx, better.id)
            return better
        }
        val t = Txn(
            kind = TxnKind.EXPENSE, amount = 0L, category = categoryFor(merchant, TxnKind.EXPENSE), merchant = merchant,
            mode = "UPI", source = "popup", note = "via $payApp", occurredAt = at, status = TxnStatus.PENDING,
        )
        db.txns().upsert(t)
        return t
    }

    // ------------------------------------------------------------------ goals
    suspend fun contribute(goalId: String, amount: Long) {
        db.goals().upsertContribution(Contribution(goalId = goalId, amount = amount))
        val g = db.goals().get(goalId) ?: return
        val saved = db.goals().savedFor(goalId)
        if (saved >= g.target && !g.hitNotified) {
            db.goals().upsert(g.copy(hitNotified = true, updatedAt = now()))
            Notifier.goalHit(ctx, g)
        } else if (saved < g.target && g.hitNotified) {
            db.goals().upsert(g.copy(hitNotified = false, updatedAt = now()))
        }
    }

    suspend fun saveGoal(g: Goal) = db.goals().upsert(g.copy(updatedAt = now()))

    companion object {
        val C_LABELS = listOf(
            0xFFD9622BL, 0xFF2F7A5BL, 0xFF2B5FD9L, 0xFF6B4FB8L, 0xFFB8860BL,
            0xFFB42318L, 0xFF0F4A46L, 0xFF7A2358L, 0xFF5E5C57L,
        )

        @Volatile private var inst: Repo? = null
        fun get(ctx: Context): Repo = inst ?: synchronized(this) {
            inst ?: Repo(ctx.applicationContext).also { inst = it }
        }
    }
}
