package com.matepad.notes.ui.canvas.input

import android.view.MotionEvent
import com.matepad.notes.domain.model.StrokePoint
import kotlin.math.hypot

sealed interface InputEvent {
    data class Draw(
        val point: StrokePoint,
        val isStart: Boolean = false,
        val isEnd: Boolean = false
    ) : InputEvent

    data class Gesture(
        val scaleDelta: Float,
        val panX: Float,
        val panY: Float
    ) : InputEvent

    data object ToggleEraser : InputEvent
}

class CanvasGestureListener(
    val classifier: StylusInputClassifier = StylusInputClassifier(),
    private val onInputEvent: (InputEvent) -> Unit = {}
) {
    // Stylus button state tracking (for M-Pencil barrel button toggle)
    private var lastButtonState: Int = 0

    // Double-tap tracking for M-Pencil / stylus
    private var lastStylusDownTime: Long = 0L
    private var lastStylusDownX: Float = 0f
    private var lastStylusDownY: Float = 0f
    var doubleTapTimeoutMs: Long = 300L
    var doubleTapSlop: Float = 40f

    // Two-finger pan & zoom tracking
    private var prevSpan: Float = 0f
    private var prevFocusX: Float = 0f
    private var prevFocusY: Float = 0f
    private var isGesturing: Boolean = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        // 1. Stylus button check (M-Pencil primary button)
        val buttonState = event.buttonState
        val isStylusButtonPressed = (buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_PRIMARY)) != 0
        val wasStylusButtonPressed = (lastButtonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_PRIMARY)) != 0
        if (isStylusButtonPressed && !wasStylusButtonPressed) {
            emit(InputEvent.ToggleEraser)
        }
        lastButtonState = buttonState

        val pointerCount = event.pointerCount

        // 2. Determine if stylus is interacting
        var stylusPointerIndex = -1
        for (i in 0 until pointerCount) {
            val toolType = event.getToolType(i)
            if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
                stylusPointerIndex = i
                break
            }
        }

        val isStylus = stylusPointerIndex >= 0
        val actionMasked = event.actionMasked

        if (isStylus) {
            when (actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    classifier.onStylusDown()
                    val x = event.getX(stylusPointerIndex)
                    val y = event.getY(stylusPointerIndex)
                    checkStylusDoubleTap(event.eventTime, x, y)
                    emit(InputEvent.Draw(createStrokePoint(event, stylusPointerIndex), isStart = true))
                }
                MotionEvent.ACTION_MOVE -> {
                    // Extract historical points for ultra-low latency 120Hz fidelity
                    val historySize = event.historySize
                    for (h in 0 until historySize) {
                        emit(
                            InputEvent.Draw(
                                StrokePoint(
                                    x = event.getHistoricalX(stylusPointerIndex, h),
                                    y = event.getHistoricalY(stylusPointerIndex, h),
                                    pressure = event.getHistoricalPressure(stylusPointerIndex, h),
                                    tilt = event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, stylusPointerIndex, h)
                                )
                            )
                        )
                    }
                    emit(InputEvent.Draw(createStrokePoint(event, stylusPointerIndex)))
                }
                MotionEvent.ACTION_UP -> {
                    classifier.onStylusUp()
                    emit(InputEvent.Draw(createStrokePoint(event, stylusPointerIndex), isEnd = true))
                }
                MotionEvent.ACTION_CANCEL -> {
                    classifier.onStylusUp()
                }
            }
            return true
        }

        // 3. Finger / Palm touch classification
        val classification = classifier.classifyPointer(isStylus = false, pointerCount = pointerCount)
        when (classification) {
            StylusInputClassifier.Action.IGNORE_PALM -> {
                // Single finger touch or palm resting while writing - rejected
                return true
            }
            StylusInputClassifier.Action.PAN_ZOOM_GESTURE -> {
                handleMultiTouchPanZoom(event)
                return true
            }
            StylusInputClassifier.Action.DRAW -> {
                // Drawing is reserved for stylus
                return true
            }
        }
    }

    private fun checkStylusDoubleTap(eventTime: Long, x: Float, y: Float) {
        val dt = eventTime - lastStylusDownTime
        val dx = x - lastStylusDownX
        val dy = y - lastStylusDownY
        val slopSq = doubleTapSlop * doubleTapSlop
        if (dt in 40..doubleTapTimeoutMs && (dx * dx + dy * dy) <= slopSq) {
            emit(InputEvent.ToggleEraser)
            lastStylusDownTime = 0L // reset after double-tap
        } else {
            lastStylusDownTime = eventTime
            lastStylusDownX = x
            lastStylusDownY = y
        }
    }

    private fun handleMultiTouchPanZoom(event: MotionEvent) {
        if (event.pointerCount < 2) {
            isGesturing = false
            prevSpan = 0f
            return
        }

        val x0 = event.getX(0)
        val y0 = event.getY(0)
        val x1 = event.getX(1)
        val y1 = event.getY(1)

        val focusX = (x0 + x1) / 2f
        val focusY = (y0 + y1) / 2f
        val span = hypot(x1 - x0, y1 - y0)

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                prevFocusX = focusX
                prevFocusY = focusY
                prevSpan = span
                isGesturing = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isGesturing && prevSpan > 0f) {
                    val scaleDelta = if (span > 0f) span / prevSpan else 1.0f
                    val panX = focusX - prevFocusX
                    val panY = focusY - prevFocusY

                    emit(InputEvent.Gesture(scaleDelta = scaleDelta, panX = panX, panY = panY))
                }
                prevFocusX = focusX
                prevFocusY = focusY
                prevSpan = span
                isGesturing = true
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isGesturing = false
                prevSpan = 0f
            }
        }
    }

    private fun createStrokePoint(event: MotionEvent, pointerIndex: Int): StrokePoint {
        return StrokePoint(
            x = event.getX(pointerIndex),
            y = event.getY(pointerIndex),
            pressure = event.getPressure(pointerIndex),
            tilt = event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex)
        )
    }

    private fun emit(event: InputEvent) {
        onInputEvent(event)
    }

    // Direct programmatic simulation methods for testing without MotionEvent
    fun onStylusDown(
        x: Float,
        y: Float,
        pressure: Float = 0.5f,
        tilt: Float = 0.0f,
        timeMs: Long = System.currentTimeMillis()
    ) {
        classifier.onStylusDown()
        checkStylusDoubleTap(timeMs, x, y)
        emit(InputEvent.Draw(StrokePoint(x, y, pressure, tilt), isStart = true))
    }

    fun onStylusMove(
        x: Float,
        y: Float,
        pressure: Float = 0.5f,
        tilt: Float = 0.0f
    ) {
        emit(InputEvent.Draw(StrokePoint(x, y, pressure, tilt)))
    }

    fun onStylusUp(
        x: Float,
        y: Float,
        pressure: Float = 0.5f,
        tilt: Float = 0.0f
    ) {
        classifier.onStylusUp()
        emit(InputEvent.Draw(StrokePoint(x, y, pressure, tilt), isEnd = true))
    }

    fun onStylusCancel() {
        classifier.onStylusUp()
    }

    fun onStylusButtonPress() {
        emit(InputEvent.ToggleEraser)
    }

    fun onTwoFingerDown(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (classifier.isStylusActive) return // rejected if stylus active
        prevFocusX = (x0 + x1) / 2f
        prevFocusY = (y0 + y1) / 2f
        prevSpan = hypot(x1 - x0, y1 - y0)
        isGesturing = true
    }

    fun onTwoFingerMove(x0: Float, y0: Float, x1: Float, y1: Float) {
        if (classifier.isStylusActive) return // rejected if stylus active
        if (!isGesturing || prevSpan <= 0f) {
            onTwoFingerDown(x0, y0, x1, y1)
            return
        }
        val focusX = (x0 + x1) / 2f
        val focusY = (y0 + y1) / 2f
        val span = hypot(x1 - x0, y1 - y0)

        val scaleDelta = if (span > 0f) span / prevSpan else 1.0f
        val panX = focusX - prevFocusX
        val panY = focusY - prevFocusY

        prevFocusX = focusX
        prevFocusY = focusY
        prevSpan = span

        emit(InputEvent.Gesture(scaleDelta = scaleDelta, panX = panX, panY = panY))
    }

    fun onTwoFingerUp() {
        isGesturing = false
        prevSpan = 0f
    }
}
