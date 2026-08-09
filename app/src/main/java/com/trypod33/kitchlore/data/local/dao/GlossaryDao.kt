package com.trypod33.kitchlore.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.trypod33.kitchlore.data.local.entity.GlossaryTerm

@Dao
interface GlossaryDao {
    @Insert
    suspend fun insertAll(terms: List<GlossaryTerm>)

    @Query("SELECT * FROM glossary_terms")
    suspend fun getAllTerms(): List<GlossaryTerm>

    @Query("SELECT * FROM glossary_terms WHERE term = :term LIMIT 1")
    suspend fun findByTerm(term: String): GlossaryTerm?
}
