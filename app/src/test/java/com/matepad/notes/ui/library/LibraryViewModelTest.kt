package com.matepad.notes.ui.library

import com.matepad.notes.data.local.dao.NoteDao
import com.matepad.notes.data.local.entity.FolderEntity
import com.matepad.notes.data.local.entity.NoteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-memory test double for [NoteDao].
 */
class FakeNoteDao : NoteDao {
    val notesFlow = MutableStateFlow<List<NoteEntity>>(emptyList())
    val foldersFlow = MutableStateFlow<List<FolderEntity>>(emptyList())

    override fun getAllNotes(): Flow<List<NoteEntity>> = notesFlow

    override fun getNotesByFolder(folderId: String): Flow<List<NoteEntity>> =
        notesFlow.map { list -> list.filter { it.folderId == folderId } }

    override suspend fun getNoteById(noteId: String): NoteEntity? =
        notesFlow.value.find { it.id == noteId }

    override suspend fun insertOrUpdateNote(note: NoteEntity) {
        notesFlow.update { current ->
            val index = current.indexOfFirst { it.id == note.id }
            if (index >= 0) {
                current.toMutableList().apply { set(index, note) }
            } else {
                listOf(note) + current
            }
        }
    }

    override suspend fun updateNote(note: NoteEntity) {
        insertOrUpdateNote(note)
    }

    override suspend fun deleteNote(note: NoteEntity) {
        deleteNoteById(note.id)
    }

    override suspend fun deleteNoteById(noteId: String) {
        notesFlow.update { current -> current.filterNot { it.id == noteId } }
    }

    override fun getAllFolders(): Flow<List<FolderEntity>> = foldersFlow

    override suspend fun getFolderById(folderId: String): FolderEntity? =
        foldersFlow.value.find { it.id == folderId }

    override suspend fun insertOrUpdateFolder(folder: FolderEntity) {
        foldersFlow.update { current ->
            val index = current.indexOfFirst { it.id == folder.id }
            if (index >= 0) {
                current.toMutableList().apply { set(index, folder) }
            } else {
                current + folder
            }
        }
    }

    override suspend fun deleteFolder(folder: FolderEntity) {
        deleteFolderById(folder.id)
    }

    override suspend fun deleteFolderById(folderId: String) {
        foldersFlow.update { current -> current.filterNot { it.id == folderId } }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @Test
    fun testInitialLibraryState() {
        val state = LibraryUiState(
            notes = listOf(NoteEntity(id = "1", title = "Welcome Note")),
            selectedFolderId = null
        )
        assertEquals(1, state.notes.size)
        assertEquals("Welcome Note", state.notes[0].title)
    }

    @Test
    fun testFolderFiltering() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher)
        val fakeDao = FakeNoteDao()

        val note1 = NoteEntity(id = "n1", title = "Math Notes", folderId = "f1")
        val note2 = NoteEntity(id = "n2", title = "Physics Notes", folderId = "f2")
        val note3 = NoteEntity(id = "n3", title = "Quick Thought", folderId = null)
        fakeDao.notesFlow.value = listOf(note1, note2, note3)

        val folder1 = FolderEntity(id = "f1", name = "Math", colorHex = "#3B82F6", orderIndex = 0)
        val folder2 = FolderEntity(id = "f2", name = "Physics", colorHex = "#10B981", orderIndex = 1)
        fakeDao.foldersFlow.value = listOf(folder1, folder2)

        val viewModel = LibraryViewModel(noteDao = fakeDao, coroutineScope = testScope)

        // Default: All Notes (null folder)
        assertEquals(3, viewModel.uiState.value.notes.size)
        assertNull(viewModel.uiState.value.selectedFolderId)

        // Filter by Folder 1
        viewModel.selectFolder("f1")
        assertEquals(1, viewModel.uiState.value.notes.size)
        assertEquals("Math Notes", viewModel.uiState.value.notes.first().title)
        assertEquals("f1", viewModel.uiState.value.selectedFolderId)

        // Filter by Folder 2
        viewModel.selectFolder("f2")
        assertEquals(1, viewModel.uiState.value.notes.size)
        assertEquals("Physics Notes", viewModel.uiState.value.notes.first().title)

        // Reset back to All Notes
        viewModel.selectFolder(null)
        assertEquals(3, viewModel.uiState.value.notes.size)
        assertNull(viewModel.uiState.value.selectedFolderId)
    }

    @Test
    fun testSearchQueryFiltering() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher)
        val fakeDao = FakeNoteDao()

        val note1 = NoteEntity(id = "1", title = "Calculus Derivations")
        val note2 = NoteEntity(id = "2", title = "Linear Algebra")
        val note3 = NoteEntity(id = "3", title = "Differential Calculus")
        fakeDao.notesFlow.value = listOf(note1, note2, note3)

        val viewModel = LibraryViewModel(noteDao = fakeDao, coroutineScope = testScope)

        // Search for "calculus" (case-insensitive)
        viewModel.setSearchQuery("calculus")
        val filtered = viewModel.uiState.value.notes
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == "1" })
        assertTrue(filtered.any { it.id == "3" })
        assertFalse(filtered.any { it.id == "2" })

        // Search with whitespace trimming
        viewModel.setSearchQuery("   Algebra   ")
        val algebraFiltered = viewModel.uiState.value.notes
        assertEquals(1, algebraFiltered.size)
        assertEquals("Linear Algebra", algebraFiltered.first().title)

        // Clear search
        viewModel.setSearchQuery("")
        assertEquals(3, viewModel.uiState.value.notes.size)
    }

    @Test
    fun testCombinedFolderAndSearchFilter() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher)
        val fakeDao = FakeNoteDao()

        val note1 = NoteEntity(id = "1", title = "Lecture 1", folderId = "courseA")
        val note2 = NoteEntity(id = "2", title = "Lecture 2", folderId = "courseA")
        val note3 = NoteEntity(id = "3", title = "Lecture 1", folderId = "courseB")
        fakeDao.notesFlow.value = listOf(note1, note2, note3)

        val viewModel = LibraryViewModel(noteDao = fakeDao, coroutineScope = testScope)

        // Filter by courseA and "2"
        viewModel.selectFolder("courseA")
        viewModel.setSearchQuery("2")

        val results = viewModel.uiState.value.notes
        assertEquals(1, results.size)
        assertEquals("2", results.first().id)
        assertEquals("Lecture 2", results.first().title)
    }

    @Test
    fun testCreateNote() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher)
        val fakeDao = FakeNoteDao()

        val viewModel = LibraryViewModel(noteDao = fakeDao, coroutineScope = testScope)
        val noteId = viewModel.createNote(title = "New Drawing", folderId = "f1", gridStyle = "ISOMETRIC")

        assertNotNull(noteId)
        assertTrue(noteId.isNotBlank())

        val created = fakeDao.getNoteById(noteId)
        assertNotNull(created)
        assertEquals("New Drawing", created!!.title)
        assertEquals("f1", created.folderId)
        assertEquals("ISOMETRIC", created.gridStyle)
        assertTrue(created.createdAt > 0)
    }

    @Test
    fun testCreateNoteWithDefaultTitleOnBlank() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher)
        val fakeDao = FakeNoteDao()

        val viewModel = LibraryViewModel(noteDao = fakeDao, coroutineScope = testScope)
        val noteId = viewModel.createNote(title = "   ")

        val created = fakeDao.getNoteById(noteId)
        assertNotNull(created)
        assertEquals("Untitled Note", created!!.title)
    }

    @Test
    fun testDeleteNote() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher)
        val fakeDao = FakeNoteDao()

        val note = NoteEntity(id = "del-1", title = "To be deleted")
        fakeDao.notesFlow.value = listOf(note)

        val viewModel = LibraryViewModel(noteDao = fakeDao, coroutineScope = testScope)
        assertEquals(1, viewModel.uiState.value.notes.size)

        viewModel.deleteNote("del-1")
        assertEquals(0, viewModel.uiState.value.notes.size)
        assertNull(fakeDao.getNoteById("del-1"))
    }

    @Test
    fun testCreateFolder() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher)
        val fakeDao = FakeNoteDao()

        val viewModel = LibraryViewModel(noteDao = fakeDao, coroutineScope = testScope)
        val folderId = viewModel.createFolder(name = "Personal", colorHex = "#10B981")

        assertNotNull(folderId)
        val created = fakeDao.getFolderById(folderId)
        assertNotNull(created)
        assertEquals("Personal", created!!.name)
        assertEquals("#10B981", created.colorHex)
    }

    @Test
    fun testDeleteFolderResetsSelectedFolder() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher)
        val fakeDao = FakeNoteDao()

        val folder = FolderEntity(id = "f-temp", name = "Temp", colorHex = "#EF4444", orderIndex = 0)
        fakeDao.foldersFlow.value = listOf(folder)

        val viewModel = LibraryViewModel(noteDao = fakeDao, coroutineScope = testScope)
        viewModel.selectFolder("f-temp")
        assertEquals("f-temp", viewModel.uiState.value.selectedFolderId)

        viewModel.deleteFolder("f-temp")
        assertNull(viewModel.uiState.value.selectedFolderId)
        assertNull(fakeDao.getFolderById("f-temp"))
    }

    @Test
    fun testRenameAndMoveNote() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testScope = CoroutineScope(testDispatcher)
        val fakeDao = FakeNoteDao()

        val note = NoteEntity(id = "move-1", title = "Original Title", folderId = null)
        fakeDao.notesFlow.value = listOf(note)

        val viewModel = LibraryViewModel(noteDao = fakeDao, coroutineScope = testScope)
        viewModel.renameNote("move-1", "Updated Title")

        val renamed = fakeDao.getNoteById("move-1")
        assertNotNull(renamed)
        assertEquals("Updated Title", renamed!!.title)

        viewModel.moveNoteToFolder("move-1", "folder-target")
        val moved = fakeDao.getNoteById("move-1")
        assertNotNull(moved)
        assertEquals("folder-target", moved!!.folderId)
    }

    @Test
    fun testStandaloneInMemoryModeWithoutDao() {
        // Test in-memory behavior when noteDao is null
        val viewModel = LibraryViewModel(noteDao = null)

        val noteId = viewModel.createNote(title = "Draft Note", gridStyle = "LINES")
        assertEquals(1, viewModel.uiState.value.notes.size)
        assertEquals("Draft Note", viewModel.uiState.value.notes.first().title)

        viewModel.setSearchQuery("NonExistent")
        assertEquals(0, viewModel.uiState.value.notes.size)

        viewModel.setSearchQuery("")
        assertEquals(1, viewModel.uiState.value.notes.size)

        viewModel.deleteNote(noteId)
        assertEquals(0, viewModel.uiState.value.notes.size)
    }
}
