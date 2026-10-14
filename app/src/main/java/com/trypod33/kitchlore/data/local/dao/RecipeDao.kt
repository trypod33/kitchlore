package com.trypod33.kitchlore.data.local.dao

import androidx.room.*
import com.trypod33.kitchlore.data.local.entity.Recipe
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Insert
    suspend fun insert(recipe: Recipe): Long

    @Update
    suspend fun update(recipe: Recipe)

    @Delete
    suspend fun delete(recipe: Recipe)

    @Query("SELECT * FROM recipes ORDER BY dateModified DESC")
    fun getAllRecipes(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE id = :recipeId")
    suspend fun getRecipeById(recipeId: Long): Recipe?

    @Query("SELECT * FROM recipes ORDER BY dateModified DESC")
    suspend fun getAllRecipesOnce(): List<Recipe>

    @Query("SELECT * FROM recipes WHERE title LIKE '%' || :query || '%'")
    fun searchByTitle(query: String): Flow<List<Recipe>>

    @Query("UPDATE recipes SET favorite = :favorite WHERE id = :recipeId")
    suspend fun setFavorite(recipeId: Long, favorite: Boolean)
}
