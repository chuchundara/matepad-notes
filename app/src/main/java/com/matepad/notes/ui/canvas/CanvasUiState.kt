package com.matepad.notes.ui.canvas

import com.matepad.notes.domain.model.ToolType
import com.matepad.notes.ui.canvas.render.InfiniteCanvasView

/**
 * Represents the edge or floating positioning of the movable tool palette on the tablet.
 */
enum class ToolbarDockEdge {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    FLOATING
}

/**
 * Indicates background debounced persistence status of notes and preview generation.
 */
enum class AutosaveStatus {
    IDLE,
    SAVING,
    SAVED,
    ERROR
}

/**
 * Immutable state model for the tablet canvas screen, floating toolbar, and navigation bar.
 */
data class CanvasUiState(
    val noteId: String = "",
    val noteTitle: String = "Untitled Note",
    val selectedTool: ToolType = ToolType.PEN,
    val selectedColor: Int = 0xFF000000.toInt(),
    val baseWidth: Float = 3.0f,
    val zoomPercentage: Int = 100,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val gridStyle: InfiniteCanvasView.GridStyle = InfiniteCanvasView.GridStyle.DOTS,
    val autosaveStatus: AutosaveStatus = AutosaveStatus.IDLE,
    val lastSavedTimeMillis: Long = 0L,
    val strokeCount: Int = 0,
    val dockEdge: ToolbarDockEdge = ToolbarDockEdge.BOTTOM,
    val isExporting: Boolean = false,
    val exportSuccessMessage: String? = null,
    val errorMessage: String? = null
)
