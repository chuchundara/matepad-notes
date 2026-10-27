package com.matepad.notes.ui.canvas.render

import com.matepad.notes.domain.geometry.PointF
import com.matepad.notes.domain.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeMeshGeneratorTest {

    @Test
    fun testComputeBoundsWithBaseWidth() {
        val points = listOf(
            StrokePoint(10f, 10f, 1.0f),
            StrokePoint(50f, 80f, 1.0f)
        )
        val bounds = StrokeMeshGenerator.computeBounds(points, baseWidth = 4.0f)

        assertEquals(8f, bounds.left, 0.001f)
        assertEquals(8f, bounds.top, 0.001f)
        assertEquals(52f, bounds.right, 0.001f)
        assertEquals(82f, bounds.bottom, 0.001f)
    }

    @Test
    fun testComputeBoundsEmptyPoints() {
        val bounds = StrokeMeshGenerator.computeBounds(emptyList(), baseWidth = 4.0f)
        assertEquals(0f, bounds.left, 0.001f)
        assertEquals(0f, bounds.top, 0.001f)
        assertEquals(0f, bounds.right, 0.001f)
        assertEquals(0f, bounds.bottom, 0.001f)
    }

    @Test
    fun testComputeBoundsSinglePoint() {
        val points = listOf(StrokePoint(20f, 30f, 0.5f))
        val bounds = StrokeMeshGenerator.computeBounds(points, baseWidth = 6.0f)

        assertEquals(17f, bounds.left, 0.001f)
        assertEquals(27f, bounds.top, 0.001f)
        assertEquals(23f, bounds.right, 0.001f)
        assertEquals(33f, bounds.bottom, 0.001f)
    }

    @Test
    fun testComputeBoundsNegativeCoordinates() {
        val points = listOf(
            StrokePoint(-40f, -60f, 0.8f),
            StrokePoint(-10f, -20f, 0.4f)
        )
        val bounds = StrokeMeshGenerator.computeBounds(points, baseWidth = 2.0f)

        assertEquals(-41f, bounds.left, 0.001f)
        assertEquals(-61f, bounds.top, 0.001f)
        assertEquals(-9f, bounds.right, 0.001f)
        assertEquals(-19f, bounds.bottom, 0.001f)
    }

    @Test
    fun testCatmullRomSplineEmptyAndSinglePoint() {
        val emptySpline = StrokeMeshGenerator.computeCatmullRomSpline(emptyList())
        assertTrue(emptySpline.isEmpty())

        val single = listOf(StrokePoint(10f, 10f))
        val singleSpline = StrokeMeshGenerator.computeCatmullRomSpline(single)
        assertEquals(1, singleSpline.size)
        assertEquals(10f, singleSpline[0].x, 0.001f)
        assertEquals(10f, singleSpline[0].y, 0.001f)
    }

    @Test
    fun testCatmullRomToBezierSegments() {
        val points = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(10f, 20f),
            StrokePoint(20f, 0f),
            StrokePoint(30f, 20f)
        )
        val segments = StrokeMeshGenerator.computeCatmullRomToBezier(points)

        // 4 points produce 3 cubic bezier segments connecting them
        assertEquals(3, segments.size)

        // First segment starts at P0 and ends at P1
        assertEquals(0f, segments[0].p0.x, 0.001f)
        assertEquals(0f, segments[0].p0.y, 0.001f)
        assertEquals(10f, segments[0].p1.x, 0.001f)
        assertEquals(20f, segments[0].p1.y, 0.001f)

        // Second segment connects P1 to P2
        assertEquals(10f, segments[1].p0.x, 0.001f)
        assertEquals(20f, segments[1].p0.y, 0.001f)
        assertEquals(20f, segments[1].p1.x, 0.001f)
        assertEquals(0f, segments[1].p1.y, 0.001f)

        // Third segment connects P2 to P3
        assertEquals(20f, segments[2].p0.x, 0.001f)
        assertEquals(0f, segments[2].p0.y, 0.001f)
        assertEquals(30f, segments[2].p1.x, 0.001f)
        assertEquals(20f, segments[2].p1.y, 0.001f)
    }

    @Test
    fun testTwoPointsBezierLinearity() {
        val points = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(30f, 60f)
        )
        val segments = StrokeMeshGenerator.computeCatmullRomToBezier(points)
        assertEquals(1, segments.size)

        val seg = segments[0]
        assertEquals(10f, seg.cp1.x, 0.001f)
        assertEquals(20f, seg.cp1.y, 0.001f)
        assertEquals(20f, seg.cp2.x, 0.001f)
        assertEquals(40f, seg.cp2.y, 0.001f)

        // Midpoint evaluation at t = 0.5 lies exactly halfway on the line
        val mid = seg.evaluate(0.5f)
        assertEquals(15f, mid.x, 0.001f)
        assertEquals(30f, mid.y, 0.001f)
    }

    @Test
    fun testCubicBezierEvaluateAtExtremes() {
        val seg = StrokeMeshGenerator.CubicBezierSegment(
            p0 = PointF(10f, 20f),
            cp1 = PointF(15f, 30f),
            cp2 = PointF(25f, 40f),
            p1 = PointF(30f, 50f)
        )

        val start = seg.evaluate(0f)
        assertEquals(10f, start.x, 0.001f)
        assertEquals(20f, start.y, 0.001f)

        val end = seg.evaluate(1f)
        assertEquals(30f, end.x, 0.001f)
        assertEquals(50f, end.y, 0.001f)

        // Clamping negative t
        val under = seg.evaluate(-0.5f)
        assertEquals(10f, under.x, 0.001f)
        assertEquals(20f, under.y, 0.001f)

        // Clamping t > 1
        val over = seg.evaluate(1.5f)
        assertEquals(30f, over.x, 0.001f)
        assertEquals(50f, over.y, 0.001f)
    }

    @Test
    fun testComputeCatmullRomSplineInterpolation() {
        val points = listOf(
            StrokePoint(0f, 0f),
            StrokePoint(50f, 100f),
            StrokePoint(100f, 0f)
        )
        val sampled = StrokeMeshGenerator.computeCatmullRomSpline(points, samplesPerSegment = 4)

        // Starts at first point
        assertEquals(0f, sampled.first().x, 0.001f)
        assertEquals(0f, sampled.first().y, 0.001f)

        // Ends at last point
        assertEquals(100f, sampled.last().x, 0.001f)
        assertEquals(0f, sampled.last().y, 0.001f)

        // Sample count is (points.size - 1) * samplesPerSegment + 1
        assertEquals(9, sampled.size)
    }
}
