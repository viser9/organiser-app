package com.viser.organiser

import com.viser.organiser.data.TxnKind
import com.viser.organiser.reminders.TimeParser
import com.viser.organiser.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class SmsParserTest {
    private fun p(sender: String, body: String) = SmsParser.parse(sender, body)

    @Test fun sbiUpiDebit() {
        val r = p("VM-SBIUPI", "Dear UPI user A/C X4821 debited by 349.0 on date 27Sep26 trf to SWIGGY Refno 426512345678. If not u? call 1800111109. -SBI")!!
        assertEquals(TxnKind.EXPENSE, r.kind); assertEquals(34900L, r.amount); assertEquals("SBI", r.bank)
        assertEquals("Swiggy", r.merchant); assertEquals("426512345678", r.upiRef); assertEquals("4821", r.accountLast4)
        assertEquals("Food delivery", SmsParser.guessCategory(r.merchant, r.kind))
    }

    @Test fun sbiUpiCredit() {
        val r = p("VM-SBIUPI", "Dear SBI UPI User, ur A/cX4821 credited by Rs500 on 27Sep26 by  (Ref no 426598765432)")!!
        assertEquals(TxnKind.INCOME, r.kind); assertEquals(50000L, r.amount); assertEquals("426598765432", r.upiRef)
    }

    @Test fun sbiCard() {
        val r = p("AD-SBICRD", "Rs.1,249.00 spent on your SBI Credit Card ending 5678 at AMAZON on 27/09/26. Trxn. not done by you? Report at https://sbicard.com/Dispute")!!
        assertEquals(TxnKind.EXPENSE, r.kind); assertEquals(124900L, r.amount); assertEquals("Amazon", r.merchant)
        assertEquals("5678", r.accountLast4); assertEquals("Card", r.mode)
    }

    @Test fun hdfcCard() {
        val r = p("AD-HDFCBK", "Spent Rs.349.00 On HDFC Bank Card 1234 At SWIGGY On 2026-09-27:16:18:00 Not You? To Block+Reissue Call 18002586161/SMS BLOCK CC 1234 to 7308080808")!!
        assertEquals(TxnKind.EXPENSE, r.kind); assertEquals(34900L, r.amount); assertEquals("Swiggy", r.merchant); assertEquals("HDFC", r.bank)
    }

    @Test fun hdfcUpiOnCard() {
        val r = p("VM-HDFCBK", "Rs.212.00 debited from HDFC Bank Credit Card XX1234 to VPA uber.india@hdfcbank UPI Ref No 426512399999. Not You? Call 18002586161")!!
        assertEquals(21200L, r.amount); assertEquals("426512399999", r.upiRef)
        assertEquals("Transport", SmsParser.guessCategory(r.merchant, r.kind))
    }

    @Test fun hdfcAccountSent() {
        val r = p("VM-HDFCBK", "Sent Rs.150.00 From HDFC Bank A/C *9012 To RAPIDO On 27/09/26 Ref 426500011122 Not You? Call 18002586161/SMS BLOCK UPI to 7308080808")!!
        assertEquals(TxnKind.EXPENSE, r.kind); assertEquals(15000L, r.amount); assertEquals("Rapido", r.merchant)
    }

    @Test fun salaryCredit() {
        val r = p("VM-HDFCBK", "Update! INR 1,20,000.00 deposited in HDFC Bank A/c XX9012 on 01-SEP-26 for NEFT Cr-ACME TECH SALARY SEP.Avl bal INR 1,50,000.00")!!
        assertEquals(TxnKind.INCOME, r.kind); assertEquals(12000000L, r.amount)
        assertEquals("Salary", SmsParser.guessCategory(r.merchant, r.kind))
    }

    @Test fun slice() {
        val a = p("JD-SLICEIT", "Rs. 212 paid to Uber India from your slice account. UPI Ref: 426511112222")!!
        assertEquals(21200L, a.amount); assertEquals("slice", a.bank); assertEquals("Uber India", a.merchant)
        val b = p("JD-SLICEIT", "You spent ₹499 at Zepto using your slice card. Avl limit: ₹20,000")!!
        assertEquals(49900L, b.amount); assertEquals("Zepto", b.merchant)
        assertEquals("Groceries", SmsParser.guessCategory(b.merchant, b.kind))
    }

    @Test fun balanceIsNotTheAmount() {
        val r = p("VM-SBIINB", "Your A/c XX4821 is debited for Rs.60.00 on 27-09-26 for transfer to Chai Point. Avl Bal Rs.12,340.50 -SBI")!!
        assertEquals(6000L, r.amount)
    }

    @Test fun ignoresOtpPromoAndBillPayments() {
        assertNull(p("VM-SBIINB", "Your OTP for transaction of Rs.500 at AMAZON is 123456. Do not share."))
        assertNull(p("VM-HDFCBK", "Your HDFC Bank Credit Card statement: Total Amount Due Rs.12,000. Min amount due Rs.600, due on 5 Oct"))
        assertNull(p("AD-HDFCBK", "Get a pre-approved loan of Rs.5,00,000 today! Apply now"))
        assertNull(p("9876543210", "Sent Rs.100 to you")) // personal numbers are ignored
        assertEquals(TxnKind.TRANSFER, p("AD-SBICRD", "Payment of Rs.15,000 received towards your SBI Credit Card ending 5678. Thank you.")!!.kind)
    }
}

class TimeParserTest {
    private val now = LocalDateTime.of(2026, 9, 27, 15, 20) // a Sunday afternoon

    @Test fun tomorrowAt11am() {
        val f = TimeParser.parse("Call bank tomorrow 11am", now)!!
        assertEquals(LocalDateTime.of(2026, 9, 28, 11, 0), f.at)
    }

    @Test fun todayEvening() {
        assertEquals(LocalDateTime.of(2026, 9, 27, 19, 30), TimeParser.parse("gym at 7:30 pm", now)!!.at)
    }

    @Test fun weekdayName() {
        assertEquals(LocalDateTime.of(2026, 10, 3, 20, 0), TimeParser.parse("Try Toit sat 8pm", now)!!.at)
    }

    @Test fun dayOfMonth() {
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 0), TimeParser.parse("Pay rent on 1st", now)!!.at)
    }

    @Test fun pastTimeTodayRollsToTomorrow() {
        assertEquals(LocalDateTime.of(2026, 9, 28, 9, 0), TimeParser.parse("standup 9am", now)!!.at)
    }

    @Test fun nothingFound() {
        assertNull(TimeParser.parse("Buy milk", now))
        assertNotNull(TimeParser.parse("Buy milk tonight", now))
    }
}

class SplitTest {
    private val t = com.viser.organiser.data.Txn(kind = TxnKind.EXPENSE, amount = 90000L, category = "Dining out", merchant = "Toit")

    @Test fun splitEquallyWithTwoPeople() {
        val d = com.viser.organiser.ui.TxnDraft(TxnKind.EXPENSE, "Dining out", "Team dinner", true, listOf("Aaquib", "Riya"), "")
        val out = d.apply(t)
        assertEquals(30000L, out.myShare); assertEquals(30000L, out.effective)
        assertEquals(listOf("Aaquib", "Riya"), out.people); assertEquals("Team dinner", out.note)
    }

    @Test fun customShareAndIncomeSwitch() {
        val d = com.viser.organiser.ui.TxnDraft(TxnKind.INCOME, "Refund", "", true, listOf("Riya"), "200")
        val out = d.apply(t)
        assertEquals(TxnKind.INCOME, out.kind); assertEquals(20000L, out.effective)
    }

    @Test fun notSplitKeepsWholeAmount() {
        val out = com.viser.organiser.ui.TxnDraft(TxnKind.EXPENSE, "Dining out", "", false, listOf("Riya"), "").apply(t)
        assertNull(out.myShare); assertEquals(90000L, out.effective); assertEquals("", out.splitWith)
    }
}
