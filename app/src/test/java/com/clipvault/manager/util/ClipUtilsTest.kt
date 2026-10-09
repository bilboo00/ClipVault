package com.clipvault.manager.util

import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipUtilsTest {

    private fun clipWithExtras(extras: PersistableBundle?): ClipData {
        val desc = mockk<ClipDescription>()
        every { desc.extras } returns extras
        val clip = mockk<ClipData>()
        every { clip.description } returns desc
        return clip
    }

    @Test
    fun isMarkedSensitive_trueWhenProducerSetsExtra() {
        val extras = mockk<PersistableBundle>()
        every { extras.getBoolean("android.content.extra.IS_SENSITIVE", false) } returns true

        assertTrue(ClipUtils.isMarkedSensitive(clipWithExtras(extras)))
    }

    @Test
    fun isMarkedSensitive_falseWhenFlagIsFalse() {
        val extras = mockk<PersistableBundle>()
        every { extras.getBoolean("android.content.extra.IS_SENSITIVE", false) } returns false

        assertFalse(ClipUtils.isMarkedSensitive(clipWithExtras(extras)))
    }

    @Test
    fun isMarkedSensitive_falseWhenExtrasNull() {
        assertFalse(ClipUtils.isMarkedSensitive(clipWithExtras(null)))
    }

    @Test
    fun isMarkedSensitive_falseWhenClipNullOrDescriptionNull() {
        assertFalse(ClipUtils.isMarkedSensitive(null))

        val clip = mockk<ClipData>()
        every { clip.description } returns null
        assertFalse(ClipUtils.isMarkedSensitive(clip))
    }
}
