package com.viser.organiser.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
fun now(): Long = System.currentTimeMillis()

object ItemType {
    const val NOTE = "note"
    const val LINK = "link"
    const val PLACE = "place"
    const val TODO = "todo"
}

object Repeat {
    const val NONE = ""
    const val DAILY = "daily"
    const val WEEKDAYS = "weekdays"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
    val all = listOf(NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY)
    fun label(r: String) = when (r) {
        DAILY -> "Daily"; WEEKDAYS -> "Weekdays"; WEEKLY -> "Weekly"; MONTHLY -> "Monthly"; else -> "None"
    }
}

/**
 * One saved thing: a note, link, place or to-do. Any item can carry a reminder.
 * `sections` is a '|'-delimited list like "|Food|Places|" so LIKE '%|Food|%' works.
 */
@Entity(tableName = "items", indices = [Index("type"), Index("remindAt")])
data class Item(
    @PrimaryKey val id: String = newId(),
    val type: String,
    val title: String = "",
    val body: String = "",
    val url: String = "",
    val domain: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val sections: String = "",
    val area: String = "",
    val status: String = "",
    val pinned: Boolean = false,
    val checklist: Boolean = false,
    val priority: Int = 1, // 0 low, 1 normal, 2 high
    val remindAt: Long? = null,
    val repeat: String = Repeat.NONE,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val deletedAt: Long? = null,
) {
    val sectionList: List<String> get() = sections.split('|').filter { it.isNotBlank() }

    companion object {
        fun encodeSections(list: Collection<String>): String =
            if (list.isEmpty()) "" else list.joinToString("|", prefix = "|", postfix = "|")
    }
}

@Entity(tableName = "sections")
data class Section(
    @PrimaryKey val name: String,
    val color: Long,
    val sort: Int = 0,
)

object TxnKind {
    const val EXPENSE = "expense"
    const val INCOME = "income"
    const val TRANSFER = "transfer"
}

object TxnStatus {
    const val PENDING = "pending"
    const val CONFIRMED = "confirmed"
    const val IGNORED = "ignored"
}

/** Amounts are stored in paise to avoid floating point errors. */
@Entity(
    tableName = "txns",
    indices = [Index("occurredAt"), Index("status"), Index(value = ["smsHash"], unique = true)],
)
data class Txn(
    @PrimaryKey val id: String = newId(),
    val kind: String,
    val amount: Long,
    val category: String = "Others",
    val merchant: String = "",
    val note: String = "",
    val mode: String = "UPI",
    val source: String = "manual", // sms | manual
    val bank: String = "",
    val accountLast4: String = "",
    val upiRef: String = "",
    val smsHash: String? = null,
    val occurredAt: Long = now(),
    val status: String = TxnStatus.CONFIRMED,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val deletedAt: Long? = null,
    /** '|'-delimited names this was split with, e.g. "|Aaquib|Riya|". Empty = not split. */
    @ColumnInfo(defaultValue = "") val splitWith: String = "",
    /** The owner's own part when split (paise); null = the whole amount is theirs. */
    val myShare: Long? = null,
) {
    val people: List<String> get() = splitWith.split('|').filter { it.isNotBlank() }
    val isSplit: Boolean get() = people.isNotEmpty()
    /** What counts toward the owner's spend / income. */
    val effective: Long get() = myShare ?: amount
}

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val target: Long, // paise
    val targetDate: Long? = null,
    val hitNotified: Boolean = false,
    val purchased: Boolean = false,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val deletedAt: Long? = null,
)

/** Money set aside for (positive) or withdrawn from (negative) a goal. */
@Entity(tableName = "contributions", indices = [Index("goalId"), Index("at")])
data class Contribution(
    @PrimaryKey val id: String = newId(),
    val goalId: String,
    val amount: Long,
    val at: Long = now(),
    val deletedAt: Long? = null,
)

/** Remembered merchant → category (learned from confirmations). */
@Entity(tableName = "rules")
data class MerchantRule(
    @PrimaryKey val merchantKey: String,
    val category: String,
)

object Categories {
    val expense = listOf(
        "Food delivery", "Groceries", "Dining out", "Shopping", "Transport", "Fuel",
        "Bills & utilities", "Rent", "Subscriptions", "Entertainment", "Health",
        "Travel", "Education", "Gifts", "Others",
    )
    val income = listOf("Salary", "Freelance", "Refund", "Cashback", "Other income")

    fun initials(c: String): String = when (c) {
        "Food delivery" -> "Fd"; "Groceries" -> "Gr"; "Dining out" -> "Di"; "Shopping" -> "Sh"
        "Transport" -> "Tr"; "Fuel" -> "Fu"; "Bills & utilities" -> "Bi"; "Rent" -> "Re"
        "Subscriptions" -> "Su"; "Entertainment" -> "En"; "Health" -> "He"; "Travel" -> "Tv"
        "Education" -> "Ed"; "Gifts" -> "Gi"
        else -> if (c in income) "In" else c.take(2)
    }
}
