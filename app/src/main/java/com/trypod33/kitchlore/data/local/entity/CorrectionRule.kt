package com.trypod33.kitchlore.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// A measurement garble the user corrected by hand: the garbled token as the
// scanner read it ("%", "1%", "Vs", "hcup") and what it actually was. The
// rules are applied deterministically before the AI cleanup pass, so each
// fix only has to be made once.
@Entity(tableName = "correction_rules", indices = [Index(value = ["garbled"], unique = true)])
data class CorrectionRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val garbled: String,
    val replacement: String,
    val count: Int = 1,
    val updatedAt: Long = System.currentTimeMillis(),
)
