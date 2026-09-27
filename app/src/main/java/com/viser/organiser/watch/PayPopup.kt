package com.viser.organiser.watch

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.viser.organiser.MainActivity
import com.viser.organiser.R
import com.viser.organiser.data.Repo
import com.viser.organiser.data.Txn
import com.viser.organiser.util.rupees
import com.viser.organiser.util.timeHm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The "Did you just pay …?" card, drawn at the top of the screen as an accessibility overlay
 * (no "display over other apps" permission needed). Yes → opens the review sheet for that entry,
 * where the owner picks spend/income, category, note and split.
 */
class PayPopup(private val service: AccessibilityService, private val scope: CoroutineScope) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var job: Job? = null

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), service.resources.displayMetrics).toInt()

    fun show(txn: Txn, payAppLabel: String, preview: Boolean = false) {
        dismiss()
        val ctx: Context = service
        val sans = runCatching { ResourcesCompat.getFont(ctx, R.font.onest) }.getOrNull() ?: Typeface.DEFAULT
        val serif = runCatching { ResourcesCompat.getFont(ctx, R.font.newsreader) }.getOrNull() ?: Typeface.SERIF

        fun text(size: Float, color: Int, bold: Boolean = false, face: Typeface = sans) = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size); setTextColor(color)
            typeface = Typeface.create(face, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        fun pill(label: String, dark: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
            text = label; gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = Typeface.create(sans, Typeface.BOLD)
            setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF151515.toInt())
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(if (dark) 0xFF151515.toInt() else 0xFFFFFFFF.toInt())
                if (!dark) setStroke(dp(1), 0xFFD4D1CB.toInt())
            }
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(46)
            setOnClickListener { onClick() }
        }

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(0xFFF7F6F3.toInt()) }
            elevation = dp(12).toFloat()
        }
        val title = text(16f, 0xFF151515.toInt(), bold = true).apply { text = "Did you just pay ${txn.merchant}?" }
        val sub = text(12f, 0xFF5E5C57.toInt()).apply { text = (if (preview) "Preview · " else "") + "via $payAppLabel · ${timeHm(txn.occurredAt)}" }
        val amount = text(28f, 0xFF151515.toInt(), bold = true, face = serif)
        val amountHint = text(12f, 0xFF1F5C40.toInt())
        fun renderAmount(t: Txn) {
            if (t.amount > 0) {
                amount.text = rupees(t.amount)
                amountHint.text = "Matched bank SMS" + if (t.accountLast4.isNotEmpty()) " · a/c ••${t.accountLast4}" else ""
                amountHint.setTextColor(0xFF1F5C40.toInt())
            } else {
                amount.text = "₹ —"
                amountHint.text = "Waiting for the bank SMS… you can also enter it yourself"
                amountHint.setTextColor(0xFF5E5C57.toInt())
            }
        }
        renderAmount(txn)

        val repo = Repo.get(ctx)
        val buttons = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val lp = { w: Float -> LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w).apply { marginEnd = dp(8) } }
        buttons.addView(pill("Yes, review", true) pill@{
            dismiss()
            if (preview) return@pill
            service.startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MainActivity.EXTRA_ROUTE, "review").putExtra(MainActivity.EXTRA_ID, txn.id),
            )
        }, lp(1.3f))
        buttons.addView(pill("No", false) pill@{
            dismiss()
            if (preview) return@pill
            repo.scope.launch { repo.db.txns().get(txn.id)?.let { repo.ignore(it) } }
        }, lp(0.8f))
        buttons.addView(pill("Later", false) { dismiss() }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.9f))

        card.addView(title)
        card.addView(sub, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        card.addView(amount, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        card.addView(amountHint)
        card.addView(buttons, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            x = 0; y = dp(36)
            horizontalMargin = 0f
        }
        val frame = LinearLayout(ctx).apply { setPadding(dp(12), 0, dp(12), dp(12)); addView(card, LinearLayout.LayoutParams(-1, -2)) }
        try {
            wm.addView(frame, params)
            view = frame
        } catch (e: Exception) {
            com.viser.organiser.reminders.Notifier.txn(ctx, txn) // fall back to a notification
            return
        }

        // Keep the amount live until the SMS lands; auto-hide after a while (the entry stays in Review).
        if (preview) { job = scope.launch { delay(15_000); dismiss() }; return }
        job = scope.launch {
            var t = txn
            repeat(POLLS) {
                delay(2000)
                val fresh = withContext(Dispatchers.IO) { repo.db.txns().get(txn.id) } ?: return@repeat
                if (fresh.status != "pending") { dismiss(); return@launch }
                if (fresh.amount != t.amount) { t = fresh; renderAmount(fresh) }
            }
            dismiss()
        }
    }

    fun dismiss() {
        job?.cancel(); job = null
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    companion object { private const val POLLS = 30 } // ~60 s on screen
}
