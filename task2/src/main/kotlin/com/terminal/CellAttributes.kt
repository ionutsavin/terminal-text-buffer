package com.terminal

/**
 * Immutable attributes applied to a terminal cell: foreground/background colors and text styles.
 */
data class CellAttributes(
    val foreground: TerminalColor = TerminalColor.DEFAULT,
    val background: TerminalColor = TerminalColor.DEFAULT,
    val styles: Set<TextStyle> = emptySet()
) {
    companion object {
        val DEFAULT: CellAttributes = CellAttributes()
    }
}
