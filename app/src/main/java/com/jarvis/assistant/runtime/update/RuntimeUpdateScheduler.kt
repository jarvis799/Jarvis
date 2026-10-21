package com.jarvis.assistant.runtime.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules periodic signed-manifest discovery (every 12h, on connected
 *  network). Keeps existing work if already scheduled. */
object RuntimeUpdateScheduler {
    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<RuntimeUpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RuntimeUpdateCheckWorker.UNIQUE_WORK, ExistingPeriodicWorkPolicy.KEEP, req
        )
    }
}
