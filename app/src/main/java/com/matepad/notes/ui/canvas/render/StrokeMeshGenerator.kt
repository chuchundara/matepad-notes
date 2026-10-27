package com.matepad.notes.ui.canvas.render

import android.graphics.Path
import com.matepad.notes.domain.geometry.PointF
import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.StrokePoint
import kotlin.math.max
import kotlin.math.min

/**
 * Generates vector stroke paths, smooth Catmull-Rom spline curves,
 * and axis-aligned bounding boxes for spatial indexing and viewport culling.
 */
object StrokeMeshGenerator {

    /**
     * Represents a cubic Bezier segment derived from Catmull-Rom spline interpolation.
     */
    data class CubicBezierSegment(
        val p0: PointF,
        val cp1: PointF,
        val cp2: PointF,
        val p1: PointF
    ) {
        /**
         * Evaluates a point along the cubic curve at parameter [t] in [0, 1].
         */
        fun evaluate(t: Float): PointF {
            val clampedT = t.coerceIn(0f, 1f)
            val u = 1.0f - clampedT
            val tt = clampedT * clampedT
            val uu = u * u
            val uuu = uu * u
            val ttt = tt * clampedT

            val x = uuu * p0.x + 3f * uu * clampedT * cp1.x + 3f * u * tt * cp2.x + ttt * p1.x
            val y = uuu * p0.y + 3f * uu * clampedT * cp1.y + 3f * u * tt * cp2.y + ttt * p1.y
            return PointF(x, y)
        }
    }

    /**
     * Computes the axis-aligned bounding box enclosing all [points] with padding for [baseWidth].
     */
    fun computeBounds(points: List<StrokePoint>, baseWidth: Float): RectF {
        if (points.isEmpty()) return RectF(0f, 0f, 0f, 0f)

        var minX = points.first().x
        var minY = points.first().y
        var maxX = points.first().x
        var maxY = points.first().y

        for (p in points) {
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }

        val padding = baseWidth / 2f
        return RectF(
            left = minX - padding,
            top = minY - padding,
            right = maxX + padding,
            bottom = maxY + padding
        )
    }

    /**
     * Converts a sequence of [StrokePoint]s into a series of [CubicBezierSegment]s
     * using centripetal/uniform Catmull-Rom spline conversion.
     * Each curve segment passes exactly through the control points with C1 continuity.
     */
    fun computeCatmullRomToBezier(points: List<StrokePoint>): List<CubicBezierSegment> {
        val n = points.size
        if (n < 2) return emptyList()

        val segments = ArrayList<CubicBezierSegment>(n - 1)

        for (i in 0 until n - 1) {
            val pCurrent = points[i]
            val pNext = points[i + 1]

            // Extrapolate virtual endpoints for first and last segments
            val pPrev = if (i > 0) {
                PointF(points[i - 1].x, points[i - 1].y)
            } else {
                PointF(2f * pCurrent.x - pNext.x, 2f * pCurrent.y - pNext.y)
            }

            val pNextNext = if (i + 2 < n) {
                PointF(points[i + 2].x, points[i + 2].y)
            } else {
                PointF(2f * pNext.x - pCurrent.x, 2f * pNext.y - pCurrent.y)
            }

            // Catmull-Rom to cubic Bezier control point conversion formula
            val cp1 = PointF(
                x = pCurrent.x + (pNext.x - pPrev.x) / 6f,
                y = pCurrent.y + (pNext.y - pPrev.y) / 6f
            )
            val cp2 = PointF(
                x = pNext.x - (pNextNext.x - pCurrent.x) / 6f,
                y = pNext.y - (pNextNext.y - pCurrent.y) / 6f
            )

            segments.add(
                CubicBezierSegment(
                    p0 = PointF(pCurrent.x, pCurrent.y),
                    cp1 = cp1,
                    cp2 = cp2,
                    p1 = PointF(pNext.x, pNext.y)
                )
            )
        }

        return segments
    }

    /**
     * Computes uniformly sampled interpolated points along the Catmull-Rom spline curve.
     */
    fun computeCatmullRomSpline(
        points: List<StrokePoint>,
        samplesPerSegment: Int = 4
    ): List<PointF> {
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return listOf(PointF(points[0].x, points[0].y))

        val segments = computeCatmullRomToBezier(points)
        val result = mutableListOf<PointF>()

        for ((index, segment) in segments.withIndex()) {
            val isLast = index == segments.size - 1
            val steps = if (isLast) samplesPerSegment else samplesPerSegment - 1
            for (step in 0..steps) {
                val t = step.toFloat() / samplesPerSegment.toFloat()
                result.add(segment.evaluate(t))
            }
        }

        return result
    }

    /**
     * Builds an Android [Path] following the Catmull-Rom smooth curve connecting [points].
     */
    fun buildPath(points: List<StrokePoint>, path: Path = Path()): Path {
        path.reset()
        if (points.isEmpty()) return path

        if (points.size == 1) {
            val p = points[0]
            path.moveTo(p.x, p.y)
            path.lineTo(p.x + 0.1f, p.y + 0.1f)
            return path
        }

        val segments = computeCatmullRomToBezier(points)
        path.moveTo(segments[0].p0.x, segments[0].p0.y)
        for (seg in segments) {
            path.cubicTo(
                seg.cp1.x, seg.cp1.y,
                seg.cp2.x, seg.cp2.y,
                seg.p1.x, seg.p1.y
            )
        }

        return path
    }
}
