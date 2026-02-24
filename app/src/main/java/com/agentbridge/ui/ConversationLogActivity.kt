package com.agentbridge.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.agentbridge.R
import com.agentbridge.db.ConversationDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationLogActivity : AppCompatActivity() {

    private lateinit var layoutContactList: LinearLayout
    private lateinit var conversationDao: ConversationDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversation_log)

        val toolbar = findViewById<Toolbar>(R.id.toolbarConversations)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Conversations"

        conversationDao = ConversationDao(this)
        layoutContactList = findViewById(R.id.layoutConversationList)

        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
    }

    private fun loadContacts() {
        layoutContactList.removeAllViews()

        val contacts = conversationDao.getRecentContacts()
        if (contacts.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No conversations yet"
                textSize = 14f
                setTextColor(0xFF999999.toInt())
                setPadding(0, 32, 0, 32)
                gravity = android.view.Gravity.CENTER
            }
            layoutContactList.addView(emptyView)
            return
        }

        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        for (contact in contacts) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
                setBackgroundColor(0xFFFAFAFA.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8 }
            }

            // Contact name and platform
            val tvHeader = TextView(this).apply {
                text = "${contact.contact}  (${contact.platform})"
                textSize = 15f
                setTextColor(0xFF212121.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            // Last message preview
            val tvPreview = TextView(this).apply {
                text = contact.lastMessage.take(120)
                textSize = 13f
                setTextColor(0xFF666666.toInt())
                maxLines = 2
                setPadding(0, 4, 0, 4)
            }

            // Metadata
            val tvMeta = TextView(this).apply {
                val time = dateFormat.format(Date(contact.lastTimestamp))
                text = "$time  |  ${contact.messageCount} messages"
                textSize = 11f
                setTextColor(0xFF999999.toInt())
            }

            card.addView(tvHeader)
            card.addView(tvPreview)
            card.addView(tvMeta)
            layoutContactList.addView(card)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
