package com.terminal

/**
 * Represents a single character cell in the terminal buffer.
 */
data class Cell(
    val char: Char = ' ',
    val attributes: CellAttributes = CellAttributes.DEFAULT
) {
    companion object {
        val EMPTY: Cell = Cell()
    }
}