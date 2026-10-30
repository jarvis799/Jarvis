package com.jarvis.assistant.subagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubAgentCatalogTest {

    @Test
    fun everyAgentHasAUniqueId() {
        val ids = SubAgentCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun bothTabsHaveAgents() {
        assertTrue(SubAgentCatalog.inCategory(SubAgentCategory.PROFESSIONAL).isNotEmpty())
        assertTrue(SubAgentCatalog.inCategory(SubAgentCategory.PREMIUM).isNotEmpty())
    }

    @Test
    fun onlyRealBackgroundAgentsAutoRun() {
        val auto = SubAgentCatalog.autoRunning().map { it.id }.toSet()
        assertEquals(setOf(SubAgentCatalog.WHATSAPP_ID, SubAgentCatalog.EMAIL_ID), auto)
        assertTrue(SubAgentCatalog.autoRunning().all { it.target == SubAgentTarget.WHATSAPP_AUTO_REPLY || it.target == SubAgentTarget.EMAIL_AUTO_REPLY })
    }

    @Test
    fun lookupByIdWorks() {
        assertNotNull(SubAgentCatalog.byId(SubAgentCatalog.WHATSAPP_ID))
        assertNull(SubAgentCatalog.byId("does_not_exist"))
    }

    @Test
    fun manualAgentIsAlwaysReady() {
        val web = SubAgentCatalog.byId("web_agent")!!
        val status = SubAgentStatus.of(web, autoEnabled = false, configured = false)
        assertEquals(SubAgentState.READY, status.state)
        assertEquals("READY", status.label)
    }

    @Test
    fun autoAgentStatusFollowsSetupAndSwitch() {
        val email = SubAgentCatalog.byId(SubAgentCatalog.EMAIL_ID)!!
        assertEquals(SubAgentState.SETUP, SubAgentStatus.of(email, false, false).state)
        assertEquals(SubAgentState.PAUSED, SubAgentStatus.of(email, false, true).state)
        assertEquals(SubAgentState.RUNNING, SubAgentStatus.of(email, true, true).state)
    }
}