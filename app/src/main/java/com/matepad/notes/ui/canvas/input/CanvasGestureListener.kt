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

    data class Erase(
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

data class PointerData(
    val id: Int,
    val toolType: Int,
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val tilt: Float = 0.0f
)

class CanvasGestureListener(
    val classifier: StylusInputClassifier = StylusInputClassifier(),
    private val onInputEvent: (InputEvent) -> Unit = {}
) {
    // Stylus button state tracking (strictly M-Pencil barrel button BUTTON_STYLUS_PRIMARY)
    private var lastButtonState: Int = 0

    // Two-finger pan & zoom tracking
    private var prevSpan: Float = 0f
    private var prevFocusX: Float = 0f
    private var prevFocusY: Float = 0f
    private var isGesturing: Boolean = false

    companion object {
        fun stylusPointer(id: Int = 0, x: Float, y: Float, pressure: Float = 0.5f, tilt: Float = 0.0f): PointerData =
            PointerData(id = id, toolType = MotionEvent.TOOL_TYPE_STYLUS, x = x, y = y, pressure = pressure, tilt = tilt)

        fun eraserPointer(id: Int = 0, x: Float, y: Float, pressure: Float = 0.5f, tilt: Float = 0.0f): PointerData =
            PointerData(id = id, toolType = MotionEvent.TOOL_TYPE_ERASER, x = x, y = y, pressure = pressure, tilt = tilt)

        fun fingerPointer(id: Int = 0, x: Float, y: Float): PointerData =
            PointerData(id = id, toolType = MotionEvent.TOOL_TYPE_FINGER, x = x, y = y, pressure = 0.0f, tilt = 0.0f)
    }

    private fun isStylus(toolType: Int): Boolean =
        toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER

    private fun isEraser(toolType: Int): Boolean =
        toolType == MotionEvent.TOOL_TYPE_ERASER

    fun onTouchEvent(event: MotionEvent): Boolean {
        // 1. Stylus button check: strictly BUTTON_STYLUS_PRIMARY (ignoring digitizer pen-tip BUTTON_PRIMARY)
        val buttonState = event.buttonState
        checkStylusButton(buttonState)

        val pointerCount = event.pointerCount
        val actionMasked = event.actionMasked
        val actionIndex = event.actionIndex

        // Extract historical points for ultra-low latency 120Hz inking on ACTION_MOVE
        if (actionMasked == MotionEvent.ACTION_MOVE && classifier.isStylusActive) {
            var stylusIndex = -1
            for (i in 0 until pointerCount) {
                if (isStylus(event.getToolType(i))) {
                    stylusIndex = i
                    break
                }
            }
            if (stylusIndex >= 0) {
                val isEraserTool = isEraser(event.getToolType(stylusIndex))
                val historySize = event.historySize
                for (h in 0 until historySize) {
                    val histPoint = StrokePoint(
                        x = event.getHistoricalX(stylusIndex, h),
                        y = event.getHistoricalY(stylusIndex, h),
                        pressure = event.getHistoricalPressure(stylusIndex, h),
                        tilt = event.getHistoricalAxisValue(MotionEvent.AXIS_TILT, stylusIndex, h)
                    )
                    if (isEraserTool) {
                        emit(InputEvent.Erase(histPoint))
                    } else {
                        emit(InputEvent.Draw(histPoint))
                    }
                }
            }
        }

        // Convert current MotionEvent pointers to PointerData
        val pointers = ArrayList<PointerData>(pointerCount)
        for (i in 0 until pointerCount) {
            pointers.add(
                PointerData(
                    id = event.getPointerId(i),
                    toolType = event.getToolType(i),
                    x = event.getX(i),
                    y = event.getY(i),
                    pressure = event.getPressure(i),
                    tilt = event.getAxisValue(MotionEvent.AXIS_TILT, i)
                )
            )
        }

        return processPointers(actionMasked, actionIndex, pointers)
    }

    // Overload for testing multi-touch lifecycles and direct dispatch
    fun onTouchEvent(
        actionMasked: Int,
        actionIndex: Int = 0,
        pointers: List<PointerData>,
        buttonState: Int = 0
    ): Boolean {
        checkStylusButton(buttonState)
        return processPointers(actionMasked, actionIndex, pointers)
    }

    private fun checkStylusButton(buttonState: Int) {
        val isStylusButtonPressed = (buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        val wasStylusButtonPressed = (lastButtonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        if (isStylusButtonPressed && !wasStylusButtonPressed) {
            emit(InputEvent.ToggleEraser)
        }
        lastButtonState = buttonState
    }

    private fun processPointers(
        actionMasked: Int,
        actionIndex: Int,
        pointers: List<PointerData>
    ): Boolean {
        when (actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val p = pointers.getOrNull(actionIndex) ?: pointers.firstOrNull() ?: return false
                if (isStylus(p.toolType)) {
                    classifier.onStylusDown()
                    emitStylusStart(p)
                } else {
                    // Single finger touch - rejected for drawing, gestures require 2 fingers
                    isGesturing = false
                    prevSpan = 0f
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                val p = pointers.getOrNull(actionIndex) ?: return false
                if (isStylus(p.toolType)) {
                    // Stylus touched down while resting palm or another finger was already down
                    classifier.onStylusDown()
                    emitStylusStart(p)
                } else {
                    // Another finger touched down
                    if (classifier.isStylusActive) {
                        // Palm rejection: ignore while stylus is active
                    } else if (pointers.size >= 2) {
                        // Start two-finger pan/zoom gesture
                        initGesture(pointers[0], pointers[1])
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // If stylus is active, ink/erase for the stylus pointer
                val stylus = pointers.firstOrNull { isStylus(it.toolType) }
                if (stylus != null && classifier.isStylusActive) {
                    emitStylusMove(stylus)
                } else if (!classifier.isStylusActive && pointers.size >= 2) {
                    // Two-finger pan & zoom
                    updateGesture(pointers[0], pointers[1])
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val p = pointers.getOrNull(actionIndex) ?: return false
                if (isStylus(p.toolType)) {
                    // Stylus lifted while palm is still on screen
                    classifier.onStylusUp()
                    emitStylusEnd(p)
                } else {
                    val remainingCount = pointers.size - 1
                    if (remainingCount < 2) {
                        isGesturing = false
                        prevSpan = 0f
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val p = pointers.getOrNull(actionIndex) ?: pointers.firstOrNull()
                if (p != null && isStylus(p.toolType)) {
                    classifier.onStylusUp()
                    emitStylusEnd(p)
                }
                if (classifier.isStylusActive) {
                    classifier.onStylusUp()
                }
                isGesturing = false
                prevSpan = 0f
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (classifier.isStylusActive) {
                    classifier.onStylusUp()
                }
                isGesturing = false
                prevSpan = 0f
                return true
            }
        }
        return false
    }

    private fun emitStylusStart(p: PointerData) {
        val pt = StrokePoint(p.x, p.y, p.pressure, p.tilt)
        if (isEraser(p.toolType)) {
            emit(InputEvent.Erase(pt, isStart = true))
        } else {
            emit(InputEvent.Draw(pt, isStart = true))
        }
    }

    private fun emitStylusMove(p: PointerData) {
        val pt = StrokePoint(p.x, p.y, p.pressure, p.tilt)
        if (isEraser(p.toolType)) {
            emit(InputEvent.Erase(pt))
        } else {
            emit(InputEvent.Draw(pt))
        }
    }

    private fun emitStylusEnd(p: PointerData) {
        val pt = StrokePoint(p.x, p.y, p.pressure, p.tilt)
        if (isEraser(p.toolType)) {
            emit(InputEvent.Erase(pt, isEnd = true))
        } else {
            emit(InputEvent.Draw(pt, isEnd = true))
        }
    }

    private fun initGesture(p0: PointerData, p1: PointerData) {
        prevFocusX = (p0.x + p1.x) / 2f
        prevFocusY = (p0.y + p1.y) / 2f
        prevSpan = hypot(p1.x - p0.x, p1.y - p0.y)
        isGesturing = true
    }

    private fun updateGesture(p0: PointerData, p1: PointerData) {
        val focusX = (p0.x + p1.x) / 2f
        val focusY = (p0.y + p1.y) / 2f
        val span = hypot(p1.x - p0.x, p1.y - p0.y)

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

    private fun emit(event: InputEvent) {
        onInputEvent(event)
    }

    fun onStylusButtonPress() {
        emit(InputEvent.ToggleEraser)
    }
}
