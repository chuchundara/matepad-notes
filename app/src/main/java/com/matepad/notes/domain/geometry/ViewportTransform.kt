package com.matepad.notes.domain.geometry

data class ViewportTransform(
    val scale: Float = 1.0f,
    val offsetX: Float = 0.0f,
    val offsetY: Float = 0.0f
) {
    companion object {
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 50.0f
    }

    fun withScale(newScale: Float): ViewportTransform {
        val clamped = newScale.coerceIn(MIN_SCALE, MAX_SCALE)
        return copy(scale = clamped)
    }

    fun withScaleAt(newScale: Float, focalPoint: PointF): ViewportTransform {
        val clamped = newScale.coerceIn(MIN_SCALE, MAX_SCALE)
        val worldFocal = screenToWorld(focalPoint)
        val newOffsetX = focalPoint.x - worldFocal.x * clamped
        val newOffsetY = focalPoint.y - worldFocal.y * clamped
        return copy(scale = clamped, offsetX = newOffsetX, offsetY = newOffsetY)
    }

    fun panBy(dx: Float, dy: Float): ViewportTransform {
        return copy(offsetX = offsetX + dx, offsetY = offsetY + dy)
    }

    fun screenToWorld(screenPoint: PointF): PointF {
        return PointF(
            x = (screenPoint.x - offsetX) / scale,
            y = (screenPoint.y - offsetY) / scale
        )
    }

    fun worldToScreen(worldPoint: PointF): PointF {
        return PointF(
            x = worldPoint.x * scale + offsetX,
            y = worldPoint.y * scale + offsetY
        )
    }

    fun getVisibleWorldBounds(screenWidth: Float, screenHeight: Float): RectF {
        val topLeft = screenToWorld(PointF(0.0f, 0.0f))
        val bottomRight = screenToWorld(PointF(screenWidth, screenHeight))
        return RectF(
            left = topLeft.x,
            top = topLeft.y,
            right = bottomRight.x,
            bottom = bottomRight.y
        )
    }
}
