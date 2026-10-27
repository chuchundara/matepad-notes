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
    val deletedStrokesWithIndices: List<Pair<Int, Stroke>>,
    val targetList: MutableList<Stroke>
) : CanvasCommand {

    constructor(
        deletedStrokes: List<Stroke>,
        targetList: MutableList<Stroke>
    ) : this(
        deletedStrokesWithIndices = deletedStrokes.mapNotNull { stroke ->
            val idx = targetList.indexOf(stroke)
            if (idx >= 0) idx to stroke else null
        },
        targetList = targetList
    )

    val deletedStrokes: List<Stroke>
        get() = deletedStrokesWithIndices.map { it.second }

    override fun execute() {
        val toRemove = deletedStrokesWithIndices.map { it.second }.toSet()
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
