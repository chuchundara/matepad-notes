package com.matepad.notes.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matepad.notes.ui.canvas.render.InfiniteCanvasView

/**
 * Top navigation and document status bar for the infinite canvas.
 *
 * Provides navigation back to the notes library, in-place title editing,
 * real-time debounced autosave indicators, zoom percentage badge with
 * one-tap reset to 100%, background grid style selection, and export actions (PDF / PNG).
 */
@Composable
fun CanvasTopBar(
    state: CanvasUiState,
    onNavigateBack: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onResetZoom: () -> Unit,
    onGridStyleChanged: (InfiniteCanvasView.GridStyle) -> Unit,
    onExportPdf: () -> Unit,
    onExportPng: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditingTitle by remember { mutableStateOf(false) }
    var titleInput by remember(state.noteTitle) { mutableStateOf(state.noteTitle) }
    var isGridMenuExpanded by remember { mutableStateOf(false) }
    var isExportMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left region: Back button & Note Title & Autosave status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Navigate back to library"
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isEditingTitle) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        BasicTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    val trimmed = titleInput.trim()
                                    if (trimmed.isNotEmpty()) {
                                        onTitleChanged(trimmed)
                                    }
                                    isEditingTitle = false
                                }
                            ),
                            modifier = Modifier.width(220.dp)
                        )

                        IconButton(
                            onClick = {
                                val trimmed = titleInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    onTitleChanged(trimmed)
                                }
                                isEditingTitle = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Confirm rename",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isEditingTitle = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = state.noteTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit title",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Autosave status badge
                AutosaveIndicator(status = state.autosaveStatus)
            }

            // Right region: Zoom % badge, Grid Style selector, Export menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Zoom Percentage Badge (Click to reset to 100%)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onResetZoom)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${state.zoomPercentage}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset zoom to 100%",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Grid Style Selector
                Box {
                    IconButton(onClick = { isGridMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Background grid style"
                        )
                    }

                    DropdownMenu(
                        expanded = isGridMenuExpanded,
                        onDismissRequest = { isGridMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Blank") },
                            trailingIcon = {
                                if (state.gridStyle == InfiniteCanvasView.GridStyle.NONE) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                onGridStyleChanged(InfiniteCanvasView.GridStyle.NONE)
                                isGridMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Dots") },
                            trailingIcon = {
                                if (state.gridStyle == InfiniteCanvasView.GridStyle.DOTS) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                onGridStyleChanged(InfiniteCanvasView.GridStyle.DOTS)
                                isGridMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Lines") },
                            trailingIcon = {
                                if (state.gridStyle == InfiniteCanvasView.GridStyle.LINES) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                onGridStyleChanged(InfiniteCanvasView.GridStyle.LINES)
                                isGridMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Grid") },
                            trailingIcon = {
                                if (state.gridStyle == InfiniteCanvasView.GridStyle.SQUARES) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                            onClick = {
                                onGridStyleChanged(InfiniteCanvasView.GridStyle.SQUARES)
                                isGridMenuExpanded = false
                            }
                        )
                    }
                }

                // Export Menu (PDF / PNG)
                Box {
                    IconButton(onClick = { isExportMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export document"
                        )
                    }

                    DropdownMenu(
                        expanded = isExportMenuExpanded,
                        onDismissRequest = { isExportMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export to PDF") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                isExportMenuExpanded = false
                                onExportPdf()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export to PNG") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                isExportMenuExpanded = false
                                onExportPng()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutosaveIndicator(status: AutosaveStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        when (status) {
            AutosaveStatus.SAVING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Saving...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AutosaveStatus.SAVED -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Saved",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Saved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AutosaveStatus.ERROR -> {
                Text(
                    text = "Save error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            AutosaveStatus.IDLE -> {
                // Subtle quiet state
            }
        }
    }
}
