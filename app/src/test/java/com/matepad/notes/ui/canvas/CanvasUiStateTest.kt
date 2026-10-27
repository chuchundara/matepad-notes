package com.matepad.notes.ui.canvas

import com.matepad.notes.domain.model.ToolType
import com.matepad.notes.ui.canvas.render.InfiniteCanvasView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanvasUiStateTest {

    @Test
    fun testToolSelection() {
        val state = CanvasUiState(
            selectedTool = ToolType.PEN,
            selectedColor = 0xFF000000.toInt(),
            baseWidth = 3.0f,
            zoomPercentage = 100
        )
        assertEquals(ToolType.PEN, state.selectedTool)
        assertEquals(100, state.zoomPercentage)
    }

    @Test
    fun testDefaultValues() {
        val defaultState = CanvasUiState()
        assertEquals("", defaultState.noteId)
        assertEquals("Untitled Note", defaultState.noteTitle)
        assertEquals(ToolType.PEN, defaultState.selectedTool)
        assertEquals(0xFF000000.toInt(), defaultState.selectedColor)
        assertEquals(3.0f, defaultState.baseWidth, 0.001f)
        assertEquals(100, defaultState.zoomPercentage)
        assertFalse(defaultState.canUndo)
        assertFalse(defaultState.canRedo)
        assertEquals(InfiniteCanvasView.GridStyle.DOTS, defaultState.gridStyle)
        assertEquals(AutosaveStatus.IDLE, defaultState.autosaveStatus)
        assertEquals(ToolbarDockEdge.BOTTOM, defaultState.dockEdge)
        assertFalse(defaultState.isExporting)
        assertNull(defaultState.exportSuccessMessage)
        assertNull(defaultState.errorMessage)
    }

    @Test
    fun testAllToolTypes() {
        val tools = listOf(
            ToolType.PEN,
            ToolType.HIGHLIGHTER,
            ToolType.STROKE_ERASER,
            ToolType.SEGMENT_ERASER,
            ToolType.LASSO
        )
        for (tool in tools) {
            val state = CanvasUiState(selectedTool = tool)
            assertEquals(tool, state.selectedTool)
        }
    }

    @Test
    fun testColorAndWidthUpdate() {
        val state = CanvasUiState()
            .copy(selectedColor = 0xFF1976D2.toInt(), baseWidth = 8.5f)
        assertEquals(0xFF1976D2.toInt(), state.selectedColor)
        assertEquals(8.5f, state.baseWidth, 0.001f)
    }

    @Test
    fun testUndoRedoState() {
        val state = CanvasUiState()
            .copy(canUndo = true, canRedo = true)
        assertTrue(state.canUndo)
        assertTrue(state.canRedo)
    }

    @Test
    fun testGridStyleUpdate() {
        val state = CanvasUiState(gridStyle = InfiniteCanvasView.GridStyle.LINES)
        assertEquals(InfiniteCanvasView.GridStyle.LINES, state.gridStyle)

        val updated = state.copy(gridStyle = InfiniteCanvasView.GridStyle.SQUARES)
        assertEquals(InfiniteCanvasView.GridStyle.SQUARES, updated.gridStyle)

        val blank = state.copy(gridStyle = InfiniteCanvasView.GridStyle.NONE)
        assertEquals(InfiniteCanvasView.GridStyle.NONE, blank.gridStyle)
    }

    @Test
    fun testDockEdgeTransitions() {
        val edges = listOf(
            ToolbarDockEdge.TOP,
            ToolbarDockEdge.BOTTOM,
            ToolbarDockEdge.LEFT,
            ToolbarDockEdge.RIGHT,
            ToolbarDockEdge.FLOATING
        )
        for (edge in edges) {
            val state = CanvasUiState(dockEdge = edge)
            assertEquals(edge, state.dockEdge)
        }
    }

    @Test
    fun testAutosaveStatusTransitions() {
        val idle = CanvasUiState()
        assertEquals(AutosaveStatus.IDLE, idle.autosaveStatus)

        val saving = idle.copy(autosaveStatus = AutosaveStatus.SAVING)
        assertEquals(AutosaveStatus.SAVING, saving.autosaveStatus)

        val saved = saving.copy(autosaveStatus = AutosaveStatus.SAVED, lastSavedTimeMillis = 1000L)
        assertEquals(AutosaveStatus.SAVED, saved.autosaveStatus)
        assertEquals(1000L, saved.lastSavedTimeMillis)

        val error = saving.copy(autosaveStatus = AutosaveStatus.ERROR, errorMessage = "Disk full")
        assertEquals(AutosaveStatus.ERROR, error.autosaveStatus)
        assertEquals("Disk full", error.errorMessage)
    }

    @Test
    fun testNoteTitleUpdate() {
        val state = CanvasUiState(noteTitle = "Meeting Notes")
        assertEquals("Meeting Notes", state.noteTitle)

        val renamed = state.copy(noteTitle = "Architecture Brainstorm")
        assertEquals("Architecture Brainstorm", renamed.noteTitle)
    }

    @Test
    fun testStrokeCountAndExportStatus() {
        val state = CanvasUiState(
            strokeCount = 42,
            isExporting = true,
            exportSuccessMessage = "Exported to PDF"
        )
        assertEquals(42, state.strokeCount)
        assertTrue(state.isExporting)
        assertEquals("Exported to PDF", state.exportSuccessMessage)
    }
}
