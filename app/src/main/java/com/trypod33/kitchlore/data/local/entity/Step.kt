package com.trypod33.kitchlore.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recipeId")]
)
data class Step(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val stepNumber: Int,
    val instructionText: String,
    val timerSeconds: Int? = null
)
