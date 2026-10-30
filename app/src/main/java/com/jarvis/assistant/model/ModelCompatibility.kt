package com.jarvis.assistant.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs

/** Checks RAM, free storage and CPU flavour before a model is downloaded. */
object ModelCompatibility {

    fun check(context: Context, sizeBytes: Long): ModelCompat {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager?.getMemoryInfo(info)
        val availableRamMb = (info.availMem / (1024 * 1024)).toInt()

        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val freeMb = if (stat != null) {
            (stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024)).toInt()
        } else 0

        val sizeMb = (sizeBytes / (1024 * 1024)).toInt()
        val neededRamMb = maxOf(sizeMb + 512, 800)
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val cpuSupported = abi.contains("arm64") || abi.contains("armeabi")

        return when {
            !cpuSupported ->
                ModelCompat(false, "CPU $abi is not supported for on-device models.", availableRamMb, freeMb)
            sizeMb > 0 && freeMb < sizeMb + 300 ->
                ModelCompat(false, "Needs ${sizeMb + 300} MB free storage, only $freeMb MB free.", availableRamMb, freeMb)
            availableRamMb < neededRamMb ->
                ModelCompat(false, "Needs about $neededRamMb MB free RAM, $availableRamMb MB free right now.", availableRamMb, freeMb)
            else ->
                ModelCompat(true, "Compatible — about $neededRamMb MB RAM and ${sizeMb + 300} MB storage.", availableRamMb, freeMb)
        }
    }
}