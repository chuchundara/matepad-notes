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
    override fun execute() {
        targetList.removeAll(deletedStrokes.toSet())
    }

    override fun undo() {
        targetList.addAll(deletedStrokes)
    }
}
