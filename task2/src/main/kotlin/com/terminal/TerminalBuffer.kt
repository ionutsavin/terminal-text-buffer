package com.terminal

/**
 * Represents a terminal screen buffer with scrollback history.
 * This class currently supports only cursor/attribute operations and content accessors.
 * Writing/editing operations will be added later.
 */
class TerminalBuffer(
    val width: Int,
    val height: Int,
    val maxScrollback: Int
) {

    // Cursor position within the visible screen grid (zero-indexed)
    private data class CursorPosition(val col: Int, val row: Int)

    // Current attributes used for future writes
    var currentAttributes: CellAttributes = CellAttributes.DEFAULT
        private set

    // The visible screen: fixed-size grid (height x width)
    private val screen: MutableList<MutableList<Cell>> = MutableList(height) { emptyRow() }

    // Scrollback: immutable rows, separate from the screen
    private val scrollback: MutableList<List<Cell>> = mutableListOf()

    // Cursor starts at top-left
    private var cursor: CursorPosition = CursorPosition(0, 0)

    // ---- Attributes ----

    fun setAttributes(fg: TerminalColor, bg: TerminalColor, styles: Set<TextStyle>) {
        currentAttributes = CellAttributes(foreground = fg, background = bg, styles = styles.toSet())
    }

    // ---- Cursor ----

    fun getCursorCol(): Int = cursor.col

    fun getCursorRow(): Int = cursor.row

    fun setCursor(col: Int, row: Int) {
        val clampedCol = col.coerceIn(0, (width - 1).coerceAtLeast(0))
        val clampedRow = row.coerceIn(0, (height - 1).coerceAtLeast(0))
        cursor = CursorPosition(clampedCol, clampedRow)
    }

    fun moveCursor(cols: Int, rows: Int) {
        setCursor(cursor.col + cols, cursor.row + rows)
    }

    // ---- Content access ----

    fun getCell(row: Int, col: Int): Cell {
        checkRowIndex(row)
        checkColIndex(col)
        return screen[row][col]
    }

    fun getScrollbackCell(scrollbackRow: Int, col: Int): Cell {
        if (scrollbackRow !in 0 until scrollback.size) throw IndexOutOfBoundsException("scrollbackRow=$scrollbackRow size=${scrollback.size}")
        checkColIndex(col)
        return scrollback[scrollbackRow][col]
    }

    fun getScreenLine(row: Int): String {
        checkRowIndex(row)
        return screen[row].asSequence().map { it.char }.joinToString("")
    }

    fun getScrollbackLine(row: Int): String {
        if (row !in 0 until scrollback.size) throw IndexOutOfBoundsException("scrollbackRow=$row size=${scrollback.size}")
        return scrollback[row].asSequence().map { it.char }.joinToString("")
    }

    fun getScreenAsString(): String {
        return screen.asSequence().map { row -> row.asSequence().map { it.char }.joinToString("") }.joinToString("\n")
    }

    fun getAllAsString(): String {
        val allLines = buildList {
            addAll(scrollback.asSequence().map { row -> row.asSequence().map { it.char }.joinToString("") })
            addAll(screen.asSequence().map { row -> row.asSequence().map { it.char }.joinToString("") })
        }
        return allLines.joinToString("\n")
    }

    // ---- Helpers ----

    private fun emptyRow(): MutableList<Cell> = MutableList(width) { Cell.EMPTY }

    private fun checkRowIndex(row: Int) {
        if (row !in 0 until height) throw IndexOutOfBoundsException("row=$row height=$height")
    }

    private fun checkColIndex(col: Int) {
        if (col !in 0 until width) throw IndexOutOfBoundsException("col=$col width=$width")
    }
}