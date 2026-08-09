package com.trypod33.kitchlore.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recipeId"), Index("matchedNutritionId")]
)
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val name: String,
    val rawText: String,
    val amount: Double?,
    val unit: String?,
    val sortOrder: Int,
    val matchedNutritionId: Long? = null
)
