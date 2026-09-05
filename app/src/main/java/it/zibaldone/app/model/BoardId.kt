package it.zibaldone.app.model

import java.util.concurrent.atomic.AtomicLong

/**
 * Source of every board element id.
 *
 * Ids used to be raw timestamps (`System.currentTimeMillis()` for the
 * defaults, `System.nanoTime()` for the pieces the eraser rebuilds), which
 * guarantees nothing: two elements created inside the same millisecond share
 * an id, and selection, moving and deletion would then act on both at once.
 * A monotonic counter, seeded from the wall clock so ids stay increasing
 * across app restarts, removes the collision entirely.
 */
object BoardId {
    private val counter = AtomicLong(System.currentTimeMillis() * 1_000L)

    fun next(): Long = counter.incrementAndGet()

    /**
     * Raises the counter above [id] so ids restored from an imported
     * .zib (generated on another device, possibly in the future) can
     * never collide with ids minted afterwards on this one.
     */
    fun observe(id: Long) {
        while (true) {
            val current = counter.get()
            if (current >= id) return
            if (counter.compareAndSet(current, id)) return
        }
    }
}
