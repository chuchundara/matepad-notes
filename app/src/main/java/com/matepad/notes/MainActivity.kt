package com.matepad.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.matepad.notes.data.local.AppDatabase
import com.matepad.notes.data.local.dao.NoteDao
import com.matepad.notes.ui.canvas.CanvasScreen
import com.matepad.notes.ui.library.LibraryViewModel
import com.matepad.notes.ui.library.NoteLibraryScreen

/**
 * Tablet Material 3 Color Schemes optimized for Huawei MatePad 11.5
 * high-refresh-rate 120 Hz display.
 */
private val MatePadDarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF00390E),
    tertiary = Color(0xFFFFCC80),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0)
)

private val MatePadLightColors = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    secondary = Color(0xFF388E3C),
    onSecondary = Color.White,
    tertiary = Color(0xFFF57C00),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A)
)

@Composable
fun MatePadNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MatePadDarkColors else MatePadLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

/**
 * Main application entry point for MatePad Notes.
 *
 * Responsibilities:
 * 1. Room Database DAO initialization ([AppDatabase])
 * 2. Material 3 tablet theme configuration ([MatePadNotesTheme])
 * 3. Master-Detail navigation between Note Library ([NoteLibraryScreen]) and Canvas Editor ([CanvasScreen])
 * 4. HarmonyOS / Android gesture back navigation handling via [BackHandler]
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Room Database DAO safely with graceful fallback
        val noteDao: NoteDao? = runCatching {
            AppDatabase.getInstance(applicationContext).noteDao()
        }.getOrNull()

        setContent {
            MatePadNotesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentNoteId by rememberSaveable { mutableStateOf<String?>(null) }
                    var isCanvasOpen by rememberSaveable { mutableStateOf(false) }

                    val libraryViewModel = remember { LibraryViewModel(noteDao) }

                    // Intercept hardware/gesture back press when canvas is open
                    BackHandler(enabled = isCanvasOpen) {
                        isCanvasOpen = false
                        currentNoteId = null
                    }

                    if (isCanvasOpen) {
                        CanvasScreen(
                            noteId = currentNoteId,
                            onNavigateBack = {
                                isCanvasOpen = false
                                currentNoteId = null
                            },
                            noteDao = noteDao,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        NoteLibraryScreen(
                            viewModel = libraryViewModel,
                            onNoteClick = { noteId ->
                                currentNoteId = noteId
                                isCanvasOpen = true
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
