package com.matepad.notes.ui.canvas.input

import android.view.MotionEvent
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
    fun testMultiTouchStylusLifecycleWithPalmDownAndGesturesAfterward() {
        val events = mutableListOf<InputEvent>()
        val listener = CanvasGestureListener { events.add(it) }

        assertFalse(listener.classifier.isStylusActive)

        val palm = CanvasGestureListener.fingerPointer(id = 0, x = 100f, y = 200f)
        val penDown = CanvasGestureListener.stylusPointer(id = 1, x = 150f, y = 250f, pressure = 0.8f, tilt = 0.1f)
        val penMove = CanvasGestureListener.stylusPointer(id = 1, x = 160f, y = 260f, pressure = 0.85f, tilt = 0.12f)
        val penUp = CanvasGestureListener.stylusPointer(id = 1, x = 170f, y = 270f, pressure = 0.5f, tilt = 0.0f)

        // 1. Palm rests on screen first (pointer 0, ACTION_DOWN)
        val handled1 = listener.onTouchEvent(
            actionMasked = MotionEvent.ACTION_DOWN,
            actionIndex = 0,
            pointers = listOf(palm)
        )
        assertTrue(handled1)
        assertFalse(listener.classifier.isStylusActive)
        assertEquals(0, events.size) // Palm rejected, no drawing

        // 2. Stylus touches down while palm is resting (pointer 1, ACTION_POINTER_DOWN)
        val handled2 = listener.onTouchEvent(
            actionMasked = MotionEvent.ACTION_POINTER_DOWN,
            actionIndex = 1,
            pointers = listOf(palm, penDown)
        )
        assertTrue(handled2)
        assertTrue(listener.classifier.isStylusActive)
        assertEquals(1, events.size)
        val drawStart = events[0] as InputEvent.Draw
        assertTrue(drawStart.isStart)
        assertFalse(drawStart.isEnd)
        assertEquals(150f, drawStart.point.x, 0.001f)
        assertEquals(250f, drawStart.point.y, 0.001f)
        assertEquals(0.8f, drawStart.point.pressure, 0.001f)
        assertEquals(0.1f, drawStart.point.tilt, 0.001f)

        // 3. Stylus moves while palm is still on screen (ACTION_MOVE)
        val handled3 = listener.onTouchEvent(
            actionMasked = MotionEvent.ACTION_MOVE,
            actionIndex = 0,
            pointers = listOf(palm, penMove)
        )
        assertTrue(handled3)
        assertEquals(2, events.size)
        val drawMove = events[1] as InputEvent.Draw
        assertFalse(drawMove.isStart)
        assertFalse(drawMove.isEnd)
        assertEquals(160f, drawMove.point.x, 0.001f)
        assertEquals(260f, drawMove.point.y, 0.001f)

        // 4. Stylus lifts up while palm is still on screen (pointer 1, ACTION_POINTER_UP)
        val handled4 = listener.onTouchEvent(
            actionMasked = MotionEvent.ACTION_POINTER_UP,
            actionIndex = 1,
            pointers = listOf(palm, penUp)
        )
        assertTrue(handled4)
        assertFalse(listener.classifier.isStylusActive) // Reset stylus active state!
        assertEquals(3, events.size)
        val drawEnd = events[2] as InputEvent.Draw
        assertFalse(drawEnd.isStart)
        assertTrue(drawEnd.isEnd)
        assertEquals(170f, drawEnd.point.x, 0.001f)
        assertEquals(270f, drawEnd.point.y, 0.001f)

        // 5. Palm lifts up (pointer 0, ACTION_UP)
        val handled5 = listener.onTouchEvent(
            actionMasked = MotionEvent.ACTION_UP,
            actionIndex = 0,
            pointers = listOf(palm)
        )
        assertTrue(handled5)
        assertFalse(listener.classifier.isStylusActive)

        // 6. Two fingers touch down and move for pan/zoom after drawing
        val finger1Down = CanvasGestureListener.fingerPointer(id = 0, x = 50f, y = 50f)
        val finger2Down = CanvasGestureListener.fingerPointer(id = 1, x = 150f, y = 50f) // span = 100, focus = (100, 50)

        listener.onTouchEvent(MotionEvent.ACTION_DOWN, actionIndex = 0, pointers = listOf(finger1Down))
        listener.onTouchEvent(MotionEvent.ACTION_POINTER_DOWN, actionIndex = 1, pointers = listOf(finger1Down, finger2Down))

        val finger1Move = CanvasGestureListener.fingerPointer(id = 0, x = 100f, y = 70f)
        val finger2Move = CanvasGestureListener.fingerPointer(id = 1, x = 300f, y = 70f) // span = 200 (scale 2x), focus = (200, 70), pan = (+100, +20)

        listener.onTouchEvent(MotionEvent.ACTION_MOVE, actionIndex = 0, pointers = listOf(finger1Move, finger2Move))

        val gestureEvents = events.filterIsInstance<InputEvent.Gesture>()
        assertEquals(1, gestureEvents.size)
        assertEquals(2.0f, gestureEvents[0].scaleDelta, 0.001f)
        assertEquals(100.0f, gestureEvents[0].panX, 0.001f)
        assertEquals(20.0f, gestureEvents[0].panY, 0.001f)
        assertEquals(200.0f, gestureEvents[0].focalPoint.x, 0.001f)
        assertEquals(70.0f, gestureEvents[0].focalPoint.y, 0.001f)
    }

    @Test
    fun testStylusDownFirstThenPalmDown() {
        val events = mutableListOf<InputEvent>()
        val listener = CanvasGestureListener { events.add(it) }

        val penDown = CanvasGestureListener.stylusPointer(id = 0, x = 10f, y = 20f)
        val palm = CanvasGestureListener.fingerPointer(id = 1, x = 200f, y = 300f)
        val penMove = CanvasGestureListener.stylusPointer(id = 0, x = 15f, y = 25f)
        val penUp = CanvasGestureListener.stylusPointer(id = 0, x = 20f, y = 30f)

        // 1. Stylus down first (ACTION_DOWN, pointer 0)
        listener.onTouchEvent(MotionEvent.ACTION_DOWN, actionIndex = 0, pointers = listOf(penDown))
        assertTrue(listener.classifier.isStylusActive)
        assertEquals(1, events.size)

        // 2. Palm rests second (ACTION_POINTER_DOWN, pointer 1)
        listener.onTouchEvent(MotionEvent.ACTION_POINTER_DOWN, actionIndex = 1, pointers = listOf(penDown, palm))
        assertTrue(listener.classifier.isStylusActive)
        assertEquals(1, events.size) // No extra event for resting palm

        // 3. Stylus moves (ACTION_MOVE)
        listener.onTouchEvent(MotionEvent.ACTION_MOVE, actionIndex = 0, pointers = listOf(penMove, palm))
        assertEquals(2, events.size)

        // 4. Stylus lifts up (ACTION_POINTER_UP, pointer 0)
        listener.onTouchEvent(MotionEvent.ACTION_POINTER_UP, actionIndex = 0, pointers = listOf(penUp, palm))
        assertFalse(listener.classifier.isStylusActive)
        assertEquals(3, events.size)
        assertTrue((events[2] as InputEvent.Draw).isEnd)

        // 5. Palm lifts up (ACTION_UP)
        listener.onTouchEvent(MotionEvent.ACTION_UP, actionIndex = 0, pointers = listOf(palm))
        assertFalse(listener.classifier.isStylusActive)
    }

    @Test
    fun testStylusBarrelButtonTogglesEraserOnlyOnStylusPrimary() {
        val events = mutableListOf<InputEvent>()
        val listener = CanvasGestureListener { events.add(it) }

        val stylus = CanvasGestureListener.stylusPointer(id = 0, x = 10f, y = 10f)

        // Pen tip contact reporting BUTTON_PRIMARY MUST NOT toggle eraser
        listener.onTouchEvent(
            actionMasked = MotionEvent.ACTION_DOWN,
            actionIndex = 0,
            pointers = listOf(stylus),
            buttonState = MotionEvent.BUTTON_PRIMARY
        )
        val toggles1 = events.filterIsInstance<InputEvent.ToggleEraser>()
        assertEquals(0, toggles1.size)

        // Pressing M-Pencil barrel button BUTTON_STYLUS_PRIMARY MUST toggle eraser
        listener.onTouchEvent(
            actionMasked = MotionEvent.ACTION_MOVE,
            actionIndex = 0,
            pointers = listOf(stylus),
            buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY
        )
        val toggles2 = events.filterIsInstance<InputEvent.ToggleEraser>()
        assertEquals(1, toggles2.size)

        // Releasing barrel button and moving should not trigger another toggle
        listener.onTouchEvent(
            actionMasked = MotionEvent.ACTION_MOVE,
            actionIndex = 0,
            pointers = listOf(stylus),
            buttonState = 0
        )
        assertEquals(1, events.filterIsInstance<InputEvent.ToggleEraser>().size)
    }

    @Test
    fun testToolTypeEraserEmitsEraseEvents() {
        val events = mutableListOf<InputEvent>()
        val listener = CanvasGestureListener { events.add(it) }

        val eraserDown = CanvasGestureListener.eraserPointer(id = 0, x = 20f, y = 30f)
        val eraserMove = CanvasGestureListener.eraserPointer(id = 0, x = 25f, y = 35f)
        val eraserUp = CanvasGestureListener.eraserPointer(id = 0, x = 30f, y = 40f)

        listener.onTouchEvent(MotionEvent.ACTION_DOWN, actionIndex = 0, pointers = listOf(eraserDown))
        assertTrue(listener.classifier.isStylusActive)

        listener.onTouchEvent(MotionEvent.ACTION_MOVE, actionIndex = 0, pointers = listOf(eraserMove))

        listener.onTouchEvent(MotionEvent.ACTION_UP, actionIndex = 0, pointers = listOf(eraserUp))
        assertFalse(listener.classifier.isStylusActive)

        assertEquals(3, events.size)

        val eStart = events[0] as InputEvent.Erase
        assertTrue(eStart.isStart)
        assertFalse(eStart.isEnd)
        assertEquals(20f, eStart.point.x, 0.001f)
        assertEquals(30f, eStart.point.y, 0.001f)

        val eMove = events[1] as InputEvent.Erase
        assertFalse(eMove.isStart)
        assertFalse(eMove.isEnd)
        assertEquals(25f, eMove.point.x, 0.001f)

        val eEnd = events[2] as InputEvent.Erase
        assertFalse(eEnd.isStart)
        assertTrue(eEnd.isEnd)
        assertEquals(30f, eEnd.point.x, 0.001f)
    }

    @Test
    fun testActionCancelResetsStylusAndGestures() {
        val events = mutableListOf<InputEvent>()
        val listener = CanvasGestureListener { events.add(it) }

        val penDown = CanvasGestureListener.stylusPointer(id = 0, x = 10f, y = 20f)
        listener.onTouchEvent(MotionEvent.ACTION_DOWN, actionIndex = 0, pointers = listOf(penDown))
        assertTrue(listener.classifier.isStylusActive)

        listener.onTouchEvent(MotionEvent.ACTION_CANCEL, actionIndex = 0, pointers = emptyList())
        assertFalse(listener.classifier.isStylusActive)
    }

    @Test
    fun testProgrammaticStylusButtonPress() {
        val events = mutableListOf<InputEvent>()
        val listener = CanvasGestureListener { events.add(it) }

        listener.onStylusButtonPress()
        assertEquals(1, events.size)
        assertTrue(events[0] is InputEvent.ToggleEraser)
    }
}
