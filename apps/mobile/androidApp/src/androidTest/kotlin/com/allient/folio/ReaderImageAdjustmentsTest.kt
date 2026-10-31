package com.allient.folio

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderImageAdjustmentsTest {
    private suspend fun adjusted(pixels: IntArray, settings: ReaderImageAdjustments): IntArray {
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        try {
            bitmap.setPixels(pixels, 0, 3, 0, 0, 3, 3)
            adjustReaderImage(bitmap, settings)
            return IntArray(9).also { bitmap.getPixels(it, 0, 3, 0, 0, 3, 3) }
        } finally { bitmap.recycle() }
    }

    @Test fun neutralSettingsPreservePixels() = runBlocking {
        val pixels = IntArray(9) { Color.rgb(it * 25, it * 20, it * 15) }
        assertArrayEquals(pixels, adjusted(pixels, ReaderImageAdjustments()))
    }

    @Test fun emboldenExpandsDarkEdgesAndSharpenIncreasesEdgeContrast() = runBlocking {
        val pixels = IntArray(9) { Color.WHITE }.apply { this[4] = Color.BLACK }
        val bold = adjusted(pixels, ReaderImageAdjustments(embolden = 5))
        assertEquals(Color.BLACK, bold[1])
        assertEquals(Color.WHITE, bold[0])
        val soft = IntArray(9) { Color.rgb(180, 180, 180) }.apply { this[4] = Color.rgb(100, 100, 100) }
        val sharp = adjusted(soft, ReaderImageAdjustments(sharpen = 5))
        assertTrue(Color.red(sharp[4]) < 100)
        assertTrue(Color.red(sharp[1]) > 180)
    }

    @Test fun bleachingLiftsPaleMarksAndDarkEnhancementPreservesWhite() = runBlocking {
        val gray = IntArray(9) { Color.rgb(190, 190, 190) }.apply { this[0] = Color.BLACK; this[8] = Color.WHITE }
        val bleached = adjusted(gray, ReaderImageAdjustments(bleach = 100))
        assertTrue(Color.red(bleached[4]) > 190)
        assertEquals(Color.BLACK, bleached[0])
        val darkened = adjusted(gray, ReaderImageAdjustments(darken = 100))
        assertTrue(Color.red(darkened[4]) < 190)
        assertEquals(Color.WHITE, darkened[8])
    }
}
