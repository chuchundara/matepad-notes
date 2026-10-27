package com.matepad.notes.domain.command

import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandHistoryTest {

    private fun createDummyStroke(id: String): Stroke {
        return Stroke(
            id = id,
            toolType = ToolType.PEN,
            color = 0xFF000000.toInt(),
            baseWidth = 2.0f,
            points = listOf(StrokePoint(0f, 0f, 0.5f, 0f)),
            bounds = RectF(0f, 0f, 2f, 2f)
        )
    }

    @Test
    fun testUndoRedoCycle() {
        val history = CommandHistory()
        val strokes = mutableListOf<Stroke>()

        val stroke1 = createDummyStroke("s1")

        val addCmd = AddStrokeCommand(stroke1, strokes)
        history.execute(addCmd)
        assertEquals(1, strokes.size)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)

        history.undo()
        assertEquals(0, strokes.size)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)

        history.redo()
        assertEquals(1, strokes.size)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun testDeleteStrokesCommandUndoRedo() {
        val history = CommandHistory()
        val stroke1 = createDummyStroke("s1")
        val stroke2 = createDummyStroke("s2")
        val strokes = mutableListOf(stroke1, stroke2)

        val deleteCmd = DeleteStrokesCommand(listOf(stroke1), strokes)
        history.execute(deleteCmd)
        assertEquals(1, strokes.size)
        assertEquals("s2", strokes[0].id)
        assertTrue(history.canUndo)

        history.undo()
        assertEquals(2, strokes.size)
        assertTrue(strokes.contains(stroke1))
        assertTrue(strokes.contains(stroke2))

        history.redo()
        assertEquals(1, strokes.size)
        assertEquals("s2", strokes[0].id)
    }

    @Test
    fun testNewCommandClearsRedoStack() {
        val history = CommandHistory()
        val strokes = mutableListOf<Stroke>()
        val stroke1 = createDummyStroke("s1")
        val stroke2 = createDummyStroke("s2")

        history.execute(AddStrokeCommand(stroke1, strokes))
        history.undo()
        assertTrue(history.canRedo)

        // Executing a new command must invalidate redo stack
        history.execute(AddStrokeCommand(stroke2, strokes))
        assertFalse(history.canRedo)
        assertTrue(history.canUndo)
        assertEquals(1, strokes.size)
        assertEquals("s2", strokes[0].id)
    }

    @Test
    fun testEmptyHistoryUndoRedoNoOp() {
        val history = CommandHistory()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)

        // Calling undo or redo on empty history should not throw
        history.undo()
        history.redo()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun testClear() {
        val history = CommandHistory()
        val strokes = mutableListOf<Stroke>()
        val stroke1 = createDummyStroke("s1")

        history.execute(AddStrokeCommand(stroke1, strokes))
        history.undo()
        assertTrue(history.canRedo)

        history.clear()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }
}
