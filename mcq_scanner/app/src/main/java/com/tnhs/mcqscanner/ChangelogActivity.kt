package com.tnhs.mcqscanner

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ChangelogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_changelog)

        val body = findViewById<android.widget.TextView>(R.id.tvChangelogBody)
        body.text = buildString {
            Changelog.entries.forEach { entry ->
                append("v${entry.version}  •  ${entry.date}\n")
                entry.notes.forEach { note -> append("   — $note\n") }
                append("\n")
            }
        }
    }
}
