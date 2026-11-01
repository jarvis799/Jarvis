package com.jarvis.assistant.backup

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.jarvis.assistant.R
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** DEV STYLE: in-app backup and restore for settings. No secret is ever written to the file. */
class BackupActivity : AppCompatActivity() {

    private lateinit var statusView: TextView

    private val createBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) writeBackup(uri)
    }

    private val chooseBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) confirmRestore(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }

        val title = TextView(this).apply {
            text = "Backup & Restore"
            textSize = 24f
            setTextColor(com.google.android.material.color.MaterialColors.getColor(
                root, com.google.android.material.R.attr.colorOnSurface
            ))
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Saves your settings, persona, voice style, orb and theme choices. " +
                "API keys, GitHub and email passwords and the creator key are never included."
            textSize = 13f
            setPadding(0, dp(10), 0, dp(24))
            setTextColor(com.google.android.material.color.MaterialColors.getColor(
                root, com.google.android.material.R.attr.colorOnSurfaceVariant
            ))
        }
        root.addView(subtitle)

        val backupButton = MaterialButton(this).apply {
            text = "Create backup file"
            isAllCaps = false
            setOnClickListener {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                createBackup.launch("jarvis-backup-$stamp.json")
            }
        }
        root.addView(
            backupButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val restoreButton = MaterialButton(this).apply {
            text = "Restore from backup"
            isAllCaps = false
            setOnClickListener {
                chooseBackup.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        }
        root.addView(
            restoreButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        )

        statusView = TextView(this).apply {
            text = "No backup made in this session."
            textSize = 13f
            gravity = Gravity.START
            setPadding(0, dp(22), 0, 0)
        }
        root.addView(statusView)

        val backButton = MaterialButton(this).apply {
            text = "Back"
            isAllCaps = false
            setOnClickListener { finish() }
        }
        root.addView(
            backButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(28) }
        )

        setContentView(root)
        ThemeManager.applyDynamicBackground(root)
    }

    private fun writeBackup(uri: Uri) {
        statusView.text = "Writing backup…"
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { BackupManager.export(applicationContext, uri) }
            }
            outcome.onSuccess { count ->
                statusView.text = "Backup saved • $count settings."
                Toast.makeText(this@BackupActivity, "Backup saved", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                statusView.text = "Backup failed: ${error.message}"
            }
        }
    }

    private fun confirmRestore(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Restore this backup?")
            .setMessage("Your current settings, persona, voice style and theme will be replaced by the ones in the file.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore") { _, _ -> restore(uri) }
            .show()
    }

    private fun restore(uri: Uri) {
        statusView.text = "Restoring…"
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { BackupManager.restore(applicationContext, uri) }
            }
            outcome.onSuccess { count ->
                statusView.text = "Restored • $count settings. Reopen Jarvis screens to see them."
                ThemeManager.applyTheme(this@BackupActivity)
                Toast.makeText(this@BackupActivity, "Settings restored", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                statusView.text = "Restore failed: ${error.message}"
            }
        }
    }
}