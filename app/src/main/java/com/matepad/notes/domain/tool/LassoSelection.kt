package com.matepad.notes.domain.tool

import com.matepad.notes.domain.geometry.PointF
import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.spatial.QuadTree

/**
 * Domain-level lasso selection engine using ray-casting point-in-polygon testing.
 *
 * Enables selecting strokes enclosed by an arbitrary user-drawn loop.
 */
object LassoSelection {

    enum class SelectionMode {
        /**
         * Stroke is selected only when all of its points are inside the lasso loop
         * and the stroke does not cross outside the loop boundary.
         */
        ENCLOSED,

        /**
         * Stroke is selected if any of its points or line segments intersect or lie inside
         * the lasso loop.
         */
        INTERSECTING
    }

    /**
     * Determines whether [point] is inside the polygon formed by [polygon] vertices
     * using the ray-casting algorithm (even-odd crossing rule).
     */
    fun isPointInPolygon(point: PointF, polygon: List<PointF>): Boolean {
        val cleanPolygon = sanitizePolygon(polygon)
        if (cleanPolygon.size < 3) return false

        val px = point.x
        val py = point.y
        var inside = false
        var j = cleanPolygon.size - 1

        for (i in cleanPolygon.indices) {
            val pi = cleanPolygon[i]
            val pj = cleanPolygon[j]

            if ((pi.y > py) != (pj.y > py)) {
                val intersectX = pi.x + (py - pi.y) * (pj.x - pi.x) / (pj.y - pi.y)
                if (px < intersectX) {
                    inside = !inside
                }
            }
            j = i
        }
        return inside
    }

    /**
     * Computes the axis-aligned bounding box of a polygon.
     */
    fun computePolygonBounds(polygon: List<PointF>): RectF {
        if (polygon.isEmpty()) return RectF(0f, 0f, 0f, 0f)
        var minX = polygon[0].x
        var minY = polygon[0].y
        var maxX = polygon[0].x
        var maxY = polygon[0].y
        for (i in 1 until polygon.size) {
            val p = polygon[i]
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return RectF(minX, minY, maxX, maxY)
    }

    /**
     * Tests whether [stroke] satisfies the selection criteria against [lassoPolygon].
     */
    fun isStrokeSelected(
        stroke: Stroke,
        lassoPolygon: List<PointF>,
        selectionMode: SelectionMode = SelectionMode.ENCLOSED
    ): Boolean {
        val cleanPolygon = sanitizePolygon(lassoPolygon)
        if (cleanPolygon.size < 3 || stroke.points.isEmpty()) return false

        return when (selectionMode) {
            SelectionMode.ENCLOSED -> {
                // All points must lie inside the polygon
                for (p in stroke.points) {
                    if (!isPointInPolygon(PointF(p.x, p.y), cleanPolygon)) {
                        return false
                    }
                }
                // No stroke segment may cross outside the polygon boundary
                if (stroke.points.size >= 2 && strokeIntersectsPolygonBoundary(stroke.points, cleanPolygon)) {
                    return false
                }
                true
            }
            SelectionMode.INTERSECTING -> {
                // True if any point is inside
                for (p in stroke.points) {
                    if (isPointInPolygon(PointF(p.x, p.y), cleanPolygon)) {
                        return true
                    }
                }
                // Or if any stroke segment crosses the polygon boundary
                if (stroke.points.size >= 2 && strokeIntersectsPolygonBoundary(stroke.points, cleanPolygon)) {
                    return true
                }
                false
            }
        }
    }

    /**
     * Finds all strokes within [strokes] selected by [lassoPolygon].
     */
    fun findSelectedStrokes(
        strokes: Collection<Stroke>,
        lassoPolygon: List<PointF>,
        selectionMode: SelectionMode = SelectionMode.ENCLOSED
    ): List<Stroke> {
        val cleanPolygon = sanitizePolygon(lassoPolygon)
        if (cleanPolygon.size < 3) return emptyList()

        val polyBounds = computePolygonBounds(cleanPolygon)
        return strokes.filter { stroke ->
            if (!polyBounds.intersects(stroke.bounds)) {
                false
            } else {
                isStrokeSelected(stroke, cleanPolygon, selectionMode)
            }
        }
    }

    /**
     * Finds all strokes within [quadTree] selected by [lassoPolygon].
     * Performs spatial index bounding box culling before detailed polygon testing.
     */
    fun findSelectedStrokes(
        quadTree: QuadTree<Stroke>,
        lassoPolygon: List<PointF>,
        selectionMode: SelectionMode = SelectionMode.ENCLOSED
    ): List<Stroke> {
        val cleanPolygon = sanitizePolygon(lassoPolygon)
        if (cleanPolygon.size < 3) return emptyList()

        val polyBounds = computePolygonBounds(cleanPolygon)
        val candidates = quadTree.query(polyBounds)
        return findSelectedStrokes(candidates, cleanPolygon, selectionMode)
    }

    /**
     * Convenience overload accepting a lasso [Stroke].
     */
    fun findSelectedStrokes(
        quadTree: QuadTree<Stroke>,
        lassoStroke: Stroke,
        selectionMode: SelectionMode = SelectionMode.ENCLOSED
    ): List<Stroke> {
        val polygon = lassoStroke.points.map { PointF(it.x, it.y) }
        return findSelectedStrokes(quadTree, polygon, selectionMode)
    }

    /**
     * Convenience overload accepting a collection of strokes and a lasso [Stroke].
     */
    fun findSelectedStrokes(
        strokes: Collection<Stroke>,
        lassoStroke: Stroke,
        selectionMode: SelectionMode = SelectionMode.ENCLOSED
    ): List<Stroke> {
        val polygon = lassoStroke.points.map { PointF(it.x, it.y) }
        return findSelectedStrokes(strokes, polygon, selectionMode)
    }

    private fun sanitizePolygon(polygon: List<PointF>): List<PointF> {
        if (polygon.size >= 2) {
            val first = polygon.first()
            val last = polygon.last()
            if (first.x == last.x && first.y == last.y) {
                return polygon.dropLast(1)
            }
        }
        return polygon
    }

    private fun strokeIntersectsPolygonBoundary(points: List<StrokePoint>, polygon: List<PointF>): Boolean {
        for (s in 0 until points.size - 1) {
            val s0 = points[s]
            val s1 = points[s + 1]

            var prev = polygon.size - 1
            for (curr in polygon.indices) {
                val p0 = polygon[prev]
                val p1 = polygon[curr]

                if (segmentsIntersect(s0.x, s0.y, s1.x, s1.y, p0.x, p0.y, p1.x, p1.y)) {
                    return true
                }
                prev = curr
            }
        }
        return false
    }

    private fun segmentsIntersect(
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
