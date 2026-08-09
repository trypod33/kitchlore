package com.trypod33.kitchlore.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
}
