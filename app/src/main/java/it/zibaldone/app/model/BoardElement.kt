package it.zibaldone.app.model

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A 2D point in world coordinates.
 * Serializable so board elements can be saved to the .zib manifest.
 */
@Serializable
data class SerializablePoint(
    val x: Float = 0f,
    val y: Float = 0f
) {
    fun toOffset(): Offset = Offset(x, y)
}

/**
 * Sealed hierarchy of every element that can be placed on the board.
 * The hierarchy is fully serializable and powers the .zib export format,
 * which makes boards portable between devices running the app.
 */
@Serializable
sealed class BoardElement {
    abstract val id: Long
    abstract val kind: String

    /**
     * A freehand stroke captured with an Android stylus or a finger.
     * [points] are stored in world coordinates.
     */
    @Serializable
    @SerialName("drawing")
    data class Drawing(
        override val id: Long = BoardId.next(),
        val points: List<SerializablePoint> = emptyList(),
        val color: Int = 0xFF000000.toInt(),
        val strokeWidth: Float = 4f
    ) : BoardElement() {
        override val kind: String = "drawing"

        fun toOffsetList(): List<Offset> = points.map { it.toOffset() }
    }

    /**
     * A text note. Can be moved with the drag strip and resized
     * with the scaling anchor. [fontSize] is the base font size in sp;
     * it keeps its relative proportions when the board is zoomed.
     */
    @Serializable
    @SerialName("text")
    data class TextNode(
        override val id: Long = BoardId.next(),
        val text: String,
        val position: SerializablePoint = SerializablePoint(),
        val fontSize: Float = 20f,
        val color: Int = 0xFF000000.toInt()
    ) : BoardElement() {
        override val kind: String = "text"

        fun positionToOffset(): Offset = position.toOffset()
    }

    /**
     * A reference image used as an inspiration source.
     * [imageUri] is either an absolute path in app storage (on a device)
     * or a path relative to the .zib package (e.g. "media/photo1.jpg").
     */
    @Serializable
    @SerialName("image")
    data class ImageNode(
        override val id: Long = BoardId.next(),
        val imageUri: String,
        val position: SerializablePoint = SerializablePoint(),
        val scale: Float = 1.0f,
        val rotation: Float = 0f
    ) : BoardElement() {
        override val kind: String = "image"

        fun positionToOffset(): Offset = position.toOffset()
    }
}
