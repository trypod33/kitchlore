package com.trypod33.kitchlore.data.local.dao

import androidx.room.*
import com.trypod33.kitchlore.data.local.entity.Ingredient
import kotlinx.coroutines.flow.Flow

@Dao
interface IngredientDao {
    @Insert
    suspend fun insertAll(ingredients: List<Ingredient>)

    @Update
    suspend fun update(ingredient: Ingredient)

    @Delete
    suspend fun delete(ingredient: Ingredient)

    @Query("SELECT * FROM ingredients WHERE recipeId = :recipeId ORDER BY sortOrder ASC")
    fun getIngredientsForRecipe(recipeId: Long): Flow<List<Ingredient>>

    @Query("SELECT * FROM ingredients WHERE recipeId = :recipeId ORDER BY sortOrder ASC")
    suspend fun getIngredientsForRecipeOnce(recipeId: Long): List<Ingredient>

    @Query("SELECT * FROM ingredients")
    suspend fun getAllOnce(): List<Ingredient>

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    suspend fun deleteAllForRecipe(recipeId: Long)
}
