package com.trypod33.kitchlore.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.trypod33.kitchlore.data.local.dao.*
import com.trypod33.kitchlore.data.local.entity.*

@Database(
    entities = [
        Recipe::class,
        Ingredient::class,
        Step::class,
        NutritionReference::class,
        GlossaryTerm::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun stepDao(): StepDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun glossaryDao(): GlossaryDao

    companion object {
        /**
         * One Migration per schema bump, in order. When an entity changes:
         * bump `version` and add the migration here (AutoMigration for
         * additive changes, a hand-written Migration for anything destructive
         * or reshaping), then build against schemas/<old>.json to make sure the
         * SQL really upgrades the previous shape. Never add
         * fallbackToDestructiveMigration: an update must never wipe or crash a
         * real user's saved library.
         */
        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}
