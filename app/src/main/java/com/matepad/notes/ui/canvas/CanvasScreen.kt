package com.matepad.notes.ui.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.matepad.notes.data.export.ImageExporter
import com.matepad.notes.data.export.PdfExporter
import com.matepad.notes.data.local.dao.NoteDao
import com.matepad.notes.data.local.entity.NoteEntity
import com.matepad.notes.data.serializer.StrokeBinarySerializer
import com.matepad.notes.domain.command.CanvasCommand
import com.matepad.notes.domain.command.CommandHistory
import com.matepad.notes.domain.geometry.ViewportTransform
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.ToolType
import com.matepad.notes.ui.canvas.render.InfiniteCanvasView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Command for adding strokes to [InfiniteCanvasView], supporting Undo and Redo while
 * properly maintaining spatial index synchronization and redraw cycles.
 */
internal class CanvasAddStrokeCommand(
    private val stroke: Stroke,
    private val canvasView: InfiniteCanvasView
) : CanvasCommand {
    private var isFirstExecution = true

    override fun execute() {
        if (!isFirstExecution) {
            canvasView.addStroke(stroke)
        }
        isFirstExecution = false
    }

    override fun undo() {
        canvasView.removeStroke(stroke)
    }
}

/**
 * Command for erasing strokes from [InfiniteCanvasView], supporting Undo and Redo.
 */
internal class CanvasDeleteStrokesCommand(
    private val deletedStrokes: List<Stroke>,
    private val canvasView: InfiniteCanvasView
) : CanvasCommand {
    private var isFirstExecution = true

    override fun execute() {
        if (!isFirstExecution) {
            for (stroke in deletedStrokes) {
                canvasView.removeStroke(stroke)
            }
        }
        isFirstExecution = false
    }

    override fun undo() {
        for (stroke in deletedStrokes) {
            canvasView.addStroke(stroke)
        }
    }
}

/**
 * Fullscreen note editor canvas screen for Huawei MatePad 11.5.
 *
 * Integrates:
 * 1. [InfiniteCanvasView] via [AndroidView] for 120 FPS multi-buffered rendering and
 *    low-latency front-buffer stylus inking.
 * 2. [CanvasTopBar] with title rename, debounced autosave indicators, 100% zoom reset,
 *    and vector PDF / PNG export triggers.
 * 3. [FloatingToolbar] with edge docking (Top, Bottom, Left, Right, Floating) for
 *    left- or right-handed stylus ergonomics.
 * 4. [CommandHistory] integration for robust Undo/Redo tracking.
 * 5. Automatic 750ms debounced persistence and guaranteed flush on `onPause()` / back navigation.
 * 6. Non-blocking asynchronous I/O and bitmap rasterization dispatched on [Dispatchers.IO].
 */
@Composable
fun CanvasScreen(
    noteId: String? = null,
    onNavigateBack: () -> Unit = {},
    noteDao: NoteDao? = null,
    notesDirectory: File? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val baseDir = remember { notesDirectory ?: context.filesDir }

    val commandHistory = remember { CommandHistory() }
    var canvasViewRef by remember { mutableStateOf<InfiniteCanvasView?>(null) }
    var loadedNoteId by remember { mutableStateOf<String?>(null) }

    var uiState by remember {
        mutableStateOf(
            CanvasUiState(
                noteId = noteId ?: UUID.randomUUID().toString(),
                noteTitle = "Untitled Note"
            )
        )
    }

    // Floating toolbar drag position
    var toolbarFloatingOffset by remember { mutableStateOf(Offset(100f, 600f)) }

    var autosaveJob by remember { mutableStateOf<Job?>(null) }

    // Immediate autosave flush routine (executes file I/O and DB operations on Dispatchers.IO)
    suspend fun flushAutosaveNow() {
        autosaveJob?.cancel()
        autosaveJob = null

        val currentStrokes = canvasViewRef?.strokes?.toList() ?: return
        val currentTransform = canvasViewRef?.viewportTransform ?: ViewportTransform()
        val currentGridStyle = canvasViewRef?.gridStyle ?: InfiniteCanvasView.GridStyle.DOTS
        val activeNoteId = uiState.noteId.ifEmpty { UUID.randomUUID().toString() }
        val currentTitle = uiState.noteTitle

        withContext(Dispatchers.IO) {
            try {
                // 1. Persist binary .note file
                val noteFile = File(baseDir, "$activeNoteId.note")
                noteFile.parentFile?.mkdirs()
                val bytes = StrokeBinarySerializer.serialize(currentStrokes)
                noteFile.writeBytes(bytes)

                // 2. Generate and persist thumbnail preview in WebP format
                val previewFile = File(baseDir, "$activeNoteId-preview.webp")
                if (currentStrokes.isNotEmpty()) {
                    ImageExporter.exportPreviewToFile(currentStrokes, previewFile)
                }

                // 3. Update Room database record
                if (noteDao != null) {
                    val existing = noteDao.getNoteById(activeNoteId)
                    val noteToSave = (existing ?: NoteEntity(
                        id = activeNoteId,
                        title = currentTitle,
                        createdAt = System.currentTimeMillis()
                    )).copy(
                        title = currentTitle,
                        updatedAt = System.currentTimeMillis(),
                        previewPath = if (previewFile.exists()) previewFile.absolutePath else null,
                        viewportZoom = currentTransform.scale,
                        viewportOffsetX = currentTransform.offsetX,
                        viewportOffsetY = currentTransform.offsetY,
                        gridStyle = currentGridStyle.name
                    )
                    noteDao.insertOrUpdateNote(noteToSave)
                }

                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        noteId = activeNoteId,
                        autosaveStatus = AutosaveStatus.SAVED,
                        lastSavedTimeMillis = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uiState = uiState.copy(
                        autosaveStatus = AutosaveStatus.ERROR,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    // Debounced autosave routine (750ms delay before executing flush)
    fun scheduleAutosave() {
        autosaveJob?.cancel()
        uiState = uiState.copy(
            autosaveStatus = AutosaveStatus.SAVING,
            canUndo = commandHistory.canUndo,
            canRedo = commandHistory.canRedo,
            strokeCount = canvasViewRef?.strokes?.size ?: 0
        )

        autosaveJob = coroutineScope.launch {
            delay(750)
            flushAutosaveNow()
        }
    }

    // Guaranteed flush on lifecycle pause / stop
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                coroutineScope.launch {
                    flushAutosaveNow()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Load existing note from Room DB and storage once InfiniteCanvasView is instantiated
    val currentCanvas = canvasViewRef
    LaunchedEffect(noteId, currentCanvas) {
        if (!noteId.isNullOrEmpty() && currentCanvas != null && loadedNoteId != noteId) {
            try {
                var loadedTitle = uiState.noteTitle
                var initialGrid = InfiniteCanvasView.GridStyle.DOTS
                var initialScale = 1.0f
                var initialOffsetX = 0f
                var initialOffsetY = 0f
                var restoredStrokes: List<Stroke> = emptyList()

                withContext(Dispatchers.IO) {
                    if (noteDao != null) {
                        val entity = noteDao.getNoteById(noteId)
                        if (entity != null) {
                            loadedTitle = entity.title
                            initialGrid = try {
                                InfiniteCanvasView.GridStyle.valueOf(entity.gridStyle)
                            } catch (_: Exception) {
                                InfiniteCanvasView.GridStyle.DOTS
                            }
                            initialScale = entity.viewportZoom
                            initialOffsetX = entity.viewportOffsetX
                            initialOffsetY = entity.viewportOffsetY
                        }
                    }

                    val noteFile = File(baseDir, "$noteId.note")
                    if (noteFile.exists()) {
                        val bytes = noteFile.readBytes()
                        restoredStrokes = StrokeBinarySerializer.deserialize(bytes)
                    }
                }

                uiState = uiState.copy(
                    noteId = noteId,
                    noteTitle = loadedTitle,
                    gridStyle = initialGrid,
                    zoomPercentage = (initialScale * 100).roundToInt(),
                    strokeCount = restoredStrokes.size
                )

                currentCanvas.gridStyle = initialGrid
                currentCanvas.setStrokes(restoredStrokes)
                currentCanvas.setViewport(
                    ViewportTransform(
                        scale = initialScale,
                        offsetX = initialOffsetX,
                        offsetY = initialOffsetY
                    )
                )

                loadedNoteId = noteId
            } catch (e: Exception) {
                uiState = uiState.copy(errorMessage = "Failed to load note: ${e.message}")
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CanvasTopBar(
                state = uiState,
                onNavigateBack = {
                    coroutineScope.launch {
                        flushAutosaveNow()
                        onNavigateBack()
                    }
                },
                onTitleChanged = { newTitle ->
                    uiState = uiState.copy(noteTitle = newTitle)
                    scheduleAutosave()
                },
                onResetZoom = {
                    canvasViewRef?.let { view ->
                        view.setViewport(ViewportTransform(scale = 1.0f, offsetX = 0f, offsetY = 0f))
                        uiState = uiState.copy(zoomPercentage = 100)
                    }
                },
                onGridStyleChanged = { newStyle ->
                    uiState = uiState.copy(gridStyle = newStyle)
                    canvasViewRef?.let { view ->
                        view.gridStyle = newStyle
                        view.redraw()
                    }
                    scheduleAutosave()
                },
                onExportPdf = {
                    coroutineScope.launch {
                        try {
                            val activeStrokes = canvasViewRef?.strokes?.toList() ?: emptyList()
                            val sanitizedTitle = uiState.noteTitle.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
                            val exportFile = File(baseDir, "$sanitizedTitle.pdf")
                            withContext(Dispatchers.IO) {
                                PdfExporter.exportToPdf(activeStrokes, exportFile)
                            }
                            snackbarHostState.showSnackbar("Exported PDF to ${exportFile.name}")
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("PDF Export failed: ${e.message}")
                        }
                    }
                },
                onExportPng = {
                    coroutineScope.launch {
                        try {
                            val activeStrokes = canvasViewRef?.strokes?.toList() ?: emptyList()
                            val sanitizedTitle = uiState.noteTitle.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
                            val exportFile = File(baseDir, "$sanitizedTitle.png")
                            withContext(Dispatchers.IO) {
                                ImageExporter.exportToFile(
                                    strokes = activeStrokes,
                                    destination = exportFile,
                                    format = ImageExporter.ExportFormat.PNG
                                )
                            }
                            snackbarHostState.showSnackbar("Exported PNG to ${exportFile.name}")
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("PNG Export failed: ${e.message}")
                        }
                    }
                }
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            // High-performance infinite canvas view
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    InfiniteCanvasView(ctx).apply {
                        currentTool = uiState.selectedTool
                        currentColor = uiState.selectedColor
                        currentBaseWidth = uiState.baseWidth
                        gridStyle = uiState.gridStyle

                        onStrokeCompleted = { stroke ->
                            commandHistory.execute(CanvasAddStrokeCommand(stroke, this))
                            uiState = uiState.copy(
                                canUndo = commandHistory.canUndo,
                                canRedo = commandHistory.canRedo,
                                strokeCount = strokes.size
                            )
                            scheduleAutosave()
                        }

                        onStrokeErased = { stroke ->
                            commandHistory.execute(CanvasDeleteStrokesCommand(listOf(stroke), this))
                            uiState = uiState.copy(
                                canUndo = commandHistory.canUndo,
                                canRedo = commandHistory.canRedo,
                                strokeCount = strokes.size
                            )
                            scheduleAutosave()
                        }

                        onViewportChanged = { transform ->
                            uiState = uiState.copy(
                                zoomPercentage = (transform.scale * 100).roundToInt()
                            )
                        }

                        onToolChanged = { tool ->
                            uiState = uiState.copy(selectedTool = tool)
                        }

                        canvasViewRef = this
                    }
                },
                update = { view ->
                    view.currentTool = uiState.selectedTool
                    view.currentColor = uiState.selectedColor
                    view.currentBaseWidth = uiState.baseWidth
                    view.gridStyle = uiState.gridStyle
                }
            )

            // Draggable Floating Toolbar
            val toolbarModifier = when (uiState.dockEdge) {
                ToolbarDockEdge.BOTTOM -> {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                }
                ToolbarDockEdge.TOP -> {
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                }
                ToolbarDockEdge.LEFT -> {
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                }
                ToolbarDockEdge.RIGHT -> {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                }
                ToolbarDockEdge.FLOATING -> {
                    Modifier
                        .offset {
                            IntOffset(
                                toolbarFloatingOffset.x.roundToInt(),
                                toolbarFloatingOffset.y.roundToInt()
                            )
                        }
                }
            }

            FloatingToolbar(
                state = uiState,
                onToolSelected = { tool ->
                    uiState = uiState.copy(selectedTool = tool)
                    canvasViewRef?.currentTool = tool
                },
                onUndo = {
                    if (commandHistory.canUndo) {
                        commandHistory.undo()
                        uiState = uiState.copy(
                            canUndo = commandHistory.canUndo,
                            canRedo = commandHistory.canRedo,
                            strokeCount = canvasViewRef?.strokes?.size ?: 0
                        )
                        scheduleAutosave()
                    }
                },
                onRedo = {
                    if (commandHistory.canRedo) {
                        commandHistory.redo()
                        uiState = uiState.copy(
                            canUndo = commandHistory.canUndo,
                            canRedo = commandHistory.canRedo,
                            strokeCount = canvasViewRef?.strokes?.size ?: 0
                        )
                        scheduleAutosave()
                    }
                },
                onColorSelected = { color ->
                    uiState = uiState.copy(selectedColor = color)
                    canvasViewRef?.currentColor = color
                },
                onBaseWidthChanged = { width ->
                    uiState = uiState.copy(baseWidth = width)
                    canvasViewRef?.currentBaseWidth = width
                },
                onDockEdgeChanged = { edge ->
                    uiState = uiState.copy(dockEdge = edge)
                },
                onDrag = { delta ->
                    // Free dragging transitions toolbar to floating mode
                    uiState = uiState.copy(dockEdge = ToolbarDockEdge.FLOATING)
                    toolbarFloatingOffset = Offset(
                        x = (toolbarFloatingOffset.x + delta.x).coerceIn(16f, 1600f),
                        y = (toolbarFloatingOffset.y + delta.y).coerceIn(16f, 1000f)
                    )
                },
                modifier = toolbarModifier
            )
        }
    }
}
