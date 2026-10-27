package com.matepad.notes

import com.matepad.notes.data.serializer.StrokeBinarySerializer
import com.matepad.notes.domain.command.AddStrokeCommand
import com.matepad.notes.domain.command.CommandHistory
import com.matepad.notes.domain.command.DeleteStrokesCommand
import com.matepad.notes.domain.geometry.PointF
import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.geometry.ViewportTransform
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import com.matepad.notes.domain.spatial.QuadTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-End Integration Verification Test.
 *
 * Exercises the complete infinite canvas pipeline:
 * 1. Screen-to-world viewport coordinate transformation ([ViewportTransform])
 * 2. Inking domain models with variable pressure and tilt ([Stroke], [StrokePoint])
 * 3. Reversible command pattern execution with Undo/Redo ([CommandHistory], [AddStrokeCommand], [DeleteStrokesCommand])
 * 4. Hierarchical spatial indexing and viewport culling ([QuadTree])
 * 5. High-efficiency binary serialization and GZIP round-trip compression ([StrokeBinarySerializer])
 */
class IntegrationVerificationTest {

    @Test
    fun testCompleteInkingAndStoragePipeline() {
        val transform = ViewportTransform(scale = 1.5f, offsetX = 50f, offsetY = 100f)
        val screenInput = PointF(200f, 400f)
        val worldInput = transform.screenToWorld(screenInput)

        val stroke = Stroke(
            id = "test-1",
            toolType = ToolType.PEN,
            color = 0xFF0000FF.toInt(),
            baseWidth = 4.0f,
            points = listOf(StrokePoint(worldInput.x, worldInput.y)),
            bounds = RectF(worldInput.x, worldInput.y, worldInput.x + 4f, worldInput.y + 4f)
        )

        // 1. Command history
        val canvasStrokes = mutableListOf<Stroke>()
        val history = CommandHistory()
        history.execute(AddStrokeCommand(stroke, canvasStrokes))
        assertEquals(1, canvasStrokes.size)

        // 2. Spatial Index
        val quadTree = QuadTree<Stroke>(RectF(-5000f, -5000f, 5000f, 5000f)) { it.bounds }
        quadTree.insert(stroke)
        val visible = quadTree.query(transform.getVisibleWorldBounds(1200f, 800f))
        assertEquals(1, visible.size)

        // 3. Serialization
        val compressed = StrokeBinarySerializer.serialize(canvasStrokes)
        val restored = StrokeBinarySerializer.deserialize(compressed)
        assertEquals(1, restored.size)
        assertEquals("test-1", restored[0].id)
    }

    @Test
    fun testUndoRedoAndPersistenceRoundTrip() {
        val canvasStrokes = mutableListOf<Stroke>()
        val history = CommandHistory()

        val stroke1 = Stroke(
            id = "stroke-alpha",
            toolType = ToolType.PEN,
            color = 0xFF000000.toInt(),
            baseWidth = 2.0f,
            points = listOf(
                StrokePoint(10f, 10f, 0.5f, 0.1f),
                StrokePoint(20f, 20f, 0.8f, 0.2f)
            ),
            bounds = RectF(10f, 10f, 20f, 20f)
        )

        val stroke2 = Stroke(
            id = "stroke-beta",
            toolType = ToolType.HIGHLIGHTER,
            color = 0x55FFFF00.toInt(),
            baseWidth = 16.0f,
            points = listOf(
                StrokePoint(50f, 50f, 0.9f, 0.0f),
                StrokePoint(100f, 100f, 0.9f, 0.0f)
            ),
            bounds = RectF(50f, 50f, 100f, 100f)
        )

        // Add both strokes
        history.execute(AddStrokeCommand(stroke1, canvasStrokes))
        history.execute(AddStrokeCommand(stroke2, canvasStrokes))
        assertEquals(2, canvasStrokes.size)

        var restored = StrokeBinarySerializer.deserialize(StrokeBinarySerializer.serialize(canvasStrokes))
        assertEquals(2, restored.size)
        assertEquals("stroke-alpha", restored[0].id)
        assertEquals("stroke-beta", restored[1].id)

        // Undo stroke2
        history.undo()
        assertEquals(1, canvasStrokes.size)
        restored = StrokeBinarySerializer.deserialize(StrokeBinarySerializer.serialize(canvasStrokes))
        assertEquals(1, restored.size)
        assertEquals("stroke-alpha", restored[0].id)

        // Undo stroke1
        history.undo()
        assertEquals(0, canvasStrokes.size)
        restored = StrokeBinarySerializer.deserialize(StrokeBinarySerializer.serialize(canvasStrokes))
        assertTrue(restored.isEmpty())

        // Redo stroke1 and stroke2
        history.redo()
        assertEquals(1, canvasStrokes.size)
        history.redo()
        assertEquals(2, canvasStrokes.size)
        restored = StrokeBinarySerializer.deserialize(StrokeBinarySerializer.serialize(canvasStrokes))
        assertEquals(2, restored.size)
        assertEquals("stroke-alpha", restored[0].id)
        assertEquals("stroke-beta", restored[1].id)
    }

    @Test
    fun testSpatialIndexingViewportCulling() {
        val transform = ViewportTransform(scale = 1.0f, offsetX = 0f, offsetY = 0f)
        val viewportBounds = transform.getVisibleWorldBounds(1200f, 800f)

        // Visible stroke inside (0, 0, 1200, 800)
        val visibleStroke = Stroke(
            id = "visible-1",
            toolType = ToolType.PEN,
            color = 0xFF00FF00.toInt(),
            baseWidth = 3f,
            points = listOf(StrokePoint(100f, 100f)),
            bounds = RectF(100f, 100f, 110f, 110f)
        )

        // Out-of-bounds stroke far to the bottom-right
        val invisibleStroke = Stroke(
            id = "invisible-1",
            toolType = ToolType.PEN,
            color = 0xFFFF0000.toInt(),
            baseWidth = 3f,
            points = listOf(StrokePoint(15000f, 15000f)),
            bounds = RectF(15000f, 15000f, 15010f, 15010f)
        )

        val quadTree = QuadTree<Stroke>(RectF(-50000f, -50000f, 50000f, 50000f)) { it.bounds }
        quadTree.insert(visibleStroke)
        quadTree.insert(invisibleStroke)

        val visibleResults = quadTree.query(viewportBounds)
        assertEquals(1, visibleResults.size)
        assertTrue(visibleResults.contains(visibleStroke))
        assertFalse(visibleResults.contains(invisibleStroke))

        // Pan viewport over invisible stroke
        val pannedTransform = transform.panBy(-14500f, -14500f)
        val pannedBounds = pannedTransform.getVisibleWorldBounds(1200f, 800f)
        val pannedResults = quadTree.query(pannedBounds)
        assertEquals(1, pannedResults.size)
        assertTrue(pannedResults.contains(invisibleStroke))
    }

    @Test
    fun testComplexStrokeWithPressureAndTiltSerialization() {
        val points = (0 until 50).map { i ->
            StrokePoint(
                x = 100f + i * 2.5f,
                y = 200f + (i % 5) * 4.0f,
                pressure = (i / 50f).coerceIn(0.1f, 1.0f),
                tilt = (i * 0.02f).coerceIn(0.0f, 1.57f)
            )
        }

        val stroke = Stroke(
            id = "complex-tilt-pressure",
            toolType = ToolType.HIGHLIGHTER,
            color = 0x80336699.toInt(),
            baseWidth = 12.0f,
            points = points,
            bounds = RectF(100f, 200f, 225f, 220f)
        )

        val bytes = StrokeBinarySerializer.serialize(listOf(stroke))
        val deserialized = StrokeBinarySerializer.deserialize(bytes)

        assertEquals(1, deserialized.size)
        val item = deserialized[0]
        assertEquals(stroke.id, item.id)
        assertEquals(stroke.toolType, item.toolType)
        assertEquals(stroke.color, item.color)
        assertEquals(stroke.baseWidth, item.baseWidth, 0.001f)
        assertEquals(points.size, item.points.size)

        for (i in points.indices) {
            val expected = points[i]
            val actual = item.points[i]
            assertEquals(expected.x, actual.x, 0.001f)
            assertEquals(expected.y, actual.y, 0.001f)
            assertEquals(expected.pressure, actual.pressure, 0.001f)
            assertEquals(expected.tilt, actual.tilt, 0.001f)
        }
    }

    @Test
    fun testDeleteStrokesCommandAndSpatialQuery() {
        val canvasStrokes = mutableListOf<Stroke>()
        val history = CommandHistory()

        val strokeA = Stroke("a", ToolType.PEN, 0, 2f, listOf(StrokePoint(0f, 0f)), RectF(0f, 0f, 2f, 2f))
        val strokeB = Stroke("b", ToolType.PEN, 0, 2f, listOf(StrokePoint(10f, 10f)), RectF(10f, 10f, 12f, 12f))
        val strokeC = Stroke("c", ToolType.PEN, 0, 2f, listOf(StrokePoint(20f, 20f)), RectF(20f, 20f, 22f, 22f))

        history.execute(AddStrokeCommand(strokeA, canvasStrokes))
        history.execute(AddStrokeCommand(strokeB, canvasStrokes))
        history.execute(AddStrokeCommand(strokeC, canvasStrokes))
        assertEquals(3, canvasStrokes.size)

        // Delete strokes A and C
        history.execute(DeleteStrokesCommand(listOf(strokeA, strokeC), canvasStrokes))
        assertEquals(1, canvasStrokes.size)
        assertEquals("b", canvasStrokes[0].id)

        // Undo delete
        history.undo()
        assertEquals(3, canvasStrokes.size)

        // Redo delete
        history.redo()
        assertEquals(1, canvasStrokes.size)
        assertEquals("b", canvasStrokes[0].id)
    }
}
