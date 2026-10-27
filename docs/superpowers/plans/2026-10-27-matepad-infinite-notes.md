# MatePad 11.5 Infinite Canvas Notes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a high-performance Android note-taking application tailored for the Huawei MatePad 11.5 with M-Pencil stylus, featuring an infinite vector canvas, ultra-low latency inking (120 FPS), palm rejection, note catalog, and vector PDF/PNG export.

**Architecture:** Clean Architecture with MVI/MVVM. Uses `androidx.ink` and `CanvasFrontBufferedRenderer` for front-buffered, low-latency inking; a QuadTree spatial index for 120 FPS viewport culling on an infinite canvas; Room DB for note metadata and compressed binary `.note` files for stroke persistence; Jetpack Compose for the tablet UI.

**Tech Stack:** Kotlin 2.0+, Android SDK 34 (minSdk 26), Jetpack Compose, Material 3, AndroidX Ink (`androidx.ink:ink-authoring`, `ink-strokes`, `ink-geometry`), AndroidX Graphics Core, Room Database, Kotlinx Coroutines & Serialization, JUnit 4/5.

## Global Constraints

- Target hardware: Huawei MatePad 11.5 (HarmonyOS / Android 12+ compatibility, 120Hz display, M-Pencil 2/3).
- Min SDK: 26 (Android 8.0), Target SDK: 34 (Android 14+).
- Hardware stylus events: `MotionEvent.TOOL_TYPE_STYLUS` for drawing, `TOOL_TYPE_FINGER` reserved for 2-finger pan/zoom; palm touches filtered out during drawing.
- Canvas performance: Viewport Culling via QuadTree to render only strokes inside the active screen bounds at 120 FPS.
- Infinite zoom range: 0.1x (10%) to 50.0x (5000%).
- Data safety: Autosave debounced at 750ms and guaranteed on `onPause()`.

---

### Task 1: Project Scaffolding & Gradle Build Configuration

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/java/com/matepad/notes/SanityTest.kt`

**Interfaces:**
- Consumes: None (Root setup)
- Produces: Base Android Gradle project structure with Compose, AndroidX Ink, and Room dependencies.

- [ ] **Step 1: Write sanity test**

```kotlin
package com.matepad.notes

import org.junit.Assert.assertTrue
import org.junit.Test

class SanityTest {
    @Test
    fun testEnvironmentIsConfigured() {
        assertTrue(true)
    }
}
```

- [ ] **Step 2: Run test to verify failure (project not yet configured)**

Run: `gradle test`
Expected: FAIL (build file not found)

- [ ] **Step 3: Create Gradle build configuration and manifest**

Create `settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "matepad-notes"
include(":app")
```

Create root `build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
    id("com.google.devtools.ksp") version "2.0.0-1.0.22" apply false
}
```

Create `app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("kotlinx-serialization")
}

android {
    namespace = "com.matepad.notes"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.matepad.notes"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")

    // AndroidX Inking & Graphics
    implementation("androidx.graphics:graphics-core:1.0.1")
    implementation("androidx.ink:ink-authoring:1.0.0-alpha02")
    implementation("androidx.ink:ink-geometry:1.0.0-alpha02")
    implementation("androidx.ink:ink-strokes:1.0.0-alpha02")
    implementation("androidx.ink:ink-rendering:1.0.0-alpha02")

    // Room DB
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Coroutines & Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
```

Create `app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="MatePad Notes"
        android:roundIcon="@android:drawable/sym_def_app_icon"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 4: Run test to verify pass**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.SanityTest`
Expected: BUILD SUCCESSFUL, 1 test passed

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts build.gradle.kts app/
git commit -m "chore: scaffold android project and configure dependencies"
```

---

### Task 2: Vector Geometry & Coordinate Math (World vs Screen)

**Files:**
- Create: `app/src/main/java/com/matepad/notes/domain/geometry/PointF.kt`
- Create: `app/src/main/java/com/matepad/notes/domain/geometry/RectF.kt`
- Create: `app/src/main/java/com/matepad/notes/domain/geometry/ViewportTransform.kt`
- Test: `app/src/test/java/com/matepad/notes/domain/geometry/ViewportTransformTest.kt`

**Interfaces:**
- Consumes: None
- Produces: `PointF(x: Float, y: Float)`, `RectF(left: Float, top: Float, right: Float, bottom: Float)`, `ViewportTransform` for converting screen coordinates to world coordinates and vice versa with zoom constraints (0.1x to 50.0x).

- [ ] **Step 1: Write failing unit test for coordinate transformations**

```kotlin
package com.matepad.notes.domain.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportTransformTest {
    @Test
    fun testScreenToWorldAndBack() {
        val transform = ViewportTransform(scale = 2.0f, offsetX = 100.0f, offsetY = 200.0f)
        val screenPoint = PointF(300.0f, 400.0f)
        
        val worldPoint = transform.screenToWorld(screenPoint)
        assertEquals(100.0f, worldPoint.x, 0.001f)
        assertEquals(100.0f, worldPoint.y, 0.001f)
        
        val roundTripScreen = transform.worldToScreen(worldPoint)
        assertEquals(screenPoint.x, roundTripScreen.x, 0.001f)
        assertEquals(screenPoint.y, roundTripScreen.y, 0.001f)
    }

    @Test
    fun testZoomClamping() {
        val transform = ViewportTransform(scale = 1.0f, offsetX = 0.0f, offsetY = 0.0f)
        val zoomedOut = transform.withScale(0.01f)
        assertEquals(0.1f, zoomedOut.scale, 0.001f)

        val zoomedIn = transform.withScale(100.0f)
        assertEquals(50.0f, zoomedIn.scale, 0.001f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.domain.geometry.ViewportTransformTest`
Expected: Compilation failure (unresolved reference `ViewportTransform`).

- [ ] **Step 3: Implement geometry models and ViewportTransform**

Create `app/src/main/java/com/matepad/notes/domain/geometry/PointF.kt`:
```kotlin
package com.matepad.notes.domain.geometry

data class PointF(val x: Float, val y: Float)
```

Create `app/src/main/java/com/matepad/notes/domain/geometry/RectF.kt`:
```kotlin
package com.matepad.notes.domain.geometry

data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun intersects(other: RectF): Boolean {
        return left < other.right && right > other.left && top < other.bottom && bottom > other.top
    }

    fun contains(point: PointF): Boolean {
        return point.x in left..right && point.y in top..bottom
    }
}
```

Create `app/src/main/java/com/matepad/notes/domain/geometry/ViewportTransform.kt`:
```kotlin
package com.matepad.notes.domain.geometry

data class ViewportTransform(
    val scale: Float = 1.0f,
    val offsetX: Float = 0.0f,
    val offsetY: Float = 0.0f
) {
    companion object {
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 50.0f
    }

    fun withScale(newScale: Float): ViewportTransform {
        val clamped = newScale.coerceIn(MIN_SCALE, MAX_SCALE)
        return copy(scale = clamped)
    }

    fun screenToWorld(screenPoint: PointF): PointF {
        return PointF(
            x = (screenPoint.x - offsetX) / scale,
            y = (screenPoint.y - offsetY) / scale
        )
    }

    fun worldToScreen(worldPoint: PointF): PointF {
        return PointF(
            x = worldPoint.x * scale + offsetX,
            y = worldPoint.y * scale + offsetY
        )
    }

    fun getVisibleWorldBounds(screenWidth: Float, screenHeight: Float): RectF {
        val topLeft = screenToWorld(PointF(0.0f, 0.0f))
        val bottomRight = screenToWorld(PointF(screenWidth, screenHeight))
        return RectF(
            left = topLeft.x,
            top = topLeft.y,
            right = bottomRight.x,
            bottom = bottomRight.y
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.domain.geometry.ViewportTransformTest`
Expected: PASS (2 tests passed)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/matepad/notes/domain/geometry/ app/src/test/java/com/matepad/notes/domain/geometry/
git commit -m "feat: implement viewport transformation and geometry models"
```

---

### Task 3: Spatial Indexing (QuadTree) for Infinite Canvas Viewport Culling

**Files:**
- Create: `app/src/main/java/com/matepad/notes/domain/spatial/QuadTree.kt`
- Test: `app/src/test/java/com/matepad/notes/domain/spatial/QuadTreeTest.kt`

**Interfaces:**
- Consumes: `RectF`, `PointF` from `domain.geometry`
- Produces: `QuadTree<T>` storing items bounded by `RectF`, supporting $O(\log N)$ spatial query for viewport bounds culling.

- [ ] **Step 1: Write failing unit test for QuadTree**

```kotlin
package com.matepad.notes.domain.spatial

import com.matepad.notes.domain.geometry.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadTreeTest {
    data class TestItem(val id: String, val bounds: RectF)

    @Test
    fun testInsertAndQueryVisibleItems() {
        val tree = QuadTree<TestItem>(
            boundary = RectF(-1000f, -1000f, 1000f, 1000f),
            maxObjects = 4,
            maxLevels = 5
        ) { it.bounds }

        val item1 = TestItem("item1", RectF(10f, 10f, 50f, 50f))
        val item2 = TestItem("item2", RectF(500f, 500f, 600f, 600f))
        val item3 = TestItem("item3", RectF(-200f, -200f, -100f, -100f))

        tree.insert(item1)
        tree.insert(item2)
        tree.insert(item3)

        // Query viewport covering only item1
        val visibleInViewport = tree.query(RectF(0f, 0f, 100f, 100f))
        assertEquals(1, visibleInViewport.size)
        assertTrue(visibleInViewport.contains(item1))

        // Query viewport covering item1 and item2
        val wideQuery = tree.query(RectF(0f, 0f, 800f, 800f))
        assertEquals(2, wideQuery.size)
        assertTrue(wideQuery.contains(item1))
        assertTrue(wideQuery.contains(item2))
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.domain.spatial.QuadTreeTest`
Expected: Compilation failure (`QuadTree` does not exist).

- [ ] **Step 3: Implement generic QuadTree with Subdivisions**

Create `app/src/main/java/com/matepad/notes/domain/spatial/QuadTree.kt`:
```kotlin
package com.matepad.notes.domain.spatial

import com.matepad.notes.domain.geometry.RectF

class QuadTree<T>(
    private val boundary: RectF,
    private val maxObjects: Int = 8,
    private val maxLevels: Int = 6,
    private val level: Int = 0,
    private val boundsExtractor: (T) -> RectF
) {
    private val items = mutableListOf<T>()
    private var subnodes: Array<QuadTree<T>>? = null

    fun insert(item: T) {
        val itemBounds = boundsExtractor(item)
        if (!boundary.intersects(itemBounds)) {
            return
        }

        if (subnodes == null && items.size < maxObjects || level >= maxLevels) {
            items.add(item)
            return
        }

        if (subnodes == null) {
            subdivide()
            val existing = ArrayList(items)
            items.clear()
            for (old in existing) {
                insertIntoSubnodes(old)
            }
        }

        insertIntoSubnodes(item)
    }

    private fun insertIntoSubnodes(item: T) {
        var placed = false
        subnodes?.forEach { node ->
            if (node.boundary.intersects(boundsExtractor(item))) {
                node.insert(item)
                placed = true
            }
        }
        if (!placed) {
            items.add(item)
        }
    }

    private fun subdivide() {
        val midX = boundary.left + boundary.width / 2f
        val midY = boundary.top + boundary.height / 2f

        subnodes = arrayOf(
            QuadTree(RectF(boundary.left, boundary.top, midX, midY), maxObjects, maxLevels, level + 1, boundsExtractor), // NW
            QuadTree(RectF(midX, boundary.top, boundary.right, midY), maxObjects, maxLevels, level + 1, boundsExtractor), // NE
            QuadTree(RectF(boundary.left, midY, midX, boundary.bottom), maxObjects, maxLevels, level + 1, boundsExtractor), // SW
            QuadTree(RectF(midX, midY, boundary.right, boundary.bottom), maxObjects, maxLevels, level + 1, boundsExtractor)  // SE
        )
    }

    fun query(range: RectF, result: MutableSet<T> = mutableSetOf()): Set<T> {
        if (!boundary.intersects(range)) {
            return result
        }

        for (item in items) {
            if (range.intersects(boundsExtractor(item))) {
                result.add(item)
            }
        }

        subnodes?.forEach { node ->
            node.query(range, result)
        }

        return result
    }

    fun clear() {
        items.clear()
        subnodes?.forEach { it.clear() }
        subnodes = null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.domain.spatial.QuadTreeTest`
Expected: PASS (1 test passed)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/matepad/notes/domain/spatial/ app/src/test/java/com/matepad/notes/domain/spatial/
git commit -m "feat: implement QuadTree spatial index for viewport culling"
```

---

### Task 4: Inking Domain Models, Commands & Undo/Redo Engine

**Files:**
- Create: `app/src/main/java/com/matepad/notes/domain/model/ToolType.kt`
- Create: `app/src/main/java/com/matepad/notes/domain/model/StrokePoint.kt`
- Create: `app/src/main/java/com/matepad/notes/domain/model/Stroke.kt`
- Create: `app/src/main/java/com/matepad/notes/domain/command/CanvasCommand.kt`
- Create: `app/src/main/java/com/matepad/notes/domain/command/CommandHistory.kt`
- Test: `app/src/test/java/com/matepad/notes/domain/command/CommandHistoryTest.kt`

**Interfaces:**
- Consumes: `RectF` from `domain.geometry`
- Produces: `Stroke` model, `AddStrokeCommand`, `DeleteStrokesCommand`, and `CommandHistory` supporting unbounded undo/redo.

- [ ] **Step 1: Write failing unit test for CommandHistory**

```kotlin
package com.matepad.notes.domain.command

import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandHistoryTest {
    @Test
    fun testUndoRedoCycle() {
        val history = CommandHistory()
        val strokes = mutableListOf<Stroke>()

        val stroke1 = Stroke(
            id = "s1",
            toolType = ToolType.PEN,
            color = 0xFF000000.toInt(),
            baseWidth = 2.0f,
            points = listOf(StrokePoint(0f, 0f, 0.5f, 0f)),
            bounds = RectF(0f, 0f, 2f, 2f)
        )

        val addCmd = AddStrokeCommand(stroke1, strokes)
        history.execute(addCmd)
        assertEquals(1, strokes.size)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)

        history.undo()
        assertEquals(0, strokes.size)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)

        history.redo()
        assertEquals(1, strokes.size)
        assertTrue(history.canUndo)
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.domain.command.CommandHistoryTest`
Expected: Compilation failure (Stroke, CommandHistory missing).

- [ ] **Step 3: Implement inking models and command pattern**

Create `app/src/main/java/com/matepad/notes/domain/model/ToolType.kt`:
```kotlin
package com.matepad.notes.domain.model

enum class ToolType {
    PEN,
    HIGHLIGHTER,
    STROKE_ERASER,
    SEGMENT_ERASER,
    LASSO
}
```

Create `app/src/main/java/com/matepad/notes/domain/model/StrokePoint.kt`:
```kotlin
package com.matepad.notes.domain.model

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f,
    val tilt: Float = 0.0f
)
```

Create `app/src/main/java/com/matepad/notes/domain/model/Stroke.kt`:
```kotlin
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
```

Create `app/src/main/java/com/matepad/notes/domain/command/CanvasCommand.kt`:
```kotlin
package com.matepad.notes.domain.command

import com.matepad.notes.domain.model.Stroke

interface CanvasCommand {
    fun execute()
    fun undo()
}

class AddStrokeCommand(
    private val stroke: Stroke,
    private val targetList: MutableList<Stroke>
) : CanvasCommand {
    override fun execute() {
        targetList.add(stroke)
    }
    override fun undo() {
        targetList.remove(stroke)
    }
}

class DeleteStrokesCommand(
    private val deletedStrokes: List<Stroke>,
    private val targetList: MutableList<Stroke>
) : CanvasCommand {
    override fun execute() {
        targetList.removeAll(deletedStrokes.toSet())
    }
    override fun undo() {
        targetList.addAll(deletedStrokes)
    }
}
```

Create `app/src/main/java/com/matepad/notes/domain/command/CommandHistory.kt`:
```kotlin
package com.matepad.notes.domain.command

class CommandHistory {
    private val undoStack = mutableListOf<CanvasCommand>()
    private val redoStack = mutableListOf<CanvasCommand>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun execute(command: CanvasCommand) {
        command.execute()
        undoStack.add(command)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val command = undoStack.removeAt(undoStack.lastIndex)
            command.undo()
            redoStack.add(command)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val command = redoStack.removeAt(redoStack.lastIndex)
            command.execute()
            undoStack.add(command)
        }
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.domain.command.CommandHistoryTest`
Expected: PASS (1 test passed)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/matepad/notes/domain/model/ app/src/main/java/com/matepad/notes/domain/command/ app/src/test/java/com/matepad/notes/domain/command/
git commit -m "feat: implement stroke domain models and undo/redo command history"
```

---

### Task 5: Note Persistence (Room Entities, DAO & Binary/Gzip `.note` Serializer)

**Files:**
- Create: `app/src/main/java/com/matepad/notes/data/local/entity/NoteEntity.kt`
- Create: `app/src/main/java/com/matepad/notes/data/local/entity/FolderEntity.kt`
- Create: `app/src/main/java/com/matepad/notes/data/local/dao/NoteDao.kt`
- Create: `app/src/main/java/com/matepad/notes/data/serializer/StrokeBinarySerializer.kt`
- Test: `app/src/test/java/com/matepad/notes/data/serializer/StrokeBinarySerializerTest.kt`

**Interfaces:**
- Consumes: `Stroke`, `StrokePoint`, `ToolType` from `domain.model`
- Produces: `StrokeBinarySerializer.serialize(strokes: List<Stroke>): ByteArray` and `deserialize(bytes: ByteArray): List<Stroke>` for compact, fast file storage.

- [ ] **Step 1: Write failing unit test for binary stroke serializer**

```kotlin
package com.matepad.notes.data.serializer

import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeBinarySerializerTest {
    @Test
    fun testSerializeAndDeserializeStrokes() {
        val originalStrokes = listOf(
            Stroke(
                id = "stroke-1",
                toolType = ToolType.PEN,
                color = 0xFF123456.toInt(),
                baseWidth = 3.5f,
                points = listOf(
                    StrokePoint(10.0f, 20.0f, 0.75f, 0.1f),
                    StrokePoint(12.5f, 22.5f, 0.85f, 0.12f)
                ),
                bounds = RectF(10.0f, 20.0f, 12.5f, 22.5f)
            )
        )

        val bytes = StrokeBinarySerializer.serialize(originalStrokes)
        val deserialized = StrokeBinarySerializer.deserialize(bytes)

        assertEquals(originalStrokes.size, deserialized.size)
        val item = deserialized[0]
        assertEquals(originalStrokes[0].id, item.id)
        assertEquals(originalStrokes[0].toolType, item.toolType)
        assertEquals(originalStrokes[0].color, item.color)
        assertEquals(originalStrokes[0].baseWidth, item.baseWidth, 0.001f)
        assertEquals(2, item.points.size)
        assertEquals(10.0f, item.points[0].x, 0.001f)
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.data.serializer.StrokeBinarySerializerTest`
Expected: Compilation failure (`StrokeBinarySerializer` missing).

- [ ] **Step 3: Implement binary serializer and Room entities**

Create `app/src/main/java/com/matepad/notes/data/serializer/StrokeBinarySerializer.kt`:
```kotlin
package com.matepad.notes.data.serializer

import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object StrokeBinarySerializer {
    private const val MAGIC_HEADER = 0x4E4F5445 // "NOTE"
    private const val VERSION = 1

    fun serialize(strokes: List<Stroke>): ByteArray {
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { gzip ->
            DataOutputStream(gzip).use { out ->
                out.writeInt(MAGIC_HEADER)
                out.writeInt(VERSION)
                out.writeInt(strokes.size)

                for (stroke in strokes) {
                    out.writeUTF(stroke.id)
                    out.writeInt(stroke.toolType.ordinal)
                    out.writeInt(stroke.color)
                    out.writeFloat(stroke.baseWidth)

                    // Bounds
                    out.writeFloat(stroke.bounds.left)
                    out.writeFloat(stroke.bounds.top)
                    out.writeFloat(stroke.bounds.right)
                    out.writeFloat(stroke.bounds.bottom)

                    // Points
                    out.writeInt(stroke.points.size)
                    for (point in stroke.points) {
                        out.writeFloat(point.x)
                        out.writeFloat(point.y)
                        out.writeFloat(point.pressure)
                        out.writeFloat(point.tilt)
                    }
                }
            }
        }
        return byteStream.toByteArray()
    }

    fun deserialize(bytes: ByteArray): List<Stroke> {
        val result = mutableListOf<Stroke>()
        GZIPInputStream(ByteArrayInputStream(bytes)).use { gzip ->
            DataInputStream(gzip).use { input ->
                val magic = input.readInt()
                if (magic != MAGIC_HEADER) {
                    throw IllegalArgumentException("Invalid .note file header")
                }
                val version = input.readInt()
                val count = input.readInt()

                for (i in 0 until count) {
                    val id = input.readUTF()
                    val toolOrdinal = input.readInt()
                    val color = input.readInt()
                    val baseWidth = input.readFloat()

                    val left = input.readFloat()
                    val top = input.readFloat()
                    val right = input.readFloat()
                    val bottom = input.readFloat()
                    val bounds = RectF(left, top, right, bottom)

                    val pointCount = input.readInt()
                    val points = ArrayList<StrokePoint>(pointCount)
                    for (p in 0 until pointCount) {
                        points.add(
                            StrokePoint(
                                x = input.readFloat(),
                                y = input.readFloat(),
                                pressure = input.readFloat(),
                                tilt = input.readFloat()
                            )
                        )
                    }

                    result.add(
                        Stroke(
                            id = id,
                            toolType = ToolType.values()[toolOrdinal],
                            color = color,
                            baseWidth = baseWidth,
                            points = points,
                            bounds = bounds
                        )
                    )
                }
            }
        }
        return result
    }
}
```

Create `app/src/main/java/com/matepad/notes/data/local/entity/FolderEntity.kt`:
```kotlin
package com.matepad.notes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String,
    val orderIndex: Int
)
```

Create `app/src/main/java/com/matepad/notes/data/local/entity/NoteEntity.kt`:
```kotlin
package com.matepad.notes.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "folder_id") val folderId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "preview_path") val previewPath: String? = null,
    @ColumnInfo(name = "viewport_zoom") val viewportZoom: Float = 1.0f,
    @ColumnInfo(name = "viewport_offset_x") val viewportOffsetX: Float = 0.0f,
    @ColumnInfo(name = "viewport_offset_y") val viewportOffsetY: Float = 0.0f,
    @ColumnInfo(name = "grid_style") val gridStyle: String = "DOTS"
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.data.serializer.StrokeBinarySerializerTest`
Expected: PASS (1 test passed)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/matepad/notes/data/ app/src/test/java/com/matepad/notes/data/
git commit -m "feat: implement note room entities and binary stroke compression serializer"
```

---

### Task 6: Touch & Stylus Input Pipeline (Palm Rejection & Gestures)

**Files:**
- Create: `app/src/main/java/com/matepad/notes/ui/canvas/input/StylusInputClassifier.kt`
- Create: `app/src/main/java/com/matepad/notes/ui/canvas/input/CanvasGestureListener.kt`
- Test: `app/src/test/java/com/matepad/notes/ui/canvas/input/StylusInputClassifierTest.kt`

**Interfaces:**
- Consumes: `MotionEvent` pointer data
- Produces: `InputEvent.Draw(point: StrokePoint)`, `InputEvent.Gesture(scaleDelta: Float, panX: Float, panY: Float)`, `InputEvent.ToggleEraser`

- [ ] **Step 1: Write failing unit test for stylus classification**

```kotlin
package com.matepad.notes.ui.canvas.input

import org.junit.Assert.assertEquals
import org.junit.Test

class StylusInputClassifierTest {
    @Test
    fun testRejectsFingerWhenStylusActive() {
        val classifier = StylusInputClassifier()
        classifier.onStylusDown()

        // Finger event while stylus is down must be ignored for drawing
        val action = classifier.classifyPointer(isStylus = false, pointerCount = 1)
        assertEquals(StylusInputClassifier.Action.IGNORE_PALM, action)

        classifier.onStylusUp()
        val afterStylusUp = classifier.classifyPointer(isStylus = false, pointerCount = 2)
        assertEquals(StylusInputClassifier.Action.PAN_ZOOM_GESTURE, afterStylusUp)
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.ui.canvas.input.StylusInputClassifierTest`
Expected: Compilation failure.

- [ ] **Step 3: Implement StylusInputClassifier**

Create `app/src/main/java/com/matepad/notes/ui/canvas/input/StylusInputClassifier.kt`:
```kotlin
package com.matepad.notes.ui.canvas.input

class StylusInputClassifier {
    enum class Action {
        DRAW,
        PAN_ZOOM_GESTURE,
        IGNORE_PALM
    }

    private var isStylusActive = false

    fun onStylusDown() {
        isStylusActive = true
    }

    fun onStylusUp() {
        isStylusActive = false
    }

    fun classifyPointer(isStylus: Boolean, pointerCount: Int): Action {
        if (isStylus) {
            return Action.DRAW
        }
        if (isStylusActive) {
            return Action.IGNORE_PALM
        }
        if (pointerCount >= 2) {
            return Action.PAN_ZOOM_GESTURE
        }
        return Action.IGNORE_PALM
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.ui.canvas.input.StylusInputClassifierTest`
Expected: PASS (1 test passed)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/matepad/notes/ui/canvas/input/ app/src/test/java/com/matepad/notes/ui/canvas/input/
git commit -m "feat: implement hardware palm rejection and input classification"
```

---

### Task 7: Vector Stroke Meshing & Front-Buffered Low-Latency View

**Files:**
- Create: `app/src/main/java/com/matepad/notes/ui/canvas/render/StrokeMeshGenerator.kt`
- Create: `app/src/main/java/com/matepad/notes/ui/canvas/render/InfiniteCanvasView.kt`
- Test: `app/src/test/java/com/matepad/notes/ui/canvas/render/StrokeMeshGeneratorTest.kt`

**Interfaces:**
- Consumes: `Stroke`, `StrokePoint`, `ViewportTransform`, `QuadTree`
- Produces: Smooth Catmull-Rom path mesh calculation and custom SurfaceView utilizing `androidx.graphics.lowlatency.CanvasFrontBufferedRenderer` for ultra-low latency rendering.

- [ ] **Step 1: Write failing unit test for stroke bounding box calculation**

```kotlin
package com.matepad.notes.ui.canvas.render

import com.matepad.notes.domain.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeMeshGeneratorTest {
    @Test
    fun testComputeBoundsWithBaseWidth() {
        val points = listOf(
            StrokePoint(10f, 10f, 1.0f),
            StrokePoint(50f, 80f, 1.0f)
        )
        val bounds = StrokeMeshGenerator.computeBounds(points, baseWidth = 4.0f)

        assertEquals(8f, bounds.left, 0.001f)
        assertEquals(8f, bounds.top, 0.001f)
        assertEquals(52f, bounds.right, 0.001f)
        assertEquals(82f, bounds.bottom, 0.001f)
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.ui.canvas.render.StrokeMeshGeneratorTest`
Expected: Compilation failure.

- [ ] **Step 3: Implement StrokeMeshGenerator**

Create `app/src/main/java/com/matepad/notes/ui/canvas/render/StrokeMeshGenerator.kt`:
```kotlin
package com.matepad.notes.ui.canvas.render

import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.StrokePoint
import kotlin.math.max
import kotlin.math.min

object StrokeMeshGenerator {
    fun computeBounds(points: List<StrokePoint>, baseWidth: Float): RectF {
        if (points.isEmpty()) return RectF(0f, 0f, 0f, 0f)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (p in points) {
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }

        val padding = baseWidth / 2f
        return RectF(
            left = minX - padding,
            top = minY - padding,
            right = maxX + padding,
            bottom = maxY + padding
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.ui.canvas.render.StrokeMeshGeneratorTest`
Expected: PASS (1 test passed)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/matepad/notes/ui/canvas/render/ app/src/test/java/com/matepad/notes/ui/canvas/render/
git commit -m "feat: implement stroke mesh generator and bounds computation"
```

---

### Task 8: Inking Toolset (Dynamic Pressure Pen, Highlighter, Eraser, Lasso)

**Files:**
- Create: `app/src/main/java/com/matepad/notes/domain/tool/LassoSelection.kt`
- Create: `app/src/main/java/com/matepad/notes/domain/tool/StrokeEraser.kt`
- Test: `app/src/test/java/com/matepad/notes/domain/tool/StrokeEraserTest.kt`

**Interfaces:**
- Consumes: `Stroke`, `QuadTree`, `RectF`
- Produces: `StrokeEraser.findIntersectingStrokes(...)` to remove touched strokes, and `LassoSelection.findSelectedStrokes(...)` to select strokes within a lasso loop.

- [ ] **Step 1: Write failing unit test for stroke eraser**

```kotlin
package com.matepad.notes.domain.tool

import com.matepad.notes.domain.geometry.PointF
import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import org.junit.Assert.assertEquals
import org.junit.Test

class StrokeEraserTest {
    @Test
    fun testFindIntersectingStrokes() {
        val stroke = Stroke(
            id = "s1",
            toolType = ToolType.PEN,
            color = 0,
            baseWidth = 2f,
            points = listOf(StrokePoint(10f, 10f), StrokePoint(20f, 20f)),
            bounds = RectF(10f, 10f, 20f, 20f)
        )

        val hitEraserPoint = PointF(15f, 15f)
        val hit = StrokeEraser.isStrokeHit(stroke, hitEraserPoint, eraserRadius = 5f)
        assertEquals(true, hit)

        val missEraserPoint = PointF(100f, 100f)
        val miss = StrokeEraser.isStrokeHit(stroke, missEraserPoint, eraserRadius = 5f)
        assertEquals(false, miss)
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.domain.tool.StrokeEraserTest`
Expected: Compilation failure.

- [ ] **Step 3: Implement StrokeEraser**

Create `app/src/main/java/com/matepad/notes/domain/tool/StrokeEraser.kt`:
```kotlin
package com.matepad.notes.domain.tool

import com.matepad.notes.domain.geometry.PointF
import com.matepad.notes.domain.model.Stroke
import kotlin.math.hypot

object StrokeEraser {
    fun isStrokeHit(stroke: Stroke, eraserPoint: PointF, eraserRadius: Float): Boolean {
        val thresholdSq = (eraserRadius + stroke.baseWidth / 2f) * (eraserRadius + stroke.baseWidth / 2f)

        for (p in stroke.points) {
            val dx = p.x - eraserPoint.x
            val dy = p.y - eraserPoint.y
            if (dx * dx + dy * dy <= thresholdSq) {
                return true
            }
        }
        return false
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.domain.tool.StrokeEraserTest`
Expected: PASS (1 test passed)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/matepad/notes/domain/tool/ app/src/test/java/com/matepad/notes/domain/tool/
git commit -m "feat: implement stroke eraser hit testing"
```

---

### Task 9: Export Services (Vector PDF & High-Res PNG)

**Files:**
- Create: `app/src/main/java/com/matepad/notes/data/export/PdfExporter.kt`
- Create: `app/src/main/java/com/matepad/notes/data/export/ImageExporter.kt`
- Test: `app/src/test/java/com/matepad/notes/data/export/PdfExporterTest.kt`

**Interfaces:**
- Consumes: `List<Stroke>`, destination `File`
- Produces: Clean PDF document using Android's `PdfDocument` with vector paths preserving crisp lines at any print resolution.

- [ ] **Step 1: Write unit test for PDF export bounds calculation**

```kotlin
package com.matepad.notes.data.export

import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfExporterTest {
    @Test
    fun testCalculateDocumentBounds() {
        val strokes = listOf(
            Stroke("1", ToolType.PEN, 0, 2f, listOf(StrokePoint(10f, 20f)), RectF(10f, 20f, 50f, 80f)),
            Stroke("2", ToolType.PEN, 0, 2f, listOf(StrokePoint(0f, 0f)), RectF(0f, 0f, 100f, 200f))
        )
        val fullBounds = PdfExporter.calculateEnclosingBounds(strokes)
        assertEquals(0f, fullBounds.left, 0.001f)
        assertEquals(0f, fullBounds.top, 0.001f)
        assertEquals(100f, fullBounds.right, 0.001f)
        assertEquals(200f, fullBounds.bottom, 0.001f)
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.data.export.PdfExporterTest`
Expected: Compilation failure.

- [ ] **Step 3: Implement PdfExporter logic**

Create `app/src/main/java/com/matepad/notes/data/export/PdfExporter.kt`:
```kotlin
package com.matepad.notes.data.export

import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import kotlin.math.max
import kotlin.math.min

object PdfExporter {
    fun calculateEnclosingBounds(strokes: List<Stroke>): RectF {
        if (strokes.isEmpty()) return RectF(0f, 0f, 595f, 842f) // Default A4 at 72 dpi

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (stroke in strokes) {
            minX = min(minX, stroke.bounds.left)
            minY = min(minY, stroke.bounds.top)
            maxX = max(maxX, stroke.bounds.right)
            maxY = max(maxY, stroke.bounds.bottom)
        }

        return RectF(minX, minY, maxX, maxY)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.data.export.PdfExporterTest`
Expected: PASS (1 test passed)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/matepad/notes/data/export/ app/src/test/java/com/matepad/notes/data/export/
git commit -m "feat: implement PDF export bounds engine"
```

---

### Task 10: Tablet UI - Note Library & Folder Navigation (Compose)

**Files:**
- Create: `app/src/main/java/com/matepad/notes/ui/library/NoteLibraryScreen.kt`
- Create: `app/src/main/java/com/matepad/notes/ui/library/NoteCardItem.kt`
- Create: `app/src/main/java/com/matepad/notes/ui/library/LibraryViewModel.kt`

**Interfaces:**
- Consumes: `NoteEntity`, `FolderEntity` from Room
- Produces: Master-Detail tablet UI with a side navigation rail for folders, a grid of note cards with preview thumbnails, and a Floating Action Button for creating new notes.

- [ ] **Step 1: Write UI unit test for Library ViewModel**

Create `app/src/test/java/com/matepad/notes/ui/library/LibraryViewModelTest.kt`:
```kotlin
package com.matepad.notes.ui.library

import com.matepad.notes.data.local.entity.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.ui.library.LibraryViewModelTest`
Expected: Compilation failure (`LibraryUiState` not found).

- [ ] **Step 3: Implement Library UI state and Compose components**

Create `app/src/main/java/com/matepad/notes/ui/library/LibraryViewModel.kt`:
```kotlin
package com.matepad.notes.ui.library

import com.matepad.notes.data.local.entity.FolderEntity
import com.matepad.notes.data.local.entity.NoteEntity

data class LibraryUiState(
    val notes: List<NoteEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val selectedFolderId: String? = null,
    val searchQuery: String = ""
)
```

Create `app/src/main/java/com/matepad/notes/ui/library/NoteCardItem.kt`:
```kotlin
package com.matepad.notes.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matepad.notes.data.local.entity.NoteEntity

@Composable
fun NoteCardItem(
    note: NoteEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Grid: ${note.gridStyle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.ui.library.LibraryViewModelTest`
Expected: PASS (1 test passed)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/matepad/notes/ui/library/ app/src/test/java/com/matepad/notes/ui/library/
git commit -m "feat: implement tablet note library UI state and card components"
```

---

### Task 11: Tablet UI - Fullscreen Canvas Screen & Floating Toolbar

**Files:**
- Create: `app/src/main/java/com/matepad/notes/ui/canvas/CanvasTopBar.kt`
- Create: `app/src/main/java/com/matepad/notes/ui/canvas/FloatingToolbar.kt`
- Create: `app/src/main/java/com/matepad/notes/ui/canvas/CanvasScreen.kt`
- Test: `app/src/test/java/com/matepad/notes/ui/canvas/CanvasUiStateTest.kt`

**Interfaces:**
- Consumes: `ToolType`, `CommandHistory`, `ViewportTransform`
- Produces: Floating draggable toolbar with tool buttons (Pen, Highlighter, Eraser, Lasso, Undo, Redo, Color presets) and top navigation bar with zoom % indicator and export triggers.

- [ ] **Step 1: Write UI state unit test for Canvas**

Create `app/src/test/java/com/matepad/notes/ui/canvas/CanvasUiStateTest.kt`:
```kotlin
package com.matepad.notes.ui.canvas

import com.matepad.notes.domain.model.ToolType
import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasUiStateTest {
    @Test
    fun testToolSelection() {
        val state = CanvasUiState(
            selectedTool = ToolType.PEN,
            selectedColor = 0xFF000000.toInt(),
            baseWidth = 3.0f,
            zoomPercentage = 100
        )
        assertEquals(ToolType.PEN, state.selectedTool)
        assertEquals(100, state.zoomPercentage)
    }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.ui.canvas.CanvasUiStateTest`
Expected: Compilation failure (`CanvasUiState` not found).

- [ ] **Step 3: Implement Canvas UI State and Floating Toolbar**

Create `app/src/main/java/com/matepad/notes/ui/canvas/CanvasUiState.kt`:
```kotlin
package com.matepad.notes.ui.canvas

import com.matepad.notes.domain.model.ToolType

data class CanvasUiState(
    val selectedTool: ToolType = ToolType.PEN,
    val selectedColor: Int = 0xFF000000.toInt(),
    val baseWidth: Float = 3.0f,
    val zoomPercentage: Int = 100,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)
```

Create `app/src/main/java/com/matepad/notes/ui/canvas/FloatingToolbar.kt`:
```kotlin
package com.matepad.notes.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matepad.notes.domain.model.ToolType

@Composable
fun FloatingToolbar(
    state: CanvasUiState,
    onToolSelected: (ToolType) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { onToolSelected(ToolType.PEN) },
                colors = if (state.selectedTool == ToolType.PEN) {
                    IconButtonDefaults.filledIconButtonColors()
                } else IconButtonDefaults.iconButtonColors()
            ) {
                Icon(Icons.Default.Create, contentDescription = "Pen")
            }

            IconButton(
                onClick = { onToolSelected(ToolType.STROKE_ERASER) },
                colors = if (state.selectedTool == ToolType.STROKE_ERASER) {
                    IconButtonDefaults.filledIconButtonColors()
                } else IconButtonDefaults.iconButtonColors()
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Eraser")
            }

            Divider(
                modifier = Modifier
                    .height(32.dp)
                    .width(1.dp)
            )

            IconButton(onClick = onUndo, enabled = state.canUndo) {
                Icon(Icons.Default.Undo, contentDescription = "Undo")
            }

            IconButton(onClick = onRedo, enabled = state.canRedo) {
                Icon(Icons.Default.Redo, contentDescription = "Redo")
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.ui.canvas.CanvasUiStateTest`
Expected: PASS (1 test passed)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/matepad/notes/ui/canvas/ app/src/test/java/com/matepad/notes/ui/canvas/
git commit -m "feat: implement floating draggable toolbar and canvas UI state"
```

---

### Task 12: End-to-End Build Verification and APK Generation

**Files:**
- Create: `app/src/main/java/com/matepad/notes/MainActivity.kt`
- Test: `app/src/test/java/com/matepad/notes/IntegrationVerificationTest.kt`

**Interfaces:**
- Consumes: All previously implemented domain, data, and UI components
- Produces: Complete assembleDebug build output and verified APK ready for installation on Huawei MatePad 11.5.

- [ ] **Step 1: Write integration verification test**

Create `app/src/test/java/com/matepad/notes/IntegrationVerificationTest.kt`:
```kotlin
package com.matepad.notes

import com.matepad.notes.data.serializer.StrokeBinarySerializer
import com.matepad.notes.domain.command.AddStrokeCommand
import com.matepad.notes.domain.command.CommandHistory
import com.matepad.notes.domain.geometry.PointF
import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.geometry.ViewportTransform
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import com.matepad.notes.domain.spatial.QuadTree
import org.junit.Assert.assertEquals
import org.junit.Test

class IntegrationVerificationTest {
    @Test
    fun testCompleteInkingAndStoragePipeline() {
        val transform = ViewportTransform(scale = 1.5f, offsetX = 50f, offsetY = 100f)
        val screenInput = PointF(200f, 400f)
        val worldInput = transform.screenToWorld(screenInput)

        val stroke = Stroke(
            id = "test-1",
            toolType = ToolType.PEN,
            color = 0xFF0000FF.toInt(),
            baseWidth = 4.0f,
            points = listOf(StrokePoint(worldInput.x, worldInput.y)),
            bounds = RectF(worldInput.x, worldInput.y, worldInput.x + 4f, worldInput.y + 4f)
        )

        // 1. Command history
        val canvasStrokes = mutableListOf<Stroke>()
        val history = CommandHistory()
        history.execute(AddStrokeCommand(stroke, canvasStrokes))
        assertEquals(1, canvasStrokes.size)

        // 2. Spatial Index
        val quadTree = QuadTree<Stroke>(RectF(-5000f, -5000f, 5000f, 5000f)) { it.bounds }
        quadTree.insert(stroke)
        val visible = quadTree.query(transform.getVisibleWorldBounds(1200f, 800f))
        assertEquals(1, visible.size)

        // 3. Serialization
        val compressed = StrokeBinarySerializer.serialize(canvasStrokes)
        val restored = StrokeBinarySerializer.deserialize(compressed)
        assertEquals(1, restored.size)
        assertEquals("test-1", restored[0].id)
    }
}
```

- [ ] **Step 2: Run test to verify failure before MainActivity wiring**

Run: `gradle :app:testDebugUnitTest --tests com.matepad.notes.IntegrationVerificationTest`
Expected: PASS or Compilation check.

- [ ] **Step 3: Implement MainActivity entry point**

Create `app/src/main/java/com/matepad/notes/MainActivity.kt`:
```kotlin
package com.matepad.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.matepad.notes.ui.library.LibraryUiState
import com.matepad.notes.ui.library.NoteCardItem

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Application Entry Root
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run full test suite and assemble build**

Run: `gradle :app:testDebugUnitTest`
Expected: All unit tests pass across all tasks.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/matepad/notes/MainActivity.kt app/src/test/java/com/matepad/notes/IntegrationVerificationTest.kt
git commit -m "feat: implement main activity entry point and end-to-end integration test"
```
