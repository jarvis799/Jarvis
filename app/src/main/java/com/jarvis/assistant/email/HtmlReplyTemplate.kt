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
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width,initial-scale=1"/>
  <title>Reply</title>
</head>
<body style="margin:0;padding:0;background:#EEF6EC;">
  <div style="display:none;max-height:0;overflow:hidden;opacity:0;">A quick reply from the DEV STYLE team.</div>
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#EEF6EC;padding:32px 14px;">
    <tr>
      <td align="center">
        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:600px;background:#FFFFFF;border-radius:24px;overflow:hidden;box-shadow:0 18px 48px rgba(22,54,24,0.14);font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
          <tr>
            <td style="background:linear-gradient(135deg,#1C1C1C 0%,#373737 55%,#5D5D5D 100%);padding:34px 34px 30px 34px;">
              <div style="font-size:11px;letter-spacing:3px;text-transform:uppercase;color:#D3D3D3;font-weight:700;">DEV STYLE &middot; Jarvis AI</div>
              <div style="margin-top:12px;font-size:25px;line-height:1.25;color:#FFFFFF;font-weight:700;">Thanks for reaching out, {name}.</div>
              <div style="margin-top:10px;font-size:13px;line-height:1.5;color:#DFDFDF;">Your message &ldquo;{subject}&rdquo; reached us &mdash; here is my reply.</div>
            </td>
          </tr>
          <tr>
            <td style="padding:30px 34px 6px 34px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border-left:4px solid #8F8F8F;background:#F6FBF5;border-radius:0 16px 16px 0;">
                <tr>
                  <td style="padding:20px 22px;font-size:15px;line-height:1.68;color:#2C2C2C;">{reply}</td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td style="padding:26px 34px 8px 34px;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border-top:1px solid #E4EFE3;">
                <tr>
                  <td style="padding-top:22px;font-size:14px;line-height:1.6;color:#444444;">
                    Warm regards,<br/>
                    <span style="font-size:16px;font-weight:700;color:#2A2A2A;">Jarvis AI</span><br/>
                    <span style="font-size:12px;color:#858585;">Personal assistant &middot; DEV STYLE</span>
                  </td>
                </tr>
              </table>
            </td>
          </tr>
          <tr>
            <td style="padding:24px 34px 30px 34px;">
              <a href="https://arian00001.site" style="display:inline-block;background:#2A2A2A;color:#D3D3D3;text-decoration:none;font-size:12px;font-weight:700;letter-spacing:0.6px;padding:12px 20px;border-radius:999px;">arian00001.site</a>
            </td>
          </tr>
          <tr>
            <td style="background:#F4FAF3;padding:20px 34px;font-size:11px;line-height:1.6;color:#949494;border-top:1px solid #E4EFE3;">
              This reply was sent automatically by Jarvis on behalf of the account owner. Reply to this email and a human will read it.<br/>
              &copy; 2026 DEV STYLE &mdash; Arian Ahamed Ayan.
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>"""

    fun defaultTemplate(): String = STARTER

    /**
     * Drops a greeting the AI draft may have started with ("Dear Ayan," / "Hi," / "Hello Sir,"),
     * because the HTML card already greets the sender. Without this the mail opened with the
     * greeting twice.
     */
    fun stripGreeting(reply: String): String {
        val lines = reply.trim().lines()
        if (lines.isEmpty()) return reply
        val first = lines.first().trim()
        val looksLikeGreeting = first.length <= 60 &&
            Regex("(?i)^(dear|hi|hello|hey|salam|namaskar|assalamu|প্রিয়|नमस्ते|प्रिय)\\b.*[,:]?$").containsMatchIn(first)
        return if (looksLikeGreeting) lines.drop(1).joinToString("\n").trimStart('\n', ' ') else reply
    }

    /** True when the saved template already looks like a full HTML document. */
    fun looksLikeHtml(template: String): Boolean {
        val trimmed = template.trimStart()
        return trimmed.startsWith("<") && trimmed.contains("<body", ignoreCase = true)
    }

    /** "Rahim Ahmed" -> "Rahim"; an address -> the part before @. */
    fun firstName(raw: String): String {
        val clean = raw.trim().trim('<', '>')
        val beforeAt = clean.substringBefore("@")
        val first = beforeAt.trim('"', ' ', '\u0027').split(Regex("[\\s.]+")).firstOrNull { it.isNotBlank() }.orEmpty()
        return first.ifBlank { "there" }
    }

    /** Plain-text replies always open with "Dear <first name>,". */
    fun ensureGreeting(reply: String, name: String): String {
        val body = reply.trim()
        if (body.isEmpty()) return body
        val first = firstName(name)
        if (first == "there") return body
        val hasGreeting = Regex("(?i)^(dear|hi|hello|hey|salam|namaskar|assalamu)\\b").containsMatchIn(body)
        return if (hasGreeting) body else "Dear " + first + ",\n\n" + body
    }

    /** Fills every placeholder. Blank name falls back to "there". */
    fun render(template: String, name: String, email: String, subject: String, reply: String): String {
        val base = if (template.isBlank()) STARTER else template
        val safeName = name.trim().ifBlank { "there" }
        return base
            .replace(PLACEHOLDER_NAME, escape(firstName(safeName)))
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