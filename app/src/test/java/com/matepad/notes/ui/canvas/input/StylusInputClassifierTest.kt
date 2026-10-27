package com.matepad.notes.ui.canvas.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StylusInputClassifierTest {

    @Test
    fun testRejectsFingerWhenStylusActive() {
        val classifier = StylusInputClassifier()
        classifier.onStylusDown()

        // Finger event while stylus is down must be ignored for drawing
        val action = classifier.classifyPointer(isStylus = false, pointerCount = 1)
        assertEquals(StylusInputClassifier.Action.IGNORE_PALM, action)

        classifier.onStylusUp()
        val afterStylusUp = classifier.classifyPointer(isStylus = false, pointerCount = 2)
        assertEquals(StylusInputClassifier.Action.PAN_ZOOM_GESTURE, afterStylusUp)
    }

    @Test
    fun testStylusAlwaysDraws() {
        val classifier = StylusInputClassifier()

        // Even if multiple pointers are present (e.g. resting palm on screen),
        // the stylus pointer itself always triggers DRAW
        val action1 = classifier.classifyPointer(isStylus = true, pointerCount = 1)
        assertEquals(StylusInputClassifier.Action.DRAW, action1)

        val action2 = classifier.classifyPointer(isStylus = true, pointerCount = 2)
        assertEquals(StylusInputClassifier.Action.DRAW, action2)

        val action3 = classifier.classifyPointer(isStylus = true, pointerCount = 5)
        assertEquals(StylusInputClassifier.Action.DRAW, action3)
    }

    @Test
    fun testSingleFingerIgnoredWhenStylusInactive() {
        val classifier = StylusInputClassifier()
        assertFalse(classifier.isStylusActive)

        // Single finger tap or drag without stylus does not draw (draw is stylus-only)
        val action = classifier.classifyPointer(isStylus = false, pointerCount = 1)
        assertEquals(StylusInputClassifier.Action.IGNORE_PALM, action)
    }

    @Test
    fun testMultiFingerGesturesWhenStylusInactive() {
        val classifier = StylusInputClassifier()

        val twoFingers = classifier.classifyPointer(isStylus = false, pointerCount = 2)
        assertEquals(StylusInputClassifier.Action.PAN_ZOOM_GESTURE, twoFingers)

        val threeFingers = classifier.classifyPointer(isStylus = false, pointerCount = 3)
        assertEquals(StylusInputClassifier.Action.PAN_ZOOM_GESTURE, threeFingers)
    }

    @Test
    fun testMultiFingerIgnoredWhenStylusActive() {
        val classifier = StylusInputClassifier()
        classifier.onStylusDown()
        assertTrue(classifier.isStylusActive)

        // While user is writing, palm or multiple fingers must not pan/zoom
        val twoFingers = classifier.classifyPointer(isStylus = false, pointerCount = 2)
        assertEquals(StylusInputClassifier.Action.IGNORE_PALM, twoFingers)

        val threeFingers = classifier.classifyPointer(isStylus = false, pointerCount = 3)
        assertEquals(StylusInputClassifier.Action.IGNORE_PALM, threeFingers)
    }

    @Test
    fun testClassifierReset() {
        val classifier = StylusInputClassifier()
        classifier.onStylusDown()
        assertTrue(classifier.isStylusActive)

        classifier.reset()
        assertFalse(classifier.isStylusActive)

        val action = classifier.classifyPointer(isStylus = false, pointerCount = 2)
        assertEquals(StylusInputClassifier.Action.PAN_ZOOM_GESTURE, action)
    }

    @Test
    fun testCanvasGestureListenerEmitsDrawEvents() {
        val events = mutableListOf<InputEvent>()
        val gestureListener = CanvasGestureListener { events.add(it) }

        gestureListener.onStylusDown(10f, 20f, pressure = 0.8f, tilt = 0.1f)
        gestureListener.onStylusMove(15f, 25f, pressure = 0.9f, tilt = 0.12f)
        gestureListener.onStylusUp(20f, 30f, pressure = 0.5f, tilt = 0.0f)

        assertEquals(3, events.size)

        val down = events[0] as InputEvent.Draw
        assertEquals(10f, down.point.x, 0.001f)
        assertEquals(20f, down.point.y, 0.001f)
        assertEquals(0.8f, down.point.pressure, 0.001f)
        assertEquals(0.1f, down.point.tilt, 0.001f)
        assertTrue(down.isStart)
        assertFalse(down.isEnd)

        val move = events[1] as InputEvent.Draw
        assertEquals(15f, move.point.x, 0.001f)
        assertEquals(25f, move.point.y, 0.001f)
        assertFalse(move.isStart)
        assertFalse(move.isEnd)

        val up = events[2] as InputEvent.Draw
        assertEquals(20f, up.point.x, 0.001f)
        assertEquals(30f, up.point.y, 0.001f)
        assertFalse(up.isStart)
        assertTrue(up.isEnd)
    }

    @Test
    fun testCanvasGestureListenerTwoFingerPanZoom() {
        val events = mutableListOf<InputEvent>()
        val gestureListener = CanvasGestureListener { events.add(it) }

        // Initial 2-finger placement: distance 100
        gestureListener.onTwoFingerDown(x0 = 0f, y0 = 0f, x1 = 100f, y1 = 0f)

        // Move: distance becomes 200 (2x zoom) and midpoint shifts by (+100, +20)
        // Focus was (50, 0), new focus is (150, 20) -> panX = 100, panY = 20
        gestureListener.onTwoFingerMove(x0 = 50f, y0 = 20f, x1 = 250f, y1 = 20f)

        assertEquals(1, events.size)
        val gesture = events[0] as InputEvent.Gesture
        assertEquals(2.0f, gesture.scaleDelta, 0.001f)
        assertEquals(100.0f, gesture.panX, 0.001f)
        assertEquals(20.0f, gesture.panY, 0.001f)

        gestureListener.onTwoFingerUp()
    }

    @Test
    fun testCanvasGestureListenerRejectsTwoFingersWhileStylusActive() {
        val events = mutableListOf<InputEvent>()
        val gestureListener = CanvasGestureListener { events.add(it) }

        gestureListener.onStylusDown(10f, 10f)
        assertEquals(1, events.size) // Draw event

        // Try two finger gesture while stylus is active (palm on screen)
        gestureListener.onTwoFingerDown(0f, 0f, 100f, 0f)
        gestureListener.onTwoFingerMove(0f, 0f, 200f, 0f)

        // No gesture event should be added
        assertEquals(1, events.size)
    }

    @Test
    fun testStylusButtonPressTogglesEraser() {
        val events = mutableListOf<InputEvent>()
        val gestureListener = CanvasGestureListener { events.add(it) }

        gestureListener.onStylusButtonPress()

        assertEquals(1, events.size)
        assertTrue(events[0] is InputEvent.ToggleEraser)
    }

    @Test
    fun testStylusDoubleTapTogglesEraser() {
        val events = mutableListOf<InputEvent>()
        val gestureListener = CanvasGestureListener { events.add(it) }

        val t0 = 1000L
        gestureListener.onStylusDown(50f, 50f, timeMs = t0)
        gestureListener.onStylusUp(50f, 50f)

        // Rapid second tap at same position 150ms later (within 300ms window)
        gestureListener.onStylusDown(52f, 51f, timeMs = t0 + 150L)

        // Should have emitted: Draw(down), Draw(up), ToggleEraser, Draw(down)
        val eraserEvents = events.filterIsInstance<InputEvent.ToggleEraser>()
        assertEquals(1, eraserEvents.size)
    }

    @Test
    fun testStylusSlowTapDoesNotToggleEraser() {
        val events = mutableListOf<InputEvent>()
        val gestureListener = CanvasGestureListener { events.add(it) }

        val t0 = 1000L
        gestureListener.onStylusDown(50f, 50f, timeMs = t0)
        gestureListener.onStylusUp(50f, 50f)

        // Second tap 500ms later (exceeds 300ms window)
        gestureListener.onStylusDown(50f, 50f, timeMs = t0 + 500L)

        val eraserEvents = events.filterIsInstance<InputEvent.ToggleEraser>()
        assertEquals(0, eraserEvents.size)
    }
}
