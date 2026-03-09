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

    // ---- Editing operations ----

    /**
     * Write characters one by one starting at the cursor using currentAttributes.
     * Handles newlines, wrapping, and scrolling with scrollback.
     */
    fun write(text: String) {
        if (text.isEmpty()) return
        for (ch in text) {
            if (ch == '\n') {
                // Move to start of next line
                cursor = CursorPosition(0, cursor.row + 1)
                scrollIfPastBottom()
                continue
            }

            if (width > 0 && height > 0) {
                // Place character at current position
                val r = cursor.row
                val c = cursor.col
                screen[r][c] = Cell(ch, currentAttributes)
            }

            // Advance cursor and wrap if needed
            if (width <= 0) {
                // No columns: just advance to next line logically
                cursor = CursorPosition(0, cursor.row + 1)
                scrollIfPastBottom()
            } else {
                val nextCol = cursor.col + 1
                if (nextCol >= width) {
                    cursor = CursorPosition(0, cursor.row + 1)
                    scrollIfPastBottom()
                } else {
                    cursor = CursorPosition(nextCol, cursor.row)
                }
            }
        }
    }

    /**
     * Insert characters at the cursor position on the current screen row only.
     * Shifts existing characters to the right; drops overflow; advances cursor by inserted length.
     */
    fun insert(text: String) {
        if (text.isEmpty()) return
        if (width <= 0 || height <= 0) return
        val rowIdx = cursor.row
        val colIdx = cursor.col
        // Safety: ensure indices valid
        if (rowIdx !in 0 until height) return
        if (colIdx !in 0 until width) return

        val insertCount = text.length
        val row = screen[rowIdx]
        val newRow: MutableList<Cell> = row.toMutableList()

        // Shift existing cells to the right within bounds
        for (i in width - 1 downTo colIdx + insertCount) {
            newRow[i] = row[i - insertCount]
        }
        // Fill inserted region with new characters (truncate if exceeds width)
        var i = 0
        while (i < insertCount && (colIdx + i) < width) {
            newRow[colIdx + i] = Cell(text[i], currentAttributes)
            i++
        }
        // Cells between last shifted index and (colIdx + insertCount - 1) are already overwritten
        // Cells beyond width-1 are dropped by construction

        screen[rowIdx] = newRow

        // Advance cursor by number of characters inserted, clamped to last column
        val newCol = (colIdx + insertCount).coerceAtMost((width - 1).coerceAtLeast(0))
        cursor = CursorPosition(newCol, rowIdx)
    }

    /**
     * Fill every cell on the given screen row with ch using currentAttributes.
     * Does not move the cursor.
     */
    fun fillLine(row: Int, ch: Char = ' ') {
        checkRowIndex(row)
        if (width <= 0) return
        val filled = MutableList(width) { Cell(ch, currentAttributes) }
        screen[row] = filled
    }

    /**
     * Push top screen row into scrollback (respecting maxScrollback),
     * shift screen rows up by one, and add a new empty row at the bottom.
     * Does not move the cursor.
     */
    fun insertEmptyLineAtBottom() {
        if (height <= 0) return
        pushTopRowToScrollback()
        scrollUpOneRow()
    }

    /**
     * Reset all screen cells to Cell.EMPTY and cursor to (0,0). Does not touch scrollback.
     */
    fun clearScreen() {
        for (r in 0 until height) {
            screen[r] = emptyRow()
        }
        cursor = CursorPosition(0, 0)
    }

    /**
     * Clear screen and also clear the entire scrollback.
     */
    fun clearAll() {
        clearScreen()
        scrollback.clear()
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

    private fun pushTopRowToScrollback() {
        if (height <= 0) return
        // Save an immutable snapshot of the top row
        val top = screen.first().toList()
        if (maxScrollback > 0) {
            scrollback.add(top)
            if (scrollback.size > maxScrollback) {
                // Remove oldest entries beyond capacity
                repeat(scrollback.size - maxScrollback) { scrollback.removeAt(0) }
            }
        }
    }

    private fun scrollUpOneRow() {
        if (height <= 0) return
        screen.removeAt(0)
        screen.add(emptyRow())
    }

    private fun scrollIfPastBottom() {
        if (cursor.row >= height) {
            if (height > 0) {
                pushTopRowToScrollback()
                scrollUpOneRow()
                val clampedCol = cursor.col.coerceIn(0, (width - 1).coerceAtLeast(0))
                cursor = CursorPosition(clampedCol, (height - 1).coerceAtLeast(0))
            } else {
                cursor = CursorPosition(0, 0)
            }
        }
    }

    private fun checkRowIndex(row: Int) {
        if (row !in 0 until height) throw IndexOutOfBoundsException("row=$row height=$height")
    }

    private fun checkColIndex(col: Int) {
        if (col !in 0 until width) throw IndexOutOfBoundsException("col=$col width=$width")
    }
}