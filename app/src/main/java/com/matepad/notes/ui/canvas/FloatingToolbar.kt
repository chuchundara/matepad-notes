package com.matepad.notes.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.matepad.notes.domain.model.ToolType
import kotlin.math.roundToInt

/**
 * Palette of standard tablet note-taking ink colors.
 */
val PresetInkColors = listOf(
    0xFF000000.toInt(), // Black
    0xFF1976D2.toInt(), // Blue
    0xFFD32F2F.toInt(), // Red
    0xFF388E3C.toInt(), // Green
    0xFFF57C00.toInt(), // Orange
    0xFF7B1FA2.toInt()  // Purple
)

/**
 * Floating, draggable and dockable tool palette designed for tablet productivity.
 *
 * Supports docking to any edge of the tablet (Top, Bottom, Left, Right) to accommodate
 * left- and right-handed stylus users, as well as floating anywhere over the canvas.
 *
 * Features:
 * - Dedicated drag handle area preventing accidental gesture interception on tool buttons
 * - Direct tool selection: Pen, Highlighter, Stroke Eraser, Lasso Selection
 * - History navigation: Undo and Redo
 * - Ink customization: 6 preset colors and variable stroke width slider
 */
@Composable
fun FloatingToolbar(
    state: CanvasUiState,
    onToolSelected: (ToolType) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
    onColorSelected: (Int) -> Unit = {},
    onBaseWidthChanged: (Float) -> Unit = {},
    onDockEdgeChanged: (ToolbarDockEdge) -> Unit = {},
    onDrag: ((Offset) -> Unit)? = null
) {
    var showColorMenu by remember { mutableStateOf(false) }
    var showWidthMenu by remember { mutableStateOf(false) }
    var currentWidthSlider by remember(state.baseWidth) { mutableFloatStateOf(state.baseWidth) }

    val isVertical = state.dockEdge == ToolbarDockEdge.LEFT || state.dockEdge == ToolbarDockEdge.RIGHT

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        if (isVertical) {
            Column(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterVertically,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dedicated drag handle box
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .then(
                            if (onDrag != null) {
                                Modifier.pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount)
                                    }
                                }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag toolbar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Tool buttons
                ToolButton(
                    selected = state.selectedTool == ToolType.PEN,
                    onClick = { onToolSelected(ToolType.PEN) },
                    icon = Icons.Default.Create,
                    description = "Pen"
                )

                ToolButton(
                    selected = state.selectedTool == ToolType.HIGHLIGHTER,
                    onClick = { onToolSelected(ToolType.HIGHLIGHTER) },
                    icon = Icons.Default.BorderColor,
                    description = "Highlighter"
                )

                ToolButton(
                    selected = state.selectedTool == ToolType.STROKE_ERASER,
                    onClick = { onToolSelected(ToolType.STROKE_ERASER) },
                    icon = Icons.Default.Delete,
                    description = "Eraser"
                )

                ToolButton(
                    selected = state.selectedTool == ToolType.LASSO,
                    onClick = { onToolSelected(ToolType.LASSO) },
                    icon = Icons.Default.CropFree,
                    description = "Lasso Selection"
                )

                HorizontalDivider(
                    modifier = Modifier.width(32.dp)
                )

                // Undo / Redo
                IconButton(onClick = onUndo, enabled = state.canUndo) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo")
                }

                IconButton(onClick = onRedo, enabled = state.canRedo) {
                    Icon(Icons.Default.Redo, contentDescription = "Redo")
                }

                HorizontalDivider(
                    modifier = Modifier.width(32.dp)
                )

                // Color swatch trigger
                ColorSwatchButton(
                    currentColor = state.selectedColor,
                    onClick = { showColorMenu = true }
                )

                // Stroke width button
                IconButton(onClick = { showWidthMenu = true }) {
                    Icon(Icons.Default.LineWeight, contentDescription = "Stroke width")
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dedicated drag handle box
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .then(
                            if (onDrag != null) {
                                Modifier.pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount)
                                    }
                                }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag toolbar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Tool buttons
                ToolButton(
                    selected = state.selectedTool == ToolType.PEN,
                    onClick = { onToolSelected(ToolType.PEN) },
                    icon = Icons.Default.Create,
                    description = "Pen"
                )

                ToolButton(
                    selected = state.selectedTool == ToolType.HIGHLIGHTER,
                    onClick = { onToolSelected(ToolType.HIGHLIGHTER) },
                    icon = Icons.Default.BorderColor,
                    description = "Highlighter"
                )

                ToolButton(
                    selected = state.selectedTool == ToolType.STROKE_ERASER,
                    onClick = { onToolSelected(ToolType.STROKE_ERASER) },
                    icon = Icons.Default.Delete,
                    description = "Eraser"
                )

                ToolButton(
                    selected = state.selectedTool == ToolType.LASSO,
                    onClick = { onToolSelected(ToolType.LASSO) },
                    icon = Icons.Default.CropFree,
                    description = "Lasso Selection"
                )

                VerticalDivider(
                    modifier = Modifier.height(32.dp)
                )

                // Undo / Redo
                IconButton(onClick = onUndo, enabled = state.canUndo) {
                    Icon(Icons.Default.Undo, contentDescription = "Undo")
                }

                IconButton(onClick = onRedo, enabled = state.canRedo) {
                    Icon(Icons.Default.Redo, contentDescription = "Redo")
                }

                VerticalDivider(
                    modifier = Modifier.height(32.dp)
                )

                // Color swatch trigger
                Box {
                    ColorSwatchButton(
                        currentColor = state.selectedColor,
                        onClick = { showColorMenu = true }
                    )

                    DropdownMenu(
                        expanded = showColorMenu,
                        onDismissRequest = { showColorMenu = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PresetInkColors.forEach { colorInt ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(colorInt))
                                        .then(
                                            if (state.selectedColor == colorInt) {
                                                Modifier.border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape
                                                )
                                            } else Modifier
                                        )
                                        .clickable {
                                            onColorSelected(colorInt)
                                            showColorMenu = false
                                        }
                                )
                            }
                        }
                    }
                }

                // Stroke width trigger
                Box {
                    IconButton(onClick = { showWidthMenu = true }) {
                        Icon(Icons.Default.LineWeight, contentDescription = "Stroke width")
                    }

                    DropdownMenu(
                        expanded = showWidthMenu,
                        onDismissRequest = { showWidthMenu = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .width(200.dp)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Width: ${currentWidthSlider.roundToInt()} pt",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Slider(
                                value = currentWidthSlider,
                                onValueChange = {
                                    currentWidthSlider = it
                                    onBaseWidthChanged(it)
                                },
                                valueRange = 1.0f..30.0f
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String
) {
    IconButton(
        onClick = onClick,
        colors = if (selected) {
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        } else {
            IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        }
    ) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
private fun ColorSwatchButton(
    currentColor: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(currentColor))
            .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick)
    )
}
