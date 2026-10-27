package com.matepad.notes.domain.command

class CommandHistory {
    private val undoStack = mutableListOf<CanvasCommand>()
    private val redoStack = mutableListOf<CanvasCommand>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    fun execute(command: CanvasCommand) {
        command.execute()
        undoStack.add(command)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val command = undoStack.removeAt(undoStack.lastIndex)
            command.undo()
            redoStack.add(command)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val command = redoStack.removeAt(redoStack.lastIndex)
            command.execute()
            undoStack.add(command)
        }
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
