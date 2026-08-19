package com.formula2latex.data.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawingExporterTest {
    @Test fun findsTightInkBounds() {
        val pixels = IntArray(25) { 0xffffffff.toInt() }
        pixels[1 * 5 + 2] = 0xff000000.toInt()
        pixels[3 * 5 + 4] = 0xff101010.toInt()
        assertEquals(PixelBounds(2, 1, 4, 3), DrawingExporter.findInkBounds(pixels, 5, 5))
    }

    @Test fun rejectsBlankPixels() {
        assertNull(DrawingExporter.findInkBounds(IntArray(9) { 0xffffffff.toInt() }, 3, 3))
    }
}
