package com.viser.organiser.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.viser.organiser.MainActivity
import com.viser.organiser.R
import com.viser.organiser.data.Goal
import com.viser.organiser.data.Item
import com.viser.organiser.data.ItemType
import com.viser.organiser.data.Repeat
import com.viser.organiser.data.Repo
import com.viser.organiser.data.Txn
import com.viser.organiser.data.TxnKind
import com.viser.organiser.data.now
import com.viser.organiser.util.rupees
import com.viser.organiser.util.toLdt
import com.viser.organiser.util.toMillis
import com.viser.organiser.util.timeHm
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

object Notifier {
    const val CH_REMIND = "reminders"
    const val CH_MONEY = "money"
    const val CH_GOALS = "goals"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "To-do and item reminders"
        })
        nm.createNotificationChannel(NotificationChannel(CH_MONEY, "Payments to confirm", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Bank SMS detected as a payment"
        })
        nm.createNotificationChannel(NotificationChannel(CH_GOALS, "Goals", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notifId(id: String) = id.hashCode()
    private fun txnNotifId(id: String) = ("txn" + id).hashCode()

    private fun openApp(ctx: Context, route: String, id: String, req: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ROUTE, route)
            putExtra(MainActivity.EXTRA_ID, id)
        }
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(ctx: Context, id: String, act: String, req: Int): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).apply {
            action = act
            putExtra("id", id)
        }
        return PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun reminder(ctx: Context, item: Item) {
        if (!canPost(ctx)) return
        val n = notifId(item.id)
        val title = item.title.ifBlank { item.body.take(60) }.ifBlank { "Reminder" }
        val text = when (item.type) {
            ItemType.LINK -> item.domain.ifBlank { "Open your saved link" }
            ItemType.PLACE -> item.area.ifBlank { "A place you saved" }
            else -> if (item.repeat.isNotEmpty()) "Repeats ${Repeat.label(item.repeat).lowercase()}" else "To-do"
        }
        val b = NotificationCompat.Builder(ctx, CH_REMIND)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, "item", item.id, n))
            .addAction(0, "Done", action(ctx, item.id, ActionReceiver.DONE, n + 1))
            .addAction(0, "Snooze 10 min", action(ctx, item.id, ActionReceiver.SNOOZE_10, n + 2))
            .addAction(0, "1 h", action(ctx, item.id, ActionReceiver.SNOOZE_60, n + 3))
            .addAction(0, "Tomorrow", action(ctx, item.id, ActionReceiver.TOMORROW, n + 4))
        try { NotificationManagerCompat.from(ctx).notify(n, b.build()) } catch (_: SecurityException) {}
    }

    fun txn(ctx: Context, t: Txn) {
        if (!canPost(ctx)) return
        val n = txnNotifId(t.id)
        val verb = if (t.kind == TxnKind.INCOME) "received from" else "paid to"
        val confirm = Intent(ctx, com.viser.organiser.sms.TxnActionReceiver::class.java).apply {
            action = com.viser.organiser.sms.TxnActionReceiver.CONFIRM; putExtra("id", t.id)
        }
        val ignore = Intent(ctx, com.viser.organiser.sms.TxnActionReceiver::class.java).apply {
            action = com.viser.organiser.sms.TxnActionReceiver.IGNORE; putExtra("id", t.id)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val b = NotificationCompat.Builder(ctx, CH_MONEY)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("${rupees(t.amount)} $verb ${t.merchant} — spend or income?")
            .setContentText("${t.category} · ${t.bank} ${if (t.accountLast4.isNotEmpty()) "••" + t.accountLast4 else ""} · ${timeHm(t.occurredAt)}")
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, "review", t.id, n))
            .addAction(0, "Review", openApp(ctx, "review", t.id, n + 1))
            .addAction(0, "Not a payment", PendingIntent.getBroadcast(ctx, n + 2, ignore, flags))
        try { NotificationManagerCompat.from(ctx).notify(n, b.build()) } catch (_: SecurityException) {}
    }

    fun goalHit(ctx: Context, g: Goal) {
        if (!canPost(ctx)) return
        val b = NotificationCompat.Builder(ctx, CH_GOALS)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Target hit: ${g.name}")
            .setContentText("You've saved ${rupees(g.target)}. Time to buy it.")
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, "money", g.id, ("goal" + g.id).hashCode()))
        try { NotificationManagerCompat.from(ctx).notify(("goal" + g.id).hashCode(), b.build()) } catch (_: SecurityException) {}
    }

    fun cancel(ctx: Context, id: String) = NotificationManagerCompat.from(ctx).cancel(notifId(id))
    fun cancelTxn(ctx: Context, id: String) = NotificationManagerCompat.from(ctx).cancel(txnNotifId(id))
}

object ReminderScheduler {
    private fun pi(ctx: Context, id: String, flags: Int): PendingIntent? {
        val i = Intent(ctx, ReminderReceiver::class.java).apply {
            action = "com.viser.organiser.REMIND"
            putExtra("id", id)
        }
        return PendingIntent.getBroadcast(ctx, id.hashCode(), i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    /** Schedules or cancels the alarm to match the item's state (T-3). */
    fun sync(ctx: Context, item: Item) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = item.remindAt
        if (at == null || item.done || item.deletedAt != null || at <= now()) {
            pi(ctx, item.id, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it) }
            return
        }
        val p = pi(ctx, item.id, PendingIntent.FLAG_UPDATE_CURRENT)!!
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    fun nextOccurrence(from: Long, repeat: String): Long {
        var t = from.toLdt()
        val nowT = now()
        do {
            t = when (repeat) {
                Repeat.DAILY -> t.plusDays(1)
                Repeat.WEEKDAYS -> {
                    var n = t.plusDays(1)
                    while (n.dayOfWeek == DayOfWeek.SATURDAY || n.dayOfWeek == DayOfWeek.SUNDAY) n = n.plusDays(1)
                    n
                }
                Repeat.WEEKLY -> t.plusWeeks(1)
                Repeat.MONTHLY -> t.plusMonths(1)
                else -> return from
            }
        } while (t.toMillis() <= nowT)
        return t.toMillis()
    }

    suspend fun rescheduleAll(ctx: Context) {
        val repo = Repo.get(ctx)
        for (it in repo.db.items().withReminders()) {
            val at = it.remindAt ?: continue
            if (at <= now() && it.repeat.isNotEmpty()) {
                repo.saveItem(it.copy(remindAt = nextOccurrence(at, it.repeat)))
            } else sync(ctx, it)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val pending = goAsync()
        val repo = Repo.get(ctx)
        repo.scope.launch {
            try {
                val item = repo.db.items().get(id)
                if (item != null && !item.done && item.deletedAt == null) {
                    Notifier.reminder(ctx, item)
                    if (item.repeat.isNotEmpty() && item.remindAt != null) {
                        repo.saveItem(item.copy(remindAt = ReminderScheduler.nextOccurrence(item.remindAt, item.repeat)))
                    }
                }
            } finally { pending.finish() }
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val DONE = "com.viser.organiser.DONE"
        const val SNOOZE_10 = "com.viser.organiser.SNOOZE_10"
        const val SNOOZE_60 = "com.viser.organiser.SNOOZE_60"
        const val TOMORROW = "com.viser.organiser.TOMORROW"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val pending = goAsync()
        val repo = Repo.get(ctx)
        repo.scope.launch {
            try {
                val item = repo.db.items().get(id) ?: return@launch
                when (intent.action) {
                    // A repeating item already moved to its next occurrence when it fired.
                    DONE -> if (item.repeat.isEmpty()) repo.setDone(id, true)
                    SNOOZE_10 -> repo.saveItem(item.copy(remindAt = now() + 10 * 60_000L))
                    SNOOZE_60 -> repo.saveItem(item.copy(remindAt = now() + 60 * 60_000L))
                    TOMORROW -> {
                        val base = (item.remindAt ?: now()).toLdt()
                        val t = java.time.LocalDate.now().plusDays(1).atTime(if (item.remindAt != null) base.toLocalTime() else LocalTime.of(9, 0))
                        repo.saveItem(item.copy(remindAt = t.toMillis()))
                    }
                }
                Notifier.cancel(ctx, id)
            } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        val repo = Repo.get(ctx)
        repo.scope.launch {
            try { ReminderScheduler.rescheduleAll(ctx) } finally { pending.finish() }
        }
    }
}
