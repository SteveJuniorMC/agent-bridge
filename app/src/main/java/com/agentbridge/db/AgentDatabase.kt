package com.agentbridge.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AgentDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "agent_bridge.db"
        private const val DB_VERSION = 3

        @Volatile
        private var instance: AgentDatabase? = null

        fun getInstance(context: Context): AgentDatabase {
            return instance ?: synchronized(this) {
                instance ?: AgentDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                contact TEXT NOT NULL,
                platform TEXT NOT NULL,
                direction TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                task_id INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE contacts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT,
                platform TEXT,
                notes TEXT,
                first_seen INTEGER,
                last_seen INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT NOT NULL,
                status TEXT DEFAULT 'pending',
                result TEXT,
                created_at INTEGER,
                completed_at INTEGER,
                steps_taken INTEGER DEFAULT 0
            )
        """)

        db.execSQL("""
            CREATE TABLE audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                tool_name TEXT NOT NULL,
                parameters TEXT,
                result TEXT,
                task_id INTEGER,
                blocked INTEGER DEFAULT 0
            )
        """)

        db.execSQL("""
            CREATE TABLE notes (
                key TEXT PRIMARY KEY,
                value TEXT,
                updated_at INTEGER
            )
        """)

        db.execSQL("""
            CREATE TABLE monitored_apps (
                package_name TEXT PRIMARY KEY,
                display_name TEXT,
                enabled INTEGER DEFAULT 1
            )
        """)

        db.execSQL("""
            CREATE TABLE task_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                task_id INTEGER NOT NULL,
                step INTEGER NOT NULL,
                type TEXT NOT NULL,
                content TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY(task_id) REFERENCES tasks(id)
            )
        """)

        db.execSQL("""
            CREATE TABLE contact_links (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name1 TEXT NOT NULL,
                platform1 TEXT NOT NULL,
                name2 TEXT NOT NULL,
                platform2 TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """)

        // Indexes
        db.execSQL("CREATE INDEX idx_messages_contact ON messages(contact, platform)")
        db.execSQL("CREATE INDEX idx_messages_timestamp ON messages(timestamp)")
        db.execSQL("CREATE INDEX idx_tasks_status ON tasks(status)")
        db.execSQL("CREATE INDEX idx_audit_timestamp ON audit_log(timestamp)")
        db.execSQL("CREATE INDEX idx_task_logs_task ON task_logs(task_id, step)")
        db.execSQL("CREATE INDEX idx_contact_links_1 ON contact_links(name1, platform1)")
        db.execSQL("CREATE INDEX idx_contact_links_2 ON contact_links(name2, platform2)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS task_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id INTEGER NOT NULL,
                    step INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    content TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    FOREIGN KEY(task_id) REFERENCES tasks(id)
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_task_logs_task ON task_logs(task_id, step)")
        }
        if (oldVersion < 3) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS contact_links (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name1 TEXT NOT NULL,
                    platform1 TEXT NOT NULL,
                    name2 TEXT NOT NULL,
                    platform2 TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_contact_links_1 ON contact_links(name1, platform1)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_contact_links_2 ON contact_links(name2, platform2)")
        }
    }
}
