package com.trypod33.kitchlore.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import com.trypod33.kitchlore.data.local.AppDatabase
import com.trypod33.kitchlore.data.local.entity.Ingredient
import com.trypod33.kitchlore.data.local.entity.Recipe
import com.trypod33.kitchlore.data.local.entity.Step
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

object FirstRunImport {
    private const val PREFS = "kitchlore"
    private const val KEY = "termux_import_done"

    suspend fun run(context: Context, db: AppDatabase) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY, false)) return
        try {
            val local = copyAsset(context, "recipes.db", "import/recipes.db")
            importAll(context, local, db)
            prefs.edit().putBoolean(KEY, true).apply()
        } catch (t: Throwable) {
            // Flag stays unset so a later launch (or a build that bundles the
            // library) can run the import again. The app works meanwhile with
            // whatever is already in the Room database.
        }
    }

    private fun copyAsset(context: Context, name: String, outName: String): File {
        val out = File(context.filesDir, outName)
        out.parentFile?.mkdirs()
        context.assets.open(name).use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        return out
    }

    private fun actualColumns(sql: SQLiteDatabase, table: String): Map<String, String> {
        val cols = mutableMapOf<String, String>()
        sql.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val ni = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (ni >= 0) cols[c.getString(ni).lowercase()] = c.getString(ni)
            }
        }
        return cols
    }

    private fun col(actual: Map<String, String>, vararg candidates: String): String? =
        candidates.firstOrNull { actual.containsKey(it) }?.let { actual[it] }

    private fun str(c: Cursor, name: String?): String? {
        if (name == null) return null
        val i = c.getColumnIndex(name)
        if (i < 0 || c.isNull(i)) return null
        return try { c.getString(i) } catch (t: Throwable) { null }
    }

    private fun dbl(c: Cursor, name: String?): Double? {
        if (name == null) return null
        val i = c.getColumnIndex(name)
        if (i < 0 || c.isNull(i)) return null
        return try { c.getDouble(i) } catch (t: Throwable) { null }
    }

    private fun toMillis(v: Long?): Long? {
        if (v == null || v <= 0L) return null
        return if (v < 100_000_000_000L) v * 1000L else v
    }

    private fun lng(c: Cursor, name: String?): Long? {
        if (name == null) return null
        val i = c.getColumnIndex(name)
        if (i < 0 || c.isNull(i)) return null
        return try { c.getLong(i) } catch (t: Throwable) { null }
    }

    private fun copyPhoto(context: Context, termuxPath: String, photoDir: File): String? {
        val base = termuxPath.substringAfterLast('/').substringAfterLast('\\')
        if (base.isBlank()) return null
        return try {
            val out = File(photoDir, base)
            if (!out.exists() || out.length() == 0L) {
                context.assets.open("photos/$base").use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
            }
            out.absolutePath
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun importAll(context: Context, localDb: File, db: AppDatabase) {
        val sql = SQLiteDatabase.openDatabase(localDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val recipeCols = actualColumns(sql, "recipes")
            val ingCols = actualColumns(sql, "ingredients")
            val stepCols = actualColumns(sql, "steps")
            val photoDir = File(context.filesDir, "photos")
            photoDir.mkdirs()

            val tId = col(recipeCols, "id")
            val tTitle = col(recipeCols, "title", "name", "recipe_title")
            val tServings = col(recipeCols, "base_servings", "servings", "baseServings")
            val tPrep = col(recipeCols, "prep_time_minutes", "prep_minutes", "prep_time", "prepTimeMinutes")
            val tCook = col(recipeCols, "cook_time_minutes", "cook_minutes", "cook_time", "cookTimeMinutes")
            val tPhoto = col(recipeCols, "photo_path", "photoPath", "image_path", "photo", "originalImagePath")
            val tNotes = col(recipeCols, "notes", "note")
            val tSource = col(recipeCols, "source_type", "sourceType", "source")
            val tSourceNote = col(recipeCols, "source_note", "sourceNote")
            val tAdded = col(recipeCols, "date_added", "dateAdded", "created_at")
            val tModified = col(recipeCols, "date_modified", "dateModified", "updated_at")

            val tIngWhere = col(ingCols, "recipe_id", "recipeId")
            val tIngName = col(ingCols, "name", "ingredient")
            val tIngRaw = col(ingCols, "raw_text", "rawText", "raw_line", "text", "original")
            val tIngAmount = col(ingCols, "amount", "qty", "quantity")
            val tIngUnit = col(ingCols, "unit", "units")
            val tIngOrder = col(ingCols, "sort_order", "sortOrder", "order", "position", "idx")

            val tStepWhere = col(stepCols, "recipe_id", "recipeId")
            val tStepNum = col(stepCols, "step_number", "stepNumber", "number")
            val tStepText = col(stepCols, "instruction_text", "instructionText", "instruction", "text")
            val tStepTimer = col(stepCols, "timer_seconds", "timerSeconds", "timer")

            db.withTransaction {
                sql.rawQuery("SELECT * FROM recipes ORDER BY id", null).use { c ->
                    while (c.moveToNext()) {
                        val oldId = lng(c, tId) ?: 0L
                        val now = System.currentTimeMillis()
                        val added = toMillis(lng(c, tAdded)) ?: now
                        val photoAsset = str(c, tPhoto)
                        val recipe = Recipe(
                            id = 0,
                            title = str(c, tTitle)?.takeIf { it.isNotBlank() } ?: "(untitled)",
                            baseServings = ((dbl(c, tServings) ?: 1.0)).roundToInt().coerceAtLeast(1),
                            sourceType = str(c, tSource)?.takeIf { it.isNotBlank() } ?: "termux",
                            sourceNote = str(c, tSourceNote),
                            originalImagePath = photoAsset?.let { copyPhoto(context, it, photoDir) },
                            notes = str(c, tNotes)?.takeIf { it.isNotBlank() },
                            prepTimeMinutes = dbl(c, tPrep)?.roundToInt(),
                            cookTimeMinutes = dbl(c, tCook)?.roundToInt(),
                            dateAdded = added,
                            dateModified = toMillis(lng(c, tModified)) ?: added,
                        )
                        val rid = db.recipeDao().insert(recipe)
                        if (tIngWhere != null) {
                            val ings = readIngredients(sql, tIngWhere, oldId, rid, tIngName, tIngRaw, tIngAmount, tIngUnit, tIngOrder)
                            if (ings.isNotEmpty()) db.ingredientDao().insertAll(ings)
                        }
                        if (tStepWhere != null) {
                            val steps = readSteps(sql, tStepWhere, oldId, rid, tStepNum, tStepText, tStepTimer)
                            if (steps.isNotEmpty()) db.stepDao().insertAll(steps)
                        }
                    }
                }
            }
        } finally {
            sql.close()
        }
    }

    private fun readIngredients(
        sql: SQLiteDatabase,
        whereCol: String,
        oldRecipeId: Long,
        newRecipeId: Long,
        tName: String?,
        tRaw: String?,
        tAmount: String?,
        tUnit: String?,
        tOrder: String?,
    ): List<Ingredient> {
        val items = mutableListOf<Ingredient>()
        sql.rawQuery("SELECT * FROM ingredients WHERE $whereCol = $oldRecipeId ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                items.add(
                    Ingredient(
                        recipeId = newRecipeId,
                        name = str(c, tName)?.takeIf { it.isNotBlank() }
                            ?: str(c, tRaw)?.takeIf { it.isNotBlank() }
                            ?: "(ingredient)",
                        rawText = str(c, tRaw) ?: str(c, tName) ?: "",
                        amount = dbl(c, tAmount),
                        unit = str(c, tUnit)?.takeIf { it.isNotBlank() },
                        sortOrder = (lng(c, tOrder) ?: items.size.toLong()).toInt(),
                    )
                )
            }
        }
        return items
    }

    private fun readSteps(
        sql: SQLiteDatabase,
        whereCol: String,
        oldRecipeId: Long,
        newRecipeId: Long,
        tNum: String?,
        tText: String?,
        tTimer: String?,
    ): List<Step> {
        val items = mutableListOf<Step>()
        sql.rawQuery("SELECT * FROM steps WHERE $whereCol = $oldRecipeId ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                val text = str(c, tText)?.takeIf { it.isNotBlank() } ?: continue
                items.add(
                    Step(
                        recipeId = newRecipeId,
                        stepNumber = (lng(c, tNum) ?: (items.size + 1).toLong()).toInt(),
                        instructionText = text,
                        timerSeconds = lng(c, tTimer)?.toInt(),
                    )
                )
            }
        }
        return items
    }
}
