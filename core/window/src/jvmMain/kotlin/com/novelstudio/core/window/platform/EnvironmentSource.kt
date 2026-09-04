package com.novelstudio.core.window.platform

/**
 * Read-only view over the ambient process environment.
 *
 * Platform detection depends on this abstraction instead of reading `java.lang.System` directly, so the
 * resolution rules can be exercised by unit tests without a display server, an AWT toolkit or a desktop
 * session. Implementations must be side-effect free.
 */
internal interface EnvironmentSource {
    /**
     * Reads a process environment variable.
     *
     * @param name variable name, case sensitive on POSIX systems.
     * @return the raw value, or `null` when the variable is undefined.
     */
    fun variable(name: String): String?

    /**
     * Reads a JVM system property.
     *
     * @param name property key.
     * @return the raw value, or `null` when the property is undefined.
     */
    fun property(name: String): String?

    /** Provides the environment source backed by the running JVM process. */
    companion object {
        /**
         * Environment source delegating to `System.getenv` and `System.getProperty`.
         *
         * Values are read on every call rather than cached, so a property written before AWT initialises,
         * such as `awt.toolkit.name`, is observed by later detection passes.
         */
        val Jvm: EnvironmentSource = JvmEnvironmentSource

        private object JvmEnvironmentSource : EnvironmentSource {
            /**
             * Reads a process environment variable from the running JVM.
             *
             * @param name variable name.
             * @return the raw value, or `null` when the variable is undefined.
             */
            override fun variable(name: String): String? = System.getenv(name)

            /**
             * Reads a system property from the running JVM.
             *
             * @param name property key.
             * @return the raw value, or `null` when the property is undefined.
             */
            override fun property(name: String): String? = System.getProperty(name)
        }
    }
}
