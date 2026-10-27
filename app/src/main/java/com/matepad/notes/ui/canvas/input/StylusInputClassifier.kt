package com.matepad.notes.ui.canvas.input

class StylusInputClassifier {
    enum class Action {
        DRAW,
        PAN_ZOOM_GESTURE,
        IGNORE_PALM
    }

    var isStylusActive: Boolean = false
        private set

    fun onStylusDown() {
        isStylusActive = true
    }

    fun onStylusUp() {
        isStylusActive = false
    }

    fun reset() {
        isStylusActive = false
    }

    fun classifyPointer(isStylus: Boolean, pointerCount: Int): Action {
        if (isStylus) {
            return Action.DRAW
        }
        if (isStylusActive) {
            return Action.IGNORE_PALM
        }
        if (pointerCount >= 2) {
            return Action.PAN_ZOOM_GESTURE
        }
        return Action.IGNORE_PALM
    }
}
