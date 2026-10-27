package com.matepad.notes.data.export

import com.matepad.notes.domain.geometry.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageExporterTest {

    @Test
    fun testCalculateDimensionsDefaultScaleAndMargin() {
        val bounds = RectF(0f, 0f, 100f, 200f)
        val dims = ImageExporter.calculateDimensions(
            bounds = bounds,
            scale = 1.0f,
            margin = 10f
        )

        // Raw width: 100 + 2 * 10 = 120
        // Raw height: 200 + 2 * 10 = 220
        assertEquals(120, dims.width)
        assertEquals(220, dims.height)
        assertEquals(1.0f, dims.scale, 0.001f)
        assertEquals(10f, dims.margin, 0.001f)
        assertEquals(bounds, dims.contentBounds)
    }

    @Test
    fun testCalculateDimensionsHighResolutionScale() {
        val bounds = RectF(0f, 0f, 100f, 200f)
        val dims = ImageExporter.calculateDimensions(
            bounds = bounds,
            scale = 2.0f,
            margin = 10f
        )

        // Scaled width: 120 * 2 = 240
        // Scaled height: 220 * 2 = 440
        assertEquals(240, dims.width)
        assertEquals(440, dims.height)
        assertEquals(2.0f, dims.scale, 0.001f)
    }

    @Test
    fun testCalculateDimensionsMaxDimensionDownscalesPortrait() {
        val bounds = RectF(0f, 0f, 1000f, 2000f)
        val dims = ImageExporter.calculateDimensions(
            bounds = bounds,
            scale = 1.0f,
            margin = 0f,
            maxDimension = 1000
        )

        // Height 2000 > maxDimension 1000 -> downscale by 0.5
        assertEquals(500, dims.width)
        assertEquals(1000, dims.height)
        assertEquals(0.5f, dims.scale, 0.001f)
    }

    @Test
    fun testCalculateDimensionsMaxDimensionDownscalesLandscape() {
        val bounds = RectF(0f, 0f, 4000f, 2000f)
        val dims = ImageExporter.calculateDimensions(
            bounds = bounds,
            scale = 1.0f,
            margin = 0f,
            maxDimension = 2000
        )

        // Width 4000 > maxDimension 2000 -> downscale by 0.5
        assertEquals(2000, dims.width)
        assertEquals(1000, dims.height)
        assertEquals(0.5f, dims.scale, 0.001f)
    }

    @Test
    fun testCalculateDimensionsMaxDimensionDoesNotUpscale() {
        val bounds = RectF(0f, 0f, 200f, 100f)
        val dims = ImageExporter.calculateDimensions(
            bounds = bounds,
            scale = 1.0f,
            margin = 0f,
            maxDimension = 1000
        )

        // Dimensions (200x100) are within 1000 -> no downscaling occurs
        assertEquals(200, dims.width)
        assertEquals(100, dims.height)
        assertEquals(1.0f, dims.scale, 0.001f)
    }

    @Test
    fun testCalculateDimensionsZeroAndNegativeBounds() {
        val zeroBounds = RectF(10f, 10f, 10f, 10f) // 0 width and height
        val dimsZero = ImageExporter.calculateDimensions(
            bounds = zeroBounds,
            scale = 1.0f,
            margin = 0f,
            minDimension = 1
        )

        assertTrue(dimsZero.width >= 1)
        assertTrue(dimsZero.height >= 1)

        val negativeBounds = RectF(50f, 50f, 10f, 10f) // negative width and height
        val dimsNeg = ImageExporter.calculateDimensions(
            bounds = negativeBounds,
            scale = 1.0f,
            margin = 0f,
            minDimension = 1
        )

        assertTrue(dimsNeg.width >= 1)
        assertTrue(dimsNeg.height >= 1)
    }

    @Test
    fun testNotePreviewDimensionsCalculation() {
        // Typical tablet aspect ratio (800x600) scaled to thumbnail preview (max 512)
        val bounds = RectF(0f, 0f, 800f, 600f)
        val dims = ImageExporter.calculateDimensions(
            bounds = bounds,
            scale = 1.0f,
            margin = 0f,
            maxDimension = 512
        )

        // Largest is 800 -> 512 / 800 = 0.64 scale
        // Width: 512, Height: 600 * 0.64 = 384
        assertEquals(512, dims.width)
        assertEquals(384, dims.height)
        assertEquals(0.64f, dims.scale, 0.001f)
    }

    @Test
    fun testExportFormatProperties() {
        assertEquals("png", ImageExporter.ExportFormat.PNG.extension)
        assertEquals("image/png", ImageExporter.ExportFormat.PNG.mimeType)

        assertEquals("jpg", ImageExporter.ExportFormat.JPEG.extension)
        assertEquals("image/jpeg", ImageExporter.ExportFormat.JPEG.mimeType)

        assertEquals("webp", ImageExporter.ExportFormat.WEBP.extension)
        assertEquals("image/webp", ImageExporter.ExportFormat.WEBP.mimeType)
    }
}
