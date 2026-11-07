package com.trypod33.kitchlore

import com.trypod33.kitchlore.data.RecipeLlm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The review button used to sit dead behind an AI pass that could hang
// forever; RecipeLlm.guarded() is the hard deadline that keeps every AI step bounded.
class RecipeLlmGuardTest {
    @Test
    fun guardedReturnsTheValueWhenTheBlockFinishesInTime() {
        assertEquals("ok", RecipeLlm.guarded(2000L) { "ok" })
    }

    @Test
    fun guardedGivesUpWhenTheBlockHangs() {
        val start = System.currentTimeMillis()
        val out = RecipeLlm.guarded(300L) { Thread.sleep(3000L); "late" }
        assertNull(out)
        assertTrue(System.currentTimeMillis() - start < 2000L)
    }
}
