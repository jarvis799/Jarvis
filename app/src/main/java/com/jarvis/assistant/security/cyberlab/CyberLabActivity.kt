package com.jarvis.assistant.security.cyberlab

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.util.ThemeManager
import com.jarvis.assistant.R
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat

/**
 * MODULE 3 UI — the Cybersecurity Lab.
 * Learning Mode, Local Audit Mode and Authorized Assessment Mode, plus the
 * skill pack loader and the creator approval flow. Findings persist locally
 * through FindingsStore; evidence is redacted and secrets are never stored.
 */
class CyberLabActivity : AppCompatActivity() {

    private lateinit var findings: FindingsStore
    private lateinit var skills: SkillPackLoader
    private lateinit var research: ResearchEngine
    private lateinit var content: LinearLayout
    private var mode = "LEARNING"
    private var rootView: LinearLayout? = null
    private val tabButtons = ArrayList<Button>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        findings = FindingsStore(this)
        skills = SkillPackLoader(this)
        research = ResearchEngine(this)
        buildUi()
        render()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(28), dp(22), dp(20)) }
        rootView = root
        root.addView(TextView(this).apply { text = "CYBER LAB — JARVIS APEX"; textSize = 20f; setTypeface(typeface, android.graphics.Typeface.BOLD); letterSpacing = 0.06f })
        root.addView(TextView(this).apply {
            text = "Learning / Local Audit / Authorized Assessment. Evidence-driven, defensive only."
            textSize = 12f; setPadding(0, 4, 0, 12)
        })
        root.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)); background = ContextCompat.getDrawable(this@CyberLabActivity, R.drawable.bg_accent_flow) })
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Learning", "Local Audit", "Assessment").forEach { label ->
            tabs.addView(Button(this).apply { tabButtons.add(this)
                text = label
                setOnClickListener { mode = label.uppercase().replace(" ", "_"); render() }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
        root.addView(tabs)
        root.addView(Button(this).apply {
            text = "Self-Improvement Research (ask Creator)"
            setOnClickListener { showResearch() }
        })
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 8) }
        root.addView(ScrollView(this).apply {
            addView(content)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(Button(this).apply { text = "Skill Packs"; setOnClickListener { showSkillPacks() } })
        bottom.addView(Button(this).apply { text = "Export"; setOnClickListener { exportFindings() } })
        bottom.addView(Button(this).apply { text = "Clear"; setOnClickListener { confirmClear() } })
        root.addView(bottom)
        skinTree(root)
        setContentView(root)
    }

        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun attrColor(attr: Int, fallback: Int): Int {
        val typed = android.util.TypedValue()
        return if (theme.resolveAttribute(attr, typed, true)) {
            if (typed.resourceId != 0) ContextCompat.getColor(this, typed.resourceId) else typed.data
        } else fallback
    }

    private fun updateTabs() {
        for (button in tabButtons) {
            val key = button.text.toString().uppercase().replace(" ", "_")
            button.alpha = if (key == mode) 1f else 0.6f
        }
    }

    /** One shared glass skin so every screen of the lab matches the rest of Jarvis. */
    private fun skinTree(view: View) {
        val primary = attrColor(R.attr.themeTextPrimary, android.graphics.Color.WHITE)
        val muted = attrColor(R.attr.themeTextMuted, primary)
        when {
            view is Button -> {
                view.background = ContextCompat.getDrawable(this, R.drawable.bg_jarvis_action_card)
                view.setTextColor(primary)
                view.isAllCaps = false
                view.textSize = 13f
                view.setPadding(dp(14), dp(10), dp(14), dp(10))
                val params = view.layoutParams as? LinearLayout.LayoutParams
                if (params != null) {
                    params.height = LinearLayout.LayoutParams.WRAP_CONTENT
                    params.marginStart = dp(4)
                    params.marginEnd = dp(4)
                }
            }

            view is EditText -> {
                view.background = ContextCompat.getDrawable(this, R.drawable.bg_input_field)
                view.setTextColor(primary)
                view.setHintTextColor(muted)
                view.setPadding(dp(14), dp(12), dp(14), dp(12))
            }

            view is TextView -> {
                view.setTextColor(primary)
                if (view.isClickable) {
                    view.background = ContextCompat.getDrawable(this, R.drawable.bg_jarvis_action_card)
                    view.setPadding(dp(14), dp(12), dp(14), dp(12))
                }
            }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) skinTree(view.getChildAt(index))
        }
    }

    private fun render() {
        content.removeAllViews()
        when (mode) {
            "LEARNING" -> renderLearning()
            "LOCAL_AUDIT" -> renderAudit()
            else -> renderAssessment()
        }
        renderFindings()
        skinTree(content)
        updateTabs()
    }

    private fun renderLearning() {
        content.addView(TextView(this).apply {
            text = "Tap a topic to learn it (defensive security fundamentals)."
            textSize = 13f; setPadding(0, 8, 0, 8)
        })
        CyberLabEngine.learningTopics.forEach { (title, body) ->
            content.addView(TextView(this).apply {
                text = "• " + title
                textSize = 15f
                setPadding(8, 16, 8, 16)
                setOnClickListener {
                    MaterialAlertDialogBuilder(this@CyberLabActivity)
                        .setTitle(title).setMessage(body)
                        .setPositiveButton("Close", null)
                        .setNeutralButton("Copy") { _, _ -> copy("[" + title + "]\n" + body) }
                        .show()
                }
            })
        }
    }

    private fun renderAudit() {
        content.addView(Button(this).apply {
            text = "Audit AndroidManifest XML"
            setOnClickListener { auditDialog("Paste your AndroidManifest.xml here", false) }
        })
        content.addView(Button(this).apply {
            text = "Audit a source code file"
            setOnClickListener { auditDialog("File path (any label)", true) }
        })
        content.addView(TextView(this).apply {
            text = "Static, offline pattern checks. Nothing is uploaded, nothing is executed."
            textSize = 12f; setPadding(0, 8, 0, 0)
        })
    }

    private fun auditDialog(pathHint: String, isSource: Boolean) {
        val ctx = this
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 8) }
        val path = EditText(ctx).apply { hint = pathHint; box.addView(this) }
        val code = EditText(ctx).apply {
            hint = if (isSource) "Paste the source code" else "Paste the manifest XML"
            minLines = 6; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            box.addView(this)
        }
        MaterialAlertDialogBuilder(ctx).setTitle(if (isSource) "Source audit" else "Manifest audit")
            .setView(ScrollView(ctx).apply { addView(box) })
            .setPositiveButton("Audit") { _, _ ->
                val text = code.text.toString()
                if (text.isBlank()) { toast("Nothing to audit."); return@setPositiveButton }
                val component = if (isSource) path.text.toString().trim().ifBlank { "pasted file" } else "AndroidManifest.xml"
                Thread {
                    val res = if (isSource) CyberLabEngine.auditSource(component, text) else CyberLabEngine.auditManifest(text)
                    runOnUiThread {
                        res.forEach { findings.addOrUpdate(it) }
                        toast(if (res.isEmpty()) "No known insecure patterns found - not proof of security, only that these checks passed."
                        else res.size.toString() + " finding(s) added.")
                        render()
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun renderAssessment() {
        content.addView(TextView(this).apply {
            text = "Authorized Assessment Mode: ONE passive HTTPS request against a single host you own or are authorized to test. Creator master key required. No scanning, no crawling, no exploitation."
            textSize = 12f; setPadding(0, 8, 0, 12)
        })
        content.addView(Button(this).apply {
            text = "New Authorized Check"
            setOnClickListener { showAssessmentDialog() }
        })
        content.addView(Button(this).apply {
            text = "Gateway Audit Log"
            setOnClickListener {
                MaterialAlertDialogBuilder(this@CyberLabActivity).setTitle("Execution Gateway audit")
                    .setMessage(ExecutionGateway.auditLog().ifBlank { "No commands audited yet." })
                    .setPositiveButton("Close", null).show()
            }
        })
    }

    private fun showAssessmentDialog() {
        val ctx = this
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 8) }
        val host = EditText(ctx).apply { hint = "Host you own (example.com)"; box.addView(this) }
        val key = EditText(ctx).apply {
            hint = "Creator master key"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            box.addView(this)
        }
        val confirm = CheckBox(ctx).apply {
            text = "I own this host or have explicit written authorization to test it"
            box.addView(this)
        }
        MaterialAlertDialogBuilder(ctx).setTitle("Authorized Assessment")
            .setView(ScrollView(ctx).apply { addView(box) })
            .setPositiveButton("Run passive check") { _, _ ->
                if (!confirm.isChecked) { toast("Authorization confirmation required."); return@setPositiveButton }
                toast("Running one passive request...")
                val h = host.text.toString()
                val k = key.text.toString()
                Thread {
                    val res = CyberLabEngine.authorizedHeaderCheck(h, k)
                    runOnUiThread {
                        toast(res.message)
                        res.findings.forEach { findings.addOrUpdate(it) }
                        render()
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun renderFindings() {
        val list = findings.all()
        content.addView(TextView(this).apply {
            text = "Findings (" + list.size + ") — severity: " +
                list.count { it.severity == "CRITICAL" } + " critical, " +
                list.count { it.severity == "HIGH" } + " high"
            textSize = 13f; setPadding(0, 16, 0, 8)
        })
        list.sortedBy { f -> FindingsStore.SEVERITY_ORDER.indexOf(f.severity).let { if (it < 0) 99 else it } }
            .forEach { f -> content.addView(findingRow(f)) }
    }

    private fun findingRow(f: FindingsStore.Finding): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16)
            setBackgroundColor(0x1A808080)
        }
        row.addView(TextView(this).apply {
            text = "[" + f.severity + "/" + f.confidence + "] " + f.title
            textSize = 14f
        })
        row.addView(TextView(this).apply {
            text = f.component + " · status: " + f.status
            textSize = 11f; setPadding(0, 2, 0, 0)
        })
        row.setOnClickListener {
            MaterialAlertDialogBuilder(this@CyberLabActivity)
                .setTitle("[" + f.severity + "] " + f.title)
                .setMessage("Component: " + f.component + "\nCategory: " + f.category + "\nStatus: " + f.status +
                    "\n\nEvidence: " + f.evidence + "\n\nImpact: " + f.impact +
                    "\n\nFix: " + f.remediation + "\n\nVerify: " + f.verification)
                .setPositiveButton("Mark status") { _, _ -> cycleStatus(f) }
                .setNeutralButton("Copy") { _, _ -> copy(findings.exportText()) }
                .setNegativeButton("Delete") { _, _ -> findings.delete(f.id); render() }
                .show()
        }
        return row
    }

    private fun cycleStatus(f: FindingsStore.Finding) {
        val idx = FindingsStore.STATUSES.indexOf(f.status)
        val next = FindingsStore.STATUSES[((idx + 1) % FindingsStore.STATUSES.size + FindingsStore.STATUSES.size) % FindingsStore.STATUSES.size]
        findings.updateStatus(f.id, next, "manual")
        render()
    }

    private fun showResearch() {
        val proposal = research.draftProposal(findings.all())
        if (proposal == null) {
            toast("No evidence-backed improvement available yet - run audits or terminal work first.")
            return
        }
        val name = runCatching { JarvisApp.instance.preferences.userName }.getOrDefault("")
        MaterialAlertDialogBuilder(this).setTitle("Improvement Proposal")
            .setMessage(research.approvalLine(name, proposal))
            .setPositiveButton("Approve") { _, _ ->
                research.setApproval(proposal.id, true)
                toast("Approved. I will sandbox-test first and report honestly.")
            }
            .setNegativeButton("Decline") { _, _ ->
                research.setApproval(proposal.id, false)
                toast("Declined - nothing will change.")
            }
            .show()
    }

    private fun showSkillPacks() {
        val packs = skills.packs()
        val names = packs.map { it.name + " v" + it.version + " (" + it.skills.size + " skills)" }.toTypedArray()
        MaterialAlertDialogBuilder(this).setTitle("Skill Packs (data only - hot reload)")
            .setItems(if (names.isEmpty()) arrayOf("No packs installed yet") else names) { _, which ->
                if (packs.isEmpty()) return@setItems
                val p = packs[which]
                MaterialAlertDialogBuilder(this).setTitle(p.name + " v" + p.version)
                    .setMessage("Integrity: " + p.sha256.take(16) + "…\nSkills:\n" + p.skills.joinToString("\n") { "• " + it.title })
                    .setPositiveButton("Roll back") { _, _ -> toast(skills.rollback(p.id).second) }
                    .setNegativeButton("Delete") { _, _ -> skills.delete(p.id); toast("Pack removed.") }
                    .show()
            }
            .setNeutralButton("Install (paste JSON)") { _, _ -> showInstallPackDialog() }
            .setPositiveButton("Close", null).show()
    }

    private fun showInstallPackDialog() {
        val ctx = this
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 8) }
        val json = EditText(ctx).apply {
            hint = "{\"name\":\"My pack\",\"skills\":[{\"title\":\"...\",\"prompt\":\"...\",\"checklist\":[\"...\"]}]}"
            minLines = 6; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            box.addView(this)
        }
        MaterialAlertDialogBuilder(ctx).setTitle("Install Skill Pack (JSON, validated)")
            .setView(ScrollView(ctx).apply { addView(box) })
            .setPositiveButton("Install") { _, _ ->
                val (pack, msg) = skills.importPack(json.text.toString())
                toast(msg)
                pack?.let { render() }
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun exportFindings() {
        copy(findings.exportText())
        toast("Findings report copied to clipboard.")
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this).setTitle("Clear all security history?")
            .setMessage("All findings and their history will be deleted from this device.")
            .setPositiveButton("Delete") { _, _ -> findings.clear(); render() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun copy(text: String) {
        runCatching {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("jarvis-cyber", text))
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
