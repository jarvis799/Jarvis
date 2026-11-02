package com.jarvis.assistant.data.repository

import com.jarvis.assistant.data.model.ChatTurn
import com.jarvis.assistant.data.preferences.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatRepository(private val preferences: AppPreferences) {

    companion object {
        /** Memory level HIGH: how many chat turns are kept on disk and reloaded at startup. */
        const val MAX_TURNS_KEPT = 300
    }

    private val _turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val turns: StateFlow<List<ChatTurn>> = _turns.asStateFlow()

    init {
        _turns.value = preferences.loadChatHistory()
    }

    @Synchronized
    fun addTurn(userText: String, jarvisText: String) {
        val cleanUser = userText.trim()
        val cleanJarvis = jarvisText.trim()

        if (cleanUser.isEmpty() && cleanJarvis.isEmpty()) return

        val currentList = _turns.value.toMutableList()

        if (currentList.isNotEmpty()) {
            val last = currentList.last()
            if (last.userTranscript == cleanUser && last.jarvisResponse == cleanJarvis) {
                return
            }
            // The message is shown the moment Send is tapped; its reply fills that same bubble.
            if (last.userTranscript == cleanUser && last.jarvisResponse.isBlank() && cleanJarvis.isNotEmpty()) {
                currentList[currentList.lastIndex] = last.copy(jarvisResponse = cleanJarvis)
                _turns.value = currentList
                preferences.saveChatHistory(currentList)
                return
            }
        }

        val newTurn = ChatTurn(
            userTranscript = cleanUser.ifEmpty { "(Audio query)" },
            jarvisResponse = cleanJarvis
        )

        currentList.add(newTurn)
        if (currentList.size > MAX_TURNS_KEPT) {
            currentList.removeAt(0)
        }

        _turns.value = currentList
        preferences.saveChatHistory(currentList)
    }

    /**
     * Fills the reply of the newest bubble that is still waiting for one, so an answer
     * always lands in the bubble the user is looking at (and the typing dots stop).
     */
    @Synchronized
    fun attachReply(jarvisText: String): Boolean {
        val clean = jarvisText.trim()
        if (clean.isEmpty()) return false
        val currentList = _turns.value.toMutableList()
        val last = currentList.lastOrNull() ?: return false
        if (last.jarvisResponse.isNotBlank()) return false
        currentList[currentList.lastIndex] = last.copy(jarvisResponse = clean)
        _turns.value = currentList
        preferences.saveChatHistory(currentList)
        return true
    }

    @Synchronized
    fun clearHistory() {
        _turns.value = emptyList()
        preferences.clearChatHistory()
    }
}