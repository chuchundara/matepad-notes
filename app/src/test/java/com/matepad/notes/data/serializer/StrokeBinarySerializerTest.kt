package com.matepad.notes.data.serializer

import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeBinarySerializerTest {
    @Test
    fun testSerializeAndDeserializeStrokes() {
        val originalStrokes = listOf(
            Stroke(
                id = "stroke-1",
                toolType = ToolType.PEN,
                color = 0xFF123456.toInt(),
                baseWidth = 3.5f,
                points = listOf(
                    StrokePoint(10.0f, 20.0f, 0.75f, 0.1f),
                    StrokePoint(12.5f, 22.5f, 0.85f, 0.12f)
                ),
                bounds = RectF(10.0f, 20.0f, 12.5f, 22.5f)
            )
        )

        val bytes = StrokeBinarySerializer.serialize(originalStrokes)
        val deserialized = StrokeBinarySerializer.deserialize(bytes)

        assertEquals(originalStrokes.size, deserialized.size)
        val item = deserialized[0]
        assertEquals(originalStrokes[0].id, item.id)
        assertEquals(originalStrokes[0].toolType, item.toolType)
        assertEquals(originalStrokes[0].color, item.color)
        assertEquals(originalStrokes[0].baseWidth, item.baseWidth, 0.001f)
        assertEquals(2, item.points.size)
        assertEquals(10.0f, item.points[0].x, 0.001f)
        assertEquals(20.0f, item.points[0].y, 0.001f)
        assertEquals(0.75f, item.points[0].pressure, 0.001f)
        assertEquals(0.1f, item.points[0].tilt, 0.001f)
        assertEquals(10.0f, item.bounds.left, 0.001f)
        assertEquals(20.0f, item.bounds.top, 0.001f)
        assertEquals(12.5f, item.bounds.right, 0.001f)
        assertEquals(22.5f, item.bounds.bottom, 0.001f)
    }

    @Test
    fun testEmptyStrokesList() {
        val bytes = StrokeBinarySerializer.serialize(emptyList())
        val deserialized = StrokeBinarySerializer.deserialize(bytes)
        assertTrue(deserialized.isEmpty())
    }

    @Test
    fun testMultipleStrokesWithDifferentTools() {
        val originalStrokes = listOf(
            Stroke(
                id = "pen-1",
                toolType = ToolType.PEN,
                color = 0xFF000000.toInt(),
                baseWidth = 2.0f,
                points = listOf(StrokePoint(0f, 0f, 0.5f, 0f)),
                bounds = RectF(0f, 0f, 1f, 1f)
            ),
            Stroke(
                id = "highlighter-1",
                toolType = ToolType.HIGHLIGHTER,
                color = 0x80FFFF00.toInt(),
                baseWidth = 15.0f,
                points = listOf(StrokePoint(5f, 10f, 1.0f, 0.2f), StrokePoint(50f, 10f, 1.0f, 0.2f)),
                bounds = RectF(5f, 5f, 50f, 15f)
            ),
            Stroke(
                id = "lasso-1",
                toolType = ToolType.LASSO,
                color = 0x00000000,
                baseWidth = 1.0f,
                points = emptyList(),
                bounds = RectF(0f, 0f, 0f, 0f)
            )
        )

        val bytes = StrokeBinarySerializer.serialize(originalStrokes)
        val deserialized = StrokeBinarySerializer.deserialize(bytes)

        assertEquals(3, deserialized.size)
        assertEquals(ToolType.PEN, deserialized[0].toolType)
        assertEquals(ToolType.HIGHLIGHTER, deserialized[1].toolType)
        assertEquals(ToolType.LASSO, deserialized[2].toolType)
        assertEquals(0, deserialized[2].points.size)
    }

    @Test
    fun testInvalidHeaderThrows() {
        val invalidBytes = byteArrayOf(0, 1, 2, 3)
        assertThrows(Exception::class.java) {
            StrokeBinarySerializer.deserialize(invalidBytes)
        }
    }

    @Test
    fun testNegativeCountThrows() {
        val byteStream = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(byteStream).use { gzip ->
            java.io.DataOutputStream(gzip).use { out ->
                out.writeInt(0x4E4F5445)
                out.writeInt(1)
                out.writeInt(-5) // Negative stroke count
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            StrokeBinarySerializer.deserialize(byteStream.toByteArray())
        }
    }
}
