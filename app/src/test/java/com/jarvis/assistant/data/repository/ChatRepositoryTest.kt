package com.jarvis.assistant.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jarvis.assistant.data.preferences.AppPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The typed message must appear at once and its reply must fill the same bubble. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [28])
class ChatRepositoryTest {

    private lateinit var repository: ChatRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences("jarvis_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        repository = ChatRepository(AppPreferences(context))
    }

    @Test fun sentMessageShowsImmediatelyWithoutReply() {
        repository.addTurn("hello jarvis", "")
        assertEquals(1, repository.turns.value.size)
        assertEquals("hello jarvis", repository.turns.value.first().userTranscript)
        assertEquals("", repository.turns.value.first().jarvisResponse)
    }

    @Test fun replyFillsTheSameBubbleInsteadOfAddingASecond() {
        repository.addTurn("hello jarvis", "")
        repository.addTurn("hello jarvis", "Hi Sir, ki korte hobe?")
        assertEquals(1, repository.turns.value.size)
        assertEquals("Hi Sir, ki korte hobe?", repository.turns.value.first().jarvisResponse)
    }

    @Test fun aNewMessageAfterTheReplyStartsANewTurn() {
        repository.addTurn("one", "")
        repository.addTurn("one", "first reply")
        repository.addTurn("two", "")
        repository.addTurn("two", "second reply")
        assertEquals(2, repository.turns.value.size)
        assertEquals("second reply", repository.turns.value.last().jarvisResponse)
    }
}