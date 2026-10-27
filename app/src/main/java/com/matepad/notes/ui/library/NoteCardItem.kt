package com.matepad.notes.ui.library

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matepad.notes.data.local.entity.NoteEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tablet note library card component.
 * Displays preview thumbnail, note title, last updated timestamp, grid style badge,
 * and contextual actions.
 */
@Composable
fun NoteCardItem(
    note: NoteEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Note preview thumbnail area (takes top 65% of the card)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
            ) {
                NoteThumbnail(
                    previewPath = note.previewPath,
                    gridStyle = note.gridStyle
                )
            }

            // Note metadata area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.9f)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (onDelete != null) {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Note options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete Note", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(note.updatedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "Grid: ${note.gridStyle}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thumbnail component that displays the generated note bitmap if present,
 * or draws an adaptive grid illustration pattern matching the note's grid style.
 */
@Composable
private fun NoteThumbnail(
    previewPath: String?,
    gridStyle: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(previewPath) {
        if (previewPath != null) {
            val file = File(previewPath)
            if (file.exists() && file.isFile && file.length() > 0) {
                BitmapFactory.decodeFile(previewPath)
            } else null
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Note preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Draw grid pattern placeholder matching note.gridStyle
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridColor = Color(0xFFD1D5DB) // Light gray
                val normalizedStyle = gridStyle.uppercase()

                when (normalizedStyle) {
                    "DOTS" -> {
                        val spacing = 16.dp.toPx()
                        var x = spacing
                        while (x < size.width) {
                            var y = spacing
                            while (y < size.height) {
                                drawCircle(
                                    color = gridColor,
                                    radius = 1.5.dp.toPx(),
                                    center = Offset(x, y)
                                )
                                y += spacing
                            }
                            x += spacing
                        }
                    }
                    "LINES" -> {
                        val spacing = 20.dp.toPx()
                        var y = spacing
                        while (y < size.height) {
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                            y += spacing
                        }
                    }
                    "ISOMETRIC" -> {
                        val spacing = 24.dp.toPx()
                        var x = 0f
                        while (x < size.width + size.height) {
                            drawLine(
                                color = gridColor.copy(alpha = 0.6f),
                                start = Offset(x, 0f),
                                end = Offset(x - size.height, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = gridColor.copy(alpha = 0.6f),
                                start = Offset(x - size.height, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                            x += spacing
                        }
                    }
                    else -> {
                        // "BLANK" - clean blank canvas
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = Color(0xFF9CA3AF).copy(alpha = 0.35f),
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

/**
 * Format timestamp into human-readable date.
 */
private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(date)
}
