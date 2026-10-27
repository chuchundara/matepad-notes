package com.matepad.notes.domain.tool

import com.matepad.notes.domain.geometry.PointF
import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import com.matepad.notes.domain.spatial.QuadTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LassoSelectionTest {

    private val boxLasso = listOf(
        PointF(0f, 0f),
        PointF(100f, 0f),
        PointF(100f, 100f),
        PointF(0f, 100f)
    )

    @Test
    fun testSelectFullyEnclosedStroke() {
        val stroke = Stroke(
            id = "enclosed-1",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(20f, 20f), StrokePoint(80f, 80f)),
            bounds = RectF(19f, 19f, 81f, 81f)
        )

        assertTrue(LassoSelection.isStrokeSelected(stroke, boxLasso, LassoSelection.SelectionMode.ENCLOSED))
        val selected = LassoSelection.findSelectedStrokes(listOf(stroke), boxLasso)
        assertEquals(1, selected.size)
        assertEquals("enclosed-1", selected[0].id)
    }

    @Test
    fun testRejectStrokeCompletelyOutsideLasso() {
        val outsideStroke = Stroke(
            id = "outside-1",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(200f, 200f), StrokePoint(250f, 250f)),
            bounds = RectF(199f, 199f, 251f, 251f)
        )

        assertFalse(LassoSelection.isStrokeSelected(outsideStroke, boxLasso, LassoSelection.SelectionMode.ENCLOSED))
        assertFalse(LassoSelection.isStrokeSelected(outsideStroke, boxLasso, LassoSelection.SelectionMode.INTERSECTING))
        val selected = LassoSelection.findSelectedStrokes(listOf(outsideStroke), boxLasso)
        assertTrue(selected.isEmpty())
    }

    @Test
    fun testPartiallyEnclosedStrokeModes() {
        // Starts inside (50, 50), exits outside to (150, 50)
        val crossingStroke = Stroke(
            id = "crossing-1",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(50f, 50f), StrokePoint(150f, 50f)),
            bounds = RectF(49f, 49f, 151f, 51f)
        )

        // In ENCLOSED mode, crossing stroke must NOT be selected
        assertFalse(LassoSelection.isStrokeSelected(crossingStroke, boxLasso, LassoSelection.SelectionMode.ENCLOSED))

        // In INTERSECTING mode, crossing stroke MUST be selected
        assertTrue(LassoSelection.isStrokeSelected(crossingStroke, boxLasso, LassoSelection.SelectionMode.INTERSECTING))
        val selected = LassoSelection.findSelectedStrokes(
            listOf(crossingStroke),
            boxLasso,
            LassoSelection.SelectionMode.INTERSECTING
        )
        assertEquals(1, selected.size)
        assertEquals("crossing-1", selected[0].id)
    }

    @Test
    fun testConcavePolygonSelection() {
        // U-shaped lasso: base y in [0, 40], left prong x in [0, 30], right prong x in [70, 100], notch in [30, 70]
        val uLasso = listOf(
            PointF(0f, 0f),
            PointF(100f, 0f),
            PointF(100f, 100f),
            PointF(70f, 100f),
            PointF(70f, 40f),
            PointF(30f, 40f),
            PointF(30f, 100f),
            PointF(0f, 100f)
        )

        // Stroke in left prong (fully inside U polygon)
        val leftProngStroke = Stroke(
            id = "left-prong",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(10f, 20f), StrokePoint(15f, 80f)),
            bounds = RectF(9f, 19f, 16f, 81f)
        )
        assertTrue(LassoSelection.isStrokeSelected(leftProngStroke, uLasso, LassoSelection.SelectionMode.ENCLOSED))

        // Stroke spanning across prongs (endpoints in prongs, but middle bridges across outside notch)
        val bridgeStroke = Stroke(
            id = "bridge",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(15f, 70f), StrokePoint(85f, 70f)),
            bounds = RectF(14f, 69f, 86f, 71f)
        )
        assertFalse(LassoSelection.isStrokeSelected(bridgeStroke, uLasso, LassoSelection.SelectionMode.ENCLOSED))

        // Stroke inside notch cavity (outside polygon, though inside polygon's outer bounding box)
        val notchStroke = Stroke(
            id = "in-notch",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(45f, 60f), StrokePoint(55f, 80f)),
            bounds = RectF(44f, 59f, 56f, 81f)
        )
        assertFalse(LassoSelection.isStrokeSelected(notchStroke, uLasso, LassoSelection.SelectionMode.ENCLOSED))
        assertFalse(LassoSelection.isStrokeSelected(notchStroke, uLasso, LassoSelection.SelectionMode.INTERSECTING))
    }

    @Test
    fun testFindSelectedStrokesWithQuadTree() {
        val quadTree = QuadTree<Stroke>(
            boundary = RectF(0f, 0f, 2000f, 2000f),
            maxObjects = 4,
            maxLevels = 4,
            boundsExtractor = { it.bounds }
        )

        val s1 = Stroke(
            id = "s1",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(30f, 30f), StrokePoint(40f, 40f)),
            bounds = RectF(29f, 29f, 41f, 41f)
        )
        val s2 = Stroke(
            id = "s2",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(500f, 500f), StrokePoint(600f, 600f)),
            bounds = RectF(499f, 499f, 601f, 601f)
        )
        quadTree.insert(s1)
        quadTree.insert(s2)

        val selected = LassoSelection.findSelectedStrokes(quadTree, boxLasso)
        assertEquals(1, selected.size)
        assertEquals("s1", selected[0].id)
    }

    @Test
    fun testLassoStrokeConvenienceOverloads() {
        val lassoStroke = Stroke(
            id = "lasso-stroke",
            toolType = ToolType.LASSO,
            color = 0,
            baseWidth = 1f,
            points = listOf(
                StrokePoint(0f, 0f),
                StrokePoint(100f, 0f),
                StrokePoint(100f, 100f),
                StrokePoint(0f, 100f)
            ),
            bounds = RectF(0f, 0f, 100f, 100f)
        )

        val stroke = Stroke(
            id = "dot",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(50f, 50f)),
            bounds = RectF(49f, 49f, 51f, 51f)
        )

        val selected = LassoSelection.findSelectedStrokes(listOf(stroke), lassoStroke)
        assertEquals(1, selected.size)
        assertEquals("dot", selected[0].id)
    }

    @Test
    fun testDegeneratePolygonAndEmptyStroke() {
        val emptyStroke = Stroke(
            id = "empty",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = emptyList(),
            bounds = RectF(0f, 0f, 0f, 0f)
        )
        assertFalse(LassoSelection.isStrokeSelected(emptyStroke, boxLasso))

        val degenerateLasso = listOf(PointF(0f, 0f), PointF(10f, 10f))
        val stroke = Stroke(
            id = "s",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(5f, 5f)),
            bounds = RectF(4f, 4f, 6f, 6f)
        )
        assertFalse(LassoSelection.isStrokeSelected(stroke, degenerateLasso))
        assertTrue(LassoSelection.findSelectedStrokes(listOf(stroke), degenerateLasso).isEmpty())
    }
}
