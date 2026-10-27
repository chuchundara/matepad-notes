package com.matepad.notes.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matepad.notes.data.local.dao.NoteDao
import com.matepad.notes.data.local.entity.FolderEntity
import com.matepad.notes.data.local.entity.NoteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * UI State for the Tablet Note Library screen.
 *
 * @param notes Filtered list of notes to display in the grid
 * @param folders List of user folders
 * @param selectedFolderId ID of the currently selected folder (null means "All Notes")
 * @param searchQuery Current search query for title filtering
 * @param isLoading Whether data is currently loading
 */
data class LibraryUiState(
    val notes: List<NoteEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val selectedFolderId: String? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

/**
 * ViewModel managing note library state, folder navigation, search filtering,
 * and CRUD operations for notes and folders.
 */
open class LibraryViewModel(
    private val noteDao: NoteDao? = null,
    coroutineScope: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = coroutineScope
        ?: runCatching { viewModelScope }.getOrElse { CoroutineScope(Dispatchers.Unconfined) }

    private val _manualNotes = MutableStateFlow<List<NoteEntity>>(emptyList())
    private val _manualFolders = MutableStateFlow<List<FolderEntity>>(emptyList())
    private val _selectedFolderId = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)

    private val allNotesFlow: Flow<List<NoteEntity>> = noteDao?.getAllNotes() ?: _manualNotes
    private val allFoldersFlow: Flow<List<FolderEntity>> = noteDao?.getAllFolders() ?: _manualFolders

    val uiState: StateFlow<LibraryUiState> = combine(
        allNotesFlow,
        allFoldersFlow,
        _selectedFolderId,
        _searchQuery,
        _isLoading
    ) { allNotes, folders, selectedFolderId, searchQuery, isLoading ->
        val filteredByFolder = if (selectedFolderId == null) {
            allNotes
        } else {
            allNotes.filter { it.folderId == selectedFolderId }
        }

        val filteredByQuery = if (searchQuery.isBlank()) {
            filteredByFolder
        } else {
            val trimmedQuery = searchQuery.trim()
            filteredByFolder.filter { note ->
                note.title.contains(trimmedQuery, ignoreCase = true)
            }
        }

        LibraryUiState(
            notes = filteredByQuery,
            folders = folders,
            selectedFolderId = selectedFolderId,
            searchQuery = searchQuery,
            isLoading = isLoading
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = LibraryUiState(isLoading = noteDao != null)
    )

    /**
     * Select a folder to filter notes by, or pass null for "All Notes".
     */
    fun selectFolder(folderId: String?) {
        _selectedFolderId.value = folderId
    }

    /**
     * Update the real-time search query.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Create a new note and return its unique ID after persistence completes.
     */
    suspend fun createNote(
        title: String = "Untitled Note",
        folderId: String? = _selectedFolderId.value,
        gridStyle: String = "DOTS"
    ): String {
        val noteId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val finalTitle = title.trim().ifEmpty { "Untitled Note" }
        val note = NoteEntity(
            id = noteId,
            title = finalTitle,
            folderId = folderId,
            createdAt = now,
            updatedAt = now,
            gridStyle = gridStyle
        )

        if (noteDao != null) {
            noteDao.insertOrUpdateNote(note)
        } else {
            _manualNotes.update { current -> listOf(note) + current }
        }

        return noteId
    }

    /**
     * Asynchronously create a new note and invoke callback upon completion.
     */
    fun createNoteAsync(
        title: String = "Untitled Note",
        folderId: String? = _selectedFolderId.value,
        gridStyle: String = "DOTS",
        onCreated: (String) -> Unit = {}
    ) {
        scope.launch {
            val noteId = createNote(title, folderId, gridStyle)
            onCreated(noteId)
        }
    }

    /**
     * Delete a note by ID.
     */
    fun deleteNote(noteId: String) {
        if (noteDao != null) {
            scope.launch {
                noteDao.deleteNoteById(noteId)
            }
        } else {
            _manualNotes.update { current -> current.filterNot { it.id == noteId } }
        }
    }

    /**
     * Delete a note entity.
     */
    fun deleteNote(note: NoteEntity) {
        deleteNote(note.id)
    }

    /**
     * Create a new folder and return its unique ID.
     */
    fun createFolder(name: String, colorHex: String = "#3B82F6"): String {
        val folderId = UUID.randomUUID().toString()
        val currentFolders = uiState.value.folders
        val nextOrder = (currentFolders.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val finalName = name.trim().ifEmpty { "New Folder" }
        val folder = FolderEntity(
            id = folderId,
            name = finalName,
            colorHex = colorHex,
            orderIndex = nextOrder
        )

        if (noteDao != null) {
            scope.launch {
                noteDao.insertOrUpdateFolder(folder)
            }
        } else {
            _manualFolders.update { current -> current + folder }
        }

        return folderId
    }

    /**
     * Delete a folder by ID. Resets selectedFolderId to null if the active folder is deleted.
     */
    fun deleteFolder(folderId: String) {
        if (_selectedFolderId.value == folderId) {
            _selectedFolderId.value = null
        }

        if (noteDao != null) {
            scope.launch {
                noteDao.deleteFolderById(folderId)
            }
        } else {
            _manualFolders.update { current -> current.filterNot { it.id == folderId } }
        }
    }

    /**
     * Rename an existing note.
     */
    fun renameNote(noteId: String, newTitle: String) {
        val trimmed = newTitle.trim().ifEmpty { "Untitled Note" }
        if (noteDao != null) {
            scope.launch {
                val existing = noteDao.getNoteById(noteId)
                if (existing != null) {
                    noteDao.updateNote(existing.copy(title = trimmed, updatedAt = System.currentTimeMillis()))
                }
            }
        } else {
            _manualNotes.update { current ->
                current.map {
                    if (it.id == noteId) it.copy(title = trimmed, updatedAt = System.currentTimeMillis()) else it
                }
            }
        }
    }

    /**
     * Move a note to a different folder (or null for root).
     */
    fun moveNoteToFolder(noteId: String, targetFolderId: String?) {
        if (noteDao != null) {
            scope.launch {
                val existing = noteDao.getNoteById(noteId)
                if (existing != null) {
                    noteDao.updateNote(existing.copy(folderId = targetFolderId, updatedAt = System.currentTimeMillis()))
                }
            }
        } else {
            _manualNotes.update { current ->
                current.map {
                    if (it.id == noteId) it.copy(folderId = targetFolderId, updatedAt = System.currentTimeMillis()) else it
                }
            }
        }
    }
}
