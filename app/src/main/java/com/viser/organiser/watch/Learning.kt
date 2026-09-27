package com.viser.organiser.watch

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.viser.organiser.MainActivity
import com.viser.organiser.R
import java.io.File
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Learning mode: while on, records which app and which screen is in front, with timestamps,
 * plus markers the owner taps after each payment. Never text, amounts or PINs.
 * The log is used to tune the "Did you just pay…?" detector to this phone's real apps.
 */
object Learning {
    private const val PREFS = "learning"
    private const val KEY_ON_UNTIL = "onUntil"
    private const val FILE = "learning-log.txt"
    private const val MAX_LINES = 6000
    const val CH = "learning"
    const val NOTIF_ID = 4242
    private const val DURATION_MS = 3 * 60 * 60 * 1000L

    /** Apps we care about most: UPI apps, bank apps, and common merchant apps. Used for the header only. */
    val knownApps = linkedMapOf(
        "com.phonepe.app" to "PhonePe",
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "com.dreamplug.androidapp" to "CRED",
        "in.amazon.mShop.android.shopping" to "Amazon",
        "indwin.c3.shareapp" to "slice",
        "com.sbi.lotusintouch" to "SBI YONO",
        "com.sbi.upi" to "SBI BHIM Pay",
        "com.snapwork.hdfc" to "HDFC MobileBanking",
        "com.hdfcbank.payzapp" to "PayZapp",
        "com.whatsapp" to "WhatsApp",
        "in.swiggy.android" to "Swiggy",
        "com.application.zomato" to "Zomato",
        "com.grofers.customerapp" to "Blinkit",
        "com.zeptoconsumerapp" to "Zepto",
        "com.flipkart.android" to "Flipkart",
        "com.ubercab" to "Uber",
        "com.olacabs.customer" to "Ola",
        "com.rapido.passenger" to "Rapido",
        "com.bt.bms" to "BookMyShow",
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun file(ctx: Context) = File(ctx.filesDir, FILE)
    private val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH)

    fun isOn(ctx: Context): Boolean {
        val until = prefs(ctx).getLong(KEY_ON_UNTIL, 0L)
        if (until == 0L) return false
        if (System.currentTimeMillis() > until) { stop(ctx, auto = true); return false }
        return true
    }

    fun onUntil(ctx: Context): Long = prefs(ctx).getLong(KEY_ON_UNTIL, 0L)

    fun serviceEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(ctx, PayWatchService::class.java)
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it)?.let { c -> c == me } ?: false
        }
    }

    fun start(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_ON_UNTIL, System.currentTimeMillis() + DURATION_MS).apply()
        if (!file(ctx).exists() || file(ctx).length() == 0L) writeHeader(ctx)
        append(ctx, "START", "learning on for 3 h", "")
        showOngoing(ctx)
    }

    fun stop(ctx: Context, auto: Boolean = false) {
        if (prefs(ctx).getLong(KEY_ON_UNTIL, 0L) == 0L) return
        prefs(ctx).edit().putLong(KEY_ON_UNTIL, 0L).apply()
        append(ctx, "STOP", if (auto) "auto-off after 3 h" else "turned off", "")
        NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
    }

    private fun writeHeader(ctx: Context) {
        val pm = ctx.packageManager
        val installed = knownApps.filter { (pkg, _) ->
            try { pm.getPackageInfo(pkg, 0); true } catch (e: PackageManager.NameNotFoundException) { false }
        }.map { (pkg, name) ->
            val v = try { pm.getPackageInfo(pkg, 0).versionName } catch (e: Exception) { "?" }
            "#   $name ($pkg) v$v"
        }
        val appV = try { pm.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "?" }
        val lines = buildList {
            add("# Organiser learning log — app/screen changes only; no text, amounts or PINs")
            add("# Device: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · Organiser $appV")
            add("# Payment-related apps installed:")
            addAll(if (installed.isEmpty()) listOf("#   (none of the known ones)") else installed)
            add("# time | kind | app | screen")
        }
        file(ctx).writeText(lines.joinToString("\n") + "\n")
    }

    @Synchronized
    fun append(ctx: Context, kind: String, app: String, screen: String) {
        val f = file(ctx)
        f.appendText("${ts.format(Date())} | $kind | $app | $screen\n")
        // keep the file bounded
        if (f.length() > 900_000) {
            val lines = f.readLines()
            val header = lines.takeWhile { it.startsWith("#") }
            f.writeText((header + lines.drop(header.size).takeLast(MAX_LINES / 2)).joinToString("\n") + "\n")
        }
    }

    fun mark(ctx: Context, what: String) {
        append(ctx, "MARK", what, "")
        if (isOn(ctx)) showOngoing(ctx, lastMark = what)
    }

    fun read(ctx: Context): String = file(ctx).takeIf { it.exists() }?.readText().orEmpty()

    fun eventCount(ctx: Context): Int = read(ctx).lineSequence().count { it.isNotBlank() && !it.startsWith("#") }

    fun markCount(ctx: Context): Int = read(ctx).lineSequence().count { it.contains("| MARK |") }

    fun clear(ctx: Context) {
        file(ctx).delete()
        if (isOn(ctx)) { writeHeader(ctx); append(ctx, "START", "log cleared, still learning", "") }
    }

    fun logFile(ctx: Context): File = file(ctx)

    fun label(pkg: String): String = knownApps[pkg]?.let { "$it · $pkg" } ?: pkg

    // ---------------------------------------------------------------- ongoing notification

    fun showOngoing(ctx: Context, lastMark: String? = null) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Learning mode", NotificationManager.IMPORTANCE_LOW))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        fun act(what: String, req: Int) = PendingIntent.getBroadcast(
            ctx, req, Intent(ctx, LearningReceiver::class.java).setAction(LearningReceiver.MARK).putExtra("what", what), flags,
        )
        val open = PendingIntent.getActivity(
            ctx, NOTIF_ID, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "learning")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags,
        )
        val stop = PendingIntent.getBroadcast(ctx, NOTIF_ID + 9, Intent(ctx, LearningReceiver::class.java).setAction(LearningReceiver.STOP), flags)
        val text = (lastMark?.let { "Marked: $it · " } ?: "") + "Tap Paid or Cancelled right after each payment"
        val b = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Learning mode is on")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Paid ✓", act("paid", NOTIF_ID + 1))
            .addAction(0, "Cancelled / failed", act("cancelled", NOTIF_ID + 2))
            .addAction(0, "Stop", stop)
        try { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, b.build()) } catch (_: SecurityException) {}
    }
}

class LearningReceiver : BroadcastReceiver() {
    companion object {
        const val MARK = "com.viser.organiser.LEARN_MARK"
        const val STOP = "com.viser.organiser.LEARN_STOP"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            MARK -> Learning.mark(ctx, intent.getStringExtra("what") ?: "marked")
            STOP -> Learning.stop(ctx)
        }
    }
}

/** On/off switch for the "Did you just pay…?" popup (on by default once the service is enabled). */
object PayWatch {
    private const val PREFS = "paywatch"
    fun askEnabled(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("ask", true)
    fun setAsk(ctx: Context, on: Boolean) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("ask", on).apply()

    fun appLabel(ctx: Context, pkg: String): String = Learning.knownApps[pkg] ?: try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}

/**
 * Accessibility service. Only reads window-change events (which app, which screen class);
 * canRetrieveWindowContent is false in its config, so it cannot read what is on screen.
 * Feeds learning mode and the "Did you just pay…?" popup.
 */
class PayWatchService : AccessibilityService() {
    private var lastPkg = ""
    private var lastCls = ""
    private val detector = PayDetector()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)
    private lateinit var popup: PayPopup

    override fun onServiceConnected() {
        super.onServiceConnected()
        popup = PayPopup(this, scope)
        if (Learning.isOn(this)) Learning.append(this, "SERVICE", "connected", "")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        if (pkg == lastPkg && cls == lastCls) return
        lastPkg = pkg; lastCls = cls

        val learning = Learning.isOn(this)
        if (learning && !(pkg == "com.android.systemui" && cls.isEmpty())) Learning.append(this, "WIN", pkg, cls)

        val hit = detector.onWindow(pkg, cls, System.currentTimeMillis()) ?: return
        if (learning) Learning.append(this, "DETECT", hit.merchantPkg, "${hit.payPkg} · ${hit.reason}")
        if (!PayWatch.askEnabled(this)) return
        val merchant = PayWatch.appLabel(this, hit.merchantPkg)
        val payApp = PayWatch.appLabel(this, hit.payPkg)
        val repo = com.viser.organiser.data.Repo.get(this)
        scope.launch {
            val txn = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.popupTxn(merchant, payApp, hit.at) }
            if (::popup.isInitialized) popup.show(txn, payApp)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (::popup.isInitialized) popup.dismiss()
        scope.cancel()
        super.onDestroy()
    }
}
