package com.novelstudio.core.window.platform

/**
 * Operating system family the application is running on.
 *
 * The family alone never decides the window chrome: it is combined with [AwtToolkitKind] and
 * [LinuxSessionType] because a Linux host can be driven by three different windowing stacks.
 */
internal enum class HostOs {
    /** Microsoft Windows, the only platform where a JetBrains Runtime custom title bar is fully featured. */
    WINDOWS,

    /** Apple macOS, where a JetBrains Runtime custom title bar is supported but only honours control visibility. */
    MAC_OS,

    /** Any Linux distribution, regardless of the display server actually in use. */
    LINUX,

    /** A host that could not be classified, for which the most conservative chrome is selected. */
    UNKNOWN,
    ;

    /** `true` when the host is Microsoft Windows. */
    val isWindows: Boolean get() = this == WINDOWS

    /** `true` when the host is Apple macOS. */
    val isMacOs: Boolean get() = this == MAC_OS

    /** `true` when the host is a Linux distribution. */
    val isLinux: Boolean get() = this == LINUX

    /** Classifies a host from plain data so the mapping can be unit tested without a JVM property. */
    companion object {
        /**
         * Classifies an operating system from the value of the `os.name` system property.
         *
         * @param osName raw `os.name` value; matching is case insensitive and tolerant of vendor suffixes.
         * @return the matching family, or [UNKNOWN] when no family applies.
         */
        fun fromOsName(osName: String): HostOs {
            val normalized = osName.lowercase()
            return when {
                normalized.startsWith("windows") -> WINDOWS
                normalized.startsWith("mac") || normalized.startsWith("darwin") -> MAC_OS
                normalized.startsWith("linux") -> LINUX
                else -> UNKNOWN
            }
        }

        /**
         * Classifies the operating system exposed by an [EnvironmentSource].
         *
         * @param environment source of the `os.name` system property.
         * @return the matching family, or [UNKNOWN] when the property is absent or unrecognised.
         */
        fun from(environment: EnvironmentSource): HostOs = fromOsName(environment.property("os.name").orEmpty())
    }
}
