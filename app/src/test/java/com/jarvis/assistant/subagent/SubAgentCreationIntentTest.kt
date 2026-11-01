package com.jarvis.assistant.subagent

import org.junit.Assert.*
import org.junit.Test

class SubAgentCreationIntentTest {
    @Test fun replyMayContainListOrStatusWords() {
        assertTrue(SubAgentBuilder.wantsCreation("create sub agent when price says reply: here is my price list"))
        assertTrue(SubAgentBuilder.wantsCreation("create sub agent when status says reply: still running"))
    }
    @Test fun listingAgentsIsNotCreation() {
        assertFalse(SubAgentBuilder.wantsCreation("show sub agent list"))
    }
}