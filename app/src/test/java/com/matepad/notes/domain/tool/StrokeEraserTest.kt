package com.matepad.notes.domain.tool

import com.matepad.notes.domain.geometry.PointF
import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import com.matepad.notes.domain.spatial.QuadTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeEraserTest {

    @Test
    fun testFindIntersectingStrokes() {
        val stroke = Stroke(
            id = "s1",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(10f, 10f), StrokePoint(20f, 20f)),
            bounds = RectF(10f, 10f, 20f, 20f)
        )

        val hitEraserPoint = PointF(15f, 15f)
        val hit = StrokeEraser.isStrokeHit(stroke, hitEraserPoint, eraserRadius = 5f)
        assertEquals(true, hit)

        val missEraserPoint = PointF(100f, 100f)
        val miss = StrokeEraser.isStrokeHit(stroke, missEraserPoint, eraserRadius = 5f)
        assertEquals(false, miss)
    }

    @Test
    fun testHitVertexProximitySinglePoint() {
        val dotStroke = Stroke(
            id = "dot",
            toolType = ToolType.PEN,
            color = 0xFF0000,
            baseWidth = 4f,
            points = listOf(StrokePoint(50f, 50f)),
            bounds = RectF(48f, 48f, 52f, 52f)
        )

        // Within radius (radius = 5, baseWidth/2 = 2 => threshold = 7)
        assertTrue(StrokeEraser.isStrokeHit(dotStroke, PointF(54f, 50f), eraserRadius = 5f))
        // Outside radius (dist = 10 > 7)
        assertFalse(StrokeEraser.isStrokeHit(dotStroke, PointF(60f, 50f), eraserRadius = 5f))
    }

    @Test
    fun testStrokeBaseWidthInfluence() {
        val stroke = Stroke(
            id = "wide",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 10f,
            points = listOf(StrokePoint(0f, 0f), StrokePoint(100f, 0f)),
            bounds = RectF(-5f, -5f, 105f, 5f)
        )

        // Point is at (50, 6). Perpendicular distance to centerline y=0 is 6.
        // Eraser radius 2 + baseWidth/2 (5) = 7. 6 <= 7 => HIT
        assertTrue(StrokeEraser.isStrokeHit(stroke, PointF(50f, 6f), eraserRadius = 2f))

        // Point is at (50, 8). Perpendicular distance is 8. 8 > 7 => MISS
        assertFalse(StrokeEraser.isStrokeHit(stroke, PointF(50f, 8f), eraserRadius = 2f))
    }

    @Test
    fun testSweepingEraserMotion() {
        val verticalStroke = Stroke(
            id = "vertical",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(50f, 10f), StrokePoint(50f, 90f)),
            bounds = RectF(49f, 9f, 51f, 91f)
        )

        // Sweeping horizontally across the stroke from x=20 to x=80 at y=50
        val sweepStart = PointF(20f, 50f)
        val sweepEnd = PointF(80f, 50f)
        assertTrue(StrokeEraser.isStrokeHit(verticalStroke, sweepStart, sweepEnd, eraserRadius = 3f))

        // Sweeping far to the left parallel to the stroke
        val missSweepStart = PointF(10f, 10f)
        val missSweepEnd = PointF(10f, 90f)
        assertFalse(StrokeEraser.isStrokeHit(verticalStroke, missSweepStart, missSweepEnd, eraserRadius = 3f))
    }

    @Test
    fun testFindIntersectingStrokesWithQuadTree() {
        val quadTree = QuadTree<Stroke>(
            boundary = RectF(0f, 0f, 1000f, 1000f),
            maxObjects = 4,
            maxLevels = 4,
            boundsExtractor = { it.bounds }
        )

        val s1 = Stroke(
            id = "s1",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(10f, 10f), StrokePoint(20f, 20f)),
            bounds = RectF(9f, 9f, 21f, 21f)
        )
        val s2 = Stroke(
            id = "s2",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(500f, 500f), StrokePoint(510f, 510f)),
            bounds = RectF(499f, 499f, 511f, 511f)
        )
        quadTree.insert(s1)
        quadTree.insert(s2)

        val hitResults = StrokeEraser.findIntersectingStrokes(quadTree, PointF(15f, 15f), eraserRadius = 5f)
        assertEquals(1, hitResults.size)
        assertEquals("s1", hitResults[0].id)

        val sweepResults = StrokeEraser.findIntersectingStrokes(
            quadTree,
            eraserStart = PointF(490f, 505f),
            eraserEnd = PointF(520f, 505f),
            eraserRadius = 2f
        )
        assertEquals(1, sweepResults.size)
        assertEquals("s2", sweepResults[0].id)

        val missResults = StrokeEraser.findIntersectingStrokes(quadTree, PointF(250f, 250f), eraserRadius = 10f)
        assertTrue(missResults.isEmpty())
    }

    @Test
    fun testEmptyPointsStroke() {
        val emptyStroke = Stroke(
            id = "empty",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = emptyList(),
            bounds = RectF(0f, 0f, 0f, 0f)
        )
        assertFalse(StrokeEraser.isStrokeHit(emptyStroke, PointF(0f, 0f), eraserRadius = 10f))
        assertFalse(StrokeEraser.isStrokeHit(emptyStroke, PointF(0f, 0f), PointF(10f, 10f), eraserRadius = 10f))
    }

    @Test
    fun testHitSegmentBetweenDistantEndpoints() {
        val longSegmentStroke = Stroke(
            id = "long-seg",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(0f, 0f), StrokePoint(1000f, 0f)),
            bounds = RectF(-1f, -1f, 1001f, 1f)
        )

        // Point is at (500, 1), perpendicular distance is 1.
        // Distance to endpoints is 500. With eraserRadius = 5, neither endpoint is touched,
        // but segment hit-testing finds the stroke.
        val hit = StrokeEraser.isStrokeHit(longSegmentStroke, PointF(500f, 1f), eraserRadius = 5f)
        assertTrue(hit)
    }
}
