package com.matepad.notes.domain.geometry

data class PointF(val x: Float, val y: Float) {
    operator fun plus(other: PointF): PointF = PointF(x + other.x, y + other.y)
    operator fun minus(other: PointF): PointF = PointF(x - other.x, y - other.y)
}
