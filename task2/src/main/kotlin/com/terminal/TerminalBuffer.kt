package com.terminal

/**
 * Represents an in-memory terminal screen buffer with scrollback history.
 *
 * The buffer maintains a fixed-size visible screen (height x width) and an optional
 * scrollback history of immutable lines (up to `maxScrollback`). It supports cursor
 * movement, attribute selection, basic editing operations (write/insert/fill),
 * clearing, resizing, and read-only content accessors.
 *
 * Note: All operations are synchronous and in-memory; no I/O occurs here.
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

    /**
     * Sets the attributes that will be applied to subsequently written cells.
     * The change does not modify existing cells, only future writes.
     */
    fun setAttributes(fg: TerminalColor, bg: TerminalColor, styles: Set<TextStyle>) {
        currentAttributes = CellAttributes(foreground = fg, background = bg, styles = styles.toSet())
    }

    // ---- Cursor ----

    /** Returns the current cursor column (zero-indexed within the visible screen). */
    fun getCursorCol(): Int = cursor.col

    /** Returns the current cursor row (zero-indexed within the visible screen). */
    fun getCursorRow(): Int = cursor.row

    /**
     * Positions the cursor within the visible screen, clamping to valid bounds.
     * Out-of-range inputs are moved to the nearest valid cell; no exception is thrown.
     */
    fun setCursor(col: Int, row: Int) {
        val clampedCol = col.coerceIn(0, (width - 1).coerceAtLeast(0))
        val clampedRow = row.coerceIn(0, (height - 1).coerceAtLeast(0))
        cursor = CursorPosition(clampedCol, clampedRow)
    }

    /**
     * Moves the cursor by the given deltas relative to its current position, with clamping.
     * Negative values move left/up; positive values move right/down.
     */
    fun moveCursor(cols: Int, rows: Int) {
        setCursor(cursor.col + cols, cursor.row + rows)
    }

    // ---- Editing operations ----

    /**
     * Writes the provided text starting at the current cursor position using the current attributes.
     * Newlines move the cursor to column 0 of the next row; writing past the bottom scrolls the screen
     * and appends scrolled lines to scrollback (subject to `maxScrollback`).
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
     * Inserts the given text at the current cursor position on the active screen row only.
     * Existing characters on the row shift right; overflow beyond the last column is dropped.
     * The cursor advances by the number of inserted characters (clamped to the last column).
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
     * Fills every cell on the specified screen row with the given character using current attributes.
     * The cursor position is not changed.
     */
    fun fillLine(row: Int, ch: Char = ' ') {
        checkRowIndex(row)
        if (width <= 0) return
        val filled = MutableList(width) { Cell(ch, currentAttributes) }
        screen[row] = filled
    }

    /**
     * Pushes the current top screen row into scrollback, shifts the screen up by one row,
     * and adds a new empty row at the bottom. The cursor position is not changed.
     */
    fun insertEmptyLineAtBottom() {
        if (height <= 0) return
        pushTopRowToScrollback()
        scrollUpOneRow()
    }

    /**
     * Clears the visible screen to empty cells and moves the cursor to (0,0).
     * Scrollback history is not modified.
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
    /**
     * Clears the visible screen and removes all scrollback history.
     * After this call the buffer is visually empty and cursor is at (0,0).
     */
    fun clearAll() {
        clearScreen()
        scrollback.clear()
    }

    // ---- Resizing ----

    /**
     * Resizes the visible screen to the given dimensions and adjusts stored rows accordingly.
     * - Rows in both screen and scrollback are truncated or padded to the new width.
     * - Shrinking height moves excess top screen rows into scrollback; growing height adds empty rows.
     * - The cursor is clamped within the new bounds. Scrollback capacity limits still apply.
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
                enforceScrollbackCapacity()
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
                enforceScrollbackCapacity()
            }
            if (screen.isNotEmpty()) screen.removeAt(0) else break
        }

        // 4) Clamp cursor to new bounds
        val clampedCol = cursor.col.coerceIn(0, (width - 1).coerceAtLeast(0))
        val clampedRow = cursor.row.coerceIn(0, (height - 1).coerceAtLeast(0))
        cursor = CursorPosition(clampedCol, clampedRow)
    }

    // Returns a resized copy of a mutable screen row, truncating or padding with EMPTY cells.
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

    // Returns a resized copy of an immutable row for scrollback, truncating or padding with EMPTY cells.
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

    /**
     * Returns the cell at the given screen coordinates.
     * Row 0 is the top of the visible screen. Throws IndexOutOfBoundsException if out of bounds.
     */
    fun getCell(row: Int, col: Int): Cell {
        checkRowIndex(row)
        checkColIndex(col)
        return screen[row][col]
    }

    /**
     * Returns the cell from scrollback at the given row and column.
     * Row 0 is the oldest scrollback line. Throws IndexOutOfBoundsException if out of bounds.
     */
    fun getScrollbackCell(scrollbackRow: Int, col: Int): Cell {
        if (scrollbackRow !in 0 until scrollback.size) throw IndexOutOfBoundsException("scrollbackRow=$scrollbackRow size=${scrollback.size}")
        checkColIndex(col)
        return scrollback[scrollbackRow][col]
    }

    /**
     * Returns the characters of the specified screen row as a String.
     * Row 0 is the top of the visible screen. Throws IndexOutOfBoundsException if out of bounds.
     */
    fun getScreenLine(row: Int): String {
        checkRowIndex(row)
        return screen[row].asSequence().map { it.char }.joinToString("")
    }

    /**
     * Returns the characters of the specified scrollback row as a String.
     * Row 0 is the oldest line in scrollback. Throws IndexOutOfBoundsException if out of bounds.
     */
    fun getScrollbackLine(row: Int): String {
        if (row !in 0 until scrollback.size) throw IndexOutOfBoundsException("scrollbackRow=$row size=${scrollback.size}")
        return scrollback[row].asSequence().map { it.char }.joinToString("")
    }

    /**
     * Returns the entire visible screen as lines joined with a newline character.
     * Lines are ordered from top (row 0) to bottom (row height-1).
     */
    fun getScreenAsString(): String {
        return screen.asSequence().map { row -> row.asSequence().map { it.char }.joinToString("") }.joinToString("\n")
    }

    /**
     * Returns all content as lines joined by newline: scrollback first (oldest to newest), then the screen.
     */
    fun getAllAsString(): String {
        val allLines = buildList {
            addAll(scrollback.asSequence().map { row -> row.asSequence().map { it.char }.joinToString("") })
            addAll(screen.asSequence().map { row -> row.asSequence().map { it.char }.joinToString("") })
        }
        return allLines.joinToString("\n")
    }

    // ---- Helpers ----

    /** Ensures scrollback size does not exceed maxScrollback by evicting oldest entries. */
    private fun enforceScrollbackCapacity() {
        if (maxScrollback <= 0) return
        val excess = scrollback.size - maxScrollback
        if (excess > 0) repeat(excess) { scrollback.removeAt(0) }
    }

    /** Returns a new empty row sized to the current width. */
    private fun emptyRow(): MutableList<Cell> = MutableList(width) { Cell.EMPTY }

    // Saves the current top visible row into scrollback (as an immutable snapshot), respecting capacity.
    private fun pushTopRowToScrollback() {
        if (height <= 0) return
        val top = screen.first().toList()
        if (maxScrollback > 0) {
            scrollback.add(top)
            enforceScrollbackCapacity()
        }
    }

    // Shifts the visible screen content up by one row and appends a fresh empty row at the bottom.
    private fun scrollUpOneRow() {
        if (height <= 0) return
        screen.removeAt(0)
        screen.add(emptyRow())
    }

    // Performs a scroll operation if the cursor has advanced beyond the last visible row.
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

    // Validates that the given screen row index is within visible bounds.
    private fun checkRowIndex(row: Int) {
        if (row !in 0 until height) throw IndexOutOfBoundsException("row=$row height=$height")
    }

    private fun checkColIndex(col: Int) {
        if (col !in 0 until width) throw IndexOutOfBoundsException("col=$col width=$width")
    }
}