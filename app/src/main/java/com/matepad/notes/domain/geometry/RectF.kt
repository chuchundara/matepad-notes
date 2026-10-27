package com.matepad.notes.domain.geometry

data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun intersects(other: RectF): Boolean {
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }

    fun contains(point: PointF): Boolean {
        return point.x in left..right && point.y in top..bottom
    }

    fun contains(other: RectF): Boolean {
        return left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
    }
}
