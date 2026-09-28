package com.viser.organiser.watch

/**
 * Spots "went from an app to a payment app and back" from window changes alone
 * (package + screen class name — never screen text).
 *
 * Tuned on the owner's learning log (Samsung, Android 16):
 *  - slice shows a transaction-status screen after paying (…transactionstatus…TransactionHostActivity);
 *  - Swiggy moves to TrackOrderActivity after a successful payment and back to the cart when cancelled;
 *  - Google Pay (Flutter) reports the same screen name for everything, so it relies on the merchant side.
 */
class PayDetector(
    /** Apps you pay *with*; a function so newly installed / switched-off apps take effect immediately. */
    private val paymentAppsProvider: () -> Set<String> = { DEFAULT_PAYMENT_APPS },
    private val ownPackage: String = "com.viser.organiser",
) {
    /** [standalone] = the payment app was opened on its own (QR scan, send to a contact) — no merchant app. */
    data class Detection(val merchantPkg: String, val payPkg: String, val at: Long, val reason: String, val standalone: Boolean = false)

    private data class Session(
        val merchant: String,
        val payApp: String,
        val start: Long,
        /** The merchant was already on a tracking/success screen when we left it (e.g. checking an order). */
        val leftFromSuccess: Boolean,
        var sawStatus: Boolean = false,
        var returnedAt: Long? = null,
    )

    private var lastApp: String? = null
    private var lastCls: String = ""
    private var lastAppAt = 0L
    private var session: Session? = null
    private var standalonePay: String? = null
    private var standaloneStart = 0L

    /** Brief system surfaces that pop up in the middle of a payment (keyboard, fingerprint, status bar). */
    private fun isTransient(pkg: String, cls: String): Boolean {
        val p = pkg.lowercase()
        return p in IGNORED || p.contains("inputmethod") || p.contains("keyboard") || p.contains("honeyboard") ||
            p.contains("biometric") || p.contains("permissioncontroller") || cls.contains("SoftInputWindow")
    }

    fun isIgnored(pkg: String, cls: String): Boolean {
        val p = pkg.lowercase()
        return pkg == ownPackage ||
            p in IGNORED ||
            p.contains("launcher") || p.contains("inputmethod") || p.contains("keyboard") || p.contains("honeyboard") ||
            p.contains("biometric") || p.contains("permissioncontroller") ||
            cls.contains("SoftInputWindow") || cls.contains("quickstep") || cls.contains("RecentsActivity")
    }

    /** Feed every window change; returns a detection when a payment very likely just happened. */
    fun onWindow(pkg: String, cls: String, t: Long): Detection? {
        val paymentApps = paymentAppsProvider()
        // Leaving a payment app that was opened on its own (home screen → GPay → home screen / another app)
        var standaloneHit: Detection? = null
        val sp = standalonePay
        if (sp != null && pkg != sp && !isTransient(pkg, cls)) {
            standalonePay = null
            if (t - standaloneStart >= STANDALONE_MIN_MS) {
                standaloneHit = Detection("", sp, standaloneStart, "stayed ${(t - standaloneStart) / 1000}s in a payment app opened on its own", standalone = true)
            }
        }
        if (isIgnored(pkg, cls)) return standaloneHit
        val s = session

        // drop stale sessions
        if (s != null) {
            val returned = s.returnedAt
            if (t - s.start > SESSION_MAX_MS || (returned != null && t - returned > AFTER_RETURN_MS)) session = null
        }

        if (pkg in paymentApps) {
            val cur = session
            if (cur == null || cur.payApp != pkg) {
                val merchant = lastApp
                session = if (merchant != null && merchant !in paymentApps && t - lastAppAt < MERCHANT_RECENT_MS) {
                    Session(merchant, pkg, t, leftFromSuccess = MERCHANT_SUCCESS_SCREEN.containsMatchIn(lastCls))
                } else null
            } else if (cur.returnedAt != null) {
                cur.returnedAt = null // went back into the payment app (retry)
            }
            session?.let { if (STATUS_SCREEN.containsMatchIn(cls)) it.sawStatus = true }
            if (session == null) {
                if (standalonePay != pkg) { standalonePay = pkg; standaloneStart = t }
            } else standalonePay = null
            return standaloneHit
        }

        // a normal app
        val cur = session
        var hit: Detection? = null
        if (cur != null) {
            if (pkg == cur.merchant) {
                if (cur.returnedAt == null) cur.returnedAt = t
                when {
                    cur.sawStatus -> hit = Detection(cur.merchant, cur.payApp, t, "payment app showed a status screen")
                    !cur.leftFromSuccess && MERCHANT_SUCCESS_SCREEN.containsMatchIn(cls) -> hit = Detection(cur.merchant, cur.payApp, t, "merchant moved to a success/tracking screen")
                }
                if (hit != null) session = null
            } else if (cur.returnedAt != null) {
                session = null // moved on to something else
            }
        }
        lastApp = pkg
        lastCls = cls
        lastAppAt = t
        return hit ?: standaloneHit
    }

    companion object {
        const val MERCHANT_RECENT_MS = 10 * 60_000L
        const val SESSION_MAX_MS = 10 * 60_000L
        const val AFTER_RETURN_MS = 90_000L
        /** Shorter stays in a payment app opened on its own are treated as just looking (balance, history). */
        const val STANDALONE_MIN_MS = 12_000L

        val DEFAULT_PAYMENT_APPS = setOf(
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",
            "net.one97.paytm",
            "in.org.npci.upiapp", // BHIM
            "com.dreamplug.androidapp", // CRED
            "indwin.c3.shareapp", // slice
            "com.sbi.lotusintouch", // SBI YONO
            "com.sbi.upi", // SBI Pay
            "com.snapwork.hdfc", // HDFC MobileBanking
            "com.hdfcbank.payzapp",
            "com.mobikwik_new",
            "com.freecharge.android",
            "com.axis.mobile",
            "com.csam.icici.bank.imobile",
        )

        private val IGNORED = setOf(
            "com.android.systemui", "android", "com.samsung.android.spay", "com.google.android.gms",
            "com.android.settings", "com.samsung.android.app.aodservice",
        )

        /** Payment-app screens that appear after a payment went through (slice: …transactionstatus.ui.TransactionHostActivity). */
        val STATUS_SCREEN = Regex("(?i)transactionstatus|txnstatus|paymentstatus|transactionhost|paymentsuccess|txnsuccess|receipt|paymentresult")

        /** Merchant screens that follow a successful payment (Swiggy: TrackOrderActivity). */
        val MERCHANT_SUCCESS_SCREEN = Regex("(?i)track|orderplaced|ordersuccess|orderconfirm|ordersummary|orderdetail|paymentsuccess|bookingconfirm|thankyou|thank_you|success")
    }
}
