package com.matepad.notes.domain.tool

import com.matepad.notes.domain.geometry.PointF
import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.spatial.QuadTree

/**
 * Domain-level hit-testing engine for stroke erasure.
 *
 * Supports both vertex-proximity and continuous segment-distance checks, allowing
 * eraser interactions to reliably hit strokes either by tapping on endpoints or by
 * sweeping through the middle of long segments.
 */
object StrokeEraser {

    /**
     * Determines whether an eraser circle centered at [eraserPoint] with [eraserRadius]
     * touches or intersects the given [stroke].
     *
     * Computes the shortest distance from [eraserPoint] to each line segment of the stroke,
     * taking the stroke's [Stroke.baseWidth] into account.
     */
    fun isStrokeHit(stroke: Stroke, eraserPoint: PointF, eraserRadius: Float): Boolean {
        val points = stroke.points
        if (points.isEmpty()) return false

        val effectiveRadius = (eraserRadius + stroke.baseWidth / 2f).coerceAtLeast(0f)
        val thresholdSq = effectiveRadius * effectiveRadius

        if (points.size == 1) {
            val p = points[0]
            val dx = p.x - eraserPoint.x
            val dy = p.y - eraserPoint.y
            return (dx * dx + dy * dy) <= thresholdSq
        }

        val px = eraserPoint.x
        val py = eraserPoint.y
        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val distSq = distanceSquaredToSegment(px, py, p0.x, p0.y, p1.x, p1.y)
            if (distSq <= thresholdSq) {
                return true
            }
        }
        return false
    }

    /**
     * Determines whether a sweeping eraser motion along the line segment from [eraserStart]
     * to [eraserEnd] with [eraserRadius] intersects the given [stroke].
     */
    fun isStrokeHit(
        stroke: Stroke,
        eraserStart: PointF,
        eraserEnd: PointF,
        eraserRadius: Float
    ): Boolean {
        val points = stroke.points
        if (points.isEmpty()) return false

        val effectiveRadius = (eraserRadius + stroke.baseWidth / 2f).coerceAtLeast(0f)
        val thresholdSq = effectiveRadius * effectiveRadius

        val esx = eraserStart.x
        val esy = eraserStart.y
        val eex = eraserEnd.x
        val eey = eraserEnd.y

        if (points.size == 1) {
            val p = points[0]
            val distSq = distanceSquaredToSegment(p.x, p.y, esx, esy, eex, eey)
            return distSq <= thresholdSq
        }

        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val distSq = distanceSquaredSegmentToSegment(
                esx, esy, eex, eey,
                p0.x, p0.y, p1.x, p1.y
            )
            if (distSq <= thresholdSq) {
                return true
            }
        }
        return false
    }

    /**
     * Finds and returns all strokes in [strokes] that are touched by the eraser at [eraserPoint].
     */
    fun findIntersectingStrokes(
        strokes: Collection<Stroke>,
        eraserPoint: PointF,
        eraserRadius: Float
    ): List<Stroke> {
        return strokes.filter { stroke ->
            isStrokeHit(stroke, eraserPoint, eraserRadius)
        }
    }

    /**
     * Finds and returns all strokes within [quadTree] that are touched by the eraser at [eraserPoint].
     * Utilizes spatial indexing for O(log N) candidate culling before exact hit testing.
     */
    fun findIntersectingStrokes(
        quadTree: QuadTree<Stroke>,
        eraserPoint: PointF,
        eraserRadius: Float
    ): List<Stroke> {
        val eraseBounds = RectF(
            eraserPoint.x - eraserRadius,
            eraserPoint.y - eraserRadius,
            eraserPoint.x + eraserRadius,
            eraserPoint.y + eraserRadius
        )
        val candidates = quadTree.query(eraseBounds)
        return findIntersectingStrokes(candidates, eraserPoint, eraserRadius)
    }

    /**
     * Finds and returns all strokes in [strokes] touched by a sweeping eraser segment.
     */
    fun findIntersectingStrokes(
        strokes: Collection<Stroke>,
        eraserStart: PointF,
        eraserEnd: PointF,
        eraserRadius: Float
    ): List<Stroke> {
        return strokes.filter { stroke ->
            isStrokeHit(stroke, eraserStart, eraserEnd, eraserRadius)
        }
    }

    /**
     * Finds and returns all strokes in [quadTree] touched by a sweeping eraser segment.
     */
    fun findIntersectingStrokes(
        quadTree: QuadTree<Stroke>,
        eraserStart: PointF,
        eraserEnd: PointF,
        eraserRadius: Float
    ): List<Stroke> {
        val minX = minOf(eraserStart.x, eraserEnd.x) - eraserRadius
        val minY = minOf(eraserStart.y, eraserEnd.y) - eraserRadius
        val maxX = maxOf(eraserStart.x, eraserEnd.x) + eraserRadius
        val maxY = maxOf(eraserStart.y, eraserEnd.y) + eraserRadius
        val sweepBounds = RectF(minX, minY, maxX, maxY)
        val candidates = quadTree.query(sweepBounds)
        return findIntersectingStrokes(candidates, eraserStart, eraserEnd, eraserRadius)
    }

    // Geometry calculation helpers (allocation-free)

    internal fun distanceSquaredToSegment(
        px: Float, py: Float,
        ax: Float, ay: Float,
        bx: Float, by: Float
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        if (lengthSq <= 1e-6f) {
            val diffX = px - ax
            val diffY = py - ay
            return diffX * diffX + diffY * diffY
        }
        val t = (((px - ax) * dx + (py - ay) * dy) / lengthSq).coerceIn(0f, 1f)
        val projX = ax + t * dx
        val projY = ay + t * dy
        val diffX = px - projX
        val diffY = py - projY
        return diffX * diffX + diffY * diffY
    }

    internal fun distanceSquaredSegmentToSegment(
        ax: Float, ay: Float,
        bx: Float, by: Float,
        cx: Float, cy: Float,
        dx: Float, dy: Float
    ): Float {
        if (segmentsIntersect(ax, ay, bx, by, cx, cy, dx, dy)) {
            return 0f
        }
        val d1 = distanceSquaredToSegment(ax, ay, cx, cy, dx, dy)
        val d2 = distanceSquaredToSegment(bx, by, cx, cy, dx, dy)
        val d3 = distanceSquaredToSegment(cx, cy, ax, ay, bx, by)
        val d4 = distanceSquaredToSegment(dx, dy, ax, ay, bx, by)
        return minOf(d1, d2, d3, d4)
    }

    internal fun segmentsIntersect(
        ax: Float, ay: Float,
        bx: Float, by: Float,
        cx: Float, cy: Float,
        dx: Float, dy: Float
    ): Boolean {
        fun ccw(p1x: Float, p1y: Float, p2x: Float, p2y: Float, p3x: Float, p3y: Float): Float {
            return (p2x - p1x) * (p3y - p1y) - (p2y - p1y) * (p3x - p1x)
        }

        val cp1 = ccw(ax, ay, bx, by, cx, cy)
        val cp2 = ccw(ax, ay, bx, by, dx, dy)
        val cp3 = ccw(cx, cy, dx, dy, ax, ay)
        val cp4 = ccw(cx, cy, dx, dy, bx, by)

        return (((cp1 > 0f && cp2 < 0f) || (cp1 < 0f && cp2 > 0f)) &&
                ((cp3 > 0f && cp4 < 0f) || (cp3 < 0f && cp4 > 0f)))
    }
}
