package com.matepad.notes.domain.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportTransformTest {
    @Test
    fun testScreenToWorldAndBack() {
        val transform = ViewportTransform(scale = 2.0f, offsetX = 100.0f, offsetY = 200.0f)
        val screenPoint = PointF(300.0f, 400.0f)
        
        val worldPoint = transform.screenToWorld(screenPoint)
        assertEquals(100.0f, worldPoint.x, 0.001f)
        assertEquals(100.0f, worldPoint.y, 0.001f)
        
        val roundTripScreen = transform.worldToScreen(worldPoint)
        assertEquals(screenPoint.x, roundTripScreen.x, 0.001f)
        assertEquals(screenPoint.y, roundTripScreen.y, 0.001f)
    }

    @Test
    fun testZoomClamping() {
        val transform = ViewportTransform(scale = 1.0f, offsetX = 0.0f, offsetY = 0.0f)
        val zoomedOut = transform.withScale(0.01f)
        assertEquals(0.1f, zoomedOut.scale, 0.001f)

        val zoomedIn = transform.withScale(100.0f)
        assertEquals(50.0f, zoomedIn.scale, 0.001f)
    }

    @Test
    fun testZoomAtFocalPointPreservesScreenPosition() {
        val transform = ViewportTransform(scale = 1.0f, offsetX = 50.0f, offsetY = 50.0f)
        val focalScreenPoint = PointF(400.0f, 300.0f)
        val worldBefore = transform.screenToWorld(focalScreenPoint)

        val zoomed = transform.withScaleAt(3.0f, focalScreenPoint)
        assertEquals(3.0f, zoomed.scale, 0.001f)

        val screenAfter = zoomed.worldToScreen(worldBefore)
        assertEquals(focalScreenPoint.x, screenAfter.x, 0.001f)
        assertEquals(focalScreenPoint.y, screenAfter.y, 0.001f)
    }

    @Test
    fun testPanBy() {
        val transform = ViewportTransform(scale = 1.5f, offsetX = 10.0f, offsetY = 20.0f)
        val panned = transform.panBy(30.0f, -10.0f)
        assertEquals(40.0f, panned.offsetX, 0.001f)
        assertEquals(10.0f, panned.offsetY, 0.001f)
        assertEquals(1.5f, panned.scale, 0.001f)
    }

    @Test
    fun testVisibleWorldBounds() {
        val transform = ViewportTransform(scale = 2.0f, offsetX = 100.0f, offsetY = 200.0f)
        val bounds = transform.getVisibleWorldBounds(screenWidth = 800.0f, screenHeight = 600.0f)
        assertEquals(-50.0f, bounds.left, 0.001f)
        assertEquals(-100.0f, bounds.top, 0.001f)
        assertEquals(350.0f, bounds.right, 0.001f)
        assertEquals(200.0f, bounds.bottom, 0.001f)
        assertEquals(400.0f, bounds.width, 0.001f)
        assertEquals(300.0f, bounds.height, 0.001f)
    }

    @Test
    fun testPointOperations() {
        val p1 = PointF(10f, 20f)
        val p2 = PointF(5f, -10f)
        val sum = p1 + p2
        val diff = p1 - p2
        assertEquals(15f, sum.x, 0.001f)
        assertEquals(10f, sum.y, 0.001f)
        assertEquals(5f, diff.x, 0.001f)
        assertEquals(30f, diff.y, 0.001f)
    }

    @Test
    fun testRectFIntersectsAndContains() {
        val rect = RectF(left = 10.0f, top = 20.0f, right = 110.0f, bottom = 120.0f)
        assertTrue(rect.contains(PointF(50.0f, 50.0f)))
        assertTrue(rect.contains(PointF(10.0f, 20.0f))) // Boundary inclusive
        assertFalse(rect.contains(PointF(5.0f, 50.0f)))

        val inside = RectF(left = 20.0f, top = 30.0f, right = 100.0f, bottom = 110.0f)
        assertTrue(rect.contains(inside))

        val overlapping = RectF(left = 50.0f, top = 50.0f, right = 150.0f, bottom = 150.0f)
        val disjoint = RectF(left = 200.0f, top = 200.0f, right = 300.0f, bottom = 300.0f)
        assertTrue(rect.intersects(overlapping))
        assertFalse(rect.intersects(disjoint))
    }
}
