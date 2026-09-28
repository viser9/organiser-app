package com.viser.organiser.sms

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.viser.organiser.data.Repo
import com.viser.organiser.reminders.Notifier
import com.viser.organiser.watch.Learning
import kotlinx.coroutines.launch

/**
 * Reads notifications from payment and bank apps only (many banks now send push alerts instead of SMS)
 * and feeds them through the same parser as bank SMS. Everything else is ignored.
 * Only the extracted fields are stored — never the notification text.
 */
class PayNotificationListener : NotificationListenerService() {

    companion object {
        /** Payment and bank apps whose notifications may describe a payment. */
        val WATCHED = mapOf(
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "com.phonepe.app" to "PhonePe",
            "net.one97.paytm" to "Paytm",
            "in.org.npci.upiapp" to "BHIM",
            "com.dreamplug.androidapp" to "CRED",
            "indwin.c3.shareapp" to "slice",
            "com.sbi.lotusintouch" to "SBI YONO",
            "com.sbi.upi" to "SBI Pay",
            "com.snapwork.hdfc" to "HDFC Bank",
            "com.hdfcbank.payzapp" to "PayZapp",
            "com.csam.icici.bank.imobile" to "ICICI iMobile",
            "com.axis.mobile" to "Axis Mobile",
            "com.msf.kbank.mobile" to "Kotak",
            "com.mobikwik_new" to "MobiKwik",
            "in.amazon.mShop.android.shopping" to "Amazon Pay",
        )

        fun enabled(ctx: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

        fun component(ctx: Context) = ComponentName(ctx, PayNotificationListener::class.java)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        // Every payment / bank app found on the phone (see PayApps), not a fixed list.
        val label = com.viser.organiser.watch.PayApps.watchedForNotifications(applicationContext)[pkg] ?: return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val body = listOf(title, text).filter { it.isNotBlank() }.joinToString(". ")
        if (body.isBlank()) return

        val ctx = applicationContext
        val repo = Repo.get(ctx)
        repo.scope.launch {
            // Sender tag keeps the SMS parser happy (letters = not a personal number) and names the app.
            val t = repo.ingestSms("APP-$label", body, sbn.postTime, source = "notif", bankLabel = label)
            if (Learning.isOn(ctx)) Learning.append(ctx, "NOTIF", pkg, t?.kind ?: "not a payment")
            if (t != null) Notifier.txn(ctx, t)
        }
    }
}
