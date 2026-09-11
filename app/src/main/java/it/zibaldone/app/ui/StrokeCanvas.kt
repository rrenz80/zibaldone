package it.zibaldone.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import it.zibaldone.app.R
import it.zibaldone.app.model.BoardElement
import it.zibaldone.app.view.BoardViewModel
import it.zibaldone.app.view.CameraState
import kotlin.math.roundToInt

/** Accent color shared by selection frames, handles and the eraser ring. */
val SelectionBlue = Color(0xFF2979FF)

/**
 * All board strokes (committed + the live one), one per board element.
 *
 * ARCHITECTURE (v1.4 — "tratto come nodo"):
 * Each stroke is rendered by its own [StrokeNode] composable: a small
 * Box positioned + sized with pure LAYOUT modifiers (`offset` + `size`),
 * whose camera values are read in the composable BODY — exactly the same
 * mechanism as [TextNodeOverlay] and [ImageNodeOverlay], which have been
 * verified on device to track pan/zoom perfectly.
 *
 * The stroke's own Canvas is drawn in LOCAL (stroke-relative)
 * coordinates: the draw closure therefore contains NO camera state at
 * all. On this device every camera-applied drawing mechanism (draw-scope
 * transforms inside the closure, and shared graphicsLayer) failed to
 * track pan/zoom, while layout offset/size tracking is rock solid — so
 * the camera enters each stroke exactly once, through layout, like the
 * notes and photos.
 */
@Composable
fun StrokesCanvas(viewModel: BoardViewModel) {
    val elements = viewModel.elements
    val activeStroke = viewModel.activeStroke
    viewModel.liveStrokeVersion
    val camera = viewModel.camera
    val densityPx = LocalDensity.current.density

    val strokes = ArrayList<BoardElement.Drawing>(elements.size)
    for (element in elements) {
        val d = element as? BoardElement.Drawing ?: continue
        if (d.points.size >= 2) strokes.add(d)
    }
    val live = activeStroke
    if (live != null && live.points.size >= 2) strokes.add(live)

    for (stroke in strokes) {
        StrokeNode(stroke = stroke, camera = camera, densityPx = densityPx)
    }
}

/**
 * A single stroke node. The box is placed in SCREEN space with layout
 * modifiers (proven on device); the canvas inside is stroke-local.
 */
@Composable
private fun StrokeNode(
    stroke: BoardElement.Drawing,
    camera: CameraState,
    densityPx: Float
) {
    val pts = stroke.points
    if (pts.size < 2) return

    // Bounding box in WORLD units (+ half stroke width, for round caps).
    val r = stroke.strokeWidth / 2f
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (p in pts) {
        if (p.x - r < minX) minX = p.x - r
        if (p.y - r < minY) minY = p.y - r
        if (p.x + r > maxX) maxX = p.x + r
        if (p.y + r > maxY) maxY = p.y + r
    }

    // Camera in BODY scope (tracked, like the nodes): the box lands in
    // screen space. The CONTENT points are transformed HERE as well
    // (body scope) into local canvas coordinates, so the draw closure
    // only iterates over precomputed plain data — no camera state inside.
    val zoom = camera.zoom
    val tl = camera.worldToScreen(Offset(minX, minY))
    val br = camera.worldToScreen(Offset(maxX, maxY))
    val widthPx = (br.x - tl.x).coerceAtLeast(1f)
    val heightPx = (br.y - tl.y).coerceAtLeast(1f)
    val strokeColor = Color(stroke.color)
    val strokeW = (stroke.strokeWidth * zoom).coerceAtLeast(1f)
    val localPts = ArrayList<Offset>(pts.size)
    for (p in pts) localPts.add(Offset((p.x - minX) * zoom, (p.y - minY) * zoom))
    // Guard: content can never exceed the box by more than half a stroke
    // width, and the box is sized from the inflated bounds, so localPts
    // always fits inside [0..widthPx] x [0..heightPx].
    Box(
        modifier =
            Modifier
                // roundToInt, not toInt: truncation biases toward zero, so a
                // node crossing the origin jittered by 1px asymmetrically.
                .offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }
                .size(width = (widthPx / densityPx).dp, height = (heightPx / densityPx).dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (localPts.size == 2) {
                drawLine(
                    color = strokeColor,
                    start = localPts[0],
                    end = localPts[1],
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            } else {
                val path =
                    Path().apply {
                        val first = localPts[0]
                        moveTo(first.x, first.y)
                        for (i in 1 until localPts.size) {
                            val lp = localPts[i]
                            lineTo(lp.x, lp.y)
                        }
                    }
                drawPath(
                    path = path,
                    color = strokeColor,
                    style =
                        Stroke(
                            width = strokeW,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                )
            }
        }
    }
}

/**
 * The eraser preview ring, drawn in SCREEN space at the finger position
 * with the chosen (screen) radius.
 */
@Composable
fun EraserPreview(viewModel: BoardViewModel) {
    val center = viewModel.eraserPreview ?: return
    val radiusPx = with(LocalDensity.current) { viewModel.eraserRadiusDp.dp.toPx() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = Color(0x22000000.toInt()),
            radius = radiusPx,
            center = center
        )
        drawCircle(
            color = SelectionBlue,
            radius = radiusPx,
            center = center,
            style = Stroke(width = 2f)
        )
    }
}

/**
 * Selection frame around a selected stroke (screen-space bounding box).
 */
@Composable
fun StrokeSelectionBox(
    element: BoardElement.Drawing,
    camera: CameraState,
    density: Density
) {
    if (element.points.isEmpty()) return
    // NEGATIVE_INFINITY, not Float.MIN_VALUE (which is the smallest POSITIVE
    // float): with the old seed a stroke drawn entirely at negative world
    // coordinates left maxX/maxY at ~0, and the selection frame visibly
    // stretched from the stroke back to the world origin.
    var minX = Float.POSITIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    for (p in element.points) {
        if (p.x < minX) minX = p.x
        if (p.y < minY) minY = p.y
        if (p.x > maxX) maxX = p.x
        if (p.y > maxY) maxY = p.y
    }
    val tl = camera.worldToScreen(Offset(minX, minY))
    val br = camera.worldToScreen(Offset(maxX, maxY))
    val width = with(density) { (br.x - tl.x).coerceAtLeast(1f).toDp() }
    val height = with(density) { (br.y - tl.y).coerceAtLeast(1f).toDp() }
    Box(
        modifier =
            Modifier
                .offset { IntOffset(tl.x.roundToInt(), tl.y.roundToInt()) }
                .size(width = width, height = height)
                .border(2.dp, SelectionBlue, RoundedCornerShape(4.dp))
    )
}

/** Visual diameter of a corner handle (the grab area is larger). */
private val HandleSize = 20.dp

/**
 * The four resize handles of a selected node, one per corner.
 *
 * Each is centred exactly ON its corner (hence the half-size outward
 * offset), because that corner is what HitTesting measures against — a
 * handle drawn inside the box would sit away from its own grab area.
 * Grab radius is [HitTesting.HANDLE_RADIUS_DP], comfortably larger than
 * what is painted.
 */
@Composable
fun BoxScope.SelectionHandles() {
    val half = HandleSize / 2
    CornerDot(Alignment.TopStart, x = -half, y = -half)
    CornerDot(Alignment.TopEnd, x = half, y = -half)
    CornerDot(Alignment.BottomStart, x = -half, y = half)
    CornerDot(Alignment.BottomEnd, x = half, y = half)
}

@Composable
private fun BoxScope.CornerDot(
    alignment: Alignment,
    x: Dp,
    y: Dp
) {
    Box(
        modifier =
            Modifier
                .align(alignment)
                .offset(x = x, y = y)
                .size(HandleSize)
                .background(Color.White, CircleShape)
                .border(3.dp, SelectionBlue, CircleShape)
    )
}

/**
 * The four curved rotation arrows shown on a photo in rotation mode
 * (double tap). They replace the resize handles so a corner never means
 * two things at once; dragging any of them spins the photo about its centre.
 */
@Composable
fun BoxScope.RotationHandles() {
    val half = RotateSize / 2
    RotateArrow(Alignment.TopStart, x = -half, y = -half)
    RotateArrow(Alignment.TopEnd, x = half, y = -half)
    RotateArrow(Alignment.BottomStart, x = -half, y = half)
    RotateArrow(Alignment.BottomEnd, x = half, y = half)
}

private val RotateSize = 30.dp

@Composable
private fun BoxScope.RotateArrow(
    alignment: Alignment,
    x: Dp,
    y: Dp
) {
    Box(
        modifier =
            Modifier
                .align(alignment)
                .offset(x = x, y = y)
                .size(RotateSize)
                .background(SelectionBlue, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.RotateRight,
            contentDescription = stringResource(R.string.cd_rotate),
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}
