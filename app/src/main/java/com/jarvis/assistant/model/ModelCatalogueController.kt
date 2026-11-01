package com.jarvis.assistant.model

import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jarvis.assistant.JarvisApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Presentation wiring stays outside the activity; the install survives screen rotation. */
class ModelCatalogueController(private val activity: ModelCatalogueActivity, private val setup: ModelSetupViewModel) {
    private val screen = OfflineModelScreen(activity) { activity.finish() }
    private val cards = mutableListOf<OfflineModelCard>()
    private var syncing = false
    private val chooseFolder = activity.registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { ModelArchive(activity).choose(uri); screen.archive.refresh(setup.state.value.busy) }
            catch (error: Exception) { screen.status.text = "Folder could not be selected: ${error.message}" }
        }
    }
    private val importFile = activity.registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) setup.importSaved(uri)
    }
    fun bind() {
        activity.setContentView(screen)
        screen.searchButton.setOnClickListener { search() }
        screen.archive.folderButton.setOnClickListener { chooseFolder.launch(ModelArchive(activity).folder) }
        screen.archive.importButton.setOnClickListener { importFile.launch(arrayOf("*/*")) }
        screen.fallback.setOnCheckedChangeListener { _, checked ->
            if (!syncing) {
                val ready = setup.store.activeModel()?.exists() == true && setup.store.readyModelId() == setup.store.activeModelId()
                if (checked && !ready) {
                    screen.fallback.isChecked = false
                    screen.status.text = "Download a model and finish auto-setup before enabling offline replies."
                } else {
                    setup.store.setOfflineFallback(checked)
                    JarvisApp.instance.preferences.offlineReplies = checked
                }
            }
        }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { setup.installed.collect { renderModels(); update() } }
                launch { setup.state.collect { update() } }
            }
        }
    }
    private fun renderModels() {
        cards.clear(); screen.recommended.removeAllViews(); screen.installed.removeAllViews(); screen.results.removeAllViews()
        RecommendedModels.all.forEach { screen.recommended.addView(card(it)) }
        val others = setup.installed.value.filter { saved -> RecommendedModels.all.none { it.id == saved.id } }
        if (others.isEmpty()) screen.installed.addView(OfflineModelStyle.text(activity,
            if (setup.installed.value.isEmpty()) "No model downloaded yet." else "Your downloaded models are shown above."))
        others.forEach { model ->
            screen.installed.addView(card(RemoteModel(model.id, model.name, 0, java.io.File(model.filePath).name, model.sizeBytes, "", model.quant)))
        }
    }
    private fun card(remote: RemoteModel): OfflineModelCard {
        val local = setup.store.models().firstOrNull { it.id == remote.id }
        val remove: (() -> Unit)? = local?.let { saved -> {
            AlertDialog.Builder(activity).setTitle("Remove runtime copy?").setMessage("Remove the private copy of ${saved.name}? The reusable file in your selected folder will be kept.")
                .setNegativeButton("Keep", null).setPositiveButton("Delete") { _, _ -> setup.delete(saved) }.show()
            Unit
        } }
        return OfflineModelCard(activity, remote.id, remote.name,
            "${remote.sizeBytes / 1048576} MB · ${remote.quant.ifBlank { "GGUF" }}\n${remote.id}",
            { if (local != null) setup.activate(local) else setup.install(remote) }, { setup.pause() }, remove)
            .also { cards.add(it) }
    }
    private fun update() {
        val state = setup.state.value
        screen.status.text = state.message
        screen.archive.refresh(state.busy)
        val active = setup.store.activeModelId()
        val ready = setup.store.readyModelId()
        cards.forEach { card -> card.bind(state, setup.installed.value.any { it.id == card.modelId },
            active == card.modelId && ready == card.modelId && setup.store.activeModel()?.exists() == true) }
        syncing = true
        screen.fallback.isChecked = setup.store.offlineFallback() && active != null && active == ready && setup.store.activeModel()?.exists() == true
        screen.fallback.isEnabled = !state.busy
        syncing = false
    }
    private fun search() {
        val query = screen.searchField.text.toString().trim()
        screen.searchButton.isEnabled = false
        cards.removeAll { it.parent === screen.results }; screen.results.removeAllViews()
        screen.status.text = "Searching the catalogue…"
        activity.lifecycleScope.launch {
            try {
                val models = ModelCatalogue.search(query)
                screen.status.text = if (models.isEmpty()) "No GGUF models found." else "${models.size} results · select one to verify and set up."
                models.forEach { screen.results.addView(card(it)) }
                cards.forEach { it.bind(setup.state.value, setup.store.models().any { model -> model.id == it.modelId }, false) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { screen.status.text = "Search failed: ${error.message}" }
            finally { screen.searchButton.isEnabled = true }
        }
    }
}