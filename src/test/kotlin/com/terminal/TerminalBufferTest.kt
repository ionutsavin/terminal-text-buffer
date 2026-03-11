package com.terminal

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class TerminalBufferTest {

    private fun newBuffer(w: Int = 5, h: Int = 3, sb: Int = 10) = TerminalBuffer(h, w, sb)
    private fun spaces(n: Int) = buildString { repeat(n) { append(' ') } }

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
            tb.setCursor(1, 1)
            tb.moveCursor(-10, -10)
            assertEquals(0, tb.getCursorCol())
            assertEquals(0, tb.getCursorRow())
            tb.moveCursor(100, 100)
            assertEquals(3, tb.getCursorCol())
            assertEquals(2, tb.getCursorRow())
        }
    }

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
            tb.setCursor(2, 0)
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
        fun `write only newlines scrolls correctly with no characters written`() {
            val tb = newBuffer(3, 2, sb = 5)
            tb.write("ABC\nDEF")
            tb.write("\n\n")
            assertEquals(tb.height - 1, tb.getCursorRow())
            assertEquals(0, tb.getCursorCol())
        }

        @Test
        fun `write past last row scrolls screen top row moves to scrollback`() {
            val tb = newBuffer(3, 2, sb = 5)
            tb.write("ABC\n")
            tb.write("DEF")
            assertEquals('A', tb.getScrollbackCell(0, 0).char)
            assertEquals('B', tb.getScrollbackCell(0, 1).char)
            assertEquals('C', tb.getScrollbackCell(0, 2).char)
            assertEquals(tb.height - 1, tb.getCursorRow())
        }

        @Test
        fun `write exactly filling screen without overflow does not go out of bounds`() {
            val tb = newBuffer(3, 2, sb = 5)
            tb.write("ABCDEF")
            assertEquals("ABC", tb.getScreenLine(0))
            assertEquals("DEF", tb.getScreenLine(1))
            assertEquals(tb.height - 1, tb.getCursorRow())
        }

        @Test
        fun `write text longer than width wraps correctly across multiple lines`() {
            val tb = newBuffer(3, 2)
            tb.write("ABCDE")
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
        fun `scrollback does not exceed maxScrollback oldest lines dropped`() {
            val tb = newBuffer(2, 1, sb = 2)
            tb.write("AB\n")
            tb.write("CD\n")
            tb.write("EF\n")
            assertDoesNotThrow { tb.getScrollbackCell(0, 0) }
            assertDoesNotThrow { tb.getScrollbackCell(1, 0) }
            assertThrows(IndexOutOfBoundsException::class.java) { tb.getScrollbackCell(2, 0) }
            assertEquals(2, tb.getScrollbackLine(0).length)
            assertEquals(2, tb.getScrollbackLine(1).length)
        }

        @Test
        fun `write with maxScrollback zero discards scrolled lines silently`() {
            val tb = newBuffer(3, 1, sb = 0)
            tb.write("ABC\n")
            tb.write("DEF")
            assertThrows(IndexOutOfBoundsException::class.java) { tb.getScrollbackCell(0, 0) }
            assertEquals("DEF", tb.getScreenLine(0))
        }
    }

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
            tb.insert("XYZ")
            assertEquals("ABCX", tb.getScreenLine(0))
            assertEquals(3, tb.getCursorCol())
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
        fun `cursor advances by inserted length clamped to last column`() {
            val tb = newBuffer(4, 1)
            tb.setCursor(2, 0)
            tb.insert("WXYZ")
            assertEquals(3, tb.getCursorCol())
        }

        @Test
        fun `insert empty string is a no-op and cursor does not move`() {
            val tb = newBuffer(4, 2)
            tb.write("ABCD")
            tb.setCursor(2, 0)
            tb.insert("")
            assertEquals("ABCD", tb.getScreenLine(0))
            assertEquals(2, tb.getCursorCol())
            assertEquals(0, tb.getCursorRow())
        }
    }

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

        @Test
        fun `fillLine on out-of-bounds row throws IndexOutOfBoundsException`() {
            val tb = newBuffer(3, 2)
            assertThrows(IndexOutOfBoundsException::class.java) { tb.fillLine(-1, 'X') }
            assertThrows(IndexOutOfBoundsException::class.java) { tb.fillLine(2, 'X') }
        }
    }

    @Nested
    inner class ScrollbackTests {

        @Test
        fun `insertEmptyLineAtBottom moves top row to scrollback`() {
            val tb = newBuffer(3, 2, sb = 5)
            tb.write("ABC")
            tb.insertEmptyLineAtBottom()
            assertEquals("ABC", tb.getScrollbackLine(0))
            assertEquals(spaces(3), tb.getScreenLine(0))
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
            assertEquals('A', tb.getScrollbackCell(0, 0).char)
            assertEquals('D', tb.getScrollbackCell(0, 3).char)
            assertEquals("ABCD", tb.getScrollbackLine(0))
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
        fun `scrollback is read-only editing operations do not affect it`() {
            val tb = newBuffer(4, 2, sb = 100)
            tb.write("WXYZ\n")
            val before = tb.getScrollbackLine(0)
            tb.setCursor(0, 0)
            tb.fillLine(0, '*')
            tb.setCursor(0, 0)
            tb.insert("QQ")
            tb.setCursor(0, 0)
            tb.write("AB")
            val after = tb.getScrollbackLine(0)
            assertEquals(before, after)
        }

        @Test
        fun `scrollback with maxScrollback zero keeps no history`() {
            val tb = newBuffer(3, 1, sb = 0)
            tb.insertEmptyLineAtBottom()
            tb.insertEmptyLineAtBottom()
            assertThrows(IndexOutOfBoundsException::class.java) { tb.getScrollbackLine(0) }
        }
    }

    @Nested
    inner class ClearTests {

        @Test
        fun `clearScreen resets all cells to empty and cursor to (0,0)`() {
            val tb = newBuffer(3, 2)
            tb.write("ABCDEF")
            tb.clearScreen()
            assertEquals(0, tb.getCursorCol())
            assertEquals(0, tb.getCursorRow())
            assertEquals(spaces(3), tb.getScreenLine(0))
            assertEquals(spaces(3), tb.getScreenLine(1))
        }

        @Test
        fun `clearScreen preserves scrollback`() {
            val tb = newBuffer(3, 1, sb = 5)
            tb.write("ABC\n")
            tb.clearScreen()
            assertEquals("ABC", tb.getScrollbackLine(0))
        }

        @Test
        fun `clearAll removes both screen content and scrollback`() {
            val tb = newBuffer(3, 1, sb = 5)
            tb.write("ABC\n")
            tb.clearAll()
            assertEquals(spaces(3), tb.getScreenLine(0))
            assertThrows(IndexOutOfBoundsException::class.java) { tb.getScrollbackCell(0, 0) }
        }
    }

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
            val expected = (0 until tb.height).joinToString("\n") { tb.getScreenLine(it) }
            assertEquals(expected, tb.getScreenAsString())
        }

        @Test
        fun `getAllAsString includes scrollback lines first then screen`() {
            val tb = newBuffer(3, 2, sb = 5)
            tb.write("ABC\nDEF\nG")
            val expected = buildList {
                var i = 0
                while (true) {
                    try { add(tb.getScrollbackLine(i++)) }
                    catch (_: IndexOutOfBoundsException) { break }
                }
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