package it.zibaldone.app.util

import androidx.compose.ui.geometry.Offset

/**
 * Coordinate math of the infinite canvas.
 *
 * The camera is described by a translation ([Offset], the `pan`)
 * and a uniform [zoom] factor, BOTH defined around the ORIGIN (0,0):
 *
 *   world -> screen :  S = W * zoom + pan
 *   screen -> world :  W = (S - pan) / zoom
 *
 * Every consumer of this math (the graphicsLayer of the camera, the
 * text/image node positioning, the hit testing) therefore renders the
 * camera box with `transformOrigin = TransformOrigin(0f, 0f)`.
 */
object CanvasMath {
    fun screenToWorld(
        screenPoint: Offset,
        pan: Offset,
        zoom: Float
    ): Offset =
        Offset(
            x = (screenPoint.x - pan.x) / zoom,
            y = (screenPoint.y - pan.y) / zoom
        )

    fun worldToScreen(
        worldPoint: Offset,
        pan: Offset,
        zoom: Float
    ): Offset =
        Offset(
            x = worldPoint.x * zoom + pan.x,
            y = worldPoint.y * zoom + pan.y
        )

    /**
     * Squared distance from [p] to the SEGMENT [a]-[b] (not to its
     * endpoints). Hit testing and erasing must measure against the drawn
     * line, not against the recorded vertices: a fast finger records points
     * tens of world units apart, and a vertex-only test makes the middle of
     * such a segment impossible to grab or erase.
     */
    fun distanceToSegmentSq(
        p: Offset,
        a: Offset,
        b: Offset
    ): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lenSq = abx * abx + aby * aby
        val t =
            if (lenSq <= 0f) {
                0f // degenerate segment: fall back to the distance from `a`
            } else {
                (((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq).coerceIn(0f, 1f)
            }
        val dx = p.x - (a.x + abx * t)
        val dy = p.y - (a.y + aby * t)
        return dx * dx + dy * dy
    }

    /** True if [p] is within [radiusPx] of the polyline [points]. */
    fun polylineContains(
        points: List<Offset>,
        p: Offset,
        radiusPx: Float
    ): Boolean {
        if (points.isEmpty()) return false
        val radiusSq = radiusPx * radiusPx
        if (points.size == 1) {
            val dx = p.x - points[0].x
            val dy = p.y - points[0].y
            return dx * dx + dy * dy <= radiusSq
        }
        for (i in 1 until points.size) {
            if (distanceToSegmentSq(p, points[i - 1], points[i]) <= radiusSq) return true
        }
        return false
    }

    /**
     * "Gomma (eraser)" trim: removes the part of the polyline that falls
     * inside the disk of center [pos] and [radiusPx] (both in world units)
     * and returns the surviving contiguous segments (each with >= 2 points).
     *
     * Segments are resampled every ~[SAMPLE_STEP_PX] so that long strokes
     * are trimmed smoothly instead of snapping to their recorded points.
     * Resampling starts at k = 1 so the shared endpoint between two source
     * segments is emitted exactly once: sampling from k = 0 duplicated every
     * interior vertex, doubling the point count on each eraser pass and
     * bloating the saved .zib.
     */
    fun trimStroke(
        points: List<Offset>,
        pos: Offset,
        radiusPx: Float
    ): List<List<Offset>> {
        if (points.size < 2) return emptyList()
        val radiusSq = radiusPx * radiusPx
        val result = mutableListOf<List<Offset>>()
        val run = ArrayList<Offset>(16)

        fun flush() {
            if (run.size >= 2) {
                result.add(run.toList())
            }
            run.clear()
        }

        fun consume(
            x: Float,
            y: Float
        ) {
            val dx = x - pos.x
            val dy = y - pos.y
            if (dx * dx + dy * dy < radiusSq) {
                flush()
            } else {
                run.add(Offset(x, y))
            }
        }

        var prev = points[0]
        consume(prev.x, prev.y)
        for (i in 1 until points.size) {
            val p = points[i]
            val dist = Math.hypot((p.x - prev.x).toDouble(), (p.y - prev.y).toDouble()).toFloat()
            val steps = maxOf(1, (dist / SAMPLE_STEP_PX).toInt())
            for (k in 1..steps) {
                val t = k / steps.toFloat()
                consume(prev.x + (p.x - prev.x) * t, prev.y + (p.y - prev.y) * t)
            }
            prev = p
        }
        flush()
        return result
    }

    private const val SAMPLE_STEP_PX = 6f
}
