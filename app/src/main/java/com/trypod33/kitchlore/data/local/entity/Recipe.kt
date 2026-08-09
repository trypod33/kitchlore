package com.trypod33.kitchlore.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val baseServings: Int,
    val sourceType: String,
    val sourceNote: String? = null,
    val originalImagePath: String? = null,
    val notes: String? = null,
    val prepTimeMinutes: Int? = null,
    val cookTimeMinutes: Int? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis()
)
