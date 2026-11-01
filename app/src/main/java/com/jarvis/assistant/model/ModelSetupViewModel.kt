package com.jarvis.assistant.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.JarvisApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ModelSetupViewModel(app: Application) : AndroidViewModel(app) {
    data class State(val id: String = "", val phase: String = "", val message: String = "Choose a model to download and set up automatically.",
        val done: Long = 0, val total: Long = 0, val busy: Boolean = false)
    val store = ModelStore(app)
    private val current = MutableStateFlow(State())
    val state = current.asStateFlow()
    private val saved = MutableStateFlow(store.models())
    val installed = saved.asStateFlow()
    private var job: Job? = null
    private var transfer: VerifiedModelTransfer? = null

    fun install(remote: RemoteModel) = start(remote.id) {
        val archive = ModelArchive(getApplication())
        check(archive.folder != null) { "Choose a reusable phone folder first, so the model file survives uninstall." }
        val resolved = ModelCatalogue.resolve(remote)
        // Transfer checks only the remaining storage, so resuming never requires a second full copy.
        val compatibility = ModelCompatibility.check(getApplication(), resolved.sizeBytes, checkStorage = false)
        check(compatibility.supported) { compatibility.reason }
        check(LocalModelRuntime.isAvailable()) { LocalModelRuntime.statusMessage }
        val download = VerifiedModelTransfer(getApplication()).also { transfer = it }
        val file = download.download(resolved,
            { done, total -> current.value = State(remote.id, "download", "Downloading ${done * 100 / total}% · ${done / 1048576} / ${total / 1048576} MB", done, total, true) },
            { stage -> current.value = State(remote.id, if (stage.startsWith("Downloading")) "download" else "verify", stage, busy = true) })
        val local = LocalModel(resolved.id, resolved.name, file.absolutePath, file.length(), System.currentTimeMillis(), resolved.quant)
        current.value = State(remote.id, "archive", "Saving reusable model in your chosen folder… Keep this file after uninstall.", busy = true)
        archive.save(file)
        local
    }

    fun importSaved(uri: android.net.Uri) = start("import") {
        current.value = State("import", "import", "Importing saved GGUF · no internet download…", busy = true)
        SavedModelImporter.read(getApplication(), uri)
    }

    fun activate(model: LocalModel) = start(model.id) {
        check(model.exists()) { "Model file is missing. Delete this entry and download it again." }
        RecommendedModels.all.firstOrNull { it.id == model.id }?.let {
            current.value = State(model.id, "verify", "Verifying saved model…", busy = true)
            ModelFileVerifier.verify(File(model.filePath), it.sizeBytes, it.sha256)
        }
        model
    }

    private fun start(id: String, prepare: suspend () -> LocalModel) {
        if (job?.isCompleted == false) return
        current.value = State(id, "resolve", "Checking model and device…", busy = true)
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val model = prepare()
                    store.add(model); saved.value = store.models()
                    check(ModelCompatibility.check(getApplication(), model.sizeBytes, checkStorage = false).supported) {
                        ModelCompatibility.check(getApplication(), model.sizeBytes, checkStorage = false).reason
                    }
                    ensureActive()
                    current.value = State(id, "test", "Auto-setup: loading model and generating a test reply on this phone…", busy = true)
                    LocalModelRuntime.reply(model.filePath, "You are JARVIS. Reply briefly.", "Say hello in one short sentence.", 12)
                    ensureActive()
                    store.activateVerified(model)
                    JarvisApp.instance.preferences.offlineReplies = true
                    saved.value = store.models()
                    current.value = State(id, "ready", "Ready · model tested, activated, and offline fallback enabled.")
                }
            } catch (error: CancellationException) {
                current.value = State(id, "paused", "Paused · tap Resume to continue. Downloaded bytes are kept.")
                throw error
            } catch (error: Exception) {
                current.value = State(id, "error", "Setup failed: ${error.message ?: "Unknown model error"}")
            } catch (_: OutOfMemoryError) {
                current.value = State(id, "error", "Not enough free RAM. Close other apps and retry the 1.1B model.")
            } finally { transfer = null }
        }
    }

    fun pause() { transfer?.cancel(); job?.cancel() }
    fun delete(model: LocalModel) {
        if (state.value.busy) return
        if (File(model.filePath).exists() && !File(model.filePath).delete()) {
            current.value = State(model.id, "error", "Could not delete the file. Close any active local reply and retry."); return
        }
        store.remove(model.id)
        saved.value = store.models()
        current.value = State(message = "Private runtime copy removed. Your reusable folder file was kept.")
    }
    override fun onCleared() { transfer?.cancel(); super.onCleared() }
}