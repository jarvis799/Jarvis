package com.jarvis.assistant.automation.command

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class NativeScheduleRouterTest {
    private val now = LocalDateTime.of(2026, 11, 1, 9, 27, 30)
    @Test fun relativeReminderPreservesSeconds() {
        assertEquals(now.plusMinutes(10), NativeScheduleRouter.parseTime("remind me in 10 minutes to drink water", now))
    }
    @Test fun afternoonAlarmUsesCorrectHour() {
        assertEquals(now.toLocalDate().atTime(19, 30), NativeScheduleRouter.parseTime("set alarm at 7:30 pm", now))
    }
    @Test fun pastMorningAlarmRollsToTomorrow() {
        assertEquals(now.toLocalDate().plusDays(1).atTime(7, 0), NativeScheduleRouter.parseTime("set alarm at 7 am", now))
    }
    @Test fun noGuessedMeridiem() { assertNull(NativeScheduleRouter.parseTime("alarm 7 ta set koro", now)) }
    @Test fun invalidTimeDoesNotSave() { assertNull(NativeScheduleRouter.parseTime("set alarm at 25:30", now)) }
    @Test fun explicitTomorrow() {
        assertEquals(now.toLocalDate().plusDays(1).atTime(19, 0), NativeScheduleRouter.parseTime("set alarm tomorrow at 7 pm", now))
    }
    @Test fun bengaliDigits() {
        assertEquals(now.toLocalDate().plusDays(1).atTime(7, 0), NativeScheduleRouter.parseTime("alarm ৭ am set koro", now))
    }
}