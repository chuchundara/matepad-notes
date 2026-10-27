package com.matepad.notes.domain.spatial

import com.matepad.notes.domain.geometry.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadTreeTest {
    data class TestItem(val id: String, val bounds: RectF)

    @Test
    fun testInsertAndQueryVisibleItems() {
        val tree = QuadTree<TestItem>(
            boundary = RectF(-1000f, -1000f, 1000f, 1000f),
            maxObjects = 4,
            maxLevels = 5
        ) { it.bounds }

        val item1 = TestItem("item1", RectF(10f, 10f, 50f, 50f))
        val item2 = TestItem("item2", RectF(500f, 500f, 600f, 600f))
        val item3 = TestItem("item3", RectF(-200f, -200f, -100f, -100f))

        tree.insert(item1)
        tree.insert(item2)
        tree.insert(item3)

        // Query viewport covering only item1
        val visibleInViewport = tree.query(RectF(0f, 0f, 100f, 100f))
        assertEquals(1, visibleInViewport.size)
        assertTrue(visibleInViewport.contains(item1))

        // Query viewport covering item1 and item2
        val wideQuery = tree.query(RectF(0f, 0f, 800f, 800f))
        assertEquals(2, wideQuery.size)
        assertTrue(wideQuery.contains(item1))
        assertTrue(wideQuery.contains(item2))
    }

    @Test
    fun testItemSpanningSubdivisions() {
        val tree = QuadTree<TestItem>(
            boundary = RectF(-100f, -100f, 100f, 100f),
            maxObjects = 1,
            maxLevels = 3
        ) { it.bounds }

        val centerSpan = TestItem("centerSpan", RectF(-10f, -10f, 10f, 10f))
        val itemNW = TestItem("nw", RectF(-80f, -80f, -60f, -60f))
        val itemSE = TestItem("se", RectF(60f, 60f, 80f, 80f))

        tree.insert(centerSpan)
        tree.insert(itemNW)
        tree.insert(itemSE)

        val queryNW = tree.query(RectF(-90f, -90f, -50f, -50f))
        assertTrue(queryNW.contains(itemNW))
        assertEquals(1, queryNW.size)

        val queryCenter = tree.query(RectF(-20f, -20f, 20f, 20f))
        assertTrue(queryCenter.contains(centerSpan))
        assertEquals(1, queryCenter.size)
    }

    @Test
    fun testItemOutsideBoundaryIgnored() {
        val tree = QuadTree<TestItem>(
            boundary = RectF(0f, 0f, 100f, 100f),
            maxObjects = 4,
            maxLevels = 4
        ) { it.bounds }

        val outsideItem = TestItem("outside", RectF(200f, 200f, 300f, 300f))
        tree.insert(outsideItem)

        val queryAll = tree.query(RectF(0f, 0f, 500f, 500f))
        assertTrue(queryAll.isEmpty())
    }

    @Test
    fun testClear() {
        val tree = QuadTree<TestItem>(
            boundary = RectF(0f, 0f, 100f, 100f),
            maxObjects = 2,
            maxLevels = 3
        ) { it.bounds }

        tree.insert(TestItem("1", RectF(10f, 10f, 20f, 20f)))
        tree.insert(TestItem("2", RectF(30f, 30f, 40f, 40f)))
        tree.insert(TestItem("3", RectF(50f, 50f, 60f, 60f)))

        assertEquals(3, tree.query(RectF(0f, 0f, 100f, 100f)).size)

        tree.clear()
        assertTrue(tree.query(RectF(0f, 0f, 100f, 100f)).isEmpty())
    }

    @Test
    fun testMaxLevelsExceeded() {
        val tree = QuadTree<TestItem>(
            boundary = RectF(0f, 0f, 100f, 100f),
            maxObjects = 1,
            maxLevels = 1
        ) { it.bounds }

        val items = (1..10).map { i ->
            TestItem("item$i", RectF(10f, 10f, 20f, 20f))
        }
        items.forEach { tree.insert(it) }

        val result = tree.query(RectF(0f, 0f, 100f, 100f))
        assertEquals(10, result.size)
    }
}
