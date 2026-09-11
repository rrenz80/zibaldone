package it.zibaldone.app.view

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.zibaldone.app.R
import it.zibaldone.app.model.BoardElement
import it.zibaldone.app.model.BoardId
import it.zibaldone.app.model.SerializablePoint
import it.zibaldone.app.util.BoardManifest
import it.zibaldone.app.util.CanvasMath
import it.zibaldone.app.util.ExportImportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The three board tools.
 *  - [SELECT]: tap to select, one finger moves elements, two fingers the camera.
 *  - [PEN]: one finger draws a stroke, two fingers the camera.
 *  - [ERASER]: one finger erases strokes, two fingers the camera.
 */
enum class BoardTool { SELECT, PEN, ERASER }

/**
 * Holds every board state and every board operation:
 * the camera (pan/zoom), the active tool, selection + text editing,
 * stroke drawing/erasing and the portable .zib export/import system.
 */
class BoardViewModel : ViewModel() {
    /** All board elements, ordered by z-index (the last element is on top). */
    val elements = mutableStateListOf<BoardElement>()

    /**
     * Board camera. Float snapshot states so pan/zoom invalidate layout
     * (the graphicsLayer transform + node positions) without recomposing
     * the screen or re-recording the strokes canvas.
     */
    val camera = CameraState()

    // ------------------------------------------------------------------
    // Tool + geometry (sizes expressed in screen dp; converted to world
    // units with the current zoom at gesture time)
    // ------------------------------------------------------------------

    var tool by mutableStateOf(BoardTool.SELECT)
    var penWidthDp by mutableStateOf(6f) // slider range 2f..24f
    var eraserRadiusDp by mutableStateOf(28f) // slider range 12f..64f

    // ------------------------------------------------------------------
    // Selection + text editing
    // ------------------------------------------------------------------

    /** The selected board element, or null. */
    var selectedId by mutableStateOf<Long?>(null)

    /** The text node currently being edited (inline field), or null. */
    var editingTextId by mutableStateOf<Long?>(null)

    /**
     * The image node in ROTATION mode (double tap on a photo), or null.
     * While set, that node shows curved rotation arrows at its corners
     * instead of the resize handles, so the two gestures never share a
     * grab area.
     */
    var rotatingId by mutableStateOf<Long?>(null)

    /** Screen-px position of the eraser preview ring while erasing. */
    var eraserPreview by mutableStateOf<Offset?>(null)

    // ------------------------------------------------------------------
    // Active pen stroke
    // ------------------------------------------------------------------

    /** The in-progress stroke (non-null while drawing with the PEN tool). */
    var activeStroke by mutableStateOf<BoardElement.Drawing?>(null)

    /**
     * Bumped on every [updateStroke]; the live canvas reads it in its own
     * composable scope so ONLY that small canvas recomposes per point
     * (the committed strokes canvas stays untouched).
     */
    var liveStrokeVersion by mutableIntStateOf(0)

    /** Double-tap detection for entering text edit mode. */
    private var lastTapId: Long? = null
    private var lastTapAtMs = 0L

    // ==================================================================
    // Camera
    // ==================================================================

    fun resetView() {
        camera.reset()
    }

    fun panByPx(delta: Offset) {
        camera.panBy(delta)
    }

    /**
     * CENTRA: fit the whole board (strokes + notes + images) into the screen
     * with a margin. Uses the same world->screen law as everything else
     * (origin 0,0), so it always lines up with the strokes and the nodes.
     */
    fun fitToContent(
        widthPx: Float,
        heightPx: Float,
        densityPx: Float
    ) {
        if (elements.isEmpty()) {
            resetView()
            return
        }
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (el in elements) {
            when (el) {
                is BoardElement.Drawing ->
                    el.points.forEach { p ->
                        val r = el.strokeWidth / 2f
                        if (p.x - r < minX) minX = p.x - r
                        if (p.x + r > maxX) maxX = p.x + r
                        if (p.y - r < minY) minY = p.y - r
                        if (p.y + r > maxY) maxY = p.y + r
                    }

                is BoardElement.TextNode -> {
                    val w = 220f * densityPx // fixed 220dp note width
                    if (el.position.x < minX) minX = el.position.x
                    if (el.position.x + w > maxX) maxX = el.position.x + w
                    if (el.position.y < minY) minY = el.position.y
                    val h = el.fontSize * 6f * densityPx
                    if (el.position.y + h > maxY) maxY = el.position.y + h
                }

                is BoardElement.ImageNode -> {
                    // `position` is the TOP-LEFT corner, exactly as
                    // ImageNodeOverlay draws it and HitTesting.nodeRect reads
                    // it — treating it as the centre (position +/- half) put
                    // this box half an image off and made CENTRA misframe any
                    // board holding photos.
                    val side = 200f * el.scale * densityPx // base 200dp * scale
                    if (el.position.x < minX) minX = el.position.x
                    if (el.position.x + side > maxX) maxX = el.position.x + side
                    if (el.position.y < minY) minY = el.position.y
                    if (el.position.y + side > maxY) maxY = el.position.y + side
                }
            }
        }
        if (!minX.isFinite()) {
            resetView()
            return
        }
        val pad = 40f
        val boxW = (maxX - minX) + pad * 2f
        val boxH = (maxY - minY) + pad * 2f
        val newZoom =
            minOf(widthPx / boxW, heightPx / boxH)
                .coerceIn(CameraState.MIN_ZOOM, CameraState.MAX_ZOOM)
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        camera.zoom = newZoom
        camera.panX = widthPx / 2f - cx * newZoom
        camera.panY = heightPx / 2f - cy * newZoom
    }

    fun pinch(
        centroidPrev: Offset?,
        centroid: Offset,
        sizePrev: Float?,
        sizeNew: Float
    ) {
        camera.pinch(centroidPrev, centroid, sizePrev, sizeNew)
    }

    fun screenToWorld(screen: Offset): Offset = camera.screenToWorld(screen)

    fun worldToScreen(world: Offset): Offset = camera.worldToScreen(world)

    // ==================================================================
    // Pen
    // ==================================================================

    /** Starts a new stroke; [worldWidthPx] is the stroke width in world units. */
    fun newStroke(
        worldPoint: Offset,
        worldWidthPx: Float
    ) {
        activeStroke =
            BoardElement.Drawing(
                points = mutableListOf(SerializablePoint(worldPoint.x, worldPoint.y)),
                color = 0xFF000000.toInt(),
                strokeWidth = worldWidthPx.coerceIn(1f, 200f)
            )
        bumpLiveVersion()
    }

    fun updateStroke(worldPoint: Offset) {
        val s = activeStroke ?: return
        (s.points as? MutableList<SerializablePoint>)
            ?.add(SerializablePoint(worldPoint.x, worldPoint.y))
        bumpLiveVersion()
    }

    /** Discards the in-progress stroke (e.g. when the user goes into the camera). */
    fun cancelStroke() {
        if (activeStroke != null) activeStroke = null
    }

    /** Commits the in-progress stroke if it captured a meaningful path. */
    fun finalizeDrawing() {
        val s = activeStroke
        activeStroke = null
        if (s != null && s.points.size >= 2) {
            elements.add(
                BoardElement.Drawing(
                    id = s.id,
                    points = s.points.toList(),
                    color = s.color,
                    strokeWidth = s.strokeWidth
                )
            )
        }
    }

    private fun bumpLiveVersion() {
        liveStrokeVersion = liveStrokeVersion + 1
    }

    // ==================================================================
    // Eraser
    // ==================================================================

    /**
     * GOMMA PARTIALE: removes only the part of each touched stroke inside
     * the eraser disk; the surviving pieces stay on the board as new
     * strokes (standard eraser behaviour, not full-stroke deletion).
     */
    fun eraseAt(
        worldPoint: Offset,
        worldRadiusPx: Float
    ) {
        val radiusPx = worldRadiusPx.coerceAtLeast(2f)
        // Walk backwards and splice in place: rebuilding the whole list with
        // clear() + addAll() on every motion event invalidated every stroke
        // node on the board for each frame of an eraser drag.
        for (index in elements.indices.reversed()) {
            val el = elements[index] as? BoardElement.Drawing ?: continue
            val grab = radiusPx + el.strokeWidth / 2f
            // Measured against the segments, not the recorded vertices: with
            // points spaced far apart the eraser used to slip between them.
            if (!CanvasMath.polylineContains(el.toOffsetList(), worldPoint, grab)) continue

            val segments =
                CanvasMath.trimStroke(
                    points = el.toOffsetList(),
                    pos = worldPoint,
                    radiusPx = grab
                )
            val pieces =
                segments.mapIndexed { segmentIndex, segment ->
                    BoardElement.Drawing(
                        // The first surviving piece keeps the original id, so
                        // an in-progress selection is not dropped and the id
                        // churn stays proportional to what actually split.
                        id = if (segmentIndex == 0) el.id else BoardId.next(),
                        points = segment.map { SerializablePoint(it.x, it.y) },
                        color = el.color,
                        strokeWidth = el.strokeWidth
                    )
                }
            elements.removeAt(index)
            if (pieces.isEmpty()) {
                if (selectedId == el.id) selectedId = null
            } else {
                elements.addAll(index, pieces)
            }
        }
    }

    // ==================================================================
    // Selection + text editing
    // ==================================================================

    /**
     * Selects [id] (or null to deselect). Selecting a different element —
     * or the empty board — closes any open text editor.
     */
    fun select(id: Long?) {
        selectedId = id
        if (editingTextId != null && editingTextId != id) {
            editingTextId = null
        }
        // Rotation mode belongs to one node: touching anything else leaves it.
        if (rotatingId != null && rotatingId != id) {
            rotatingId = null
        }
    }

    fun startEditingText(id: Long) {
        selectedId = id
        editingTextId = id
        rotatingId = null
    }

    fun stopEditingText() {
        editingTextId = null
    }

    /** Enters rotation mode on a photo (double tap). */
    fun startRotating(id: Long) {
        selectedId = id
        editingTextId = null
        rotatingId = id
    }

    fun stopRotating() {
        rotatingId = null
    }

    /** True if [id] was tapped twice within [DOUBLE_TAP_MS]. */
    fun isDoubleTap(id: Long): Boolean {
        val now = SystemClock.elapsedRealtime()
        return if (lastTapId == id && now - lastTapAtMs <= DOUBLE_TAP_MS) {
            lastTapId = null
            lastTapAtMs = 0L
            true
        } else {
            lastTapId = id
            lastTapAtMs = now
            false
        }
    }

    // ==================================================================
    // Element operations
    // ==================================================================

    fun addText(
        text: String,
        worldPosition: Offset
    ) {
        val id = System.currentTimeMillis()
        elements.add(
            BoardElement.TextNode(
                id = id,
                text = text,
                position = SerializablePoint(worldPosition.x, worldPosition.y)
            )
        )
        selectedId = id
        editingTextId = null
    }

    /**
     * Imports a reference image from a content URI: the file is copied into
     * the private app storage and an image node is added where [screenCenter]
     * currently projects in world space.
     */
    fun addImageFromUri(
        context: Context,
        uri: Uri,
        screenCenter: Offset
    ) {
        viewModelScope.launch {
            runCatching {
                // Copying the picked file is disk work; viewModelScope runs on
                // Main, so without the IO hop this stalled the UI thread for
                // the whole copy.
                val destination =
                    withContext(Dispatchers.IO) {
                        val mediaDir = File(context.filesDir, "media")
                        mediaDir.mkdirs()
                        val file = File(mediaDir, "img_${BoardId.next()}.jpg")
                        context.contentResolver.openInputStream(uri)
                            ?.use { input -> file.outputStream().use { input.copyTo(it) } }
                            ?: throw IllegalStateException("Could not open image stream for: $uri")
                        file
                    }

                val worldCenter = camera.screenToWorld(screenCenter)
                val node =
                    BoardElement.ImageNode(
                        imageUri = destination.absolutePath,
                        position = SerializablePoint(worldCenter.x, worldCenter.y)
                    )
                elements.add(node)
                selectedId = node.id
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_image_import_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun updateText(
        id: Long,
        newText: String,
        newSize: Float
    ) {
        elements.replaceAll { element ->
            if (element is BoardElement.TextNode && element.id == id) {
                element.copy(text = newText, fontSize = newSize)
            } else {
                element
            }
        }
    }

    /**
     * Sets the font size of a text node, clamped to practical bounds.
     * Absolute rather than incremental so a handle drag is driven by the size
     * at grab time and never accumulates drift over the gesture.
     */
    fun setTextSize(
        id: Long,
        newSize: Float
    ) {
        val size = newSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        elements.replaceAll { element ->
            if (element is BoardElement.TextNode && element.id == id) {
                element.copy(fontSize = size)
            } else {
                element
            }
        }
    }

    /**
     * Moves any board element (text, image or stroke) by a [worldDelta]
     * already divided by the current zoom.
     */
    fun translateElement(
        id: Long,
        worldDelta: Offset
    ) {
        val dx = worldDelta.x
        val dy = worldDelta.y
        elements.replaceAll { element ->
            when (element) {
                is BoardElement.Drawing ->
                    if (element.id == id) {
                        element.copy(
                            points =
                                element.points.map {
                                    SerializablePoint(it.x + dx, it.y + dy)
                                }
                        )
                    } else {
                        element
                    }
                is BoardElement.TextNode ->
                    if (element.id == id) {
                        element.copy(
                            position =
                                SerializablePoint(
                                    element.position.x + dx,
                                    element.position.y + dy
                                )
                        )
                    } else {
                        element
                    }
                is BoardElement.ImageNode ->
                    if (element.id == id) {
                        element.copy(
                            position =
                                SerializablePoint(
                                    element.position.x + dx,
                                    element.position.y + dy
                                )
                        )
                    } else {
                        element
                    }
            }
        }
    }

    /** Sets the scale of an image node (base node is 200 dp at zoom 1). */
    fun setImageScale(
        id: Long,
        newScale: Float
    ) {
        val s = newScale.coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)
        elements.replaceAll { element ->
            if (element is BoardElement.ImageNode && element.id == id) {
                element.copy(scale = s)
            } else {
                element
            }
        }
    }

    /**
     * Resizes an image node while keeping [anchorWorld] — the corner OPPOSITE
     * to the grabbed one — pinned in world space, which is what makes a corner
     * drag feel right: the corner you are not touching stays put.
     *
     * [anchorAtRight] / [anchorAtBottom] say which corner the anchor is, so
     * the top-left `position` can be recomputed from the new side length.
     */
    fun resizeImageAnchored(
        id: Long,
        newScale: Float,
        anchorWorld: Offset,
        anchorAtRight: Boolean,
        anchorAtBottom: Boolean,
        densityPx: Float
    ) {
        val s = newScale.coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)
        val side = IMAGE_BASE_DP * s * densityPx
        val x = if (anchorAtRight) anchorWorld.x - side else anchorWorld.x
        val y = if (anchorAtBottom) anchorWorld.y - side else anchorWorld.y
        elements.replaceAll { element ->
            if (element is BoardElement.ImageNode && element.id == id) {
                element.copy(scale = s, position = SerializablePoint(x, y))
            } else {
                element
            }
        }
    }

    /** Sets the rotation of an image node, in degrees. */
    fun setImageRotation(
        id: Long,
        degrees: Float
    ) {
        val normalized = ((degrees % 360f) + 360f) % 360f
        elements.replaceAll { element ->
            if (element is BoardElement.ImageNode && element.id == id) {
                element.copy(rotation = normalized)
            } else {
                element
            }
        }
    }

    fun removeElement(id: Long) {
        elements.removeAll { it.id == id }
        if (selectedId == id) selectedId = null
        if (editingTextId == id) editingTextId = null
        if (rotatingId == id) rotatingId = null
    }

    fun clearBoard() {
        cancelStroke()
        elements.clear()
        selectedId = null
        editingTextId = null
        rotatingId = null
        eraserPreview = null
    }

    // ==================================================================
    // .zib export / import
    // ==================================================================

    /**
     * Saves the current board as a .zib package at [outputUri].
     * Reference images are embedded in the package, so the board travels
     * together with its inspiration sources.
     */
    fun saveBoard(
        context: Context,
        outputUri: Uri
    ) {
        val imageNodes = elements.filterIsInstance<BoardElement.ImageNode>()
        val mediaSources =
            imageNodes.associate { node ->
                "media/" + File(node.imageUri).name to File(node.imageUri)
            }

        val manifestElements =
            elements.map { element ->
                if (element is BoardElement.ImageNode) {
                    element.copy(imageUri = "media/" + File(element.imageUri).name)
                } else {
                    element
                }
            }

        val manifest =
            BoardManifest(
                name = "board_${System.currentTimeMillis()}",
                panX = camera.panX,
                panY = camera.panY,
                zoom = camera.zoom,
                elements = manifestElements
            )

        viewModelScope.launch {
            runCatching {
                val saved = ExportImportManager.saveBoard(context, outputUri, manifest, mediaSources)
                val message =
                    if (saved.missingMedia > 0) {
                        // Never silent: the manifest still references these
                        // images, so the exported package is incomplete.
                        context.resources.getQuantityString(
                            R.plurals.toast_export_missing_media,
                            saved.missingMedia,
                            saved.missingMedia
                        )
                    } else {
                        context.getString(R.string.toast_export_ok)
                    }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_export_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Restores a board from a .zib package: reference images are
     * re-homed into app storage and the camera restores the saved view.
     */
    fun loadBoard(
        context: Context,
        inputUri: Uri
    ) {
        viewModelScope.launch {
            runCatching {
                val loaded = ExportImportManager.loadBoard(context, inputUri)

                elements.clear()
                for (element in loaded.manifest.elements) {
                    val restored =
                        when (element) {
                            is BoardElement.ImageNode ->
                                element.copy(
                                    imageUri =
                                        loaded.mediaFiles[element.imageUri]?.absolutePath
                                            ?: element.imageUri
                                )
                            else -> element
                        }
                    // Ids in the package were minted on another device (or
                    // another run); push the local counter past them so
                    // nothing created afterwards can collide.
                    BoardId.observe(restored.id)
                    elements.add(restored)
                }

                camera.panX = loaded.manifest.panX
                camera.panY = loaded.manifest.panY
                camera.zoom = loaded.manifest.zoom
                selectedId = null
                editingTextId = null
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_import_ok),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_import_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        /** Window for recognizing a double tap (text editor / rotation mode). */
        const val DOUBLE_TAP_MS = 400L

        /** Side of an image node at scale 1, in dp. */
        const val IMAGE_BASE_DP = 200f

        const val MIN_IMAGE_SCALE = 0.2f
        const val MAX_IMAGE_SCALE = 12f

        const val MIN_FONT_SIZE = 8f
        const val MAX_FONT_SIZE = 160f
    }
}
