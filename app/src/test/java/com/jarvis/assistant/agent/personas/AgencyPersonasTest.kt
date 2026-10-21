package com.jarvis.assistant.agent.personas

import org.junit.Assert.assertEquals
import org.junit.Test

class AgencyPersonasTest {

    @Test
    fun androidKotlinQueryPicksMobileEngineer() {
        assertEquals(
            SpecialistPersonas.Persona.MOBILE_ENGINEER,
            SpecialistPersonas.select("amar android app e crash hoy, kotlin diye fix lagbe")
        )
    }

    @Test
    fun frontendQueryPicksFrontendDeveloper() {
        assertEquals(
            SpecialistPersonas.Persona.FRONTEND_DEVELOPER,
            SpecialistPersonas.select("make the frontend more responsive")
        )
    }

    @Test
    fun plainGreetingStaysGeneral() {
        assertEquals(SpecialistPersonas.Persona.GENERAL, SpecialistPersonas.select("hello how are you"))
    }
}
