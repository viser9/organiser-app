package com.viser.organiser.watch

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Finds every payment app on the phone instead of relying on a fixed list:
 *  - UPI apps: anything that can open a upi://pay link (GPay, PhonePe, Paytm, BHIM, CRED, slice,
 *    bank apps with UPI, super.money, Jupiter, Fi, Amazon Pay, WhatsApp…);
 *  - bank and wallet apps, recognised by name;
 *  - apps the owner adds by hand.
 * The owner can switch any of them off.
 *
 * Shopping and chat apps that also take UPI (Amazon, Flipkart, WhatsApp…) are "dual": their
 * payment notifications are read, but they're treated as the app you pay *in*, not the app you pay *with*,
 * so opening them never triggers "Did you just pay…?".
 */
object PayApps {
    private const val PREFS = "payapps"
    private const val KEY_DETECTED = "detected"
    private const val KEY_ADDED = "added"
    private const val KEY_OFF = "off"

    enum class Kind(val label: String) { UPI("UPI app"), BANK("Bank / wallet app"), ADDED("Added by you") }

    data class PayApp(val pkg: String, val label: String, val kind: Kind, val dual: Boolean, val on: Boolean)

    /** Apps that take UPI but are mainly for shopping, chatting or browsing. */
    private val KNOWN_DUAL = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "in.amazon.mShop.android.shopping", "com.flipkart.android",
        "com.myntra.android", "com.meesho.supply", "com.jio.jiomart", "com.instagram.android",
        "com.facebook.katana", "com.truecaller", "in.swiggy.android", "com.application.zomato",
        "com.grofers.customerapp", "com.zeptoconsumerapp", "com.bigbasket.mobileapp", "com.ubercab",
        "com.olacabs.customer", "com.rapido.passenger", "com.bt.bms", "com.makemytrip", "com.goibibo",
        "com.android.chrome", "com.google.android.googlequicksearchbox",
    )

    private val BANKISH = Regex("(?i)\\bbank\\b|banking|yono|imobile|payzapp|wallet|\\bupi\\b|\\bpay\\b|mobikwik|freecharge|netbank|finserv|money")
    private val BANKISH_PKG = Regex("(?i)bank|upi|wallet|payzapp|mobikwik|freecharge|paisa|phonepe|paytm")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile private var cache: List<PayApp>? = null

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg.substringAfterLast('.') }

    /** Scans installed apps. Cheap enough to run when Organiser opens and when the watcher starts. */
    fun refresh(ctx: Context): List<PayApp> {
        val pm = ctx.packageManager
        val own = ctx.packageName
        val upi = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay?pa=test@upi&pn=Test&am=1")), 0)
            .map { it.activityInfo.packageName }.toSet() - own

        val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }.toSet() - own
        val bankish = launchable.filter { pkg ->
            pkg !in upi && (BANKISH_PKG.containsMatchIn(pkg) || BANKISH.containsMatchIn(label(ctx, pkg)))
        }.toSet()

        val p = prefs(ctx)
        val added = p.getStringSet(KEY_ADDED, emptySet())!!.filter { it in launchable || it in upi }.toSet()
        val off = p.getStringSet(KEY_OFF, null) ?: setOf("com.whatsapp", "com.whatsapp.w4b", "com.android.chrome")

        val list = (upi.map { it to Kind.UPI } + bankish.map { it to Kind.BANK } + added.filter { it !in upi && it !in bankish }.map { it to Kind.ADDED })
            .map { (pkg, kind) -> PayApp(pkg, label(ctx, pkg), kind, isDual(pm, pkg), pkg !in off) }
            .sortedWith(compareBy({ !it.on }, { it.dual }, { it.label.lowercase() }))
        p.edit().putStringSet(KEY_DETECTED, list.map { it.pkg }.toSet()).apply()
        cache = list
        return list
    }

    private fun isDual(pm: PackageManager, pkg: String): Boolean {
        if (pkg in KNOWN_DUAL) return true
        return try {
            val cat = pm.getApplicationInfo(pkg, 0).category
            cat == ApplicationInfo.CATEGORY_SOCIAL || cat == ApplicationInfo.CATEGORY_GAME ||
                cat == ApplicationInfo.CATEGORY_VIDEO || cat == ApplicationInfo.CATEGORY_NEWS
        } catch (e: Exception) { false }
    }

    fun all(ctx: Context): List<PayApp> = cache ?: refresh(ctx)

    /** Apps whose notifications may describe a payment (all switched-on apps, including dual ones). */
    fun watchedForNotifications(ctx: Context): Map<String, String> =
        all(ctx).filter { it.on }.associate { it.pkg to it.label }

    /** Apps you pay *with* — used by the "Did you just pay…?" detector. */
    fun paymentAppsForDetector(ctx: Context): Set<String> =
        all(ctx).filter { it.on && !it.dual }.map { it.pkg }.toSet()

    fun setOn(ctx: Context, pkg: String, on: Boolean) {
        val p = prefs(ctx)
        val off = (p.getStringSet(KEY_OFF, null) ?: setOf("com.whatsapp", "com.whatsapp.w4b", "com.android.chrome")).toMutableSet()
        if (on) off -= pkg else off += pkg
        p.edit().putStringSet(KEY_OFF, off).apply()
        refresh(ctx)
    }

    fun add(ctx: Context, pkg: String) {
        val p = prefs(ctx)
        p.edit().putStringSet(KEY_ADDED, p.getStringSet(KEY_ADDED, emptySet())!! + pkg).apply()
        setOn(ctx, pkg, true)
    }

    /** Every launchable app, for the "add an app" picker. */
    fun launchableApps(ctx: Context): List<Pair<String, String>> {
        val pm = ctx.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }.distinct().filter { it != ctx.packageName }
            .map { it to label(ctx, it) }.sortedBy { it.second.lowercase() }
    }
}
