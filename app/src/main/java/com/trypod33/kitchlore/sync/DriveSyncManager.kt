package com.trypod33.kitchlore.sync

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.client.http.javanet.NetHttpTransport
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Ingredient
import com.trypod33.kitchlore.data.local.entity.Recipe
import com.trypod33.kitchlore.data.local.entity.Step
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections

// Two-way sync of the Room library with the Kitchlore/ folder on the user's
// Google Drive, mirroring the Termux drive_sync.py role: the JSON library file
// is the record, photos ride along in the same folder.

object DriveSyncManager {

    const val FOLDER_NAME = "Kitchlore"
    const val JSON_NAME = "kitchlore-library.json"

    private const val PREFS = "kitchlore"
    private const val KEY_ACCOUNT = "drive_account"

    fun hasAccount(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACCOUNT, null) != null

    fun saveAccount(context: Context, name: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACCOUNT, name).apply()
    }

    fun credential(context: Context): GoogleAccountCredential {
        val cred = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singletonList(DriveScopes.DRIVE_FILE),
        )
        cred.selectedAccountName =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACCOUNT, null)
        return cred
    }

    private fun drive(context: Context): Drive =
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential(context),
        ).setApplicationName("Kitchlore").build()

    suspend fun sync(context: Context, db: AppDatabase, onProgress: (String) -> Unit = {}): String =
        withContext(Dispatchers.IO) {
            val d = drive(context)
            val folderId = findFolder(d) ?: createFolder(d)
            importFrom(d, folderId, context, db, onProgress)
            exportTo(d, folderId, context, db, onProgress)
            "Library synced with Drive"
        }

    private fun findFolder(d: Drive): String? {
        val result = d.files().list()
            .setQ("name='$FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false")
            .setFields("files(id)")
            .setPageSize(5)
            .execute()
        return result.files.firstOrNull()?.id
    }

    private fun createFolder(d: Drive): String {
        val meta = com.google.api.services.drive.model.File()
            .setName(FOLDER_NAME)
            .setMimeType("application/vnd.google-apps.folder")
        return d.files().create(meta).setFields("id").execute().id
    }

    private suspend fun exportTo(
        d: Drive,
        folderId: String,
        context: Context,
        db: AppDatabase,
        onProgress: (String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        onProgress("Backing up to Drive")
        val recipes = db.recipeDao().getAllRecipesOnce()
        val arr = JSONArray()
        for (r in recipes) {
            val ings = db.ingredientDao().getIngredientsForRecipeOnce(r.id)
            val sts = db.stepDao().getStepsForRecipeOnce(r.id)
            arr.put(encodeRecipe(r, ings, sts))
        }
        val root = JSONObject()
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("recipes", arr)
        val tmp = File(context.cacheDir, JSON_NAME)
        tmp.writeText(root.toString())

        val old = d.files().list()
            .setQ("name='$JSON_NAME' and '$folderId' in parents and trashed=false")
            .setFields("files(id)")
            .setPageSize(5)
            .execute()
        old.files.forEach { d.files().delete(it.id).execute() }
        val meta = com.google.api.services.drive.model.File()
            .setName(JSON_NAME)
            .setParents(listOf(folderId))
        d.files().create(meta, FileContent("application/json", tmp)).setFields("id").execute()

        onProgress("Uploading photos")
        val existing = d.files().list()
            .setQ("'$folderId' in parents and trashed=false")
            .setFields("files(id,name)")
            .setPageSize(200)
            .execute()
            .files
            .map { it.name }
            .toSet()
        for (r in recipes) {
            val p = r.originalImagePath ?: continue
            val f = File(p)
            if (!f.exists() || f.name in existing) continue
            val type = if (f.extension.equals("png", true)) "image/png" else "image/jpeg"
            val meta2 = com.google.api.services.drive.model.File()
                .setName(f.name)
                .setParents(listOf(folderId))
            d.files().create(meta2, FileContent(type, f)).setFields("id").execute()
        }
        recipes.size
    }

    private fun encodeRecipe(
        r: Recipe,
        ings: List<Ingredient>,
        sts: List<Step>,
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
        o.put("photo", r.originalImagePath?.substringAfterLast('/') ?: JSONObject.NULL)
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
        d: Drive,
        folderId: String,
        context: Context,
        db: AppDatabase,
        onProgress: (String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        onProgress("Checking the Drive library")
        val listing = d.files().list()
            .setQ("name='$JSON_NAME' and '$folderId' in parents and trashed=false")
            .setFields("files(id)")
            .setPageSize(5)
            .execute()
        val fileId = listing.files.firstOrNull()?.id ?: return@withContext 0
        val tmp = File(context.cacheDir, "import-$JSON_NAME")
        d.files().get(fileId).executeMediaAsInputStream().use { ins ->
            tmp.outputStream().use { ins.copyTo(it) }
        }
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
            val localPhoto = photoName?.let { downloadPhoto(d, folderId, photoDir, it) }
            val addedMs = o.optLong("dateAdded", now)
            val rid = db.recipeDao().insert(
                Recipe(
                    title = title,
                    baseServings = o.optInt("baseServings", 1).coerceAtLeast(1),
                    sourceType = o.optString("sourceType").ifBlank { "drive" },
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
        if (added > 0) onProgress("Pulled $added recipes from Drive")
        added
    }

    private fun downloadPhoto(d: Drive, folderId: String, dir: File, name: String): String? {
        return try {
            val out = File(dir, name)
            if (out.exists() && out.length() > 0L) return out.absolutePath
            val listing = d.files().list()
                .setQ("name='$name' and '$folderId' in parents and trashed=false")
                .setFields("files(id)")
                .setPageSize(2)
                .execute()
            val id = listing.files.firstOrNull()?.id ?: return null
            d.files().get(id).executeMediaAsInputStream().use { ins ->
                out.outputStream().use { ins.copyTo(it) }
            }
            out.absolutePath
        } catch (t: Throwable) {
            null
        }
    }
}
