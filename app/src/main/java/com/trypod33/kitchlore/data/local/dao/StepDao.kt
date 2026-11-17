package com.trypod33.kitchlore.data.local.dao

import androidx.room.*
import com.trypod33.kitchlore.data.local.entity.Step
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {
    @Insert
    suspend fun insertAll(steps: List<Step>)

    @Update
    suspend fun update(step: Step)

    @Query("SELECT * FROM steps WHERE recipeId = :recipeId ORDER BY stepNumber ASC")
    fun getStepsForRecipe(recipeId: Long): Flow<List<Step>>

    @Query("SELECT * FROM steps WHERE recipeId = :recipeId ORDER BY stepNumber ASC")
    suspend fun getStepsForRecipeOnce(recipeId: Long): List<Step>

    @Query("DELETE FROM steps WHERE recipeId = :recipeId")
    suspend fun deleteAllForRecipe(recipeId: Long)
}
