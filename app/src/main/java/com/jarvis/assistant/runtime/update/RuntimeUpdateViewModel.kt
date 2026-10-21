package com.jarvis.assistant.runtime.update

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RuntimeUpdateViewModel(app: Application) : AndroidViewModel(app) {

    sealed class UiState {
        object Idle : UiState()
        object Checking : UiState()
        object UpToDate : UiState()
        data class Available(val manifest: UpdateManifest, val totalSize: Long, val moduleCount: Int) : UiState()
        data class Downloading(val runtimeVersion: String, val moduleIndex: Int, val moduleCount: Int, val percent: Int, val moduleName: String) : UiState()
        data class Installing(val runtimeVersion: String, val moduleIndex: Int, val moduleCount: Int) : UiState()
        data class Applied(val runtimeVersion: String, val modules: List<String>) : UiState()
        data class RequiresAppUpdate(val reason: String) : UiState()
        data class Disabled(val message: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val updater = RuntimeUpdater(getApplication())
    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    val currentRuntimeVersion: String get() = ModuleStore.activeRuntimeVersion(getApplication())

    init { refreshIdle() }

    fun refreshIdle() {
        _state.value = if (BuildConfig.RUNTIME_MANIFEST_URL.isBlank()) UiState.Disabled("No update server configured.")
        else UiState.Idle
    }

    fun check() {
        if (_state.value is UiState.Checking) return
        _state.value = UiState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            val result = UpdateDiscovery.discover(
                ModuleStore.activeRuntimeVersion(getApplication()),
                appVersion(), Build.VERSION.SDK_INT
            )
            ModuleStore.setLastChecked(getApplication(), System.currentTimeMillis())
            _state.value = when (result) {
                is UpdateDiscovery.Result.Disabled -> UiState.Disabled("No update server configured.")
                is UpdateDiscovery.Result.UpToDate -> UiState.UpToDate
                is UpdateDiscovery.Result.Available -> UiState.Available(result.manifest, result.totalSize, result.manifest.modules.size)
                is UpdateDiscovery.Result.RequiresAppUpdate -> UiState.RequiresAppUpdate(result.reason)
                is UpdateDiscovery.Result.Failed -> UiState.Error(result.message)
            }
        }
    }

    fun apply(manifest: UpdateManifest) {
        if (_state.value is UiState.Downloading || _state.value is UiState.Installing) return
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = updater.apply(manifest) { i, total, percent, name ->
                _state.value = UiState.Downloading(manifest.runtimeVersion, i + 1, total, percent, name)
            }
            _state.value = when (outcome) {
                is RuntimeUpdater.Outcome.Applied -> UiState.Applied(outcome.runtimeVersion, outcome.modules)
                is RuntimeUpdater.Outcome.Failed -> UiState.Error(if (outcome.rolledBack) outcome.message + " Rolled back to previous runtime." else outcome.message)
            }
        }
    }

    fun rollback(moduleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = updater.rollback(moduleId)
            _state.value = if (ok) UiState.Applied(ModuleStore.activeRuntimeVersion(getApplication()), listOf(moduleId))
            else UiState.Error("No previous version to roll back to.")
        }
    }

    fun cancel() { updater.cancelled.set(true) }

    private fun appVersion(): String = try {
        getApplication<Application>().packageManager
            .getPackageInfo(getApplication<Application>().packageName, 0).versionName ?: "1.0.0"
    } catch (_: Exception) { "1.0.0" }
}
