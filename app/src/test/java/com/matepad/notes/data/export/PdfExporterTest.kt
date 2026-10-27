package com.matepad.notes.data.export

import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfExporterTest {

    @Test
    fun testCalculateDocumentBounds() {
        val strokes = listOf(
            Stroke("1", ToolType.PEN, 0, 2f, listOf(StrokePoint(10f, 20f)), RectF(10f, 20f, 50f, 80f)),
            Stroke("2", ToolType.PEN, 0, 2f, listOf(StrokePoint(0f, 0f)), RectF(0f, 0f, 100f, 200f))
        )
        val fullBounds = PdfExporter.calculateEnclosingBounds(strokes)
        assertEquals(0f, fullBounds.left, 0.001f)
        assertEquals(0f, fullBounds.top, 0.001f)
        assertEquals(100f, fullBounds.right, 0.001f)
        assertEquals(200f, fullBounds.bottom, 0.001f)
    }

    @Test
    fun testCalculateDocumentBoundsEmpty() {
        val emptyStrokes = emptyList<Stroke>()
        val defaultBounds = PdfExporter.calculateEnclosingBounds(emptyStrokes)

        // Default A4 dimensions at 72 DPI (595 x 842 pt)
        assertEquals(0f, defaultBounds.left, 0.001f)
        assertEquals(0f, defaultBounds.top, 0.001f)
        assertEquals(595f, defaultBounds.right, 0.001f)
        assertEquals(842f, defaultBounds.bottom, 0.001f)
        assertEquals(595f, defaultBounds.width, 0.001f)
        assertEquals(842f, defaultBounds.height, 0.001f)
    }

    @Test
    fun testCalculateDocumentBoundsSingleStroke() {
        val strokes = listOf(
            Stroke("s1", ToolType.PEN, 0xFF0000, 3f, listOf(StrokePoint(15f, 25f)), RectF(15f, 25f, 75f, 120f))
        )
        val bounds = PdfExporter.calculateEnclosingBounds(strokes)
        assertEquals(15f, bounds.left, 0.001f)
        assertEquals(25f, bounds.top, 0.001f)
        assertEquals(75f, bounds.right, 0.001f)
        assertEquals(120f, bounds.bottom, 0.001f)
    }

    @Test
    fun testCalculateDocumentBoundsNegativeCoordinates() {
        val strokes = listOf(
            Stroke("s1", ToolType.PEN, 0, 2f, listOf(StrokePoint(-120f, -80f)), RectF(-120f, -80f, -40f, -10f)),
            Stroke("s2", ToolType.HIGHLIGHTER, 0, 5f, listOf(StrokePoint(-30f, 10f)), RectF(-30f, 10f, 80f, 150f))
        )
        val bounds = PdfExporter.calculateEnclosingBounds(strokes)
        assertEquals(-120f, bounds.left, 0.001f)
        assertEquals(-80f, bounds.top, 0.001f)
        assertEquals(80f, bounds.right, 0.001f)
        assertEquals(150f, bounds.bottom, 0.001f)
    }

    @Test
    fun testCalculatePageDimensionsWithMargin() {
        val bounds = RectF(0f, 0f, 200f, 300f)
        val margin = 25f
        val pageDims = PdfExporter.calculatePageDimensions(bounds, margin = margin)

        // Width: 200 + 2 * 25 = 250
        assertEquals(250, pageDims.width)
        // Height: 300 + 2 * 25 = 350
        assertEquals(350, pageDims.height)
        assertEquals(margin, pageDims.margin, 0.001f)
        assertEquals(bounds, pageDims.contentBounds)
    }

    @Test
    fun testCalculatePageDimensionsClampsToMinimum() {
        val tinyBounds = RectF(10f, 10f, 12f, 12f) // 2x2 width & height
        val pageDims = PdfExporter.calculatePageDimensions(
            bounds = tinyBounds,
            margin = 5f,
            minWidth = 100f,
            minHeight = 120f
        )

        // Raw width: 2 + 10 = 12, clamped to minWidth = 100
        assertEquals(100, pageDims.width)
        // Raw height: 2 + 10 = 12, clamped to minHeight = 120
        assertEquals(120, pageDims.height)
    }

    @Test
    fun testCalculateDynamicStrokeWidth() {
        val pointsAverage = listOf(
            StrokePoint(0f, 0f, 0.5f),
            StrokePoint(10f, 10f, 0.5f)
        )
        // Dynamic formula: baseWidth * (0.3f + 1.4f * avgPressure)
        // For pressure 0.5: 4.0 * (0.3 + 0.7) = 4.0
        val widthNorm = PdfExporter.calculateDynamicStrokeWidth(4.0f, pointsAverage)
        assertEquals(4.0f, widthNorm, 0.001f)

        // Full pressure: 1.0 -> 4.0 * (0.3 + 1.4) = 6.8
        val pointsMax = listOf(StrokePoint(0f, 0f, 1.0f))
        val widthMax = PdfExporter.calculateDynamicStrokeWidth(4.0f, pointsMax)
        assertEquals(6.8f, widthMax, 0.001f)

        // Minimum pressure: 0.0 -> 4.0 * 0.3 = 1.2
        val pointsMin = listOf(StrokePoint(0f, 0f, 0.0f))
        val widthMin = PdfExporter.calculateDynamicStrokeWidth(4.0f, pointsMin)
        assertEquals(1.2f, widthMin, 0.001f)

        // Empty points fallback
        val widthEmpty = PdfExporter.calculateDynamicStrokeWidth(4.0f, emptyList())
        assertEquals(4.0f, widthEmpty, 0.001f)
    }

    @Test
    fun testIsDrawableTool() {
        assertTrue(PdfExporter.isDrawableTool(ToolType.PEN))
        assertTrue(PdfExporter.isDrawableTool(ToolType.HIGHLIGHTER))
        assertFalse(PdfExporter.isDrawableTool(ToolType.STROKE_ERASER))
        assertFalse(PdfExporter.isDrawableTool(ToolType.SEGMENT_ERASER))
        assertFalse(PdfExporter.isDrawableTool(ToolType.LASSO))
    }

    @Test
    fun testCalculateEnclosingBoundsFiltersNonDrawable() {
        val penStroke = Stroke("1", ToolType.PEN, 0, 2f, listOf(StrokePoint(10f, 10f)), RectF(10f, 10f, 50f, 50f))
        val eraserStroke = Stroke("2", ToolType.STROKE_ERASER, 0, 10f, listOf(StrokePoint(0f, 0f)), RectF(-1000f, -1000f, 1000f, 1000f))

        // When filtering drawable strokes only, eraser bounds should be ignored
        val filteredBounds = PdfExporter.calculateEnclosingBounds(listOf(penStroke, eraserStroke), filterDrawableOnly = true)
        assertEquals(10f, filteredBounds.left, 0.001f)
        assertEquals(10f, filteredBounds.top, 0.001f)
        assertEquals(50f, filteredBounds.right, 0.001f)
        assertEquals(50f, filteredBounds.bottom, 0.001f)

        // When not filtering, all strokes are included
        val unfilteredBounds = PdfExporter.calculateEnclosingBounds(listOf(penStroke, eraserStroke), filterDrawableOnly = false)
        assertEquals(-1000f, unfilteredBounds.left, 0.001f)
        assertEquals(1000f, unfilteredBounds.right, 0.001f)
    }
}
