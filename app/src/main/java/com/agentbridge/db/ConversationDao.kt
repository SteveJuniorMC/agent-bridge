package com.agentbridge.db

import android.content.ContentValues
import android.content.Context

class ConversationDao(context: Context) {

    private val db = AgentDatabase.getInstance(context)

    fun saveMessage(contact: String, platform: String, direction: String, content: String, taskId: Long? = null): Long {
        val values = ContentValues().apply {
            put("contact", contact)
            put("platform", platform)
            put("direction", direction)
            put("content", content)
            put("timestamp", System.currentTimeMillis())
            if (taskId != null) put("task_id", taskId)
        }
        return db.writableDatabase.insert("messages", null, values)
    }

    fun getConversation(contact: String, platform: String? = null, limit: Int = 20): List<Message> {
        val messages = mutableListOf<Message>()
        val selection = if (platform != null) "contact = ? AND platform = ?" else "contact = ?"
        val args = if (platform != null) arrayOf(contact, platform) else arrayOf(contact)

        db.readableDatabase.query(
            "messages", null, selection, args,
            null, null, "timestamp DESC", limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                messages.add(Message(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    contact = cursor.getString(cursor.getColumnIndexOrThrow("contact")),
                    platform = cursor.getString(cursor.getColumnIndexOrThrow("platform")),
                    direction = cursor.getString(cursor.getColumnIndexOrThrow("direction")),
                    content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    taskId = if (cursor.isNull(cursor.getColumnIndexOrThrow("task_id"))) null
                             else cursor.getLong(cursor.getColumnIndexOrThrow("task_id"))
                ))
            }
        }
        return messages.reversed()
    }

    fun getRecentContacts(limit: Int = 20): List<ContactSummary> {
        val contacts = mutableListOf<ContactSummary>()
        db.readableDatabase.rawQuery("""
            SELECT contact, platform, content, MAX(timestamp) as last_time, COUNT(*) as msg_count
            FROM messages
            GROUP BY contact, platform
            ORDER BY last_time DESC
            LIMIT ?
        """, arrayOf(limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                contacts.add(ContactSummary(
                    contact = cursor.getString(0),
                    platform = cursor.getString(1),
                    lastMessage = cursor.getString(2),
                    lastTimestamp = cursor.getLong(3),
                    messageCount = cursor.getInt(4)
                ))
            }
        }
        return contacts
    }

    fun saveContact(name: String, phone: String? = null, platform: String? = null, notes: String? = null): Long {
        val existing = findContact(name, platform)
        if (existing != null) {
            val values = ContentValues().apply {
                put("last_seen", System.currentTimeMillis())
                if (notes != null) put("notes", notes)
                if (phone != null) put("phone", phone)
            }
            db.writableDatabase.update("contacts", values, "id = ?", arrayOf(existing.id.toString()))
            return existing.id
        }

        val values = ContentValues().apply {
            put("name", name)
            put("phone", phone)
            put("platform", platform)
            put("notes", notes)
            put("first_seen", System.currentTimeMillis())
            put("last_seen", System.currentTimeMillis())
        }
        return db.writableDatabase.insert("contacts", null, values)
    }

    private fun findContact(name: String, platform: String?): Contact? {
        val selection = if (platform != null) "name = ? AND platform = ?" else "name = ?"
        val args = if (platform != null) arrayOf(name, platform) else arrayOf(name)

        db.readableDatabase.query("contacts", null, selection, args, null, null, null, "1").use { cursor ->
            if (cursor.moveToFirst()) {
                return Contact(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    phone = cursor.getString(cursor.getColumnIndexOrThrow("phone")),
                    platform = cursor.getString(cursor.getColumnIndexOrThrow("platform")),
                    notes = cursor.getString(cursor.getColumnIndexOrThrow("notes"))
                )
            }
        }
        return null
    }

    // Contact linking
    fun linkContacts(name1: String, platform1: String, name2: String, platform2: String) {
        // Check if link already exists in either direction
        val existing = db.readableDatabase.rawQuery(
            """SELECT id FROM contact_links
               WHERE (name1 = ? AND platform1 = ? AND name2 = ? AND platform2 = ?)
                  OR (name1 = ? AND platform1 = ? AND name2 = ? AND platform2 = ?)""",
            arrayOf(name1, platform1, name2, platform2, name2, platform2, name1, platform1)
        ).use { it.count > 0 }

        if (existing) return

        val values = ContentValues().apply {
            put("name1", name1)
            put("platform1", platform1)
            put("name2", name2)
            put("platform2", platform2)
            put("created_at", System.currentTimeMillis())
        }
        db.writableDatabase.insert("contact_links", null, values)
    }

    data class LinkedContact(val name: String, val platform: String)

    fun getLinkedContacts(name: String, platform: String): List<LinkedContact> {
        val linked = mutableListOf<LinkedContact>()
        db.readableDatabase.rawQuery(
            """SELECT name2, platform2 FROM contact_links WHERE name1 = ? AND platform1 = ?
               UNION
               SELECT name1, platform1 FROM contact_links WHERE name2 = ? AND platform2 = ?""",
            arrayOf(name, platform, name, platform)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                linked.add(LinkedContact(cursor.getString(0), cursor.getString(1)))
            }
        }
        return linked
    }

    fun saveNote(key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
            put("updated_at", System.currentTimeMillis())
        }
        db.writableDatabase.insertWithOnConflict("notes", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getNote(key: String): String? {
        db.readableDatabase.query("notes", arrayOf("value"), "key = ?", arrayOf(key), null, null, null).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    fun logAudit(toolName: String, parameters: String?, result: String?, taskId: Long? = null, blocked: Boolean = false): Long {
        val values = ContentValues().apply {
            put("timestamp", System.currentTimeMillis())
            put("tool_name", toolName)
            put("parameters", parameters)
            put("result", result)
            if (taskId != null) put("task_id", taskId)
            put("blocked", if (blocked) 1 else 0)
        }
        return db.writableDatabase.insert("audit_log", null, values)
    }

    fun getRecentAuditLog(limit: Int = 20): List<AuditEntry> {
        val entries = mutableListOf<AuditEntry>()
        db.readableDatabase.query(
            "audit_log", null, null, null, null, null, "timestamp DESC", limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries.add(AuditEntry(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    toolName = cursor.getString(cursor.getColumnIndexOrThrow("tool_name")),
                    parameters = cursor.getString(cursor.getColumnIndexOrThrow("parameters")),
                    result = cursor.getString(cursor.getColumnIndexOrThrow("result")),
                    taskId = if (cursor.isNull(cursor.getColumnIndexOrThrow("task_id"))) null
                             else cursor.getLong(cursor.getColumnIndexOrThrow("task_id")),
                    blocked = cursor.getInt(cursor.getColumnIndexOrThrow("blocked")) == 1
                ))
            }
        }
        return entries
    }

    fun getTodayStats(): Stats {
        val dayStart = System.currentTimeMillis() - (System.currentTimeMillis() % 86400000)
        var messagesHandled = 0
        var tasksCompleted = 0

        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE direction = 'outgoing' AND timestamp >= ?",
            arrayOf(dayStart.toString())
        ).use { if (it.moveToFirst()) messagesHandled = it.getInt(0) }

        db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM tasks WHERE status = 'completed' AND completed_at >= ?",
            arrayOf(dayStart.toString())
        ).use { if (it.moveToFirst()) tasksCompleted = it.getInt(0) }

        return Stats(messagesHandled, tasksCompleted)
    }

    data class Message(
        val id: Long, val contact: String, val platform: String,
        val direction: String, val content: String, val timestamp: Long, val taskId: Long?
    )

    data class Contact(
        val id: Long, val name: String, val phone: String?,
        val platform: String?, val notes: String?
    )

    data class ContactSummary(
        val contact: String, val platform: String, val lastMessage: String,
        val lastTimestamp: Long, val messageCount: Int
    )

    data class AuditEntry(
        val id: Long, val timestamp: Long, val toolName: String,
        val parameters: String?, val result: String?, val taskId: Long?, val blocked: Boolean
    )

    data class Stats(val messagesHandled: Int, val tasksCompleted: Int)
}
