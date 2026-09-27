package com.viser.organiser

import com.viser.organiser.watch.PayDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Replays sequences from the owner's real learning log (2026-09-27, Samsung S24 / Android 16). */
class PayDetectorTest {
    private fun replay(lines: String): List<PayDetector.Detection> {
        val d = PayDetector()
        val out = mutableListOf<PayDetector.Detection>()
        lines.trim().lines().forEach { l ->
            val p = l.split(" | ").map { it.trim() }
            if (p.size < 4 || p[1] != "WIN") return@forEach
            val (hh, mm, ss) = p[0].substringAfter(' ').split(':')
            val t = ((hh.toInt() * 60 + mm.toInt()) * 60 * 1000L + (ss.toDouble() * 1000).toLong())
            d.onWindow(p[2], p[3], t)?.let { out += it }
        }
        return out
    }

    @Test fun googlePayCancelled_noPopup() {
        val r = replay("""
2026-09-27 16:15:03.943 | WIN | in.swiggy.android | android.widget.FrameLayout
2026-09-27 16:15:04.784 | WIN | in.swiggy.android | in.swiggy.android.swiggylynx.ui.LynxActivity
2026-09-27 16:15:11.385 | WIN | com.google.android.apps.nbu.paisa.user | android.widget.FrameLayout
2026-09-27 16:15:11.386 | WIN | com.google.android.apps.nbu.paisa.user | com.google.nbu.paisa.flutter.gpay.app.MainActivity
2026-09-27 16:15:11.681 | WIN | com.google.android.apps.nbu.paisa.user | android.widget.FrameLayout
2026-09-27 16:15:13.566 | WIN | com.android.systemui | android.view.View
2026-09-27 16:15:14.762 | WIN | in.swiggy.android | in.swiggy.android.swiggylynx.ui.LynxActivity
2026-09-27 16:15:15.191 | WIN | com.google.android.apps.nbu.paisa.user | android.widget.FrameLayout
2026-09-27 16:15:19.962 | WIN | com.sec.android.app.launcher | android.widget.FrameLayout
2026-09-27 16:15:20.088 | WIN | com.android.systemui | android.widget.FrameLayout
""")
        assertEquals(0, r.size)
    }

    @Test fun sliceCancelledAtPin_backToCart_noPopup() {
        val r = replay("""
2026-09-27 16:16:18.645 | WIN | in.swiggy.android | in.swiggy.android.foodcart.ViewCartLynxActivity
2026-09-27 16:16:25.502 | WIN | indwin.c3.shareapp | android.widget.FrameLayout
2026-09-27 16:16:25.502 | WIN | indwin.c3.shareapp | com.slice.android.main.SingleActivity
2026-09-27 16:16:25.699 | WIN | com.samsung.android.biometrics.app.setting | android.widget.FrameLayout
2026-09-27 16:16:26.931 | WIN | com.android.systemui | android.view.View
2026-09-27 16:16:27.142 | WIN | indwin.c3.shareapp | com.slice.android.mpin.interfaces.MpinHeadlessActivity
2026-09-27 16:16:27.437 | WIN | com.samsung.android.honeyboard | android.inputmethodservice.SoftInputWindow
2026-09-27 16:16:28.119 | WIN | com.android.systemui | android.view.View
2026-09-27 16:16:32.783 | WIN | com.samsung.android.spay | android.widget.FrameLayout
2026-09-27 16:16:33.354 | WIN | com.sec.android.app.launcher | com.android.quickstep.RecentsActivity
2026-09-27 16:16:33.831 | WIN | in.swiggy.android | in.swiggy.android.foodcart.ViewCartLynxActivity
2026-09-27 16:16:42.833 | WIN | com.android.systemui | android.widget.FrameLayout
2026-09-27 16:16:49.248 | WIN | in.swiggy.android | in.swiggy.android.foodcart.ViewCartLynxActivity
2026-09-27 16:16:50.163 | WIN | in.swiggy.android | in.swiggy.android.menupage.MenuV2Activity
""")
        assertEquals(0, r.size)
    }

    @Test fun slicePaid_popupOnceOnReturn() {
        val r = replay("""
2026-09-27 16:19:26.421 | WIN | in.swiggy.android | in.swiggy.android.swiggylynx.ui.LynxActivity
2026-09-27 16:19:59.296 | WIN | indwin.c3.shareapp | android.widget.FrameLayout
2026-09-27 16:19:59.332 | WIN | indwin.c3.shareapp | com.slice.android.main.SingleActivity
2026-09-27 16:19:59.799 | WIN | com.samsung.android.biometrics.app.setting | android.widget.FrameLayout
2026-09-27 16:20:06.804 | WIN | indwin.c3.shareapp | com.google.android.material.bottomsheet.BottomSheetDialog
2026-09-27 16:20:09.948 | WIN | indwin.c3.shareapp | com.sliceit.android.transactionstatus.ui.TransactionHostActivity
2026-09-27 16:20:14.566 | WIN | in.swiggy.android | in.swiggy.android.swiggylynx.ui.LynxActivity
2026-09-27 16:20:17.760 | WIN | com.android.systemui | android.widget.FrameLayout
2026-09-27 16:20:18.165 | WIN | in.swiggy.android | in.swiggy.android.track.activities.TrackOrderActivity
""")
        assertEquals(1, r.size)
        assertEquals("in.swiggy.android", r[0].merchantPkg)
        assertEquals("indwin.c3.shareapp", r[0].payPkg)
    }

    @Test fun googlePayPaid_detectedFromSwiggyTracking() {
        val r = replay("""
2026-09-27 17:00:00.000 | WIN | in.swiggy.android | in.swiggy.android.foodcart.ViewCartLynxActivity
2026-09-27 17:00:05.000 | WIN | com.google.android.apps.nbu.paisa.user | com.google.nbu.paisa.flutter.gpay.app.MainActivity
2026-09-27 17:00:20.000 | WIN | in.swiggy.android | in.swiggy.android.swiggylynx.ui.LynxActivity
2026-09-27 17:00:23.000 | WIN | in.swiggy.android | in.swiggy.android.track.activities.TrackOrderActivity
""")
        assertNotNull(r.singleOrNull())
    }

    @Test fun checkingGPayWhileTrackingAnOrder_noPopup() {
        val r = replay("""
2026-09-27 18:00:00.000 | WIN | in.swiggy.android | in.swiggy.android.track.activities.TrackOrderActivity
2026-09-27 18:00:05.000 | WIN | com.google.android.apps.nbu.paisa.user | com.google.nbu.paisa.flutter.gpay.app.MainActivity
2026-09-27 18:00:20.000 | WIN | in.swiggy.android | in.swiggy.android.track.activities.TrackOrderActivity
""")
        assertEquals(0, r.size)
    }

    @Test fun openingGPayFromHomeScreen_noPopup() {
        val r = replay("""
2026-09-27 19:00:00.000 | WIN | com.sec.android.app.launcher | com.sec.android.app.launcher.Launcher
2026-09-27 19:00:05.000 | WIN | com.google.android.apps.nbu.paisa.user | com.google.nbu.paisa.flutter.gpay.app.MainActivity
2026-09-27 19:00:30.000 | WIN | com.sec.android.app.launcher | com.sec.android.app.launcher.Launcher
""")
        assertNull(r.firstOrNull())
    }
}
