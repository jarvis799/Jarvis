package com.jarvis.assistant.email

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlReplyTemplateTest {

    @Test
    fun starterIsAFullHtmlDocumentWithReplyPlaceholder() {
        assertTrue(HtmlReplyTemplate.looksLikeHtml(HtmlReplyTemplate.STARTER))
        assertTrue(HtmlReplyTemplate.STARTER.contains(HtmlReplyTemplate.PLACEHOLDER_REPLY))
        assertTrue(HtmlReplyTemplate.STARTER.contains(HtmlReplyTemplate.PLACEHOLDER_NAME))
    }

    @Test
    fun placeholdersAreReplaced() {
        val html = HtmlReplyTemplate.render(
            HtmlReplyTemplate.STARTER,
            name = "Ayan",
            email = "ayan@example.com",
            subject = "Order",
            reply = "Thanks for your email."
        )
        assertTrue(html.contains("Hi Ayan,"))
        assertTrue(html.contains("Thanks for your email."))
        assertFalse(html.contains(HtmlReplyTemplate.PLACEHOLDER_NAME))
        assertFalse(html.contains(HtmlReplyTemplate.PLACEHOLDER_REPLY))
    }

    @Test
    fun blankNameFallsBackToThere() {
        val html = HtmlReplyTemplate.render(HtmlReplyTemplate.STARTER, "", "", "", "Hello")
        assertTrue(html.contains("Hi there,"))
    }

    @Test
    fun untrustedValuesAreEscaped() {
        val html = HtmlReplyTemplate.render(
            HtmlReplyTemplate.STARTER,
            name = "<script>alert(1)</script>",
            email = "a@b.c",
            subject = "x",
            reply = "<b>bold</b>"
        )
        assertFalse(html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("&lt;b&gt;bold&lt;/b&gt;"))
    }

    @Test
    fun blankTemplateFallsBackToStarter() {
        val html = HtmlReplyTemplate.render("", "Ayan", "", "", "Reply")
        assertTrue(HtmlReplyTemplate.looksLikeHtml(html))
        assertTrue(html.contains("Reply"))
    }

    @Test
    fun plainTextAlternativeRemovesMarkup() {
        val text = HtmlReplyTemplate.stripToPlainText(
            "<html><body><style>p{color:red}</style><p>Hello &amp; welcome</p><p>Second line</p></body></html>"
        )
        assertFalse(text.contains("<"))
        assertFalse(text.contains("color:red"))
        assertTrue(text.contains("Hello & welcome"))
        assertTrue(text.contains("Second line"))
    }
}