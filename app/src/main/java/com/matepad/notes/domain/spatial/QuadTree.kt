package com.matepad.notes.domain.spatial

import com.matepad.notes.domain.geometry.RectF

class QuadTree<T>(
    val boundary: RectF,
    private val maxObjects: Int = 8,
    private val maxLevels: Int = 6,
    private val level: Int = 0,
    private val boundsExtractor: (T) -> RectF
) {
    constructor(boundary: RectF, boundsExtractor: (T) -> RectF) : this(
        boundary = boundary,
        maxObjects = 8,
        maxLevels = 6,
        level = 0,
        boundsExtractor = boundsExtractor
    )

    private val items = mutableListOf<T>()
    private var subnodes: Array<QuadTree<T>>? = null

    fun insert(item: T) {
        val itemBounds = boundsExtractor(item)
        if (!boundary.intersects(itemBounds)) {
            return
        }

        if (level >= maxLevels || (subnodes == null && items.size < maxObjects)) {
            items.add(item)
            return
        }

        if (subnodes == null) {
            subdivide()
            val existing = ArrayList(items)
            items.clear()
            for (old in existing) {
                insertIntoSubnodes(old)
            }
        }

        insertIntoSubnodes(item)
    }

    private fun insertIntoSubnodes(item: T) {
        var placed = false
        val itemBounds = boundsExtractor(item)
        subnodes?.forEach { node ->
            if (node.boundary.intersects(itemBounds)) {
                node.insert(item)
                placed = true
            }
        }
        if (!placed) {
            items.add(item)
        }
    }

    private fun subdivide() {
        val midX = boundary.left + boundary.width / 2f
        val midY = boundary.top + boundary.height / 2f

        subnodes = arrayOf(
            QuadTree(RectF(boundary.left, boundary.top, midX, midY), maxObjects, maxLevels, level + 1, boundsExtractor), // NW
            QuadTree(RectF(midX, boundary.top, boundary.right, midY), maxObjects, maxLevels, level + 1, boundsExtractor), // NE
            QuadTree(RectF(boundary.left, midY, midX, boundary.bottom), maxObjects, maxLevels, level + 1, boundsExtractor), // SW
            QuadTree(RectF(midX, midY, boundary.right, boundary.bottom), maxObjects, maxLevels, level + 1, boundsExtractor)  // SE
        )
    }

    fun query(range: RectF, result: MutableSet<T> = mutableSetOf()): Set<T> {
        if (!boundary.intersects(range)) {
            return result
        }

        for (item in items) {
            if (range.intersects(boundsExtractor(item))) {
                result.add(item)
            }
        }

        subnodes?.forEach { node ->
            node.query(range, result)
        }

        return result
    }

    fun clear() {
        items.clear()
        subnodes?.forEach { it.clear() }
        subnodes = null
    }
}
