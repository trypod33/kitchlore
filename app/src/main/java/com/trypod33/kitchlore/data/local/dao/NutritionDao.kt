package com.trypod33.kitchlore.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.trypod33.kitchlore.data.local.entity.NutritionReference

@Dao
interface NutritionDao {
    @Query("SELECT * FROM nutrition_reference WHERE foodName LIKE '%' || :name || '%' LIMIT 1")
    suspend fun findByName(name: String): NutritionReference?

    @Query("SELECT * FROM nutrition_reference WHERE id = :id")
    suspend fun getById(id: Long): NutritionReference?
}
