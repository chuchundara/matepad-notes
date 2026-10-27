package com.matepad.notes.data.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.pdf.PdfDocument
import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import com.matepad.notes.domain.model.StrokePoint
import com.matepad.notes.domain.model.ToolType
import com.matepad.notes.ui.canvas.render.StrokeMeshGenerator
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * High-performance vector PDF exporter.
 *
 * Computes tight enclosing document bounds across all strokes, calculates page
 * dimensions with configurable margins, and formats vector paths into an Android
 * [PdfDocument] canvas to preserve infinite crispness at any print resolution.
 */
object PdfExporter {

    // Default A4 dimensions at 72 DPI (points)
    const val DEFAULT_A4_WIDTH = 595f
    const val DEFAULT_A4_HEIGHT = 842f

    /**
     * Calculated page dimensions for PDF document generation.
     */
    data class PageDimensions(
        val width: Int,
        val height: Int,
        val contentBounds: RectF,
        val margin: Float
    )

    /**
     * Determines whether the given [toolType] produces visible strokes that should
     * be rendered in exported documents. Erasers and lasso selections are omitted.
     */
    fun isDrawableTool(toolType: ToolType): Boolean {
        return when (toolType) {
            ToolType.PEN, ToolType.HIGHLIGHTER -> true
            ToolType.STROKE_ERASER, ToolType.SEGMENT_ERASER, ToolType.LASSO -> false
        }
    }

    /**
     * Computes the axis-aligned enclosing bounds across all [strokes].
     *
     * @param strokes The list of strokes to enclose.
     * @param filterDrawableOnly If true, filters out non-drawable strokes (erasers, lasso).
     * @return Axis-aligned [RectF] bounding box, or default A4 bounds if empty.
     */
    fun calculateEnclosingBounds(
        strokes: List<Stroke>,
        filterDrawableOnly: Boolean = false
    ): RectF {
        val candidates = if (filterDrawableOnly) {
            strokes.filter { isDrawableTool(it.toolType) }
        } else {
            strokes
        }

        if (candidates.isEmpty()) {
            return RectF(0f, 0f, DEFAULT_A4_WIDTH, DEFAULT_A4_HEIGHT)
        }

        var minX = candidates[0].bounds.left
        var minY = candidates[0].bounds.top
        var maxX = candidates[0].bounds.right
        var maxY = candidates[0].bounds.bottom

        for (i in 1 until candidates.size) {
            val b = candidates[i].bounds
            minX = min(minX, b.left)
            minY = min(minY, b.top)
            maxX = max(maxX, b.right)
            maxY = max(maxY, b.bottom)
        }

        return RectF(minX, minY, maxX, maxY)
    }

    /**
     * Calculates PDF page dimensions in points (72 DPI) enclosing the given [bounds]
     * padded by [margin] and constrained to [minWidth] and [minHeight].
     */
    fun calculatePageDimensions(
        bounds: RectF,
        margin: Float = 20f,
        minWidth: Float = 100f,
        minHeight: Float = 100f
    ): PageDimensions {
        val rawWidth = max(0f, bounds.width) + 2f * margin
        val rawHeight = max(0f, bounds.height) + 2f * margin
        val width = max(minWidth, rawWidth).roundToInt().coerceAtLeast(1)
        val height = max(minHeight, rawHeight).roundToInt().coerceAtLeast(1)
        return PageDimensions(
            width = width,
            height = height,
            contentBounds = bounds,
            margin = margin
        )
    }

    /**
     * Computes dynamic stroke width based on average point pressure,
     * maintaining parity with on-screen canvas rendering.
     */
    fun calculateDynamicStrokeWidth(baseWidth: Float, points: List<StrokePoint>): Float {
        if (points.isEmpty()) return baseWidth
        var sum = 0f
        for (p in points) {
            sum += p.pressure
        }
        val avgPressure = sum / points.size
        return baseWidth * (0.3f + 1.4f * avgPressure)
    }

    /**
     * Draws a single stroke onto the provided [canvas] using vector path interpolation.
     */
    fun drawSingleStroke(
        canvas: Canvas,
        stroke: Stroke,
        path: Path = Path(),
        paint: Paint = Paint()
    ) {
        if (stroke.points.isEmpty()) return

        paint.reset()
        paint.isAntiAlias = true

        val dynamicWidth = calculateDynamicStrokeWidth(stroke.baseWidth, stroke.points)

        when (stroke.toolType) {
            ToolType.HIGHLIGHTER -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.SQUARE
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeWidth = dynamicWidth * 2.5f
                val alphaColor = (stroke.color and 0x00FFFFFF) or 0x66000000
                paint.color = alphaColor
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
            }
            else -> {
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeWidth = dynamicWidth
                paint.color = stroke.color
                paint.xfermode = null
            }
        }

        if (stroke.points.size == 1) {
            val p = stroke.points[0]
            val radius = dynamicWidth / 2f
            paint.style = Paint.Style.FILL
            canvas.drawCircle(p.x, p.y, radius, paint)
            return
        }

        StrokeMeshGenerator.buildPath(stroke.points, path)
        canvas.drawPath(path, paint)
    }

    /**
     * Renders vector strokes onto a [canvas] transformed to fit within [bounds] with [margin].
     * Renders highlighters first to keep opaque pen ink on top.
     */
    fun drawStrokesToCanvas(
        canvas: Canvas,
        strokes: List<Stroke>,
        bounds: RectF = calculateEnclosingBounds(strokes),
        margin: Float = 20f,
        scale: Float = 1.0f,
        backgroundColor: Int? = Color.WHITE
    ) {
        if (backgroundColor != null) {
            canvas.drawColor(backgroundColor)
        }

        canvas.save()
        if (scale != 1.0f) {
            canvas.scale(scale, scale)
        }
        canvas.translate(-bounds.left + margin, -bounds.top + margin)

        val drawableStrokes = strokes.filter { isDrawableTool(it.toolType) }
        val path = Path()
        val paint = Paint()

        // 1. Highlighters rendered first so pen ink stays on top
        for (stroke in drawableStrokes) {
            if (stroke.toolType == ToolType.HIGHLIGHTER) {
                drawSingleStroke(canvas, stroke, path, paint)
            }
        }

        // 2. Regular pen strokes
        for (stroke in drawableStrokes) {
            if (stroke.toolType != ToolType.HIGHLIGHTER) {
                drawSingleStroke(canvas, stroke, path, paint)
            }
        }

        canvas.restore()
    }

    /**
     * Exports [strokes] into a clean vector PDF document written to [outputStream].
     */
    fun exportToPdf(
        strokes: List<Stroke>,
        outputStream: OutputStream,
        margin: Float = 20f,
        backgroundColor: Int = Color.WHITE
    ) {
        val bounds = calculateEnclosingBounds(strokes, filterDrawableOnly = true)
        val pageDims = calculatePageDimensions(bounds, margin = margin)

        val document = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(pageDims.width, pageDims.height, 1).create()
            val page = document.startPage(pageInfo)

            drawStrokesToCanvas(
                canvas = page.canvas,
                strokes = strokes,
                bounds = bounds,
                margin = margin,
                scale = 1.0f,
                backgroundColor = backgroundColor
            )

            document.finishPage(page)
            document.writeTo(outputStream)
        } finally {
            document.close()
        }
    }

    /**
     * Convenience method to export [strokes] into a vector PDF file at [destination].
     */
    fun exportToPdf(
        strokes: List<Stroke>,
        destination: File,
        margin: Float = 20f,
        backgroundColor: Int = Color.WHITE
    ) {
        destination.parentFile?.mkdirs()
        destination.outputStream().use { out ->
            exportToPdf(
                strokes = strokes,
                outputStream = out,
                margin = margin,
                backgroundColor = backgroundColor
            )
        }
    }
}
