package org.avmedia.gshockapi.io

import org.avmedia.gshockapi.model.ExerciseProfile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseIOTest {
    private fun bytes(vararg v: Int) = v.map { it.toByte() }.toByteArray()

    @Test
    fun `encodes goal as BCD and height and weight as inverted BCD`() {
        // 10000 steps -> 00 00 01 00; 180 cm -> 80 01 inverted 7F FE; 75 kg -> 75 00 inverted 8A FF
        assertArrayEquals(
            bytes(0x2D, 0x00, 0x00, 0x00, 0x01, 0x00, 0x7F, 0xFE, 0x8A, 0xFF),
            ExerciseIO.encode(ExerciseProfile(stepGoal = 10000, height = 180, weight = 75)),
        )
    }

    @Test
    fun `decode reverses encode`() {
        val profile = ExerciseProfile(stepGoal = 8500, height = 172, weight = 68)
        assertEquals(profile, ExerciseIO.decode(ExerciseIO.encode(profile)))
    }
}
