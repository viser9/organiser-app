package com.viser.organiser.backup

import android.content.Context
import android.net.Uri
import com.viser.organiser.data.Contribution
import com.viser.organiser.data.Goal
import com.viser.organiser.data.Item
import com.viser.organiser.data.MerchantRule
import com.viser.organiser.data.Repo
import com.viser.organiser.data.Section
import com.viser.organiser.data.Txn
import com.viser.organiser.reminders.ReminderScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Versioned JSON backup, encrypted with a password (PBKDF2 + AES-GCM).
 * File layout: "ORG1" + salt(16) + iv(12) + ciphertext.
 */
object Backup {
    private const val MAGIC = "ORG1"
    private const val SCHEMA = 1

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(k, "AES")
    }

    fun encrypt(plain: ByteArray, password: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return MAGIC.toByteArray() + salt + iv + c.doFinal(plain)
    }

    fun decrypt(data: ByteArray, password: String): ByteArray {
        require(data.size > 32 && String(data.copyOfRange(0, 4)) == MAGIC) { "Not an Organiser backup file" }
        val salt = data.copyOfRange(4, 20)
        val iv = data.copyOfRange(20, 32)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return c.doFinal(data, 32, data.size - 32)
    }

    // ----------------------------------------------------------------- JSON mapping
    private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else getLong(k)
    private fun JSONObject.optDoubleOrNull(k: String): Double? = if (isNull(k) || !has(k)) null else getDouble(k)

    private fun Item.json() = JSONObject().apply {
        put("id", id); put("type", type); put("title", title); put("body", body); put("url", url)
        put("domain", domain); put("lat", lat ?: JSONObject.NULL); put("lng", lng ?: JSONObject.NULL)
        put("sections", sections); put("area", area); put("status", status); put("pinned", pinned)
        put("checklist", checklist); put("priority", priority); put("remindAt", remindAt ?: JSONObject.NULL)
        put("repeat", repeat); put("done", done); put("doneAt", doneAt ?: JSONObject.NULL)
        put("createdAt", createdAt); put("updatedAt", updatedAt); put("deletedAt", deletedAt ?: JSONObject.NULL)
    }

    private fun item(o: JSONObject) = Item(
        id = o.getString("id"), type = o.getString("type"), title = o.optString("title"), body = o.optString("body"),
        url = o.optString("url"), domain = o.optString("domain"), lat = o.optDoubleOrNull("lat"), lng = o.optDoubleOrNull("lng"),
        sections = o.optString("sections"), area = o.optString("area"), status = o.optString("status"),
        pinned = o.optBoolean("pinned"), checklist = o.optBoolean("checklist"), priority = o.optInt("priority", 1),
        remindAt = o.optLongOrNull("remindAt"), repeat = o.optString("repeat"), done = o.optBoolean("done"),
        doneAt = o.optLongOrNull("doneAt"), createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
        deletedAt = o.optLongOrNull("deletedAt"),
    )

    private fun Txn.json() = JSONObject().apply {
        put("id", id); put("kind", kind); put("amount", amount); put("category", category); put("merchant", merchant)
        put("note", note); put("mode", mode); put("source", source); put("bank", bank); put("accountLast4", accountLast4)
        put("upiRef", upiRef); put("smsHash", smsHash ?: JSONObject.NULL); put("occurredAt", occurredAt); put("status", status)
        put("createdAt", createdAt); put("updatedAt", updatedAt); put("deletedAt", deletedAt ?: JSONObject.NULL)
        put("splitWith", splitWith); put("myShare", myShare ?: JSONObject.NULL)
    }

    private fun txn(o: JSONObject) = Txn(
        id = o.getString("id"), kind = o.getString("kind"), amount = o.getLong("amount"), category = o.optString("category"),
        merchant = o.optString("merchant"), note = o.optString("note"), mode = o.optString("mode"), source = o.optString("source"),
        bank = o.optString("bank"), accountLast4 = o.optString("accountLast4"), upiRef = o.optString("upiRef"),
        smsHash = if (o.isNull("smsHash")) null else o.optString("smsHash"), occurredAt = o.getLong("occurredAt"),
        status = o.getString("status"), createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
        deletedAt = o.optLongOrNull("deletedAt"), splitWith = o.optString("splitWith"), myShare = o.optLongOrNull("myShare"),
    )

    private fun Goal.json() = JSONObject().apply {
        put("id", id); put("name", name); put("target", target); put("targetDate", targetDate ?: JSONObject.NULL)
        put("hitNotified", hitNotified); put("purchased", purchased); put("createdAt", createdAt)
        put("updatedAt", updatedAt); put("deletedAt", deletedAt ?: JSONObject.NULL)
    }

    private fun goal(o: JSONObject) = Goal(
        id = o.getString("id"), name = o.getString("name"), target = o.getLong("target"),
        targetDate = o.optLongOrNull("targetDate"), hitNotified = o.optBoolean("hitNotified"),
        purchased = o.optBoolean("purchased"), createdAt = o.getLong("createdAt"), updatedAt = o.getLong("updatedAt"),
        deletedAt = o.optLongOrNull("deletedAt"),
    )

    suspend fun export(ctx: Context, uri: Uri, password: String) {
        val db = Repo.get(ctx).db
        val root = JSONObject().apply {
            put("app", "organiser"); put("schema", SCHEMA); put("exportedAt", System.currentTimeMillis())
            put("items", JSONArray().apply { db.items().all().forEach { put(it.json()) } })
            put("txns", JSONArray().apply { db.txns().all().forEach { put(it.json()) } })
            put("goals", JSONArray().apply { db.goals().allGoals().forEach { put(it.json()) } })
            put("contributions", JSONArray().apply {
                db.goals().allContributions().forEach {
                    put(JSONObject().apply {
                        put("id", it.id); put("goalId", it.goalId); put("amount", it.amount); put("at", it.at)
                        put("deletedAt", it.deletedAt ?: JSONObject.NULL)
                    })
                }
            })
            put("sections", JSONArray().apply {
                db.sections().all().forEach { put(JSONObject().apply { put("name", it.name); put("color", it.color); put("sort", it.sort) }) }
            })
            put("rules", JSONArray().apply {
                db.rules().all().forEach { put(JSONObject().apply { put("k", it.merchantKey); put("c", it.category) }) }
            })
        }
        val bytes = encrypt(root.toString().toByteArray(), password)
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
        ctx.getSharedPreferences("backup", Context.MODE_PRIVATE).edit().putLong("last", System.currentTimeMillis()).apply()
    }

    /** Merges a backup into the database; for the same record the newer updatedAt wins. */
    suspend fun import(ctx: Context, uri: Uri, password: String): Int {
        val raw = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val root = JSONObject(String(decrypt(raw, password)))
        val db = Repo.get(ctx).db
        var n = 0

        fun arr(k: String): List<JSONObject> = root.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } } ?: emptyList()

        val existingItems = db.items().all().associateBy { it.id }
        val items = arr("items").map(::item).filter { (existingItems[it.id]?.updatedAt ?: -1) < it.updatedAt }
        db.items().upsertAll(items); n += items.size

        val existingTx = db.txns().all().associateBy { it.id }
        val txns = arr("txns").map(::txn).filter { (existingTx[it.id]?.updatedAt ?: -1) < it.updatedAt }
        txns.forEach { db.txns().upsert(it) }; n += txns.size

        val existingGoals = db.goals().allGoals().associateBy { it.id }
        val goals = arr("goals").map(::goal).filter { (existingGoals[it.id]?.updatedAt ?: -1) < it.updatedAt }
        db.goals().upsertAllGoals(goals); n += goals.size

        db.goals().upsertAllContributions(arr("contributions").map {
            Contribution(it.getString("id"), it.getString("goalId"), it.getLong("amount"), it.getLong("at"), it.optLongOrNull("deletedAt"))
        })
        db.sections().insertAll(arr("sections").map { Section(it.getString("name"), it.getLong("color"), it.optInt("sort")) })
        db.rules().upsertAll(arr("rules").map { MerchantRule(it.getString("k"), it.getString("c")) })

        ReminderScheduler.rescheduleAll(ctx)
        return n
    }

    fun lastBackup(ctx: Context): Long = ctx.getSharedPreferences("backup", Context.MODE_PRIVATE).getLong("last", 0L)
}
