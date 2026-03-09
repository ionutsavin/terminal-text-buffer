package com.terminal

/**
 * Represents a terminal screen buffer with scrollback history.
 * This class currently supports only cursor/attribute operations and content accessors.
 * Writing/editing operations will be added later.
 */
class TerminalBuffer(
    var height: Int,
    var width: Int,
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
        for ((index, ch) in text.withIndex()) {
            if (ch == '\n') {
                // Move to start of next line immediately on newline
                cursor = CursorPosition(0, cursor.row + 1)
                scrollIfPastBottom()
                continue
            }

            // Place character at current position if within a visible grid
            if (width > 0 && height > 0) {
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
                    val isLastChar = index == text.lastIndex
                    val isBottomRow = cursor.row >= (height - 1).coerceAtLeast(0)
                    if (isBottomRow) {
                        // At bottom row: defer wrap/scroll only if this is the last character
                        if (!isLastChar) {
                            cursor = CursorPosition(0, cursor.row + 1)
                            scrollIfPastBottom()
                        }
                    } else {
                        // Not bottom row: always wrap to next line immediately
                        cursor = CursorPosition(0, cursor.row + 1)
                        // No need to scroll check here because we're not on bottom row
                    }
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

    // ---- Resizing ----

    /**
     * Resize the buffer's visible screen (width x height) and adjust scrollback rows' width.
     * Strategy:
     * - If width changes: truncate or pad each screen row and each scrollback row to newWidth.
     * - If height shrinks: move excess top screen rows into scrollback (oldest first), then trim the screen.
     * - If height grows: append empty rows at the bottom.
     * - Clamp cursor within the new bounds.
     * - Scrollback overflow rules still apply after resize.
     */
    fun resize(newWidth: Int, newHeight: Int) {
        val targetWidth = newWidth.coerceAtLeast(0)
        val targetHeight = newHeight.coerceAtLeast(0)

        val widthChanged = targetWidth != width
        val heightChanged = targetHeight != height
        if (!widthChanged && !heightChanged) return

        // 1) Adjust row widths for screen and scrollback if needed
        if (widthChanged) {
            // Resize screen rows
            for (r in 0 until screen.size) {
                screen[r] = resizeMutableRow(screen[r], targetWidth)
            }
            // Resize scrollback rows (immutable entries)
            for (i in 0 until scrollback.size) {
                scrollback[i] = resizeImmutableRow(scrollback[i], targetWidth)
            }
        }

        // 2) Adjust screen height (move to scrollback or add empty rows)
        if (targetHeight < height) {
            val moveCount = (height - targetHeight).coerceAtMost(screen.size)
            // Move top rows to scrollback as immutable snapshots
            if (maxScrollback > 0 && moveCount > 0) {
                for (i in 0 until moveCount) {
                    val snapshot = screen[i].toList()
                    scrollback.add(snapshot)
                }
                // Enforce maxScrollback capacity
                if (scrollback.size > maxScrollback) {
                    val excess = scrollback.size - maxScrollback
                    repeat(excess) { scrollback.removeAt(0) }
                }
            }
            // Trim the screen's top rows
            repeat(moveCount) {
                if (screen.isNotEmpty()) screen.removeAt(0)
            }
        } else if (targetHeight > height) {
            val addCount = targetHeight - height
            repeat(addCount) { screen.add(MutableList(targetWidth) { Cell.EMPTY }) }
        }

        // 3) Update dimensions
        width = targetWidth
        height = targetHeight

        // Ensure screen has exactly height rows (guard against edge cases when height==0)
        while (screen.size < height) {
            screen.add(MutableList(width) { Cell.EMPTY })
        }
        while (screen.size > height) {
            // If we still have more rows than height, drop from top (older content)
            if (maxScrollback > 0 && screen.isNotEmpty()) {
                val snapshot = screen.first().toList()
                scrollback.add(snapshot)
                if (scrollback.size > maxScrollback) {
                    val excess = scrollback.size - maxScrollback
                    repeat(excess) { scrollback.removeAt(0) }
                }
            }
            if (screen.isNotEmpty()) screen.removeAt(0) else break
        }

        // 4) Clamp cursor to new bounds
        val clampedCol = cursor.col.coerceIn(0, (width - 1).coerceAtLeast(0))
        val clampedRow = cursor.row.coerceIn(0, (height - 1).coerceAtLeast(0))
        cursor = CursorPosition(clampedCol, clampedRow)
    }

    private fun resizeMutableRow(row: MutableList<Cell>, newWidth: Int): MutableList<Cell> {
        return when {
            newWidth <= 0 -> mutableListOf()
            row.size == newWidth -> row
            row.size > newWidth -> row.subList(0, newWidth).toMutableList()
            else -> {
                val padded = row.toMutableList()
                val toAdd = newWidth - row.size
                repeat(toAdd) { padded.add(Cell.EMPTY) }
                padded
            }
        }
    }

    private fun resizeImmutableRow(row: List<Cell>, newWidth: Int): List<Cell> {
        return when {
            newWidth <= 0 -> emptyList()
            row.size == newWidth -> row
            row.size > newWidth -> row.subList(0, newWidth).toList()
            else -> {
                val padded = row.toMutableList()
                val toAdd = newWidth - row.size
                repeat(toAdd) { padded.add(Cell.EMPTY) }
                padded.toList()
            }
        }
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