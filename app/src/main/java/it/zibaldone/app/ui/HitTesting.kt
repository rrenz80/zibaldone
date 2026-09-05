package it.zibaldone.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import it.zibaldone.app.model.BoardElement
import it.zibaldone.app.util.CanvasMath
import it.zibaldone.app.view.CameraState
import kotlin.math.ceil

/** Euclidean distance between two points. */
fun Offset.distance(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return (dx * dx + dy * dy).let { kotlin.math.sqrt(it) }
}

/**
 * Which corner of a node a handle sits on. Resizing anchors the OPPOSITE
 * corner, so the corner the finger is not holding stays where it is.
 */
enum class HandleCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    val isRight: Boolean get() = this == TOP_RIGHT || this == BOTTOM_RIGHT

    val isBottom: Boolean get() = this == BOTTOM_LEFT || this == BOTTOM_RIGHT

    /** The corner diagonally across from this one. */
    fun opposite(): HandleCorner =
        when (this) {
            TOP_LEFT -> BOTTOM_RIGHT
            TOP_RIGHT -> BOTTOM_LEFT
            BOTTOM_LEFT -> TOP_RIGHT
            BOTTOM_RIGHT -> TOP_LEFT
        }
}

/**
 * Result of a board hit test. All positions are in SCREEN px.
 */
sealed class BoardHit {
    /** The pointer is on one of the four resize handles of the selection. */
    data class Handle(
        val elementId: Long,
        val corner: HandleCorner
    ) : BoardHit()

    /** The pointer is on a rotation arrow of the photo in rotation mode. */
    data class RotateHandle(val elementId: Long) : BoardHit()

    /** The pointer is on a board element (topmost wins). */
    data class Element(val elementId: Long) : BoardHit()

    /** Nothing under the pointer (empty board space). */
    object Empty : BoardHit()
}

/**
 * Manual hit testing for the board.
 *
 * The canvas layer (strokes) and the plain text nodes are not interactive
 * composables, so ALL board selection logic lives here: a single pointer
 * down is resolved against the resize handle of the selected element
 * (highest priority), then against the topmost element under the point.
 *
 * The screen-space size of a node mirrors what the overlays render:
 *  - TextNode : 220 dp (width) at zoom 1, height estimated from the text
 *  - ImageNode: 200 dp * scale at zoom 1
 */
object HitTesting {
    /** Touch-friendly radius (dp) of a resize handle. */
    const val HANDLE_RADIUS_DP = 28f

    /** Touch-friendly radius (dp) of a rotation arrow. */
    const val ROTATE_RADIUS_DP = 28f

    /** Extra grab margin (dp) around strokes. */
    const val TOUCH_SLOP_DP = 14f

    fun hit(
        screenPos: Offset,
        elements: List<BoardElement>,
        selectedId: Long?,
        rotatingId: Long?,
        camera: CameraState,
        densityPx: Float
    ): BoardHit {
        val selected = selectedId?.let { id -> elements.find { it.id == id } }

        // 1) The handles of the selected node have priority, so they stay
        //    grabbable even when another element overlaps them. A node in
        //    rotation mode shows arrows INSTEAD of resize handles, so the two
        //    never compete for the same corner.
        if (selected != null && selected !is BoardElement.Drawing) {
            val rect = nodeRect(selected, camera, densityPx)
            if (selected.id == rotatingId) {
                if (nearestCorner(screenPos, rect, ROTATE_RADIUS_DP * densityPx) != null) {
                    return BoardHit.RotateHandle(selected.id)
                }
            } else {
                val corner = nearestCorner(screenPos, rect, HANDLE_RADIUS_DP * densityPx)
                if (corner != null) {
                    return BoardHit.Handle(selected.id, corner)
                }
            }
        }

        // 2) Topmost element under the pointer (last in the list = on top).
        for (el in elements.asReversed()) {
            when (el) {
                is BoardElement.Drawing ->
                    if (strokeBbox(el, camera).contains(screenPos) &&
                        strokeContainsPoint(screenPos, el, camera, densityPx)
                    ) {
                        return BoardHit.Element(el.id)
                    }

                is BoardElement.TextNode,
                is BoardElement.ImageNode ->
                    if (nodeRect(el, camera, densityPx).contains(screenPos)) {
                        return BoardHit.Element(el.id)
                    }
            }
        }
        return BoardHit.Empty
    }

    /**
     * The corner of [rect] within [radiusPx] of [screenPos], or null.
     * Closest wins, so overlapping grab areas on a small node still resolve
     * to the corner the finger is actually nearest to.
     */
    fun nearestCorner(
        screenPos: Offset,
        rect: Rect,
        radiusPx: Float
    ): HandleCorner? {
        var best: HandleCorner? = null
        var bestDistance = radiusPx
        for (corner in HandleCorner.entries) {
            val distance = screenPos.distance(cornerOf(rect, corner))
            if (distance <= bestDistance) {
                bestDistance = distance
                best = corner
            }
        }
        return best
    }

    /** Screen-space position of one corner of [rect]. */
    fun cornerOf(
        rect: Rect,
        corner: HandleCorner
    ): Offset =
        when (corner) {
            HandleCorner.TOP_LEFT -> rect.topLeft
            HandleCorner.TOP_RIGHT -> rect.topRight
            HandleCorner.BOTTOM_LEFT -> rect.bottomLeft
            HandleCorner.BOTTOM_RIGHT -> rect.bottomRight
        }

    /** Screen-space rectangle of a text/image node (top-left = world position). */
    fun nodeRect(
        el: BoardElement,
        camera: CameraState,
        densityPx: Float
    ): Rect {
        return when (el) {
            is BoardElement.ImageNode -> {
                val tl = camera.worldToScreen(Offset(el.position.x, el.position.y))
                val s = 200f * el.scale * camera.zoom * densityPx
                Rect(tl.x, tl.y, tl.x + s, tl.y + s)
            }

            is BoardElement.TextNode -> {
                val tl = camera.worldToScreen(Offset(el.position.x, el.position.y))
                val w = 220f * camera.zoom * densityPx
                // Usable text width = 220dp minus the 8dp padding on each side.
                val charsPerLine = (204f / (el.fontSize * 0.5f)).coerceAtLeast(8f)
                val lines =
                    ceil(el.text.length.toFloat() / charsPerLine)
                        .toInt()
                        .coerceIn(1, 10)
                // Mirrors what TextNodeOverlay actually renders: 8dp top +
                // 2dp bottom padding, lineHeight = fontSize * 1.4. Every term
                // must carry densityPx — the line term used to omit it, so on
                // a 2x-3x density screen the rect ended far above the drawn
                // note: its lower half was not selectable and the resize
                // handle could not be grabbed where it is painted.
                val h =
                    (10f + lines * el.fontSize * 1.4f) * camera.zoom * densityPx
                Rect(tl.x, tl.y, tl.x + w, tl.y + h)
            }

            is BoardElement.Drawing -> Rect.Zero
        }
    }

    /** Screen-space bounding box of a stroke. */
    fun strokeBbox(
        el: BoardElement.Drawing,
        camera: CameraState
    ): Rect {
        if (el.points.isEmpty()) return Rect.Zero
        // NEGATIVE_INFINITY, not Float.MIN_VALUE: the latter is the smallest
        // POSITIVE float (1.4E-45), so a stroke drawn entirely at negative
        // world coordinates kept maxX/maxY at ~0 and produced a box stretching
        // all the way back to the world origin.
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in el.points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        val tl = camera.worldToScreen(Offset(minX, minY))
        val br = camera.worldToScreen(Offset(maxX, maxY))
        return Rect(tl.x, tl.y, br.x, br.y)
    }

    /**
     * True if the screen point falls on the stroke polyline (with touch slop).
     *
     * Measured against the SEGMENTS, not the recorded vertices: a fast finger
     * records points far apart, and a vertex-only test left the middle of
     * those segments unselectable.
     */
    fun strokeContainsPoint(
        screenPos: Offset,
        el: BoardElement.Drawing,
        camera: CameraState,
        densityPx: Float
    ): Boolean {
        val w = camera.screenToWorld(screenPos)
        val r = (TOUCH_SLOP_DP * densityPx) / camera.zoom + el.strokeWidth / 2f
        return CanvasMath.polylineContains(el.toOffsetList(), w, r)
    }
}
