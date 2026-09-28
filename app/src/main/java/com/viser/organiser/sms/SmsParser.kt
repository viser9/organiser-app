package com.viser.organiser.sms

import com.viser.organiser.data.TxnKind
import java.security.MessageDigest
import java.util.Locale

/**
 * On-device parser for bank / card / UPI SMS (E-1).
 * Tuned for SBI (account + card), HDFC (card + account) and slice, with a generic fallback.
 * Only extracted fields are stored; the SMS text itself is not kept.
 */
object SmsParser {

    data class Parsed(
        val kind: String,
        val amount: Long, // paise
        val merchant: String,
        val bank: String,
        val accountLast4: String,
        val upiRef: String,
        val mode: String,
        val isCard: Boolean,
    )

    private val amountRx = Regex(
        """(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)|([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:rs\.?|inr)\b""",
        RegexOption.IGNORE_CASE,
    )
    // "debited by 349.0" (SBI UPI puts no currency before the amount)
    private val byAmountRx = Regex("""(?:debited|credited)\s+(?:by|with|for)\s+(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)

    private val refRx = Regex(
        """(?:upi\s*ref(?:erence)?(?:\s*no)?|ref(?:erence)?\s*(?:no|number|#)?|refno|rrn|utr|txn\s*id|transaction\s*id)\.?\s*[:\-]?\s*([0-9]{9,14})""",
        RegexOption.IGNORE_CASE,
    )
    private val last4Rx = Regex(
        """(?:a/c|ac|acct|account|card|cc)\s*(?:no\.?|number|ending(?:\s+with)?|end(?:ing)?)?\s*[:\-]?\s*(?:[x*•.]+\s*)?([0-9]{3,4})\b""",
        RegexOption.IGNORE_CASE,
    )
    private const val END = """(?:\s+on\s|\s+from\s|\s+via\s|\s+using\s|\s+with\s|\s+ref|\s+upi|\s+avl|\s+not you|\s*\(|\.\s|\.$|,|$)"""
    private const val NAME = """([A-Za-z0-9][A-Za-z0-9 &'._@*/\-]{1,40}?)"""
    private val IC = RegexOption.IGNORE_CASE

    /** (pattern, direction): 'o' = only for money out, 'i' = only for money in. */
    private val merchantRxs: List<Pair<Regex, Char>> = listOf(
        Regex("""\btrf to\s+(.+?)(?:\s+ref|\s+refno|\s+on\s|\.\s|$)""", IC) to 'o',
        Regex("""\bto vpa\s+([\w.\-]+@[\w.\-]+)""", IC) to 'o',
        Regex("""\b(?:at|@)\s+$NAME$END""", IC) to 'o',
        Regex("""\b(?:sent|paid|transferred)\s+to\s+$NAME$END""", IC) to 'o',
        Regex("""\bto\s+$NAME$END""", IC) to 'o',
        Regex("""\bfrom vpa\s+([\w.\-]+@[\w.\-]+)""", IC) to 'i',
        Regex("""\bfrom\s+$NAME$END""", IC) to 'i',
        Regex("""\bby\s+(?!rs|inr|₹|\d)$NAME$END""", IC) to 'i',
        Regex("""\btransfer from\s+$NAME$END""", IC) to 'i',
        Regex("""^$NAME\s+(?:paid|sent)\s+you\b""", IC) to 'i',
        Regex("""\b(?:neft|imps|rtgs)\s*(?:cr)?[\s\-]*$NAME(?:\.|$|\s+avl)""", IC) to 'i',
    )

    private val ignoreRx = Regex(
        """\botp\b|one[\s-]time\s+password|verification code|\bdo not share\b|will be debited|is due|due on|due date|min(?:imum)?\s+amount\s+due|total\s+amount\s+due|statement|requested\s+money|collect\s+request|has requested|\bfailed\b|\bdeclined\b|unsuccessful|could not be|pre-?approved|\boffer\b|cashback of up to|\bloan\b.*\beligible|\bapply now\b|reward points|mandate|autopay.*(?:set up|registered|created)|e-?mandate|\bpending\b|\bprocessing\b|in progress|scratch\s*card|\breward\b|you\s+won|\bcoupon\b|\breminder\b|pay\s+now|\brequest(?:ed|ing)?\s+(?:₹|rs|inr)""",
        RegexOption.IGNORE_CASE,
    )
    private val debitRx = Regex("""\b(debited|spent|sent|paid|withdrawn|purchase|transferred|txn of|transaction of|debit)\b""", RegexOption.IGNORE_CASE)
    private val paidYouRx = Regex("""\b(?:paid|sent)\s+you\b|\bto\s+you\b|\bmoney\s+received\b""", RegexOption.IGNORE_CASE)
    private val creditRx = Regex("""\b(credited|received|deposited|refund(?:ed)?|reversed|credit of)\b""", RegexOption.IGNORE_CASE)
    private val cardBillRx = Regex("""payment\s+(?:of\s+.*?)?(?:has been\s+)?received.*(?:credit\s*card|card\s+(?:ending|no|xx))|(?:credit\s*card|card)\s+(?:bill|payment)\s+(?:of|received)|towards\s+your\s+(?:\w+\s+)?credit\s*card|cc\s*payment|\bbillpay\b""", RegexOption.IGNORE_CASE)

    fun hash(sender: String, body: String, time: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("$sender|$body|${time / 60000}".toByteArray())
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }

    /** Bank SMS come from alphanumeric headers like VM-SBIUPI, AD-HDFCBK, JD-SLICEIT. */
    fun looksLikeBankSender(sender: String): Boolean {
        val s = sender.uppercase(Locale.ROOT)
        if (s.any { it.isLetter() }.not()) return false // plain phone numbers
        return true
    }

    fun bankOf(sender: String, body: String): String {
        val s = (sender + " " + body).uppercase(Locale.ROOT)
        return when {
            "SLICE" in s -> "slice"
            "HDFC" in s -> "HDFC"
            "SBI" in s || "STATE BANK" in s -> "SBI"
            "ICICI" in s -> "ICICI"
            "AXIS" in s -> "Axis"
            "KOTAK" in s -> "Kotak"
            else -> "Bank"
        }
    }

    fun parse(sender: String, rawBody: String): Parsed? {
        val body = rawBody.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (!looksLikeBankSender(sender)) return null
        if (ignoreRx.containsMatchIn(body)) return null

        val isDebit = debitRx.containsMatchIn(body)
        val isCredit = creditRx.containsMatchIn(body)
        if (!isDebit && !isCredit) return null

        val amount = extractAmount(body) ?: return null
        if (amount <= 0) return null

        val bank = bankOf(sender, body)
        val lower = body.lowercase(Locale.ROOT)
        val isCard = "card" in lower
        val cardBill = cardBillRx.containsMatchIn(body)

        // Decide direction from whichever keyword appears first; "debited ... credited to X" is a debit.
        val dIdx = debitRx.find(body)?.range?.first ?: Int.MAX_VALUE
        val cIdx = creditRx.find(body)?.range?.first ?: Int.MAX_VALUE
        var kind = if (dIdx <= cIdx) TxnKind.EXPENSE else TxnKind.INCOME
        if (paidYouRx.containsMatchIn(body)) kind = TxnKind.INCOME
        // "credited to your card" on a card = refund/reversal (income); card bill payments are transfers.
        if (cardBill) kind = TxnKind.TRANSFER

        val merchant = extractMerchant(body, kind)
        val ref = refRx.find(body)?.groupValues?.get(1).orEmpty()
        val last4 = last4Rx.find(body)?.groupValues?.get(1).orEmpty()
        val mode = when {
            "upi" in lower || "vpa" in lower || ref.isNotEmpty() && !isCard -> "UPI"
            isCard -> "Card"
            "atm" in lower || "withdrawn" in lower -> "Cash"
            else -> "Bank"
        }
        return Parsed(kind, amount, merchant, bank, last4, ref, mode, isCard)
    }

    private fun toPaise(s: String): Long? =
        s.replace(",", "").toBigDecimalOrNull()?.movePointRight(2)?.toLong()

    private fun extractAmount(body: String): Long? {
        // Prefer the amount right after debited/credited/spent, skip "Avl Bal"/"available limit" amounts.
        byAmountRx.find(body)?.let { return toPaise(it.groupValues[1]) }
        for (m in amountRx.findAll(body)) {
            val before = body.substring(0, m.range.first).takeLast(25).lowercase(Locale.ROOT)
            if (Regex("""(avl|avail|available|bal|balance|limit|lmt)\W*(bal|balance|limit|lmt)?\W*(is|:)?\W*$""").containsMatchIn(before)) continue
            val v = m.groupValues[1].ifEmpty { m.groupValues[2] }
            return toPaise(v)
        }
        return null
    }

    private fun extractMerchant(body: String, kind: String): String {
        val dir = if (kind == TxnKind.INCOME) 'i' else 'o'
        for ((rx, d) in merchantRxs) {
            if (d != dir) continue
            for (m in rx.findAll(body)) {
                val v = clean(m.groupValues[1])
                if (v.isNotBlank() && !isNoise(v)) return v
            }
        }
        return ""
    }

    private fun isNoise(v: String): Boolean {
        val l = v.lowercase(Locale.ROOT)
        return l.startsWith("your ") || l.startsWith("a/c") || l.startsWith("ac ") || l.startsWith("account") ||
            l.matches(Regex("""[x*\d\s]+""")) || l.startsWith("card") || l == "you" || l.startsWith("block")
    }

    private fun clean(s: String): String {
        var v = s.trim().trim('.', ',', '-', ':', ';')
        // VPA -> readable name: swiggy.stores@axl -> swiggy.stores
        if ('@' in v && ' ' !in v) v = v.substringBefore('@')
        v = v.replace(Regex("""\s{2,}"""), " ")
        return v.split(' ').joinToString(" ") { w ->
            if (w.length <= 3 && w.all { it.isUpperCase() }) w
            else w.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }.take(40)
    }

    /** Merchant -> default category (E-2, E-8 before learning). */
    fun guessCategory(merchant: String, kind: String): String {
        if (kind == TxnKind.INCOME) {
            val m = merchant.lowercase(Locale.ROOT)
            return when {
                "salary" in m || "sal " in m || "payroll" in m -> "Salary"
                "refund" in m -> "Refund"
                "cashback" in m -> "Cashback"
                else -> "Other income"
            }
        }
        val m = merchant.lowercase(Locale.ROOT)
        fun has(vararg k: String) = k.any { it in m }
        return when {
            has("swiggy", "zomato", "eatsure", "dominos", "domino", "faasos", "box8") -> "Food delivery"
            has("blinkit", "zepto", "bigbasket", "instamart", "dunzo", "jiomart", "dmart", "grofers", "more retail", "ratnadeep") -> "Groceries"
            has("uber", "ola", "rapido", "metro", "bmtc", "irctc", "namma yatri", "yulu", "redbus") -> "Transport"
            has("petrol", "fuel", "hpcl", "bpcl", "iocl", "indian oil", "shell", "bharat petroleum", "hp pay") -> "Fuel"
            has("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "croma", "reliance digital", "decathlon", "tata cliq") -> "Shopping"
            has("netflix", "spotify", "hotstar", "prime", "youtube", "apple", "google play", "jiocinema", "sonyliv") -> "Subscriptions"
            has("bookmyshow", "pvr", "inox", "district") -> "Entertainment"
            has("airtel", "jio", "vodafone", "vi ", "bescom", "bwssb", "tata power", "electricity", "gas", "broadband", "act fibernet", "recharge") -> "Bills & utilities"
            has("pharm", "apollo", "hospital", "clinic", "1mg", "netmeds", "medplus", "practo") -> "Health"
            has("makemytrip", "goibibo", "cleartrip", "indigo", "air india", "akasa", "oyo", "airbnb", "ixigo") -> "Travel"
            has("cafe", "restaurant", "brew", "bar ", "kitchen", "coffee", "starbucks", "third wave", "chai", "bistro", "hotel") -> "Dining out"
            has("rent", "nobroker") -> "Rent"
            has("udemy", "coursera", "unacademy", "byju", "school", "college") -> "Education"
            else -> "Others"
        }
    }

    fun merchantKey(merchant: String): String =
        merchant.lowercase(Locale.ROOT).replace(Regex("""[^a-z]"""), "").take(12)
}
