package com.jarvis.assistant.email

import org.junit.Assert.*
import org.junit.Test

class GmailReplyPolicyTest {
    private fun mail(sender: String = "person@example.com", bulk: Boolean = false, subject: String = "Project update") =
        GmailMessage(12L, sender, "Person", subject, "Hello", 1L, "<unique@example.com>", bulk)

    @Test fun personalMailCanReceiveReply() { assertTrue(GmailReplyPolicy.allows(mail(), "owner@example.com", emptyList())) }
    @Test fun ownMailNeverReceivesReply() { assertFalse(GmailReplyPolicy.allows(mail("OWNER@example.com"), "owner@example.com", emptyList())) }
    @Test fun automatedMailNeverReceivesReply() { assertFalse(GmailReplyPolicy.allows(mail(bulk = true), "owner@example.com", emptyList())) }
    @Test fun bounceAndNoReplyAddressesAreSkipped() {
        for (sender in listOf("noreply@example.com", "mailer-daemon@example.com", "postmaster@example.com"))
            assertFalse(GmailReplyPolicy.allows(mail(sender), "owner@example.com", emptyList()))
    }
    @Test fun keywordsAreCaseInsensitive() {
        assertTrue(GmailReplyPolicy.allows(mail(), "owner@example.com", listOf("PROJECT")))
        assertFalse(GmailReplyPolicy.allows(mail(), "owner@example.com", listOf("invoice")))
    }
}
