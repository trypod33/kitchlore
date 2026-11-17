package com.trypod33.kitchlore.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.trypod33.kitchlore.data.local.entity.CorrectionRule

@Dao
interface CorrectionRuleDao {
    @Query("SELECT * FROM correction_rules ORDER BY count DESC")
    suspend fun getAllOnce(): List<CorrectionRule>

    @Query("SELECT * FROM correction_rules WHERE garbled = :garbled LIMIT 1")
    suspend fun find(garbled: String): CorrectionRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CorrectionRule)
}
