package com.matepad.notes.domain.model

import com.matepad.notes.domain.geometry.RectF

data class Stroke(
    val id: String,
    val toolType: ToolType,
    val color: Int,
    val baseWidth: Float,
    val points: List<StrokePoint>,
    val bounds: RectF
)
