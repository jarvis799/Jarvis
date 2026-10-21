package com.jarvis.assistant.automation.command

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.jarvis.assistant.automation.phone.PhoneControlManager
import com.jarvis.assistant.automation.whatsapp.WhatsAppAssistant
import com.jarvis.assistant.automation.youtube.YouTubeAutomationManager
import com.jarvis.assistant.data.memory.MemoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CommandExecutor(
    private val phoneManager: PhoneControlManager,
    private val youtubeManager: YouTubeAutomationManager,
    private val whatsappAssistant: WhatsAppAssistant,
    private val memoryManager: MemoryManager
) {
    companion object {
        private const val TAG = "CommandExecutor"
    }

    suspend fun execute(command: JarvisCommand): String = withContext(Dispatchers.Main) {
        Log.i(TAG, "Executing command: $command")
        when (command) {
            // DEV STYLE: AI Background Remover
            is JarvisCommand.RemoveBackground -> {
                val appContext = com.jarvis.assistant.JarvisApp.instance
                appContext.startActivity(
                    Intent(appContext, com.jarvis.assistant.bgremove.BackgroundRemovalActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "Opening the AI Background Remover."
            }
            // YouTube
            is JarvisCommand.PlayYouTube -> {

                // DEV STYLE: songs already on this device play inside Jarvis itself.
                val ctx = com.jarvis.assistant.JarvisApp.instance
                val q = command.query.trim().lowercase()
                val localTracks = runCatching { com.jarvis.assistant.media.MusicStore(ctx).songs() }.getOrDefault(emptyList<com.jarvis.assistant.media.MusicTrack>())
                val generic = q.isBlank() || listOf("music", "song", "songs", "gaana", "gana", "gaan", "track", "playlist", "my favorite song").any { q == it || q.contains(it) }
                val localIndex = if (generic) 0 else localTracks.indexOfFirst { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
                if (localTracks.isNotEmpty() && localIndex >= 0) {
                    com.jarvis.assistant.media.JarvisMusicPlayer.play(ctx, localTracks, localIndex)
                    return@withContext "Chalo Sir, apnar library theke " + localTracks[localIndex].title + " play korchi"
                }

                val resolvedSong = memoryManager.resolveSongQuery(command.query)
                memoryManager.trackSongPlay(resolvedSong)
                youtubeManager.playVideo(resolvedSong)
            }
            is JarvisCommand.SearchYouTube -> youtubeManager.searchYouTube(command.query)
            is JarvisCommand.PauseMedia -> {
                // DEV STYLE: "stop the song" now really stops it - JARVIS's own
                // player AND any external app (YouTube, Spotify) via the media key.
                val ctx = com.jarvis.assistant.JarvisApp.instance
                runCatching {
                    if (com.jarvis.assistant.media.JarvisMusicPlayer.isPlaying) {
                        com.jarvis.assistant.media.JarvisMusicPlayer.toggle(ctx)
                    }
                }
                youtubeManager.pausePlayback()
            }
            is JarvisCommand.ResumeMedia -> {
                val ctx = com.jarvis.assistant.JarvisApp.instance
                runCatching {
                    with(com.jarvis.assistant.media.JarvisMusicPlayer) {
                        if (!isPlaying && current != null) toggle(ctx)
                    }
                }
                youtubeManager.resumePlayback()
            }
            is JarvisCommand.NextMedia -> youtubeManager.nextMedia()
            is JarvisCommand.PreviousMedia -> youtubeManager.previousMedia()
            is JarvisCommand.SubscribeChannel -> youtubeManager.subscribeToChannel()
            is JarvisCommand.ToggleFullscreen -> youtubeManager.toggleFullscreen()

            // Phone Controls
            is JarvisCommand.Flash -> phoneManager.setFlashlight(command.enabled)
            is JarvisCommand.Brightness -> {
                if (command.isRelative) {
                    phoneManager.adjustBrightness(command.delta > 0)
                } else {
                    phoneManager.setBrightnessPercent(command.level)
                }
            }
            is JarvisCommand.Volume -> {
                if (command.isRelative) {
                    phoneManager.adjustVolume(command.delta > 0)
                } else {
                    phoneManager.setVolumePercent(command.level)
                }
            }
            is JarvisCommand.OpenApp -> phoneManager.openApp(command.packageName)
            is JarvisCommand.OpenWebUrls -> phoneManager.openMultipleUrls(command.urls)
            is JarvisCommand.PhoneCall -> phoneManager.makePhoneCall(command.contactOrNumber)
            is JarvisCommand.SearchWeb -> phoneManager.searchWeb(command.query)
            is JarvisCommand.AcceptCall -> phoneManager.acceptIncomingCall()
            is JarvisCommand.SaveContact -> phoneManager.saveContactAlias(command.name, command.number)
            is JarvisCommand.CreateWebsite -> com.jarvis.assistant.developer.WebsiteBuilder.createWebsiteAndReport(command.brief)
            is JarvisCommand.ShareApkOnTelegram -> com.jarvis.assistant.util.ApkShareHelper.shareViaTelegram(com.jarvis.assistant.JarvisApp.instance)

            // WhatsApp
            is JarvisCommand.WhatsAppMessage -> whatsappAssistant.sendMessageDirectly(command.contact, command.message)
            is JarvisCommand.WhatsAppCall -> whatsappAssistant.makeCall(command.contact, command.video)
            is JarvisCommand.WhatsAppDeleteMessage -> whatsappAssistant.deleteLastMessage(command.contact, command.forEveryone)
            is JarvisCommand.ConfirmAction -> {
                // DEV STYLE: a pending Gmail reply draft wins this confirmation
                val gmailResult = com.jarvis.assistant.email.GmailVoiceCommands.onConfirm(com.jarvis.assistant.JarvisApp.instance, command.confirmed)
                if (gmailResult != null) {
                    gmailResult
                } else if (command.confirmed) {
                    whatsappAssistant.confirmAndSend()
                } else {
                    whatsappAssistant.cancelDraft()
                }
            }
            is JarvisCommand.DisambiguationChoice -> whatsappAssistant.handleDisambiguationChoice(command.choice)

            // Memory
            is JarvisCommand.Remember -> memoryManager.rememberFact(command.text)
            is JarvisCommand.Forget -> memoryManager.forgetFact(command.query)
            is JarvisCommand.WhatDoYouRemember -> memoryManager.getWhatDoYouRemember()
            is JarvisCommand.RecallBirthday -> memoryManager.getBirthdayResponse()
            is JarvisCommand.RecallFavoriteSong -> memoryManager.getFavoriteSongResponse()
            is JarvisCommand.RecallRoutine -> memoryManager.getRoutineResponse()
            is JarvisCommand.Decision -> com.jarvis.assistant.util.DecisionEngine.decide(command.topic)
            is JarvisCommand.GoToBackground -> {
                com.jarvis.assistant.JarvisApp.instance.moveAppToBack()
                "Going to background."
            }
            is JarvisCommand.SetUserName -> {
                val app = com.jarvis.assistant.JarvisApp.instance
                app.preferences.userName = command.name
                "Yes - from now on I will call you " + command.name + ". You can also wake me by saying your name."
            }
            is JarvisCommand.SetWakeWord -> {
                val app = com.jarvis.assistant.JarvisApp.instance
                app.preferences.customWakeWord = command.name
                "Yes - saved. From now on say \"" + command.name + "\" or \"Jarvis\" to wake me."
            }
            is JarvisCommand.SkillLab -> com.jarvis.assistant.agent.skills.SkillEvolutionLab.labReport()
            is JarvisCommand.PlanGoal -> com.jarvis.assistant.agent.planning.ParallelUniversePlanner.report(command.goal)
            is JarvisCommand.LockPhone -> phoneManager.lockPhone()
            is JarvisCommand.ShutdownPhone -> phoneManager.lockPhone()
            is JarvisCommand.MicOff -> {
                val app = com.jarvis.assistant.JarvisApp.instance
                app.preferences.isMicMuted = true
                com.jarvis.assistant.background.JarvisConversationService.setMicMuted(true)
                "Microphone off. Typed commands still work - tap the mic to talk again."
            }
            is JarvisCommand.MicOn -> {
                val app = com.jarvis.assistant.JarvisApp.instance
                app.preferences.isMicMuted = false
                com.jarvis.assistant.background.JarvisConversationService.setMicMuted(false)
                "Microphone on. I am listening again."
            }
            // DEV STYLE: Macro Center - MacroDroid-style automations from a sentence
            is JarvisCommand.CreateMacro -> com.jarvis.assistant.macro.MacroBrain.createFromPrompt(command.prompt)
            is JarvisCommand.ListMacros -> {
                val appCtx = com.jarvis.assistant.JarvisApp.instance
                appCtx.startActivity(
                    Intent(appCtx, com.jarvis.assistant.macro.MacroListActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                "Macro Center khulchi, Sir - apnar automation gulo ekhane."
            }
            // DEV STYLE: Gmail IMAP voice commands
            is JarvisCommand.ReadLatestEmail -> com.jarvis.assistant.email.GmailVoiceCommands.readLatest(com.jarvis.assistant.JarvisApp.instance)
            is JarvisCommand.SummarizeEmails -> com.jarvis.assistant.email.GmailVoiceCommands.summarize(com.jarvis.assistant.JarvisApp.instance)
            is JarvisCommand.DraftEmailReply -> com.jarvis.assistant.email.GmailVoiceCommands.draftReply(com.jarvis.assistant.JarvisApp.instance, command.instruction)
            is JarvisCommand.SendEmailReply -> com.jarvis.assistant.email.GmailVoiceCommands.sendDraft(com.jarvis.assistant.JarvisApp.instance)
            is JarvisCommand.ToggleOnGuard -> toggleOnGuard(command.enable)
        }
    }

    // DEV STYLE: On-Guard (thief watch) voice control
    private fun toggleOnGuard(enable: Boolean): String {
        val app = com.jarvis.assistant.JarvisApp.instance
        val intent = Intent(app, com.jarvis.assistant.security.ThiefGuardService::class.java)
        return if (enable) {
            if (ContextCompat.checkSelfPermission(app, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                val settings = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", app.packageName, null))
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(settings)
                "On-Guard needs the Camera permission first. I opened the app settings - allow Camera for Jarvis, then ask me again."
            } else {
                app.preferences.isThiefGuardEnabled = true
                ContextCompat.startForegroundService(app, intent)
                "On-Guard is ON. If someone lifts the phone while it is resting, I will quietly take their photo, and I will tell you when you come back."
            }
        } else {
            app.preferences.isThiefGuardEnabled = false
            app.stopService(intent)
            "On-Guard mode OFF."
        }
    }
}
