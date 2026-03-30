package com.miladnalbandi.zpl

import java.awt.Graphics2D
import java.util.*

/**
 * Single responsibility unit in the ZPL command processing chain.
 *
 * Each implementation handles a specific family of ZPL commands (e.g. text,
 * barcodes, graphic shapes). Handlers are registered with [ZplEngine] and
 * tried in order; the first handler that returns `true` wins.
 *
 * Handlers receive the full [ListIterator] so they can consume additional
 * commands (e.g. a barcode handler reading ahead to find `^FD`).
 */
interface CommandHandler {
    /**
     * Attempt to handle [cmd].
     *
     * @param cmd     The ZPL command token (no leading `^`), e.g. `"FO10,20"`.
     * @param it      Iterator over the full command list — may be advanced to
     *                consume lookahead commands.
     * @param g       Graphics context to draw on.
     * @param ctx     Mutable rendering state shared across all handlers.
     * @return `true` if the command was handled; `false` to pass to the next handler.
     */
    fun handle(cmd: String, it: ListIterator<String>, g: Graphics2D, ctx: RenderContext): Boolean
}
