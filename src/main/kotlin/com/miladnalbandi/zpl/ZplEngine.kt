package com.miladnalbandi.zpl

import java.awt.Graphics2D
import java.util.concurrent.ConcurrentHashMap

/**
 * Core ZPL command dispatcher using the **Chain of Responsibility** pattern.
 *
 * [CommandHandler] implementations are registered in priority order.
 * For each command token the engine tries the cached handler first (O(1)),
 * then falls back to the full handler list.  Once a handler claims a command
 * its two-character prefix is cached for subsequent tokens.
 *
 * A single `ZplEngine` instance is not thread-safe for concurrent [dispatch]
 * calls that share the same [RenderContext]; create one instance per render
 * thread if needed.
 */
class ZplEngine {

    private val handlers    = mutableListOf<CommandHandler>()
    private val prefixCache = ConcurrentHashMap<String, CommandHandler>()
    private var debug       = false

    /**
     * Register a handler.  Handlers are tried in registration order.
     *
     * @return `this` for fluent chaining.
     */
    fun register(handler: CommandHandler): ZplEngine {
        handlers += handler
        prefixCache.clear()   // invalidate prefix cache on structural change
        return this
    }

    /** Enable or disable verbose debug logging to stdout. */
    fun setDebug(enabled: Boolean) { debug = enabled }

    /**
     * Dispatch a pre-split list of ZPL command tokens.
     *
     * @param cmds Tokens produced by splitting the ZPL source on `^`/`~`.
     * @param g    Graphics context to render into.
     * @param ctx  Mutable rendering state.
     */
    fun dispatch(cmds: List<String>, g: Graphics2D, ctx: RenderContext) {
        if (handlers.isEmpty()) {
            if (debug) println("[ZplEngine] No handlers registered")
            return
        }

        val it = cmds.listIterator()
        var total    = 0
        var handled  = 0
        val skipped  = mutableListOf<String>()

        while (it.hasNext()) {
            val cmd = it.next().trim()
            if (cmd.isBlank()) continue
            total++

            val prefix       = cmd.take(2)
            val cachedHandler = prefixCache[prefix]
            var ok = false

            try {
                if (cachedHandler != null && cachedHandler.handle(cmd, it, g, ctx)) {
                    ok = true
                } else if (cachedHandler == null) {
                    for (h in handlers) {
                        if (h.handle(cmd, it, g, ctx)) {
                            prefixCache[prefix] = h
                            ok = true
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                if (debug) println("[ZplEngine] Error in '$cmd': ${e.message}")
            }

            if (ok) handled++ else skipped += cmd
            if (debug && !ok) println("[ZplEngine] Unhandled: $cmd")
        }

        if (debug && total > 0) {
            println("[ZplEngine] $handled/$total handled (${handled * 100 / total}%)")
            if (skipped.isNotEmpty())
                println("[ZplEngine] Unhandled (first 10): ${skipped.take(10).joinToString()}")
        }
    }
}
