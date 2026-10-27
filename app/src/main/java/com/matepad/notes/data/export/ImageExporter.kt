package com.matepad.notes.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import com.matepad.notes.domain.geometry.RectF
import com.matepad.notes.domain.model.Stroke
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * High-performance raster image exporter.
 *
 * Renders vector strokes onto high-resolution [Bitmap]s at arbitrary scale factors
 * (up to 4K ultra HD) with configurable backgrounds (solid or transparent) and
 * compresses output to PNG, JPEG, or WebP for sharing and note thumbnail preview generation.
 */
object ImageExporter {

    /**
     * Supported image export formats with associated file extensions and MIME types.
     */
    enum class ExportFormat(val extension: String, val mimeType: String) {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg"),
        WEBP("webp", "image/webp")
    }

    /**
     * Calculated raster image dimensions including effective scaling factor.
     */
    data class Dimensions(
        val width: Int,
        val height: Int,
        val scale: Float,
        val contentBounds: RectF,
        val margin: Float
    )

    /**
     * Calculates pixel dimensions and effective scale for rendering strokes.
     *
     * @param bounds The world-space bounding box enclosing the content.
     * @param scale Desired base scale multiplier (e.g., 2.0 for 2x super-sampling).
     * @param margin Padding margin around content in world coordinates.
     * @param maxDimension Optional maximum pixel ceiling (e.g., 3840 for 4K, 512 for previews).
     * @param minDimension Minimum pixel constraint (at least 1px).
     * @return [Dimensions] with calculated pixel width, height, and effective scale factor.
     */
    fun calculateDimensions(
        bounds: RectF,
        scale: Float = 1.0f,
        margin: Float = 20f,
        maxDimension: Int? = 4096,
        minDimension: Int = 1
    ): Dimensions {
        val safeMargin = max(0f, margin)
        val rawWidth = max(0f, bounds.width) + 2f * safeMargin
        val rawHeight = max(0f, bounds.height) + 2f * safeMargin

        val effectiveBaseWidth = if (rawWidth <= 0f) minDimension.toFloat() else rawWidth
        val effectiveBaseHeight = if (rawHeight <= 0f) minDimension.toFloat() else rawHeight

        var scaledWidth = effectiveBaseWidth * scale
        var scaledHeight = effectiveBaseHeight * scale
        var effectiveScale = scale

        if (maxDimension != null && maxDimension > 0) {
            val largestSide = max(scaledWidth, scaledHeight)
            if (largestSide > maxDimension) {
                val downscaleFactor = maxDimension.toFloat() / largestSide
                effectiveScale = scale * downscaleFactor
                scaledWidth *= downscaleFactor
                scaledHeight *= downscaleFactor
            }
        }

        val finalWidth = max(minDimension, scaledWidth.roundToInt())
        val finalHeight = max(minDimension, scaledHeight.roundToInt())

        return Dimensions(
            width = finalWidth,
            height = finalHeight,
            scale = effectiveScale,
            contentBounds = bounds,
            margin = safeMargin
        )
    }

    /**
     * Renders [strokes] onto a newly allocated [Bitmap].
     *
     * @param strokes The list of strokes to render.
     * @param bounds Explicit bounds to export, or null to auto-compute enclosing bounds.
     * @param scale Scale factor for high-resolution output.
     * @param margin Padding margin around content.
     * @param backgroundColor Background color (e.g., Color.WHITE), or null for transparent PNG.
     * @param maxDimension Optional max pixel constraint for downscaling.
     */
    fun renderToBitmap(
        strokes: List<Stroke>,
        bounds: RectF? = null,
        scale: Float = 1.0f,
        margin: Float = 20f,
        backgroundColor: Int? = Color.WHITE,
        maxDimension: Int? = 4096
    ): Bitmap {
        val effectiveBounds = bounds ?: PdfExporter.calculateEnclosingBounds(strokes, filterDrawableOnly = true)
        val dims = calculateDimensions(
            bounds = effectiveBounds,
            scale = scale,
            margin = margin,
            maxDimension = maxDimension
        )

        val bitmap = Bitmap.createBitmap(dims.width, dims.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        PdfExporter.drawStrokesToCanvas(
            canvas = canvas,
            strokes = strokes,
            bounds = effectiveBounds,
            margin = margin,
            scale = dims.scale,
            backgroundColor = backgroundColor
        )

        return bitmap
    }

    /**
     * Maps [ExportFormat] to the appropriate Android [Bitmap.CompressFormat].
     */
    fun toCompressFormat(format: ExportFormat): Bitmap.CompressFormat {
        return when (format) {
            ExportFormat.PNG -> Bitmap.CompressFormat.PNG
            ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ExportFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }
    }

    /**
     * Renders [strokes] and compresses the output image directly into [outputStream].
     */
    fun exportToStream(
        strokes: List<Stroke>,
        outputStream: OutputStream,
        format: ExportFormat = ExportFormat.PNG,
        quality: Int = 100,
        bounds: RectF? = null,
        scale: Float = 1.0f,
        margin: Float = 20f,
        backgroundColor: Int? = Color.WHITE,
        maxDimension: Int? = 4096
    ) {
        val bitmap = renderToBitmap(
            strokes = strokes,
            bounds = bounds,
            scale = scale,
            margin = margin,
            backgroundColor = backgroundColor,
            maxDimension = maxDimension
        )
        try {
            val compressFormat = toCompressFormat(format)
            val clampedQuality = quality.coerceIn(0, 100)
            val success = bitmap.compress(compressFormat, clampedQuality, outputStream)
            if (!success) {
                throw IOException("Failed to compress bitmap into format: $format")
            }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Renders [strokes] and writes the compressed image file to [destination].
     */
    fun exportToFile(
        strokes: List<Stroke>,
        destination: File,
        format: ExportFormat = ExportFormat.PNG,
        quality: Int = 100,
        bounds: RectF? = null,
        scale: Float = 1.0f,
        margin: Float = 20f,
        backgroundColor: Int? = Color.WHITE,
        maxDimension: Int? = 4096
    ) {
        destination.parentFile?.mkdirs()
        destination.outputStream().use { out ->
            exportToStream(
                strokes = strokes,
                outputStream = out,
                format = format,
                quality = quality,
                bounds = bounds,
                scale = scale,
                margin = margin,
                backgroundColor = backgroundColor,
                maxDimension = maxDimension
            )
        }
    }

    /**
     * Convenience method to render a note thumbnail preview [Bitmap] (default max 512px).
     */
    fun renderPreview(
        strokes: List<Stroke>,
        maxDimension: Int = 512,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        return renderToBitmap(
            strokes = strokes,
            scale = 1.0f,
            margin = 20f,
            backgroundColor = backgroundColor,
            maxDimension = maxDimension
        )
    }

    /**
     * Convenience method to export a note thumbnail preview WebP file (default max 512px).
     */
    fun exportPreviewToFile(
        strokes: List<Stroke>,
        destination: File,
        maxDimension: Int = 512,
        backgroundColor: Int = Color.WHITE,
        quality: Int = 85
    ) {
        exportToFile(
            strokes = strokes,
            destination = destination,
            format = ExportFormat.WEBP,
            quality = quality,
            scale = 1.0f,
            margin = 20f,
            backgroundColor = backgroundColor,
            maxDimension = maxDimension
        )
    }
}
