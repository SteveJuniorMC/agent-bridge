package com.agentbridge.db

import android.content.ContentValues
import android.content.Context

class TaskDao(context: Context) {

    private val db = AgentDatabase.getInstance(context)

    fun createTask(description: String): Long {
        val values = ContentValues().apply {
            put("description", description)
            put("status", "pending")
            put("created_at", System.currentTimeMillis())
            put("steps_taken", 0)
        }
        return db.writableDatabase.insert("tasks", null, values)
    }

    fun updateTaskStatus(taskId: Long, status: String, result: String? = null) {
        val values = ContentValues().apply {
            put("status", status)
            if (result != null) put("result", result)
            if (status == "completed" || status == "failed") {
                put("completed_at", System.currentTimeMillis())
            }
        }
        db.writableDatabase.update("tasks", values, "id = ?", arrayOf(taskId.toString()))
    }

    fun incrementSteps(taskId: Long) {
        db.writableDatabase.execSQL(
            "UPDATE tasks SET steps_taken = steps_taken + 1 WHERE id = ?",
            arrayOf(taskId)
        )
    }

    fun getTask(taskId: Long): Task? {
        db.readableDatabase.query(
            "tasks", null, "id = ?", arrayOf(taskId.toString()), null, null, null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return Task(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                    status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                    result = cursor.getString(cursor.getColumnIndexOrThrow("result")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    completedAt = if (cursor.isNull(cursor.getColumnIndexOrThrow("completed_at"))) null
                                  else cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
                    stepsTaken = cursor.getInt(cursor.getColumnIndexOrThrow("steps_taken"))
                )
            }
        }
        return null
    }

    fun getPendingTasks(): List<Task> {
        return getTasksByStatus("pending")
    }

    fun getRunningTasks(): List<Task> {
        return getTasksByStatus("running")
    }

    fun getAllTasks(limit: Int = 50): List<Task> {
        val tasks = mutableListOf<Task>()
        db.readableDatabase.query(
            "tasks", null, null, null, null, null, "created_at DESC", limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tasks.add(cursorToTask(cursor))
            }
        }
        return tasks
    }

    private fun getTasksByStatus(status: String): List<Task> {
        val tasks = mutableListOf<Task>()
        db.readableDatabase.query(
            "tasks", null, "status = ?", arrayOf(status), null, null, "created_at ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tasks.add(cursorToTask(cursor))
            }
        }
        return tasks
    }

    private fun cursorToTask(cursor: android.database.Cursor): Task {
        return Task(
            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
            status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
            result = cursor.getString(cursor.getColumnIndexOrThrow("result")),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            completedAt = if (cursor.isNull(cursor.getColumnIndexOrThrow("completed_at"))) null
                          else cursor.getLong(cursor.getColumnIndexOrThrow("completed_at")),
            stepsTaken = cursor.getInt(cursor.getColumnIndexOrThrow("steps_taken"))
        )
    }

    // Monitored apps
    fun getMonitoredApps(): List<MonitoredApp> {
        val apps = mutableListOf<MonitoredApp>()
        db.readableDatabase.query(
            "monitored_apps", null, null, null, null, null, "display_name ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                apps.add(MonitoredApp(
                    packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
                    displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
                    enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) == 1
                ))
            }
        }
        return apps
    }

    fun setMonitoredApp(packageName: String, displayName: String, enabled: Boolean) {
        val values = ContentValues().apply {
            put("package_name", packageName)
            put("display_name", displayName)
            put("enabled", if (enabled) 1 else 0)
        }
        db.writableDatabase.insertWithOnConflict("monitored_apps", null, values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun isAppMonitored(packageName: String): Boolean {
        db.readableDatabase.query(
            "monitored_apps", arrayOf("enabled"), "package_name = ? AND enabled = 1",
            arrayOf(packageName), null, null, null
        ).use { return it.count > 0 }
    }

    // Task logs
    fun addLog(taskId: Long, step: Int, type: String, content: String) {
        val values = ContentValues().apply {
            put("task_id", taskId)
            put("step", step)
            put("type", type)
            put("content", content)
            put("timestamp", System.currentTimeMillis())
        }
        db.writableDatabase.insert("task_logs", null, values)
    }

    fun getTaskLogs(taskId: Long): List<TaskLog> {
        val logs = mutableListOf<TaskLog>()
        db.readableDatabase.query(
            "task_logs", null, "task_id = ?", arrayOf(taskId.toString()),
            null, null, "step ASC, id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                logs.add(TaskLog(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    taskId = cursor.getLong(cursor.getColumnIndexOrThrow("task_id")),
                    step = cursor.getInt(cursor.getColumnIndexOrThrow("step")),
                    type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
                    content = cursor.getString(cursor.getColumnIndexOrThrow("content")),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
                ))
            }
        }
        return logs
    }

    data class Task(
        val id: Long, val description: String, val status: String,
        val result: String?, val createdAt: Long, val completedAt: Long?,
        val stepsTaken: Int
    )

    data class TaskLog(
        val id: Long, val taskId: Long, val step: Int,
        val type: String, val content: String, val timestamp: Long
    )

    data class MonitoredApp(
        val packageName: String, val displayName: String, val enabled: Boolean
    )
}
