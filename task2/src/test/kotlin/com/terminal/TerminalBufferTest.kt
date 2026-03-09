package com.terminal

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TerminalBufferTest {

    private fun newBuffer(w: Int = 5, h: Int = 3, sb: Int = 10) = TerminalBuffer(w, h, sb)

    private fun spaces(n: Int) = buildString { repeat(n) { append(' ') } }

    // ---- Cursor tests ----
    @Nested
    inner class CursorTests {
        @Test
        fun `setCursor clamps to (0,0) minimum`() {
            val tb = newBuffer(4, 2)
            tb.setCursor(-10, -5)
            assertEquals(0, tb.getCursorCol())
            assertEquals(0, tb.getCursorRow())
        }

        @Test
        fun `setCursor clamps to (width-1, height-1) maximum`() {
            val tb = newBuffer(4, 2)
            tb.setCursor(100, 100)
            assertEquals(3, tb.getCursorCol())
            assertEquals(1, tb.getCursorRow())
        }

        @Test
        fun `moveCursor relative moves correctly`() {
            val tb = newBuffer(5, 5)
            tb.setCursor(1, 1)
            tb.moveCursor(2, 3)
            assertEquals(3, tb.getCursorCol())
            assertEquals(4, tb.getCursorRow())
        }

        @Test
        fun `moveCursor clamps at all four boundaries`() {
            val tb = newBuffer(4, 3)
            // Left and top clamp
            tb.setCursor(1, 1)
            tb.moveCursor(-10, -10)
            assertEquals(0, tb.getCursorCol())
            assertEquals(0, tb.getCursorRow())
            // Right and bottom clamp
            tb.moveCursor(100, 100)
            assertEquals(3, tb.getCursorCol())
            assertEquals(2, tb.getCursorRow())
        }
    }

    // ---- Write tests ----
    @Nested
    inner class WriteTests {
        @Test
        fun `write single character updates cell and advances cursor`() {
            val tb = newBuffer(4, 2)
            tb.write("A")
            assertEquals('A', tb.getCell(0, 0).char)
            assertEquals(1, tb.getCursorCol())
            assertEquals(0, tb.getCursorRow())
        }

        @Test
        fun `write at end of line wraps to next line col 0`() {
            val tb = newBuffer(3, 2)
            tb.setCursor(2, 0) // last column
            tb.write("X")
            assertEquals('X', tb.getCell(0, 2).char)
            assertEquals(0, tb.getCursorCol())
            assertEquals(1, tb.getCursorRow())
        }

        @Test
        fun `write newline moves cursor to next line col 0`() {
            val tb = newBuffer(3, 2)
            tb.setCursor(2, 0)
            tb.write("\n")
            assertEquals(0, tb.getCursorCol())
            assertEquals(1, tb.getCursorRow())
        }

        @Test
        fun `write past last row scrolls screen (top row moves to scrollback)`() {
            val tb = newBuffer(3, 2, sb = 5)
            // Fill first line and cause wrap to second, then write one more to trigger scroll
            tb.write("ABC\n") // newline moves to row 1
            tb.write("DEF")    // writing will go to row 1 col 0..2, then wrap to row 2 -> scroll
            // After scrolling: original row0 should be in scrollback[0]
            assertDoesNotThrow { tb.getScrollbackCell(0, 0) }
            assertEquals('A', tb.getScrollbackCell(0, 0).char)
            assertEquals('B', tb.getScrollbackCell(0, 1).char)
            assertEquals('C', tb.getScrollbackCell(0, 2).char)
            // Cursor stays on last row
            assertEquals(tb.height - 1, tb.getCursorRow())
            assertEquals(0, tb.getCursorCol())
        }

        @Test
        fun `write text longer than width wraps correctly across multiple lines`() {
            val tb = newBuffer(3, 2)
            tb.write("ABCDE")
            // Expect row0 = ABC, row1 starts with DE and then space
            assertEquals("ABC", tb.getScreenLine(0))
            assertEquals("DE ", tb.getScreenLine(1))
        }

        @Test
        fun `write preserves currentAttributes on written cells`() {
            val tb = newBuffer(3, 2)
            tb.setAttributes(TerminalColor.BRIGHT_GREEN, TerminalColor.BLACK, setOf(TextStyle.BOLD, TextStyle.UNDERLINE))
            tb.write("Z")
            val cell = tb.getCell(0, 0)
            assertEquals('Z', cell.char)
            assertEquals(TerminalColor.BRIGHT_GREEN, cell.attributes.foreground)
            assertEquals(TerminalColor.BLACK, cell.attributes.background)
            assertEquals(setOf(TextStyle.BOLD, TextStyle.UNDERLINE), cell.attributes.styles)
        }

        @Test
        fun `scrollback does not exceed maxScrollback (oldest lines dropped)`() {
            val tb = newBuffer(2, 1, sb = 2) // height 1 so every wrap scrolls
            // This sequence will produce 3 lines into scrollback if unlimited
            tb.write("AB\n") // push 1
            tb.write("CD\n") // push 2
            tb.write("EF\n") // push 3 -> should evict oldest
            // Verify we can access indices 0 and 1 but not 2
            assertDoesNotThrow { tb.getScrollbackCell(0, 0) }
            assertDoesNotThrow { tb.getScrollbackCell(1, 0) }
            assertThrows(IndexOutOfBoundsException::class.java) { tb.getScrollbackCell(2, 0) }
            // And both remaining lines have correct width
            assertEquals(2, tb.getScrollbackLine(0).length)
            assertEquals(2, tb.getScrollbackLine(1).length)
        }
    }

    // ---- Insert tests ----
    @Nested
    inner class InsertTests {
        @Test
        fun `insert shifts existing chars right`() {
            val tb = newBuffer(5, 2)
            tb.write("HELLO")
            tb.setCursor(2, 0)
            tb.insert("X")
            assertEquals("HEXLL", tb.getScreenLine(0))
            assertEquals(3, tb.getCursorCol())
        }

        @Test
        fun `insert at end of line drops overflow characters`() {
            val tb = newBuffer(4, 2)
            tb.write("ABCD")
            tb.setCursor(3, 0)
            tb.insert("XYZ") // Only X fits at col3, Y and Z should drop
            assertEquals("ABCX", tb.getScreenLine(0))
            assertEquals(3, tb.getCursorCol()) // clamped to last column
        }

        @Test
        fun `insert at col 0 shifts entire line right`() {
            val tb = newBuffer(5, 2)
            tb.write("12345")
            tb.setCursor(0, 0)
            tb.insert("AA")
            assertEquals("AA123", tb.getScreenLine(0))
            assertEquals(2, tb.getCursorCol())
        }

        @Test
        fun `cursor advances by inserted length (clamped)`() {
            val tb = newBuffer(4, 1)
            tb.setCursor(2, 0)
            tb.insert("WXYZ")
            assertEquals(3, tb.getCursorCol()) // clamped to last column
        }
    }

    // ---- FillLine tests ----
    @Nested
    inner class FillLineTests {
        @Test
        fun `fillLine fills all cells with given char and currentAttributes`() {
            val tb = newBuffer(4, 2)
            tb.setAttributes(TerminalColor.RED, TerminalColor.CYAN, setOf(TextStyle.ITALIC))
            tb.fillLine(1, '#')
            for (c in 0 until 4) {
                val cell = tb.getCell(1, c)
                assertEquals('#', cell.char)
                assertEquals(TerminalColor.RED, cell.attributes.foreground)
                assertEquals(TerminalColor.CYAN, cell.attributes.background)
                assertEquals(setOf(TextStyle.ITALIC), cell.attributes.styles)
            }
        }

        @Test
        fun `fillLine does not move cursor`() {
            val tb = newBuffer(3, 2)
            tb.setCursor(2, 1)
            tb.fillLine(0, '*')
            assertEquals(2, tb.getCursorCol())
            assertEquals(1, tb.getCursorRow())
        }

        @Test
        fun `fillLine with default space produces empty-looking line`() {
            val tb = newBuffer(5, 1)
            tb.fillLine(0)
            assertEquals(spaces(5), tb.getScreenLine(0))
        }
    }

    // ---- Scrollback tests ----
    @Nested
    inner class ScrollbackTests {
        @Test
        fun `insertEmptyLineAtBottom moves top row to scrollback`() {
            val tb = newBuffer(3, 2, sb = 5)
            tb.write("ABC")
            tb.insertEmptyLineAtBottom()
            assertEquals("ABC", tb.getScrollbackLine(0))
            assertEquals(spaces(3), tb.getScreenLine(0)) // row0 removed; new row0 was old row1 (spaces)
        }

        @Test
        fun `insertEmptyLineAtBottom does not move cursor`() {
            val tb = newBuffer(3, 2)
            tb.setCursor(2, 1)
            tb.insertEmptyLineAtBottom()
            assertEquals(2, tb.getCursorCol())
            assertEquals(1, tb.getCursorRow())
        }

        @Test
        fun `scrollback is accessible via getScrollbackCell and getScrollbackLine`() {
            val tb = newBuffer(4, 2, sb = 5)
            tb.write("ABCD\nEFGH\n")
            // First line must be ABCD regardless of intermediate pushes
            assertEquals('A', tb.getScrollbackCell(0, 0).char)
            assertEquals('D', tb.getScrollbackCell(0, 3).char)
            assertEquals("ABCD", tb.getScrollbackLine(0))
            // Any additional lines, if present, are readable and have width length
            var i = 0
            while (true) {
                try {
                    val line = tb.getScrollbackLine(i)
                    assertEquals(4, line.length)
                    i++
                } catch (e: IndexOutOfBoundsException) {
                    break
                }
            }
            assertTrue(i >= 1)
        }

        @Test
        fun `scrollback is read-only (no editing operations affect it)`() {
            val tb = newBuffer(4, 2, sb = 100)
            tb.write("WXYZ\n") // push to scrollback
            val before = tb.getScrollbackLine(0)
            // Modify screen but avoid scrolling (no newline, no crossing bottom)
            tb.fillLine(0, '*')
            tb.setCursor(0, 0)
            tb.insert("QQ")
            tb.write("ABCD") // fits on current row without causing scroll
            val after = tb.getScrollbackLine(0)
            assertEquals(before, after) // unchanged
        }
    }

    // ---- Clear tests ----
    @Nested
    inner class ClearTests {
        @Test
        fun `clearScreen resets all cells to empty and cursor to (0,0)`() {
            val tb = newBuffer(3, 2)
            tb.write("ABCDEF")
            tb.setCursor(2, 1)
            tb.clearScreen()
            assertEquals(0, tb.getCursorCol())
            assertEquals(0, tb.getCursorRow())
            assertEquals(spaces(3), tb.getScreenLine(0))
            assertEquals(spaces(3), tb.getScreenLine(1))
        }

        @Test
        fun `clearScreen preserves scrollback`() {
            val tb = newBuffer(3, 1, sb = 5)
            tb.write("ABC\n") // push to scrollback
            tb.clearScreen()
            assertEquals("ABC", tb.getScrollbackLine(0))
        }

        @Test
        fun `clearAll removes both screen content and scrollback`() {
            val tb = newBuffer(3, 1, sb = 5)
            tb.write("ABC\n") // push to scrollback
            tb.clearAll()
            assertEquals(spaces(3), tb.getScreenLine(0))
            assertThrows(IndexOutOfBoundsException::class.java) { tb.getScrollbackCell(0, 0) }
        }
    }

    // ---- Content access tests ----
    @Nested
    inner class ContentAccessTests {
        @Test
        fun `getScreenLine returns correct string representation`() {
            val tb = newBuffer(4, 2)
            tb.write("Hi!")
            assertEquals("Hi! ", tb.getScreenLine(0))
        }

        @Test
        fun `getScreenAsString returns all screen lines joined by newline`() {
            val tb = newBuffer(3, 2)
            tb.write("ABCDEF")
            // Expected is the concatenation of individual screen lines with newline
            val expected = (0 until tb.height).joinToString("\n") { tb.getScreenLine(it) }
            assertEquals(expected, tb.getScreenAsString())
        }

        @Test
        fun `getAllAsString includes scrollback lines first, then screen`() {
            val tb = newBuffer(3, 2, sb = 5)
            tb.write("ABC\nDEF\nG") // ABC and DEF to scrollback, G on screen row0 col0
            // Build expected dynamically from getters (verifies ordering rather than duplicating logic)
            val expected = buildList {
                // scrollback first
                var i = 0
                while (true) {
                    try {
                        add(tb.getScrollbackLine(i))
                        i++
                    } catch (_: IndexOutOfBoundsException) {
                        break
                    }
                }
                // then screen lines
                for (r in 0 until tb.height) add(tb.getScreenLine(r))
            }.joinToString("\n")
            assertEquals(expected, tb.getAllAsString())
        }

        @Test
        fun `getCell returns correct cell at position`() {
            val tb = newBuffer(2, 2)
            tb.setAttributes(TerminalColor.BLUE, TerminalColor.YELLOW, emptySet())
            tb.write("Z")
            val cell = tb.getCell(0, 0)
            assertEquals('Z', cell.char)
            assertEquals(TerminalColor.BLUE, cell.attributes.foreground)
            assertEquals(TerminalColor.YELLOW, cell.attributes.background)
        }

        @Test
        fun `getScrollbackCell returns correct cell from history`() {
            val tb = newBuffer(3, 1, sb = 5)
            tb.write("ABC\n")
            assertEquals('B', tb.getScrollbackCell(0, 1).char)
        }
    }
}
