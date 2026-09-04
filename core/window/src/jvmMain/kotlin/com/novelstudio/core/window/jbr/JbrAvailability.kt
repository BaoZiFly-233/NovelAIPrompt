package com.novelstudio.core.window.jbr

import com.jetbrains.JBR

/**
 * Cached probes of the JetBrains Runtime services the window layer can use.
 *
 * Every probe is evaluated once and never throws: a JVM that is not a JetBrains Runtime simply reports
 * everything as unsupported and the window layer degrades to its Compose only path.
 *
 * The support matrix is not symmetric and knowing it is what makes the strategy selection correct:
 * - `WindowDecorations.CustomTitleBar` is implemented in `java.awt.Window` and delegates to a native peer
 *   that only exists under the Windows and macOS sources of the runtime. On both X11 and Wayland the
 *   service constructor throws and the runtime reports it as unsupported.
 * - `WindowMove` is implemented for both Unix toolkits: the runtime tries the Wayland service first and
 *   falls back to the X11 one, so window manager assisted dragging is available on X11, XWayland and native
 *   Wayland alike.
 * - `RoundedCornersManager` covers macOS, Windows 11 and, since the recent runtime versions, the Wayland
 *   toolkit; it is never available on X11.
 */
internal object JbrAvailability {
    /** `true` when the JetBrains Runtime API is backed by an actual JetBrains Runtime. */
    val isRuntimeAvailable: Boolean by lazy { probe { JBR.isAvailable() } }

    /**
     * `true` when a native custom title bar can be installed on a frame.
     *
     * Expect `false` on every Linux toolkit; the runtime ships no Unix implementation of the service.
     */
    val isCustomTitleBarSupported: Boolean by lazy { isRuntimeAvailable && probe { JBR.isWindowDecorationsSupported() } }

    /** `true` when the window manager assisted move service can start an interactive drag. */
    val isWindowMoveSupported: Boolean by lazy { isRuntimeAvailable && probe { JBR.isWindowMoveSupported() } }

    /** `true` when the platform can round the corners of a window on request. */
    val isRoundedCornersSupported: Boolean by lazy {
        isRuntimeAvailable && probe { JBR.isRoundedCornersManagerSupported() }
    }

    private fun probe(check: () -> Boolean): Boolean = try {
        check()
    } catch (runtimeError: RuntimeException) {
        false
    } catch (linkageError: LinkageError) {
        false
    }
}
