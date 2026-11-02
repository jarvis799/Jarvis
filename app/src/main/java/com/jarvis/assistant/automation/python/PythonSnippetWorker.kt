package com.jarvis.assistant.automation.python

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jarvis.assistant.JarvisApp
import java.util.concurrent.TimeUnit

/**
 * DEV STYLE: automatic Python execution.
 *
 * Any snippet with "run automatically" switched on is executed in the background on a schedule,
 * so bot tasks keep working without the user opening the app.
 */
class PythonSnippetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val snippets = PythonSnippetStore.enabledAutoRun(context)
        snippets.forEach { snippet ->
            runCatching { PythonSnippetRunner.runSaved(context, snippet) }
        }
        return Result.success()
    }
}

object PythonSnippetScheduler {

    private const val WORK_NAME = "jarvis_python_snippets"
    private const val INTERVAL_MINUTES = 15L

    /** Keeps the background schedule in sync with the saved snippets. */
    fun sync(context: Context = JarvisApp.instance) {
        val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        val hasAutoRun = PythonSnippetStore.enabledAutoRun(context).isNotEmpty()
        if (!hasAutoRun) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<PythonSnippetWorker>(
            INTERVAL_MINUTES, TimeUnit.MINUTES
        ).build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}