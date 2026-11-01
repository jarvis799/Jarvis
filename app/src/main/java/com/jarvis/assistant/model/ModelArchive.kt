package com.jarvis.assistant.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/** A user-selected Documents/Downloads folder, outside uninstallable app storage. */
class ModelArchive(private val context: Context) {
    private val prefs = context.getSharedPreferences("model_archive", Context.MODE_PRIVATE)
    val folder: Uri? get() = prefs.getString("folder", null)?.let(Uri::parse)?.takeIf { uri ->
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
    }
    fun choose(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        check(prefs.edit().putString("folder", uri.toString()).commit()) { "Could not save the folder permission." }
    }
    fun save(file: File) {
        val tree = checkNotNull(folder) { "Choose a reusable model folder first." }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val resolver = context.contentResolver
        val doc = checkNotNull(DocumentsContract.createDocument(resolver, parent, "application/octet-stream",
            "${System.currentTimeMillis()}-${file.name}.part")) { "Could not create the reusable model file." }
        try {
            checkNotNull(resolver.openOutputStream(doc, "w")).use { output ->
                file.inputStream().use { input -> input.copyTo(output, 256 * 1024) }
                output.flush()
            }
            resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) check(it.getLong(0) == file.length()) { "Reusable copy is incomplete." }
            }
            checkNotNull(DocumentsContract.renameDocument(resolver, doc,
                "${System.currentTimeMillis()}-${file.name}")) { "Could not finish saving the reusable copy." }
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw error
        }
    }
}