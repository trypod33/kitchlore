package com.trypod33.kitchlore.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nutrition_reference")
data class NutritionReference(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodName: String,
    val caloriesPer100g: Double,
    val proteinGPer100g: Double,
    val fatGPer100g: Double,
    val carbsGPer100g: Double,
    val fiberGPer100g: Double,
    val sugarGPer100g: Double,
    val sodiumMgPer100g: Double
)
