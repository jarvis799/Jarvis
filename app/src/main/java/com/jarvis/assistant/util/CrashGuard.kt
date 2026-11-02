package com.jarvis.assistant.util

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.jarvis.assistant.ui.home.MainActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stops a crash from ending the session with the system's "JARVIS has stopped" dialog.
 *
 * Every uncaught exception is written to a small on-device log and the app is reopened on
 * the home screen, so a single failing click never closes Jarvis. A restart loop is
 * impossible: a second crash within 15 seconds goes back to the system handler.
 */
object CrashGuard {
    private const val TAG = "CrashGuard"
    private const val PREFS = "jarvis_crash_guard"
    private const val LOG_FILE = "crash-log.txt"
    private const val MAX_LOG = 40000

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val recovered = runCatching { recover(app, throwable) }.getOrDefault(false)
            if (!recovered) previous?.uncaughtException(thread, throwable)
        }
    }

    fun record(context: Context, where: String, throwable: Throwable) {
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val builder = StringBuilder()
            builder.append(stamp).append(" [").append(where).append("] ")
                .append(throwable.javaClass.simpleName).append(": ").append(throwable.message).append('\n')
            throwable.stackTrace.take(14).forEach { frame -> builder.append("    at ").append(frame).append('\n') }
            val file = File(context.filesDir, LOG_FILE)
            val previous = if (file.exists()) file.readText() else ""
            file.writeText((builder.toString() + previous).take(MAX_LOG))
        }
    }

    fun readRecent(context: Context): String = runCatching {
        File(context.filesDir, LOG_FILE).takeIf { it.exists() }?.readText().orEmpty()
    }.getOrDefault("")

    private fun recover(app: Application, throwable: Throwable): Boolean {
        Log.e(TAG, "Uncaught: " + throwable.message, throwable)
        record(app, "uncaught", throwable)
        val prefs = app.getSharedPreferences(PREFS, 0)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_restart", 0L) < 15000L) return false
        prefs.edit().putLong("last_restart", now).apply()
        val intent = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("restarted_after_crash", true)
        runCatching { app.startActivity(intent) }
        runCatching { Process.killProcess(Process.myPid()) }
        return true
    }
}
