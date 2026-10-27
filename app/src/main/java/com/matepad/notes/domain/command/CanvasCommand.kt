package com.matepad.notes.domain.command

import com.matepad.notes.domain.model.Stroke

interface CanvasCommand {
    fun execute()
    fun undo()
}

class AddStrokeCommand(
    val stroke: Stroke,
    val targetList: MutableList<Stroke>
) : CanvasCommand {
    override fun execute() {
        targetList.add(stroke)
    }

    override fun undo() {
        targetList.remove(stroke)
    }
}

class DeleteStrokesCommand(
    val deletedStrokes: List<Stroke>,
    val targetList: MutableList<Stroke>
) : CanvasCommand {

    val deletedStrokesWithIndices: List<Pair<Int, Stroke>> = deletedStrokes.mapNotNull { stroke ->
        val idx = targetList.indexOf(stroke)
        if (idx >= 0) idx to stroke else null
    }

    override fun execute() {
        val toRemove = deletedStrokes.toSet()
        targetList.removeAll(toRemove)
    }

    override fun undo() {
        val sorted = deletedStrokesWithIndices.sortedBy { it.first }
        for ((index, stroke) in sorted) {
            val safeIndex = index.coerceIn(0, targetList.size)
            targetList.add(safeIndex, stroke)
        }
    }
}
