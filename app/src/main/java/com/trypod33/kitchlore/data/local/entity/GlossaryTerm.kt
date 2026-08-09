package com.trypod33.kitchlore.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "glossary_terms")
data class GlossaryTerm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val term: String,
    val definition: String,
    val videoUrl: String? = null,
    val localExplainerPath: String? = null
)
