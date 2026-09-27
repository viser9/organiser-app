package com.viser.organiser.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.viser.organiser.data.Repo
import com.viser.organiser.data.now
import com.viser.organiser.reminders.Notifier
import kotlinx.coroutines.launch

/** New SMS → pending transaction + a confirm notification. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        val sender = msgs[0].originatingAddress ?: return
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val time = msgs[0].timestampMillis.takeIf { it > 0 } ?: now()
        val pending = goAsync()
        val repo = Repo.get(ctx)
        repo.scope.launch {
            try {
                repo.ingestSms(sender, body, time)?.let { Notifier.txn(ctx, it) }
            } finally { pending.finish() }
        }
    }
}

class TxnActionReceiver : BroadcastReceiver() {
    companion object {
        const val CONFIRM = "com.viser.organiser.TXN_CONFIRM"
        const val IGNORE = "com.viser.organiser.TXN_IGNORE"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val pending = goAsync()
        val repo = Repo.get(ctx)
        repo.scope.launch {
            try {
                val t = repo.db.txns().get(id) ?: return@launch
                when (intent.action) {
                    CONFIRM -> repo.confirm(t, t.category)
                    IGNORE -> repo.ignore(t)
                }
            } finally { pending.finish() }
        }
    }
}

object SmsImporter {
    private const val PREFS = "sms"
    private const val KEY_LAST = "lastImported"

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * Scans the inbox for bank SMS since the last scan (first run: last 45 days).
     * Catches anything the receiver missed while the phone maker killed the app.
     */
    suspend fun importInbox(ctx: Context, days: Int = 45): Int {
        if (!hasPermission(ctx)) return 0
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val since = prefs.getLong(KEY_LAST, now() - days * 24L * 3600_000L)
        val repo = Repo.get(ctx)
        var added = 0
        var newest = since
        val cursor = try {
            ctx.contentResolver.query(
                Uri.parse("content://sms/inbox"),
                arrayOf("address", "body", "date"),
                "date > ?", arrayOf(since.toString()), "date ASC",
            )
        } catch (e: SecurityException) { null } ?: return 0
        cursor.use { c ->
            val ia = c.getColumnIndex("address")
            val ib = c.getColumnIndex("body")
            val id = c.getColumnIndex("date")
            while (c.moveToNext()) {
                val addr = c.getString(ia) ?: continue
                val body = c.getString(ib) ?: continue
                val date = c.getLong(id)
                if (date > newest) newest = date
                if (repo.ingestSms(addr, body, date) != null) added++
            }
        }
        prefs.edit().putLong(KEY_LAST, newest).apply()
        return added
    }

    fun resetScan(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LAST).apply()
    }
}
