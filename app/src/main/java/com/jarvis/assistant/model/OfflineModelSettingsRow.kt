package com.jarvis.assistant.model

import android.content.Context
import android.content.Intent
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.databinding.FragmentSettingsBinding

object OfflineModelSettingsRow {
    fun bind(context: Context, binding: FragmentSettingsBinding) {
        val store = ModelStore(context)
        binding.tvOfflineModelStatus.text = OfflineModelStatus.describe(context)
        binding.rowOfflineReplies.setOnClickListener {
            context.startActivity(Intent(context, ModelCatalogueActivity::class.java))
        }
        binding.switchOfflineReplies.setOnCheckedChangeListener(null)
        binding.switchOfflineReplies.isChecked = store.offlineFallback() && OfflineModelStatus.ready(context)
        binding.switchOfflineReplies.setOnCheckedChangeListener { button, checked ->
            if (checked && !OfflineModelStatus.ready(context)) {
                button.isChecked = false
                context.startActivity(Intent(context, ModelCatalogueActivity::class.java))
            } else {
                store.setOfflineFallback(checked)
                JarvisApp.instance.preferences.offlineReplies = checked
                binding.tvOfflineModelStatus.text = OfflineModelStatus.describe(context)
            }
        }
    }
}