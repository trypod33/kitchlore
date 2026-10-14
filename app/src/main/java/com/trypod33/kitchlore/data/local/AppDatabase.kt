package com.trypod33.kitchlore.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.trypod33.kitchlore.data.local.dao.*
import com.trypod33.kitchlore.data.local.entity.*

@Database(
    entities = [
        Recipe::class,
        Ingredient::class,
        Step::class,
        NutritionReference::class,
        GlossaryTerm::class,
        CorrectionRule::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun stepDao(): StepDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun glossaryDao(): GlossaryDao
    abstract fun correctionRuleDao(): CorrectionRuleDao

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
        val MIGRATIONS: Array<Migration> = arrayOf(
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `correction_rules` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`garbled` TEXT NOT NULL, `replacement` TEXT NOT NULL, " +
                            "`count` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_correction_rules_garbled` " +
                            "ON `correction_rules` (`garbled`)"
                    )
                }
            },
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `recipes` ADD COLUMN `tagsCsv` TEXT NOT NULL DEFAULT ''")
                }
            },
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `recipes` ADD COLUMN `scaledServings` INTEGER NOT NULL DEFAULT 0")
                }
            }
        )
    }
}
