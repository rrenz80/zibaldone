package it.zibaldone.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import it.zibaldone.app.model.BoardElement
import it.zibaldone.app.view.BoardTool
import it.zibaldone.app.view.BoardViewModel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * The gesture the current single-finger touch has been classified as.
 */
private enum class Gesture { PEN, ERASE, MOVE, HANDLE, ROTATE, CAMERA }

/** Rotation snaps to a multiple of this when it lands close enough. */
private const val ROTATION_SNAP_DEG = 90f

/** How near a snap angle the rotation must be, in degrees, to stick to it. */
private const val ROTATION_SNAP_TOLERANCE_DEG = 4f

/** Movement in screen px required before a selected element/handle starts moving. */
private const val MOVE_SLOP_PX = 12f

/** Angle of a vector in degrees, measured like the rotation of a node. */
private fun angleOf(v: Offset): Float = Math.toDegrees(atan2(v.y.toDouble(), v.x.toDouble())).toFloat()

/**
 * Snaps [degrees] to the nearest quarter turn when it is within
 * [ROTATION_SNAP_TOLERANCE_DEG], which makes straightening a photo easy
 * without fighting the finger elsewhere on the dial.
 */
private fun snapAngle(degrees: Float): Float {
    val normalized = ((degrees % 360f) + 360f) % 360f
    val nearest = (normalized / ROTATION_SNAP_DEG).roundToInt() * ROTATION_SNAP_DEG
    return if (abs(normalized - nearest) <= ROTATION_SNAP_TOLERANCE_DEG) nearest else normalized
}

/**
 * The single dispatcher for every board touch.
 *
 * With ONE finger (tool dependent):
 *  - PEN    -> draw a new stroke;
 *  - ERASER -> partially erase the strokes under the finger;
 *  - SELECT -> tap = select, drag = move (after a small slop),
 *              drag over empty space = pan, drag on the corner handle = resize,
 *              double tap on a text note = open the inline editor.
 *
 * With TWO or more fingers, in ANY tool: the camera — anchored pinch zoom
 * plus pan — takes over immediately (a live stroke is cancelled).
 */
suspend fun PointerInputScope.boardDispatch(
    vm: BoardViewModel,
    densityPx: Float
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false).position
        val cam = vm.camera
        val worldDown = cam.screenToWorld(down)

        var mode = Gesture.CAMERA
        var targetId: Long? = null
        var handleStartScale = 1f
        var handleStartFontSize = 20f
        // The corner held FIXED while resizing, in world units, plus which
        // corner of the node it is (so the top-left can be recomputed).
        var anchorWorld = worldDown
        var anchorAtRight = false
        var anchorAtBottom = false
        var handleStartDist = 1f
        var rotateCenter = down
        var rotateStartAngle = 0f
        var rotateStartRotation = 0f
        var lastPointer = down
        var movedPx = 0f
        var pinchCentroid: Offset? = null
        var pinchSize: Float? = null

        when (vm.tool) {
            BoardTool.PEN -> {
                mode = Gesture.PEN
                vm.newStroke(
                    worldPoint = worldDown,
                    worldWidthPx = vm.penWidthDp * densityPx / cam.zoom
                )
            }

            BoardTool.ERASER -> {
                mode = Gesture.ERASE
                vm.eraseAt(
                    worldPoint = worldDown,
                    worldRadiusPx = vm.eraserRadiusDp * densityPx / cam.zoom
                )
                vm.eraserPreview = down
            }

            BoardTool.SELECT -> {
                val hit =
                    HitTesting.hit(
                        screenPos = down,
                        elements = vm.elements,
                        selectedId = vm.selectedId,
                        rotatingId = vm.rotatingId,
                        camera = cam,
                        densityPx = densityPx
                    )
                when (hit) {
                    is BoardHit.Handle -> {
                        mode = Gesture.HANDLE
                        targetId = hit.elementId
                        val el = vm.elements.find { it.id == hit.elementId }
                        if (el is BoardElement.ImageNode) handleStartScale = el.scale
                        if (el is BoardElement.TextNode) handleStartFontSize = el.fontSize
                        val rect = el?.let { HitTesting.nodeRect(it, cam, densityPx) }
                        // Anchor on the OPPOSITE corner. Anchoring on the
                        // grabbed corner (as before) made the starting
                        // distance ~0, so the very first pixel of movement
                        // multiplied the scale into its clamp.
                        val opposite = hit.corner.opposite()
                        anchorAtRight = opposite.isRight
                        anchorAtBottom = opposite.isBottom
                        val anchorScreen =
                            rect?.let { HitTesting.cornerOf(it, opposite) } ?: down
                        anchorWorld = cam.screenToWorld(anchorScreen)
                        handleStartDist = down.distance(anchorScreen).coerceAtLeast(1f)
                    }

                    is BoardHit.RotateHandle -> {
                        mode = Gesture.ROTATE
                        targetId = hit.elementId
                        val el = vm.elements.find { it.id == hit.elementId }
                        val rect = el?.let { HitTesting.nodeRect(it, cam, densityPx) }
                        rotateCenter = rect?.center ?: down
                        rotateStartAngle = angleOf(down - rotateCenter)
                        rotateStartRotation =
                            (el as? BoardElement.ImageNode)?.rotation ?: 0f
                    }

                    is BoardHit.Element -> {
                        mode = Gesture.MOVE
                        targetId = hit.elementId
                        vm.select(hit.elementId)
                    }

                    BoardHit.Empty -> {
                        mode = Gesture.CAMERA
                        vm.select(null)
                    }
                }
            }
        }

        // ------------------------------------------------------------------
        // Event loop until every pointer is up
        // ------------------------------------------------------------------
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }

            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            // Two or more pointers: always the camera (anchored pinch).
            if (pressed.size >= 2) {
                if (mode == Gesture.PEN) vm.cancelStroke()
                if (mode == Gesture.ERASE) vm.eraserPreview = null
                // A second finger during a resize/rotate hands the gesture to
                // the camera; the edit made so far is simply kept.
                if (mode != Gesture.CAMERA) {
                    mode = Gesture.CAMERA
                    pinchCentroid = null
                    pinchSize = null
                    lastPointer = pressed[0].position
                }
                val centroid = event.calculateCentroid()
                val size = event.calculateCentroidSize()
                if (centroid != Offset.Unspecified) {
                    lastPointer = centroid
                } else if (pressed.isNotEmpty()) {
                    lastPointer = pressed[0].position
                }
                if (centroid != Offset.Unspecified && size > 0f) {
                    val prevCentroid = pinchCentroid
                    val prevSize = pinchSize
                    if (prevCentroid != null && prevSize != null && prevSize > 0f) {
                        vm.pinch(prevCentroid, centroid, prevSize, size)
                    }
                    pinchCentroid = centroid
                    pinchSize = size
                }
                continue
            }

            // Exactly one pointer: tool-specific motion.
            val p = pressed[0].position
            movedPx += p.distance(lastPointer)

            when (mode) {
                Gesture.PEN ->
                    vm.updateStroke(cam.screenToWorld(p))

                Gesture.ERASE -> {
                    vm.eraseAt(
                        worldPoint = cam.screenToWorld(p),
                        worldRadiusPx = vm.eraserRadiusDp * densityPx / cam.zoom
                    )
                    vm.eraserPreview = p
                }

                Gesture.MOVE -> {
                    // SLOP: a light touch only selects; the element starts
                    // moving only after MOVE_SLOP_PX of accumulated movement,
                    // so a stray light touch never drags it away.
                    if (targetId != null && movedPx >= MOVE_SLOP_PX) {
                        val id = targetId
                        val dx = (p.x - lastPointer.x) / cam.zoom
                        val dy = (p.y - lastPointer.y) / cam.zoom
                        vm.translateElement(id, Offset(dx, dy))
                    }
                }

                Gesture.HANDLE -> {
                    if (targetId != null && movedPx >= MOVE_SLOP_PX) {
                        val id = targetId
                        // Ratio of the current finger-to-anchor distance over
                        // the one at grab time: pulling away from the anchored
                        // corner grows the node, pushing toward it shrinks it,
                        // and it behaves the same from any of the four corners.
                        val anchorScreen = cam.worldToScreen(anchorWorld)
                        val ratio =
                            p.distance(anchorScreen).coerceAtLeast(1f) / handleStartDist
                        when (vm.elements.find { it.id == id }) {
                            is BoardElement.TextNode ->
                                // A note keeps its 220dp width and its top-left
                                // corner; only the type scales.
                                vm.setTextSize(id, handleStartFontSize * ratio)

                            is BoardElement.ImageNode ->
                                vm.resizeImageAnchored(
                                    id = id,
                                    newScale = handleStartScale * ratio,
                                    anchorWorld = anchorWorld,
                                    anchorAtRight = anchorAtRight,
                                    anchorAtBottom = anchorAtBottom,
                                    densityPx = densityPx
                                )

                            else -> Unit
                        }
                    }
                }

                Gesture.ROTATE -> {
                    if (targetId != null) {
                        val id = targetId
                        val swept = angleOf(p - rotateCenter) - rotateStartAngle
                        vm.setImageRotation(id, snapAngle(rotateStartRotation + swept))
                    }
                }

                Gesture.CAMERA ->
                    vm.panByPx(Offset(p.x - lastPointer.x, p.y - lastPointer.y))
            }

            lastPointer = p
        }

        // ------------------------------------------------------------------
        // Gesture ended: finalize
        // ------------------------------------------------------------------
        when (mode) {
            Gesture.PEN -> vm.finalizeDrawing()

            Gesture.ERASE -> vm.eraserPreview = null

            Gesture.MOVE -> {
                // Two quick taps with almost no movement: a note opens its
                // inline editor, a photo enters rotation mode.
                if (movedPx < 12f) {
                    val id = targetId
                    if (id != null && vm.isDoubleTap(id)) {
                        when (vm.elements.find { it.id == id }) {
                            is BoardElement.TextNode -> vm.startEditingText(id)
                            is BoardElement.ImageNode -> vm.startRotating(id)
                            else -> Unit
                        }
                    }
                }
            }

            Gesture.HANDLE, Gesture.ROTATE, Gesture.CAMERA -> Unit
        }
    }
}
