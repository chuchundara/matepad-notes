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
                if (version != VERSION) {
                    throw IllegalArgumentException("Unsupported .note version: $version")
                }
                val count = input.readInt()
                require(count >= 0) { "Stroke count cannot be negative: $count" }

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
                    require(pointCount >= 0) { "Point count cannot be negative: $pointCount" }
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
                            toolType = ToolType.entries.getOrElse(toolOrdinal) { ToolType.PEN },
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
