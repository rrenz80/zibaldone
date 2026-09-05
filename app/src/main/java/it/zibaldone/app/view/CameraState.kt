package it.zibaldone.app.view

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import it.zibaldone.app.util.CanvasMath

/**
 * Board camera: pan (screen pixels) + zoom factor.
 *
 * All values are snapshot `FloatState`s. Reading them inside
 * `Modifier.graphicsLayer { }` or `Modifier.offset { }` blocks is tracked
 * by the LAYOUT system (verified: the block form applies through
 * `placeWithLayer`, which runs inside the node/layout snapshot scope), so a
 * camera change re-applies the transform without re-running composition.
 *
 * Consequences for smoothness:
 *  - the strokes Canvas (inside the camera layer) is NEVER re-recorded
 *    during pan/zoom,
 *  - node overlays relayout only (their body reads `camera.zoom` in their
 *    own composable scope, so only they recompose, not the whole screen).
 */
class CameraState {
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)
    var zoom by mutableFloatStateOf(1f)

    fun screenToWorld(screen: Offset): Offset = CanvasMath.screenToWorld(screen, Offset(panX, panY), zoom)

    fun worldToScreen(world: Offset): Offset = CanvasMath.worldToScreen(world, Offset(panX, panY), zoom)

    /** One pan step: pointer delta in screen pixels. */
    fun panBy(deltaPx: Offset) {
        panX += deltaPx.x
        panY += deltaPx.y
    }

    /**
     * One pinch step: the inter-finger distance went [sizePrev] -> [sizeNew]
     * while the centroid moved [centroidPrev] -> [centroid] (screen pixels).
     *
     * The zoom is ANCHORED on the pinch centroid: the world point under the
     * fingers stays under the fingers, so elements never "fly away" when
     * zooming out (the classic board jank that made elements disappear).
     */
    fun pinch(
        centroidPrev: Offset?,
        centroid: Offset,
        sizePrev: Float?,
        sizeNew: Float
    ) {
        if (centroid == Offset.Unspecified || sizeNew <= 0f) return
        val previous = if (sizePrev != null && sizePrev > 0f) sizePrev else null
        val ratio = if (previous != null) sizeNew / previous else 1f
        val oldZoom = zoom
        val newZoom = (oldZoom * ratio).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val k = newZoom / oldZoom
        val c = centroid
        val c0 = centroidPrev ?: c
        // Anchor the world point that was under the PREVIOUS centroid so it
        // lands under the new one — that single law covers zoom and centroid
        // translation at once:
        //   w = (c0 - oldPan) / oldZoom   and   c = w * newZoom + newPan
        //   => newPan = c - (c0 - oldPan) * k
        // The earlier form, c - (c - oldPan) * k + (c - c0), added the
        // centroid delta on top of an already-translating anchor and drifted
        // by (c - c0) * (1 - k) per frame whenever the fingers moved while
        // zooming (it was exact only at k == 1, i.e. pure pan).
        val newPanX = c.x - (c0.x - panX) * k
        val newPanY = c.y - (c0.y - panY) * k
        zoom = newZoom
        panX = newPanX
        panY = newPanY
    }

    fun reset() {
        panX = 0f
        panY = 0f
        zoom = 1f
    }

    companion object {
        const val MIN_ZOOM = 0.1f
        const val MAX_ZOOM = 10f
    }
}
