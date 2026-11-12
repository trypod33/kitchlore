package com.trypod33.kitchlore.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Ingredient
import com.trypod33.kitchlore.data.local.entity.Recipe
import com.trypod33.kitchlore.data.local.entity.Step
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

// Two-way sync of the Room library with a folder the user picks in the system
// file picker (a Google Drive folder works through the Drive app). The JSON
// library file is the record, photos ride along in the same folder. No Google
// account APIs: the folder pick itself grants lasting read/write access, so
// there is nothing to sign in to and no OAuth client to register.

object FolderSyncManager {

    const val JSON_STEM = "kitchlore-library"
    const val FOLDER_HINT = "Kitchlore"

    private const val PREFS = "kitchlore_sync"
    private const val KEY_FOLDER = "folder_uri"

    fun hasFolder(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FOLDER, null) != null

    fun saveFolder(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_FOLDER, uri.toString()).apply()
    }

    suspend fun sync(context: Context, db: AppDatabase, onProgress: (String) -> Unit = {}): String =
        withContext(Dispatchers.IO) {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_FOLDER, null) ?: throw IOException("No folder chosen yet")
            val folder = try {
                DocumentFile.fromTreeUri(context, Uri.parse(raw))
            } catch (t: Throwable) {
                null
            }
            if (folder == null || !folder.exists()) {
                throw IOException("The picked folder is no longer available; tap Sync and choose it again")
            }
            val imported = importFrom(folder, context, db, onProgress)
            val count = exportTo(folder, context, db, onProgress)
            if (imported > 0) {
                "Library synced: pulled $imported from the other device, $count backed up"
            } else {
                "Library synced: $count recipes backed up"
            }
        }

    private fun childMap(folder: DocumentFile): MutableMap<String, DocumentFile> {
        val map = mutableMapOf<String, DocumentFile>()
        for (c in folder.listFiles()) {
            val n = c.name ?: continue
            map[n] = c
        }
        return map
    }

    private suspend fun exportTo(
        folder: DocumentFile,
        context: Context,
        db: AppDatabase,
        onProgress: (String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        onProgress("Backing up to the picked folder")
        val recipes = db.recipeDao().getAllRecipesOnce()
        val children = childMap(folder)

        // Upload photos first so the library file can record the names the
        // folder actually gave them.
        val remoteNames = mutableMapOf<String, String>()
        for (r in recipes) {
            val p = r.originalImagePath ?: continue
            val f = File(p)
            if (!f.exists()) continue
            val want = f.name
            val existing = children[want] ?: children[want.substringBeforeLast('.')]
            if (existing != null && existing.isFile && existing.length() > 0L) {
                remoteNames[p] = want
                continue
            }
            val mime = if (f.extension.equals("png", true)) "image/png" else "image/jpeg"
            val doc = folder.createFile(mime, want.substringBeforeLast('.'))
                ?: throw IOException("The picked folder would not accept a new photo")
            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                f.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("The picked folder would not accept a new photo")
            remoteNames[p] = doc.name ?: want
        }

        val arr = JSONArray()
        for (r in recipes) {
            val ings = db.ingredientDao().getIngredientsForRecipeOnce(r.id)
            val sts = db.stepDao().getStepsForRecipeOnce(r.id)
            arr.put(encodeRecipe(r, ings, sts, remoteNames[r.originalImagePath]))
        }
        val root = JSONObject()
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("recipes", arr)
        val tmp = File(context.cacheDir, "$JSON_STEM-export.json")
        tmp.writeText(root.toString())

        // Replace the old library file rather than editing it in place.
        children.entries.filter { it.key.startsWith(JSON_STEM, ignoreCase = true) }
            .forEach { it.value.delete() }
        val doc = folder.createFile("application/json", JSON_STEM)
            ?: throw IOException("The picked folder would not accept the library file")
        context.contentResolver.openOutputStream(doc.uri)?.use { out ->
            tmp.inputStream().use { it.copyTo(out) }
        } ?: throw IOException("The picked folder would not accept the library file")

        onProgress("Backed up ${recipes.size} recipes")
        recipes.size
    }

    private fun encodeRecipe(
        r: Recipe,
        ings: List<Ingredient>,
        sts: List<Step>,
        photoName: String?,
    ): JSONObject {
        val o = JSONObject()
        o.put("title", r.title)
        o.put("baseServings", r.baseServings)
        o.put("sourceType", r.sourceType)
        o.put("sourceNote", r.sourceNote ?: JSONObject.NULL)
        o.put("notes", r.notes ?: JSONObject.NULL)
        o.put("prepTimeMinutes", r.prepTimeMinutes ?: JSONObject.NULL)
        o.put("cookTimeMinutes", r.cookTimeMinutes ?: JSONObject.NULL)
        o.put("dateAdded", r.dateAdded)
        o.put("dateModified", r.dateModified)
        o.put("photo", photoName ?: JSONObject.NULL)
        o.put(
            "ingredients",
            JSONArray().apply {
                ings.forEach { i ->
                    put(
                        JSONObject()
                            .put("name", i.name)
                            .put("rawText", i.rawText)
                            .put("amount", i.amount ?: JSONObject.NULL)
                            .put("unit", i.unit ?: JSONObject.NULL)
                            .put("sortOrder", i.sortOrder)
                    )
                }
            },
        )
        o.put(
            "steps",
            JSONArray().apply {
                sts.forEach { s ->
                    put(
                        JSONObject()
                            .put("stepNumber", s.stepNumber)
                            .put("instructionText", s.instructionText)
                            .put("timerSeconds", s.timerSeconds ?: JSONObject.NULL)
                    )
                }
            },
        )
        return o
    }

    private suspend fun importFrom(
        folder: DocumentFile,
        context: Context,
        db: AppDatabase,
        onProgress: (String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        onProgress("Checking the other device's library")
        val children = childMap(folder)
        val doc = children.entries
            .firstOrNull { it.key.startsWith(JSON_STEM, ignoreCase = true) }
            ?.value ?: return@withContext 0
        val tmp = File(context.cacheDir, "$JSON_STEM-import.json")
        context.contentResolver.openInputStream(doc.uri)?.use { ins ->
            tmp.outputStream().use { ins.copyTo(it) }
        } ?: return@withContext 0
        val root = JSONObject(tmp.readText())
        val arr = root.optJSONArray("recipes") ?: return@withContext 0

        val existing = db.recipeDao().getAllRecipesOnce()
            .map { it.title.trim().lowercase() }
            .toMutableSet()
        val photoDir = File(context.filesDir, "photos")
        photoDir.mkdirs()

        var added = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val title = o.optString("title").ifBlank { "(untitled)" }
            if (title.trim().lowercase() in existing) continue
            val now = System.currentTimeMillis()
            val photoName = o.optString("photo").takeIf { it.isNotBlank() && it != "null" }
            val localPhoto = photoName?.let { downloadPhoto(context, children, photoDir, it) }
            val addedMs = o.optLong("dateAdded", now)
            val rid = db.recipeDao().insert(
                Recipe(
                    title = title,
                    baseServings = o.optInt("baseServings", 1).coerceAtLeast(1),
                    sourceType = o.optString("sourceType").ifBlank { "imported" },
                    sourceNote = o.optString("sourceNote").takeIf { it.isNotBlank() && it != "null" },
                    notes = o.optString("notes").takeIf { it.isNotBlank() && it != "null" },
                    prepTimeMinutes = if (o.has("prepTimeMinutes") && !o.isNull("prepTimeMinutes")) o.optInt("prepTimeMinutes") else null,
                    cookTimeMinutes = if (o.has("cookTimeMinutes") && !o.isNull("cookTimeMinutes")) o.optInt("cookTimeMinutes") else null,
                    originalImagePath = localPhoto,
                    dateAdded = addedMs,
                    dateModified = o.optLong("dateModified", addedMs),
                ),
            )
            val iarr = o.optJSONArray("ingredients")
            if (iarr != null) {
                val ings = mutableListOf<Ingredient>()
                for (j in 0 until iarr.length()) {
                    val io = iarr.getJSONObject(j)
                    ings.add(
                        Ingredient(
                            recipeId = rid,
                            name = io.optString("name").ifBlank { "(ingredient)" },
                            rawText = io.optString("rawText").ifBlank { io.optString("name") },
                            amount = if (io.isNull("amount")) null else io.optDouble("amount"),
                            unit = if (io.isNull("unit")) null else io.optString("unit"),
                            sortOrder = io.optInt("sortOrder", j),
                        ),
                    )
                }
                if (ings.isNotEmpty()) db.ingredientDao().insertAll(ings)
            }
            val sarr = o.optJSONArray("steps")
            if (sarr != null) {
                val sts = mutableListOf<Step>()
                for (j in 0 until sarr.length()) {
                    val so = sarr.getJSONObject(j)
                    val t = so.optString("instructionText")
                    if (t.isBlank()) continue
                    sts.add(
                        Step(
                            recipeId = rid,
                            stepNumber = so.optInt("stepNumber", j + 1),
                            instructionText = t,
                            timerSeconds = if (so.isNull("timerSeconds")) null else so.optInt("timerSeconds"),
                        ),
                    )
                }
                if (sts.isNotEmpty()) db.stepDao().insertAll(sts)
            }
            existing.add(title.trim().lowercase())
            added++
        }
        if (added > 0) onProgress("Pulled $added recipes from the other device")
        added
    }

    private fun downloadPhoto(
        context: Context,
        children: Map<String, DocumentFile>,
        dir: File,
        name: String,
    ): String? {
        return try {
            val out = File(dir, name)
            if (out.exists() && out.length() > 0L) return out.absolutePath
            val doc = children[name] ?: children[name.substringBeforeLast('.')] ?: return null
            context.contentResolver.openInputStream(doc.uri)?.use { ins ->
                out.outputStream().use { ins.copyTo(it) }
            } ?: return null
            if (out.exists() && out.length() > 0L) out.absolutePath else null
        } catch (t: Throwable) {
            null
        }
    }
}
