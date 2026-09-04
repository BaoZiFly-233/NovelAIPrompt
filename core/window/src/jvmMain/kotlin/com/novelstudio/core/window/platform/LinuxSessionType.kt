package com.novelstudio.core.window.platform

/**
 * Display session the desktop is running, as advertised by the session manager.
 *
 * The session type answers a question the toolkit class name cannot: whether an X11 toolkit is talking to a
 * real X server or to XWayland. It never decides on its own which chrome to use, because a Wayland session
 * still runs the X11 toolkit unless the launcher opted into `WLToolkit`.
 *
 * @property id lower-case identifier used in diagnostics and matching the `XDG_SESSION_TYPE` vocabulary.
 */
internal enum class LinuxSessionType(val id: String) {
    /** A real X server session. */
    X11("x11"),

    /** A Wayland compositor session, which may still host X11 clients through XWayland. */
    WAYLAND("wayland"),

    /** A text console session with no display server. */
    TTY("tty"),

    /** No session information could be obtained, which is the normal case on Windows and macOS. */
    UNKNOWN("unknown"),
    ;

    /** Resolves the session from plain environment values so the mapping can be unit tested. */
    companion object {
        private const val XDG_SESSION_TYPE = "XDG_SESSION_TYPE"
        private const val WAYLAND_DISPLAY = "WAYLAND_DISPLAY"
        private const val DISPLAY = "DISPLAY"

        /**
         * Resolves the session type from the three variables every desktop session exports.
         *
         * `XDG_SESSION_TYPE` is authoritative when present. Otherwise a non-blank `WAYLAND_DISPLAY` proves a
         * compositor socket is reachable and a non-blank `DISPLAY` proves an X server or XWayland is
         * reachable. The JetBrains Runtime additionally probes `$XDG_RUNTIME_DIR/wayland-0` when
         * `WAYLAND_DISPLAY` is unset; that probe is deliberately not mirrored here because the toolkit class
         * name already tells us what AWT actually bound to, and file system access would break purity.
         *
         * @param xdgSessionType value of `XDG_SESSION_TYPE`, or `null`.
         * @param waylandDisplay value of `WAYLAND_DISPLAY`, or `null`.
         * @param x11Display value of `DISPLAY`, or `null`.
         * @return the resolved session type, or [UNKNOWN] when nothing indicates a display server.
         */
        fun resolve(xdgSessionType: String?, waylandDisplay: String?, x11Display: String?): LinuxSessionType {
            entries.firstOrNull { it != UNKNOWN && it.id.equals(xdgSessionType?.trim(), ignoreCase = true) }?.let { return it }
            return when {
                !waylandDisplay.isNullOrBlank() -> WAYLAND
                !x11Display.isNullOrBlank() -> X11
                else -> UNKNOWN
            }
        }

        /**
         * Resolves the session type from the variables exposed by an [EnvironmentSource].
         *
         * @param environment source of `XDG_SESSION_TYPE`, `WAYLAND_DISPLAY` and `DISPLAY`.
         * @return the resolved session type, or [UNKNOWN] when nothing indicates a display server.
         */
        fun from(environment: EnvironmentSource): LinuxSessionType = resolve(
            xdgSessionType = environment.variable(XDG_SESSION_TYPE),
            waylandDisplay = environment.variable(WAYLAND_DISPLAY),
            x11Display = environment.variable(DISPLAY),
        )
    }
}
