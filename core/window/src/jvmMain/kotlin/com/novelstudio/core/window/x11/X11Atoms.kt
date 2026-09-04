package com.novelstudio.core.window.x11

import java.util.concurrent.ConcurrentHashMap

/**
 * Cache of the X11 atoms the window layer sends.
 *
 * Interning an atom is a round trip to the X server, and the resize handles intern one on every gesture, so
 * the identifiers are memoised for the lifetime of the process. Atoms are stable for the lifetime of a
 * display connection, which is the lifetime of the application.
 */
internal object X11Atoms {
    /**
     * Name of the EWMH atom that asks the window manager to take over a move or resize gesture.
     *
     * A window manager advertises support for it by listing the atom in the `_NET_SUPPORTED` property of the
     * root window. Every mainstream desktop, including Mutter, KWin, Xfwm and the tiling window managers,
     * implements it.
     */
    const val NET_WM_MOVERESIZE: String = "_NET_WM_MOVERESIZE"

    private const val NO_ATOM = 0L

    private val cache = ConcurrentHashMap<String, Long>()

    /** Interned identifier of [NET_WM_MOVERESIZE], or `null` when the display is unreachable. */
    val netWmMoveResize: Long? get() = atom(NET_WM_MOVERESIZE)

    /**
     * Interns an atom, reusing the cached identifier when it is already known.
     *
     * A failed lookup is cached as well, so an unusable display is probed only once.
     *
     * @param name atom name.
     * @return the non-zero atom identifier, or `null` when it could not be interned.
     */
    fun atom(name: String): Long? =
        cache.computeIfAbsent(name) { X11Reflection.atom(it) ?: NO_ATOM }.takeIf { it != NO_ATOM }
}
