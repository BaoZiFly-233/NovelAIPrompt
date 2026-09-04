package com.novelstudio.core.window.x11

import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Component
import java.awt.Window
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * The single place in the application that reaches into the `sun.awt` internals of the runtime.
 *
 * Everything the EWMH workaround needs lives behind unexported packages: the X display pointer and the root
 * window identifier on `sun.awt.X11.XToolkit`, the peer of an AWT window through `sun.awt.AWTAccessor`, the
 * X window identifier on `sun.awt.X11.XBaseWindow`, atom interning on `sun.awt.X11.XAtom`, the raw Xlib entry
 * points and their scratch buffers on `sun.awt.X11.XlibWrapper`, and the reads of those buffers on
 * `sun.awt.X11.Native`. Reaching them requires
 * `--add-opens=java.desktop/sun.awt=ALL-UNNAMED` and `--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED`;
 * both are configured by the build for the application, the tests and the packaged distribution.
 *
 * Every handle is resolved once and cached. Any failure, whether a missing class, a missing module opening
 * or a wrong signature, disables the whole facility permanently and turns every operation into a `false` or
 * `null` result, so the window layer degrades to its Compose only path instead of crashing. This class and
 * the domain error mapper are the two places allowed to catch `Throwable`, precisely because a reflective
 * bridge can fail in ways no narrower type describes.
 */
internal object X11Reflection {
    private const val CLIENT_MESSAGE_DATA_SIZE = 5
    private const val CLIENT_MESSAGE_FORMAT = 32
    private const val POINTER_QUERY_ARITY = 9
    private const val POINTER_SCRATCH_SIZE = 7
    private const val POINTER_ROOT_X_SCRATCH = 2
    private const val POINTER_ROOT_Y_SCRATCH = 3

    private val logger = KotlinLogging.logger {}

    private val handles: Handles? by lazy { resolveHandles() }

    private val pointerHandles: PointerHandles? by lazy { resolvePointerHandles() }

    /**
     * `true` when the reflective bridge is usable.
     *
     * A `true` result only proves the X11 classes are present and open; it does not prove AWT is actually
     * running on the X11 toolkit, because those classes also ship with a Wayland toolkit runtime. Callers
     * must combine this with the detected toolkit before acting on it.
     */
    val isAvailable: Boolean get() = handles != null

    /**
     * Resolves the X window identifier of the shell window managed by the window manager.
     *
     * The shell window, not the content window, is the one EWMH client messages must target.
     *
     * @param window displayable AWT window.
     * @return the non-zero X window identifier, or `null` when the window has no X11 peer.
     */
    fun windowId(window: Window): Long? {
        val resolved = handles ?: return null
        return try {
            val peer = resolved.getPeer.invoke(resolved.componentAccessor, window) ?: return null
            (resolved.getWindowId.invoke(peer) as Long).takeIf { it != 0L }
        } catch (failure: Throwable) {
            logger.debug(failure) { "Unable to resolve the X11 window identifier of $window" }
            null
        }
    }

    /**
     * Interns an X11 atom by name.
     *
     * @param name atom name, for instance `_NET_WM_MOVERESIZE`.
     * @return the non-zero atom identifier, or `null` when the display is unreachable.
     */
    fun atom(name: String): Long? {
        val resolved = handles ?: return null
        return try {
            resolved.awtLock.invoke(null)
            try {
                val atom = resolved.atomGet.invoke(null, name) ?: return null
                (resolved.atomGetAtom.invoke(atom) as Long).takeIf { it != 0L }
            } finally {
                resolved.awtUnlock.invoke(null)
            }
        } catch (failure: Throwable) {
            logger.debug(failure) { "Unable to intern the X11 atom $name" }
            null
        }
    }

    /**
     * Reads the current pointer position in root window coordinates.
     *
     * This is the only reliable way to obtain the device pixels an EWMH message needs. AWT reports the pointer
     * in user space, and converting it back is not a multiplication: `X11GraphicsDevice` scales the width and
     * the height of a screen but leaves its origin in device pixels, so the mapping is anchored on that origin
     * and a second monitor makes a scaled user space coordinate land on the wrong screen. Asking the X server
     * skips the whole conversion.
     *
     * The query goes to the default root window, which spans every monitor of a virtual screen; on the split
     * screen layouts where it does not, the server answers that the pointer is elsewhere and the caller
     * degrades instead of aiming at the wrong place. Like every other entry point, a failure answers `null`.
     *
     * @return the pointer position in root coordinates and device pixels, or `null` when it cannot be read.
     */
    fun pointerRootLocation(): Pair<Long, Long>? {
        val resolved = handles ?: return null
        val pointer = pointerHandles ?: return null
        return try {
            val display = resolved.getDisplay.invoke(null) as Long
            val root = if (display == 0L) 0L else resolved.getDefaultRootWindow.invoke(null) as Long
            if (root == 0L) {
                null
            } else {
                resolved.awtLock.invoke(null)
                try {
                    queryPointer(pointer, display, root)
                } finally {
                    resolved.awtUnlock.invoke(null)
                }
            }
        } catch (failure: Throwable) {
            logger.debug(failure) { "Unable to read the X11 pointer position" }
            null
        }
    }

    /**
     * Sends a 32 bit format client message to the root window on behalf of a client window.
     *
     * The event is sent to the root window with `SubstructureRedirectMask` and `SubstructureNotifyMask`,
     * which is how the specification requires a client to address the window manager. All raw Xlib calls are
     * made under the AWT lock, mirroring what the runtime does for every X access.
     *
     * @param targetWindowId identifier of the client window the message is about.
     * @param messageType interned atom of the message type.
     * @param data exactly five 32 bit payload values, as required by the format.
     * @param releaseGrabs `true` to ungrab the pointer and the keyboard before sending. The specification
     *   requires a client to release all grabs before asking the window manager to take over a gesture, and
     *   the runtime does it twice because a button press can install an implicit grab that is otherwise
     *   never dropped on Xorg. The cancel message is the single one that must be sent without releasing.
     * @return `true` when the message was handed to Xlib.
     * @throws IllegalArgumentException when [data] does not hold exactly five values.
     */
    fun sendClientMessageToRoot(
        targetWindowId: Long,
        messageType: Long,
        data: LongArray,
        releaseGrabs: Boolean,
    ): Boolean {
        require(data.size == CLIENT_MESSAGE_DATA_SIZE) {
            "An X11 client message carries exactly $CLIENT_MESSAGE_DATA_SIZE values, got ${data.size}"
        }
        val resolved = handles ?: return false
        if (targetWindowId == 0L || messageType == 0L) return false
        return try {
            val display = resolved.getDisplay.invoke(null) as Long
            if (display == 0L) {
                false
            } else {
                val root = resolved.getDefaultRootWindow.invoke(null) as Long
                resolved.awtLock.invoke(null)
                try {
                    if (releaseGrabs) {
                        resolved.ungrabPointer.invoke(null, display, resolved.currentTime)
                        resolved.ungrabKeyboard?.invoke(null, display, resolved.currentTime)
                        resolved.flush.invoke(null, display)
                    }
                    dispatch(resolved, display, root, targetWindowId, messageType, data)
                } finally {
                    resolved.awtUnlock.invoke(null)
                }
                true
            }
        } catch (failure: Throwable) {
            logger.debug(failure) { "Unable to send an X11 client message to the root window" }
            false
        }
    }

    private fun queryPointer(pointer: PointerHandles, display: Long, root: Long): Pair<Long, Long>? {
        val scratch = pointer.scratch.map { field -> field.getLong(null) }
        val found = pointer.queryPointer.invoke(null, display, root, *scratch.toTypedArray()) as Boolean
        if (!found) return null
        val x = pointer.getInt.invoke(null, scratch[POINTER_ROOT_X_SCRATCH]) as Int
        val y = pointer.getInt.invoke(null, scratch[POINTER_ROOT_Y_SCRATCH]) as Int
        return x.toLong() to y.toLong()
    }

    private fun dispatch(
        resolved: Handles,
        display: Long,
        root: Long,
        targetWindowId: Long,
        messageType: Long,
        data: LongArray,
    ) {
        val event = resolved.eventConstructor.newInstance()
        try {
            resolved.setType.invoke(event, resolved.clientMessage)
            resolved.setDisplay.invoke(event, display)
            resolved.setWindow.invoke(event, targetWindowId)
            resolved.setMessageType.invoke(event, messageType)
            resolved.setFormat.invoke(event, CLIENT_MESSAGE_FORMAT)
            data.forEachIndexed { index, value -> resolved.setData.invoke(event, index, value) }
            val pointer = resolved.getPointer.invoke(event) as Long
            resolved.sendEvent.invoke(null, display, root, false, resolved.substructureMask, pointer)
            resolved.flush.invoke(null, display)
        } finally {
            resolved.dispose.invoke(event)
        }
    }

    /**
     * Loads an internal AWT class without running its static initialiser.
     *
     * `sun.awt.X11.XToolkit` initialises by casting the ambient `GraphicsEnvironment` to its X11 subtype, which
     * throws on a runtime whose toolkit is the native Wayland one. Resolving a method handle needs the class but
     * not its initialisation, so deferring the latter keeps the probe side-effect free: the initialiser runs on
     * the first actual invocation, which only ever happens on the X11 path.
     *
     * @param name binary name of the class to load.
     * @return the loaded, uninitialised class.
     * @throws ClassNotFoundException when the runtime does not ship the class.
     */
    private fun load(name: String): Class<*> = Class.forName(name, false, X11Reflection::class.java.classLoader)

    private fun resolveHandles(): Handles? = try {
        buildHandles()
    } catch (failure: Throwable) {
        logger.debug(failure) {
            "The sun.awt.X11 bridge is unavailable; window manager assisted resizing is disabled"
        }
        null
    }

    private fun buildHandles(): Handles? {
        val longType = java.lang.Long.TYPE
        val intType = java.lang.Integer.TYPE
        val booleanType = java.lang.Boolean.TYPE

        val toolkitClass = load("sun.awt.X11.XToolkit")
        val accessorClass = load("sun.awt.AWTAccessor")
        val componentAccessorClass = load("sun.awt.AWTAccessor\$ComponentAccessor")
        val baseWindowClass = load("sun.awt.X11.XBaseWindow")
        val atomClass = load("sun.awt.X11.XAtom")
        val xlibClass = load("sun.awt.X11.XlibWrapper")
        val eventClass = load("sun.awt.X11.XClientMessageEvent")
        val sunToolkitClass = load("sun.awt.SunToolkit")
        val constantsClass = load("sun.awt.X11.XConstants")

        val componentAccessor = accessorClass.getDeclaredMethod("getComponentAccessor").open().invoke(null)
            ?: return null

        return Handles(
            componentAccessor = componentAccessor,
            getDisplay = toolkitClass.getDeclaredMethod("getDisplay").open(),
            getDefaultRootWindow = toolkitClass.getDeclaredMethod("getDefaultRootWindow").open(),
            getPeer = componentAccessorClass.getMethod("getPeer", Component::class.java).open(),
            getWindowId = baseWindowClass.getMethod("getWindow").open(),
            atomGet = atomClass.getMethod("get", String::class.java).open(),
            atomGetAtom = atomClass.getDeclaredMethod("getAtom").open(),
            ungrabPointer = xlibClass.getDeclaredMethod("XUngrabPointer", longType, longType).open(),
            ungrabKeyboard = optionalMethod(xlibClass, "XUngrabKeyboard", longType, longType),
            flush = xlibClass.getDeclaredMethod("XFlush", longType).open(),
            sendEvent = xlibClass
                .getDeclaredMethod("XSendEvent", longType, longType, booleanType, longType, longType)
                .open(),
            eventConstructor = eventClass.getConstructor().apply { isAccessible = true },
            setType = eventClass.getMethod("set_type", intType).open(),
            setDisplay = eventClass.getMethod("set_display", longType).open(),
            setWindow = eventClass.getMethod("set_window", longType).open(),
            setMessageType = eventClass.getMethod("set_message_type", longType).open(),
            setFormat = eventClass.getMethod("set_format", intType).open(),
            setData = eventClass.getMethod("set_data", intType, longType).open(),
            getPointer = eventClass.getMethod("getPData").open(),
            dispose = eventClass.getMethod("dispose").open(),
            awtLock = sunToolkitClass.getDeclaredMethod("awtLock").open(),
            awtUnlock = sunToolkitClass.getDeclaredMethod("awtUnlock").open(),
            currentTime = constantsClass.getDeclaredField("CurrentTime").apply { isAccessible = true }.getLong(null),
            clientMessage = constantsClass.getDeclaredField("ClientMessage").apply { isAccessible = true }.getInt(null),
            substructureMask = constantsClass
                .getDeclaredField("SubstructureRedirectMask")
                .apply { isAccessible = true }
                .getLong(null) or
                constantsClass.getDeclaredField("SubstructureNotifyMask").apply { isAccessible = true }.getLong(null),
        )
    }

    private fun resolvePointerHandles(): PointerHandles? = try {
        buildPointerHandles()
    } catch (failure: Throwable) {
        logger.debug(failure) {
            "The sun.awt.X11 pointer query is unavailable; window manager assisted gestures are disabled"
        }
        null
    }

    private fun buildPointerHandles(): PointerHandles {
        val longType = java.lang.Long.TYPE
        val xlibClass = load("sun.awt.X11.XlibWrapper")
        val nativeClass = load("sun.awt.X11.Native")
        val signature = Array<Class<*>>(POINTER_QUERY_ARITY) { longType }
        return PointerHandles(
            queryPointer = xlibClass.getDeclaredMethod("XQueryPointer", *signature).open(),
            getInt = nativeClass.getDeclaredMethod("getInt", longType).open(),
            scratch = (1..POINTER_SCRATCH_SIZE).map { index ->
                xlibClass.getDeclaredField("larg$index").apply { isAccessible = true }
            },
        )
    }

    private fun optionalMethod(owner: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? = try {
        owner.getDeclaredMethod(name, *parameterTypes).open()
    } catch (missing: NoSuchMethodException) {
        null
    }

    private fun Method.open(): Method = apply { isAccessible = true }

    private class Handles(
        val componentAccessor: Any,
        val getDisplay: Method,
        val getDefaultRootWindow: Method,
        val getPeer: Method,
        val getWindowId: Method,
        val atomGet: Method,
        val atomGetAtom: Method,
        val ungrabPointer: Method,
        val ungrabKeyboard: Method?,
        val flush: Method,
        val sendEvent: Method,
        val eventConstructor: Constructor<*>,
        val setType: Method,
        val setDisplay: Method,
        val setWindow: Method,
        val setMessageType: Method,
        val setFormat: Method,
        val setData: Method,
        val getPointer: Method,
        val dispose: Method,
        val awtLock: Method,
        val awtUnlock: Method,
        val currentTime: Long,
        val clientMessage: Int,
        val substructureMask: Long,
    )

    private class PointerHandles(
        val queryPointer: Method,
        val getInt: Method,
        val scratch: List<Field>,
    )
}
