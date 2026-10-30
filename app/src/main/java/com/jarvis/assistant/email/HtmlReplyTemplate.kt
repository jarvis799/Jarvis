package com.jarvis.assistant.email

/**
 * DEV STYLE — HTML auto-reply template engine (pure Kotlin, unit-testable).
 *
 * The user builds the reply design once in the Email sub agent screen; every
 * auto reply is then sent as multipart/alternative: plain text fallback plus
 * this HTML card. Placeholders: {name} {email} {subject} {reply}
 *
 * Incoming values and the AI draft are untrusted, so everything interpolated
 * is HTML-escaped before it reaches the document.
 */
object HtmlReplyTemplate {

    const val PLACEHOLDER_NAME = "{name}"
    const val PLACEHOLDER_EMAIL = "{email}"
    const val PLACEHOLDER_SUBJECT = "{subject}"
    const val PLACEHOLDER_REPLY = "{reply}"

    val STARTER: String = """<!DOCTYPE html>
<html>
<body style="margin:0;padding:24px;background:#F1F8E9;font-family:-apple-system,'Segoe UI',Roboto,Arial,sans-serif;color:#263238;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;margin:0 auto;background:#FFFFFF;border:1px solid #C8E6C9;border-radius:20px;">
    <tr>
      <td style="padding:26px 24px 6px 24px;">
        <div style="font-size:11px;letter-spacing:2px;text-transform:uppercase;color:#7CB342;">Jarvis Auto Reply</div>
        <h1 style="margin:10px 0 0 0;font-size:20px;line-height:1.3;color:#1B2A1C;">Hi {name},</h1>
      </td>
    </tr>
    <tr>
      <td style="padding:8px 24px 24px 24px;font-size:15px;line-height:1.6;color:#37474F;">{reply}</td>
    </tr>
    <tr>
      <td style="padding:18px 24px 24px 24px;border-top:1px solid #E0F2F1;font-size:12px;line-height:1.5;color:#78909C;">
        Reply to this email if you need anything else.<br/>
        Sent automatically by Jarvis on behalf of the account owner.
      </td>
    </tr>
  </table>
</body>
</html>"""

    fun defaultTemplate(): String = STARTER

    /** True when the saved template already looks like a full HTML document. */
    fun looksLikeHtml(template: String): Boolean {
        val trimmed = template.trimStart()
        return trimmed.startsWith("<") && trimmed.contains("<body", ignoreCase = true)
    }

    /** Fills every placeholder. Blank name falls back to "there". */
    fun render(template: String, name: String, email: String, subject: String, reply: String): String {
        val base = if (template.isBlank()) STARTER else template
        val safeName = name.trim().ifBlank { "there" }
        return base
            .replace(PLACEHOLDER_NAME, escape(safeName))
            .replace(PLACEHOLDER_EMAIL, escape(email.trim()))
            .replace(PLACEHOLDER_SUBJECT, escape(subject.trim()))
            .replace(PLACEHOLDER_REPLY, paragraphHtml(reply))
    }

    /** Reply body (AI draft or plain template) → safe HTML paragraphs. */
    fun paragraphHtml(reply: String): String {
        val clean = reply.trim()
        if (clean.isEmpty()) return ""
        return clean
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { block ->
                "<p style=\"margin:0 0 14px 0;\">" + escape(block).replace("\n", "<br/>") + "</p>"
            }
    }

    fun escape(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    /** Plain-text alternative for mail clients that refuse HTML. */
    fun stripToPlainText(html: String): String {
        var text = html
        text = text.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
        text = text.replace(Regex("(?i)</(p|div|tr|h1|h2|h3|li)>"), "\n")
        text = text.replace(Regex("<[^>]+>"), " ")
        text = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return text
            .split("\n")
            .joinToString("\n") { line -> line.replace(Regex("[ \\t]+"), " ").trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}