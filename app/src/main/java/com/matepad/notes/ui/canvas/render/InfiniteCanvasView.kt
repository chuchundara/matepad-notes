package com.matepad.notes.ui.canvas.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceView
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer
import com.matepad.notes.domain.geometry.PointF
import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.geometry.ViewportTransform
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import com.matepad.notes.domain.spatial.QuadTree
import com.matepad.notes.ui.canvas.input.CanvasGestureListener
import com.matepad.notes.ui.canvas.input.InputEvent
import com.matepad.notes.ui.canvas.input.StylusInputClassifier
import java.util.UUID
import kotlin.math.floor
import kotlin.math.max

/**
 * High-performance infinite canvas view supporting:
 * 1. Ultra-low latency in-progress inking via [CanvasFrontBufferedRenderer] directly under the stylus tip (< 8ms).
 * 2. Multi-buffered 120 FPS rendering for committed strokes with viewport culling via [QuadTree].
 * 3. Infinite pan and zoom with dynamic coordinate transformation via [ViewportTransform].
 * 4. Hardware palm rejection and stylus gesture routing via [CanvasGestureListener].
 */
class InfiniteCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr) {

    enum class GridStyle {
        NONE,
        DOTS,
        LINES,
        SQUARES
    }

    /**
     * Payload passed to front-buffered renderer representing active inking increments.
     */
    data class InProgressSegment(
        val points: List<StrokePoint>,
        val toolType: ToolType,
        val color: Int,
        val baseWidth: Float
    )

    // Viewport and spatial state
    var viewportTransform: ViewportTransform = ViewportTransform()
    val strokes: MutableList<Stroke> = mutableListOf()
    val quadTree: QuadTree<Stroke> = createQuadTree()

    // Current active tool settings
    var currentTool: ToolType = ToolType.PEN
    var currentColor: Int = Color.BLACK
    var currentBaseWidth: Float = 4.0f
    var canvasBackgroundColor: Int = Color.WHITE

    // Background grid settings
    var gridStyle: GridStyle = GridStyle.DOTS
    var gridSpacing: Float = 40.0f
    var gridColor: Int = 0x1A000000 // 10% opacity black

    // Listeners and callbacks
    var onStrokeCompleted: ((Stroke) -> Unit)? = null
    var onViewportChanged: ((ViewportTransform) -> Unit)? = null
    var onToolChanged: ((ToolType) -> Unit)? = null
    var onStrokeErased: ((Stroke) -> Unit)? = null

    // Touch and gesture classification pipeline
    val gestureListener: CanvasGestureListener = CanvasGestureListener(
        classifier = StylusInputClassifier(),
        onInputEvent = { event -> handleInputEvent(event) }
    )

    // In-progress stroke accumulation
    private val activeStrokePoints = mutableListOf<StrokePoint>()

    // Reusable paint and path instances for allocation-free 120 FPS rendering
    private val frontBufferPaint = Paint().apply { isAntiAlias = true }
    private val strokePaint = Paint().apply { isAntiAlias = true }
    private val gridPaint = Paint().apply { isAntiAlias = true }
    private val cachedPath = Path()

    // Front-buffered renderer callback
    private val frontBufferedCallback = object : CanvasFrontBufferedRenderer.Callback<InProgressSegment> {
        override fun onDrawFrontBufferedLayer(
            canvas: Canvas,
            bufferWidth: Int,
            bufferHeight: Int,
            param: InProgressSegment
        ) {
            renderFrontBufferedStrokeSegment(canvas, param)
        }

        override fun onDrawMultiBufferedLayer(
            canvas: Canvas,
            bufferWidth: Int,
            bufferHeight: Int,
            params: Collection<InProgressSegment>
        ) {
            renderMultiBufferedScene(canvas, bufferWidth, bufferHeight)
        }
    }

    private var frontBufferedRenderer: CanvasFrontBufferedRenderer<InProgressSegment>? = null

    init {
        setWillNotDraw(false)
        initRenderer()
    }

    private fun initRenderer() {
        try {
            frontBufferedRenderer = CanvasFrontBufferedRenderer(this, frontBufferedCallback)
        } catch (_: Throwable) {
            // Graceful fallback when running in testing or non-SurfaceControl environments
            frontBufferedRenderer = null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (frontBufferedRenderer == null) {
            initRenderer()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            frontBufferedRenderer?.release(cancelPending = true)
        } catch (_: Throwable) {
            // Ignore release exceptions during teardown
        }
        frontBufferedRenderer = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestureListener.onTouchEvent(event)
        return handled || super.onTouchEvent(event)
    }

    /**
     * Routes classified gestures and inking events to their respective subsystems.
     */
    private fun handleInputEvent(event: InputEvent) {
        when (event) {
            is InputEvent.Draw -> handleDrawEvent(event)
            is InputEvent.Erase -> handleEraseEvent(event)
            is InputEvent.Gesture -> handleGestureEvent(event)
            is InputEvent.ToggleEraser -> toggleEraserTool()
        }
    }

    private fun handleDrawEvent(event: InputEvent.Draw) {
        val worldPoint = viewportTransform.screenToWorld(PointF(event.point.x, event.point.y))
        val strokePoint = StrokePoint(worldPoint.x, worldPoint.y, event.point.pressure, event.point.tilt)

        when {
            event.isStart -> {
                activeStrokePoints.clear()
                activeStrokePoints.add(strokePoint)
                frontBufferedRenderer?.renderFrontBufferedLayer(
                    InProgressSegment(
                        points = listOf(strokePoint),
                        toolType = currentTool,
                        color = currentColor,
                        baseWidth = currentBaseWidth
                    )
                )
            }
            event.isEnd -> {
                activeStrokePoints.add(strokePoint)
                if (activeStrokePoints.isNotEmpty()) {
                    val bounds = StrokeMeshGenerator.computeBounds(activeStrokePoints, currentBaseWidth)
                    val stroke = Stroke(
                        id = UUID.randomUUID().toString(),
                        toolType = currentTool,
                        color = currentColor,
                        baseWidth = currentBaseWidth,
                        points = activeStrokePoints.toList(),
                        bounds = bounds
                    )
                    addStroke(stroke)
                    onStrokeCompleted?.invoke(stroke)
                }
                activeStrokePoints.clear()
                frontBufferedRenderer?.commit()
            }
            else -> {
                val prevPoint = activeStrokePoints.lastOrNull()
                activeStrokePoints.add(strokePoint)
                if (prevPoint != null) {
                    frontBufferedRenderer?.renderFrontBufferedLayer(
                        InProgressSegment(
                            points = listOf(prevPoint, strokePoint),
                            toolType = currentTool,
                            color = currentColor,
                            baseWidth = currentBaseWidth
                        )
                    )
                }
            }
        }
    }

    private fun handleEraseEvent(event: InputEvent.Erase) {
        val worldPoint = viewportTransform.screenToWorld(PointF(event.point.x, event.point.y))
        eraseAt(worldPoint)
    }

    private fun handleGestureEvent(event: InputEvent.Gesture) {
        val prev = viewportTransform
        val newScale = (prev.scale * event.scaleDelta).coerceIn(
            ViewportTransform.MIN_SCALE,
            ViewportTransform.MAX_SCALE
        )
        viewportTransform = prev.panBy(event.panX, event.panY).withScale(newScale)
        onViewportChanged?.invoke(viewportTransform)
        redraw()
    }

    private fun toggleEraserTool() {
        currentTool = if (currentTool == ToolType.STROKE_ERASER || currentTool == ToolType.SEGMENT_ERASER) {
            ToolType.PEN
        } else {
            ToolType.STROKE_ERASER
        }
        onToolChanged?.invoke(currentTool)
    }

    /**
     * Renders incremental in-progress segments directly onto the low-latency front buffer.
     */
    private fun renderFrontBufferedStrokeSegment(canvas: Canvas, segment: InProgressSegment) {
        if (segment.points.isEmpty()) return

        canvas.save()
        canvas.translate(viewportTransform.offsetX, viewportTransform.offsetY)
        canvas.scale(viewportTransform.scale, viewportTransform.scale)

        frontBufferPaint.reset()
        frontBufferPaint.isAntiAlias = true

        when (segment.toolType) {
            ToolType.HIGHLIGHTER -> {
                frontBufferPaint.style = Paint.Style.STROKE
                frontBufferPaint.strokeCap = Paint.Cap.SQUARE
                frontBufferPaint.strokeJoin = Paint.Join.ROUND
                frontBufferPaint.strokeWidth = segment.baseWidth * 2.5f
                val alphaColor = (segment.color and 0x00FFFFFF) or 0x66000000
                frontBufferPaint.color = alphaColor
                frontBufferPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            }
            else -> {
                frontBufferPaint.style = Paint.Style.STROKE
                frontBufferPaint.strokeCap = Paint.Cap.ROUND
                frontBufferPaint.strokeJoin = Paint.Join.ROUND
                frontBufferPaint.strokeWidth = segment.baseWidth
                frontBufferPaint.color = segment.color
                frontBufferPaint.xfermode = null
            }
        }

        if (segment.points.size == 1) {
            val p = segment.points[0]
            val radius = (segment.baseWidth * (0.3f + 1.4f * p.pressure)) / 2f
            frontBufferPaint.style = Paint.Style.FILL
            canvas.drawCircle(p.x, p.y, radius, frontBufferPaint)
        } else {
            val p0 = segment.points[segment.points.size - 2]
            val p1 = segment.points[segment.points.size - 1]
            val avgPressure = (p0.pressure + p1.pressure) / 2f
            frontBufferPaint.strokeWidth = segment.baseWidth * (0.3f + 1.4f * avgPressure)
            canvas.drawLine(p0.x, p0.y, p1.x, p1.y, frontBufferPaint)
        }

        canvas.restore()
    }

    /**
     * Renders the complete multi-buffered frame, querying visible strokes from [QuadTree].
     */
    private fun renderMultiBufferedScene(canvas: Canvas, bufferWidth: Int, bufferHeight: Int) {
        canvas.drawColor(canvasBackgroundColor)

        canvas.save()
        canvas.translate(viewportTransform.offsetX, viewportTransform.offsetY)
        canvas.scale(viewportTransform.scale, viewportTransform.scale)

        val visibleBounds = viewportTransform.getVisibleWorldBounds(
            bufferWidth.toFloat(),
            bufferHeight.toFloat()
        )

        drawGrid(canvas, visibleBounds)

        // Viewport culling query
        val visibleStrokes = quadTree.query(visibleBounds)

        // Highlighters rendered first so ink stays on top
        for (stroke in visibleStrokes) {
            if (stroke.toolType == ToolType.HIGHLIGHTER) {
                drawCommittedStroke(canvas, stroke)
            }
        }

        // Regular pens and other strokes
        for (stroke in visibleStrokes) {
            if (stroke.toolType != ToolType.HIGHLIGHTER) {
                drawCommittedStroke(canvas, stroke)
            }
        }

        canvas.restore()
    }

    private fun drawCommittedStroke(canvas: Canvas, stroke: Stroke) {
        if (stroke.points.isEmpty()) return

        strokePaint.reset()
        strokePaint.isAntiAlias = true

        when (stroke.toolType) {
            ToolType.HIGHLIGHTER -> {
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeCap = Paint.Cap.SQUARE
                strokePaint.strokeJoin = Paint.Join.ROUND
                strokePaint.strokeWidth = stroke.baseWidth * 2.5f
                val alphaColor = (stroke.color and 0x00FFFFFF) or 0x66000000
                strokePaint.color = alphaColor
                strokePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            }
            else -> {
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeCap = Paint.Cap.ROUND
                strokePaint.strokeJoin = Paint.Join.ROUND
                strokePaint.strokeWidth = stroke.baseWidth
                strokePaint.color = stroke.color
                strokePaint.xfermode = null
            }
        }

        if (stroke.points.size == 1) {
            val p = stroke.points[0]
            val radius = stroke.baseWidth / 2f
            strokePaint.style = Paint.Style.FILL
            canvas.drawCircle(p.x, p.y, radius, strokePaint)
            return
        }

        StrokeMeshGenerator.buildPath(stroke.points, cachedPath)
        canvas.drawPath(cachedPath, strokePaint)
    }

    private fun drawGrid(canvas: Canvas, bounds: RectF) {
        if (gridStyle == GridStyle.NONE) return

        val effectiveScale = viewportTransform.scale
        var step = gridSpacing
        while (step * effectiveScale < 16f) {
            step *= 2f
        }

        gridPaint.reset()
        gridPaint.isAntiAlias = true
        gridPaint.color = gridColor

        val startX = floor(bounds.left / step) * step
        val endX = bounds.right
        val startY = floor(bounds.top / step) * step
        val endY = bounds.bottom

        when (gridStyle) {
            GridStyle.DOTS -> {
                gridPaint.style = Paint.Style.FILL
                val dotRadius = max(1.0f, 1.5f / effectiveScale)
                var x = startX
                while (x <= endX) {
                    var y = startY
                    while (y <= endY) {
                        canvas.drawCircle(x, y, dotRadius, gridPaint)
                        y += step
                    }
                    x += step
                }
            }
            GridStyle.LINES -> {
                gridPaint.style = Paint.Style.STROKE
                gridPaint.strokeWidth = 1.0f / effectiveScale
                var y = startY
                while (y <= endY) {
                    canvas.drawLine(bounds.left, y, bounds.right, y, gridPaint)
                    y += step
                }
            }
            GridStyle.SQUARES -> {
                gridPaint.style = Paint.Style.STROKE
                gridPaint.strokeWidth = 1.0f / effectiveScale
                var x = startX
                while (x <= endX) {
                    canvas.drawLine(x, bounds.top, x, bounds.bottom, gridPaint)
                    x += step
                }
                var y = startY
                while (y <= endY) {
                    canvas.drawLine(bounds.left, y, bounds.right, y, gridPaint)
                    y += step
                }
            }
            GridStyle.NONE -> {}
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderMultiBufferedScene(canvas, width, height)
    }

    // Public Canvas API

    fun addStroke(stroke: Stroke, updateQuadTree: Boolean = true) {
        strokes.add(stroke)
        if (updateQuadTree) {
            quadTree.insert(stroke)
        }
        redraw()
    }

    fun removeStroke(stroke: Stroke) {
        strokes.remove(stroke)
        rebuildQuadTree()
        redraw()
    }

    fun setStrokes(newStrokes: List<Stroke>) {
        strokes.clear()
        strokes.addAll(newStrokes)
        rebuildQuadTree()
        redraw()
    }

    fun clearCanvas() {
        strokes.clear()
        quadTree.clear()
        redraw()
    }

    fun setViewport(transform: ViewportTransform) {
        viewportTransform = transform
        onViewportChanged?.invoke(viewportTransform)
        redraw()
    }

    fun eraseAt(worldPoint: PointF, eraseRadius: Float = currentBaseWidth * 3f) {
        val eraseBounds = RectF(
            worldPoint.x - eraseRadius,
            worldPoint.y - eraseRadius,
            worldPoint.x + eraseRadius,
            worldPoint.y + eraseRadius
        )
        val candidates = quadTree.query(eraseBounds)
        val toRemove = candidates.filter { stroke ->
            stroke.bounds.intersects(eraseBounds) && stroke.points.any { p ->
                val dx = p.x - worldPoint.x
                val dy = p.y - worldPoint.y
                (dx * dx + dy * dy) <= (eraseRadius * eraseRadius)
            }
        }

        if (toRemove.isNotEmpty()) {
            for (stroke in toRemove) {
                removeStroke(stroke)
                onStrokeErased?.invoke(stroke)
            }
        }
    }

    fun redraw() {
        frontBufferedRenderer?.commit() ?: postInvalidate()
    }

    private fun rebuildQuadTree() {
        quadTree.clear()
        for (s in strokes) {
            quadTree.insert(s)
        }
    }

    private fun createQuadTree(): QuadTree<Stroke> {
        return QuadTree(
            boundary = RectF(-500000f, -500000f, 500000f, 500000f),
            maxObjects = 8,
            maxLevels = 8,
            boundsExtractor = { it.bounds }
        )
    }
}
