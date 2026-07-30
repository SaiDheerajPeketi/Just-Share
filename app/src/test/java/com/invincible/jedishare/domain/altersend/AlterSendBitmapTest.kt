package com.invincible.jedishare.domain.altersend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlterSendBitmapTest {
    @Test
    fun tracksMissingChunksUsingAlterSendBitOrder() {
        val bitmap = AlterSendBitmap(10)

        bitmap.set(0)
        bitmap.set(3)
        bitmap.set(9)

        assertTrue(bitmap.get(0))
        assertTrue(bitmap.get(3))
        assertTrue(bitmap.get(9))
        assertFalse(bitmap.get(1))
        assertEquals(3, bitmap.count())
        assertEquals(listOf(1, 2, 4, 5, 6, 7, 8), bitmap.missing())
        assertFalse(bitmap.allSet())
    }

    @Test
    fun serializesAndDeserializesResumeBits() {
        val bitmap = AlterSendBitmap(9)
        bitmap.set(1)
        bitmap.set(8)

        val restored = AlterSendBitmap.deserialize(9, bitmap.serialize())

        assertEquals(2, restored.count())
        assertTrue(restored.get(1))
        assertTrue(restored.get(8))
        assertEquals(listOf(0, 2, 3, 4, 5, 6, 7), restored.missing())
    }
}
