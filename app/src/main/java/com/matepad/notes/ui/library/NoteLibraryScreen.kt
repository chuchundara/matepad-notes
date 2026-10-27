package com.matepad.notes.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matepad.notes.data.local.entity.FolderEntity
import com.matepad.notes.data.local.entity.NoteEntity
import kotlinx.coroutines.launch

/**
 * Stateful Tablet Note Library Screen connected to [LibraryViewModel].
 */
@Composable
fun NoteLibraryScreen(
    viewModel: LibraryViewModel,
    onNoteClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    NoteLibraryScreen(
        state = state,
        onNoteClick = onNoteClick,
        onCreateNote = { title, folderId, gridStyle ->
            coroutineScope.launch {
                val noteId = viewModel.createNote(title, folderId, gridStyle)
                onNoteClick(noteId)
            }
        },
        onDeleteNote = { noteId ->
            viewModel.deleteNote(noteId)
        },
        onSelectFolder = { folderId ->
            viewModel.selectFolder(folderId)
        },
        onCreateFolder = { name, colorHex ->
            viewModel.createFolder(name, colorHex)
        },
        onDeleteFolder = { folderId ->
            viewModel.deleteFolder(folderId)
        },
        onSearchQueryChange = { query ->
            viewModel.setSearchQuery(query)
        },
        modifier = modifier
    )
}

/**
 * Stateless Tablet Note Library Screen.
 * Master-Detail tablet UI with side navigation rail for folders,
 * real-time search, adaptive grid of notes, and note creation dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteLibraryScreen(
    state: LibraryUiState,
    onNoteClick: (String) -> Unit,
    onCreateNote: (title: String, folderId: String?, gridStyle: String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: (name: String, colorHex: String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateNoteDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Left Side Panel: Tablet Folder Navigation & Search (280dp)
        FolderNavigationSidePanel(
            folders = state.folders,
            selectedFolderId = state.selectedFolderId,
            searchQuery = state.searchQuery,
            totalNotesCount = state.notes.size,
            onSelectFolder = onSelectFolder,
            onSearchQueryChange = onSearchQueryChange,
            onOpenCreateFolderDialog = { showCreateFolderDialog = true },
            onDeleteFolder = onDeleteFolder,
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
        )

        VerticalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // Right Main Area: Note Catalog Grid and Top Bar
        Scaffold(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            topBar = {
                val currentFolderTitle = when {
                    state.selectedFolderId == null -> "All Notes"
                    else -> state.folders.find { it.id == state.selectedFolderId }?.name ?: "Folder"
                }

                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentFolderTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${state.notes.size} ${if (state.notes.size == 1) "note" else "notes"}" +
                                        if (state.searchQuery.isNotBlank()) " matching \"${state.searchQuery}\"" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    text = { Text("New Note") },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Create note") },
                    onClick = { showCreateNoteDialog = true }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (state.notes.isEmpty()) {
                    EmptyNotesPlaceholder(
                        searchQuery = state.searchQuery,
                        onCreateNote = { showCreateNoteDialog = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 220.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = state.notes, key = { it.id }) { note ->
                            NoteCardItem(
                                note = note,
                                onClick = { onNoteClick(note.id) },
                                onDelete = { onDeleteNote(note.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showCreateNoteDialog) {
        CreateNoteDialog(
            currentFolderId = state.selectedFolderId,
            onDismiss = { showCreateNoteDialog = false },
            onConfirm = { title, gridStyle ->
                showCreateNoteDialog = false
                onCreateNote(title, state.selectedFolderId, gridStyle)
            }
        )
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name, colorHex ->
                showCreateFolderDialog = false
                onCreateFolder(name, colorHex)
            }
        )
    }
}

/**
 * Tablet Side Navigation Panel containing App header, search bar, and folder list.
 */
@Composable
private fun FolderNavigationSidePanel(
    folders: List<FolderEntity>,
    selectedFolderId: String?,
    searchQuery: String,
    totalNotesCount: Int,
    onSelectFolder: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenCreateFolderDialog: () -> Unit,
    onDeleteFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // App Branding Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "MatePad Notes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Real-time Note Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search notes...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Folders Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FOLDERS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = onOpenCreateFolderDialog,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = "New folder",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Folder List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // "All Notes" Item
                item {
                    FolderListItem(
                        title = "All Notes",
                        icon = Icons.Default.Description,
                        iconTint = MaterialTheme.colorScheme.primary,
                        isSelected = selectedFolderId == null,
                        count = totalNotesCount,
                        onClick = { onSelectFolder(null) }
                    )
                }

                // Custom Folders
                items(items = folders, key = { it.id }) { folder ->
                    val folderColor = parseHexColor(folder.colorHex)
                    FolderListItem(
                        title = folder.name,
                        icon = Icons.Default.Folder,
                        iconTint = folderColor,
                        isSelected = selectedFolderId == folder.id,
                        onClick = { onSelectFolder(folder.id) },
                        onDelete = { onDeleteFolder(folder.id) }
                    )
                }
            }
        }
    }
}

/**
 * Individual row item inside the side folder navigation rail.
 */
@Composable
private fun FolderListItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isSelected: Boolean,
    count: Int? = null,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (count != null) {
                Badge(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "$count",
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Friendly empty state displayed when no notes are found in the active folder or search query.
 */
@Composable
private fun EmptyNotesPlaceholder(
    searchQuery: String,
    onCreateNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (searchQuery.isNotBlank()) "No notes found" else "No notes in this folder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (searchQuery.isNotBlank()) {
                "No notes match \"$searchQuery\". Try another keyword."
            } else {
                "Start an infinite sketch by creating your first note."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (searchQuery.isBlank()) {
            Spacer(modifier = Modifier.height(20.dp))

            ExtendedFloatingActionButton(
                onClick = onCreateNote,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Create Note") }
            )
        }
    }
}

/**
 * Dialog for creating a new note with custom title and grid style selector.
 */
@Composable
private fun CreateNoteDialog(
    currentFolderId: String?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, gridStyle: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedGridStyle by remember { mutableStateOf("DOTS") }
    val gridStyles = listOf("DOTS", "LINES", "ISOMETRIC", "BLANK")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Note") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Note Title") },
                    placeholder = { Text("Untitled Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Background Grid Style",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gridStyles.forEach { style ->
                        FilterChip(
                            selected = selectedGridStyle == style,
                            onClick = { selectedGridStyle = style },
                            label = { Text(style.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.ifBlank { "Untitled Note" }, selectedGridStyle) }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for creating a new folder with custom name and color palette picker.
 */
@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    val colorPresets = listOf(
        "#3B82F6", // Blue
        "#10B981", // Emerald
        "#8B5CF6", // Violet
        "#F59E0B", // Amber
        "#EF4444", // Crimson
        "#6366F1"  // Indigo
    )
    var selectedColor by remember { mutableStateOf(colorPresets.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Folder") },
        text = {
            Column {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    placeholder = { Text("e.g. Work, Sketches, Math") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Folder Color",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    colorPresets.forEach { hex ->
                        val color = parseHexColor(hex)
                        val isSelected = selectedColor == hex
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = hex }
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else Modifier
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(folderName.ifBlank { "New Folder" }, selectedColor) }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Safely parse a hex color string into a Compose [Color].
 */
private fun parseHexColor(hex: String, fallback: Color = Color(0xFF3B82F6)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}
