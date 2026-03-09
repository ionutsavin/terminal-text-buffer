package org.example
import com.terminal.TerminalBuffer
import com.terminal.TerminalColor
import com.terminal.TextStyle

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    println("=== Basic Write ===")
    val buf = TerminalBuffer(24, 80, 100)
    buf.write("Hello, World!")
    println(buf.getScreenLine(0))
    println("Cursor: (${buf.getCursorCol()}, ${buf.getCursorRow()})")

    println("\n=== Newline Handling ===")
    val buf2 = TerminalBuffer(24, 80, 100)
    buf2.write("Line 1\nLine 2\nLine 3")
    println(buf2.getScreenAsString().trimEnd())

    println("\n=== Scroll + Scrollback ===")
    val buf3 = TerminalBuffer(3, 10, 5)
    buf3.write("Row 0\nRow 1\nRow 2\nRow 3\nRow 4")
    println("Screen:")
    println(buf3.getScreenAsString().trimEnd())
    println("Scrollback lines: ${buf3.getScrollbackLine(0)}, ${buf3.getScrollbackLine(1)}")

    println("\n=== Attributes ===")
    val buf4 = TerminalBuffer(24, 80, 100)
    buf4.setAttributes(TerminalColor.GREEN, TerminalColor.BLACK, setOf(TextStyle.BOLD))
    buf4.write("Styled text")
    val cell = buf4.getCell(0, 0)
    println("Char: '${cell.char}' FG: ${cell.attributes.foreground} BG: ${cell.attributes.background} Styles: ${cell.attributes.styles}")

    println("\n=== Insert ===")
    val buf5 = TerminalBuffer(24, 80, 100)
    buf5.write("Hello World")
    buf5.setCursor(5, 0)
    buf5.insert(">>> ")
    println(buf5.getScreenLine(0))

    println("\n=== Fill Line ===")
    val buf6 = TerminalBuffer(24, 80, 100)
    buf6.write("Some content on line 0")
    buf6.fillLine(0, '-')
    println(buf6.getScreenLine(0).trimEnd())

    println("\n=== Clear Screen (preserves scrollback) ===")
    val buf7 = TerminalBuffer(3, 10, 10)
    buf7.write("A\nB\nC\nD")  // D scrolls A into scrollback
    buf7.clearScreen()
    println("Screen empty: ${buf7.getScreenAsString().isBlank()}")
    println("Scrollback line 0: '${buf7.getScrollbackLine(0).trimEnd()}'")

    println("\n=== Clear All ===")
    val buf8 = TerminalBuffer(3, 10, 10)
    buf8.write("A\nB\nC\nD")
    buf8.clearAll()
    println("Screen empty: ${buf8.getScreenAsString().isBlank()}")

    println("\n=== Scrollback Overflow (maxScrollback=2) ===")
    val buf9 = TerminalBuffer(2, 10, 2)
    buf9.write("Line0\nLine1\nLine2\nLine3\nLine4")
    println("Scrollback line 0: '${buf9.getScrollbackLine(0).trimEnd()}'")
    println("Scrollback line 1: '${buf9.getScrollbackLine(1).trimEnd()}'")

    println("\n=== Resize (if implemented) ===")
    try {
        val buf10 = TerminalBuffer(5, 10, 20)
        buf10.write("Hello\nWorld\nFoo")
        buf10.resize(8, 20)
        println("After resize to 20x8:")
        println(buf10.getScreenAsString().trimEnd())
    } catch (e: NotImplementedError) {
        println("Resize not implemented yet")
    } catch (e: Exception) {
        println("Resize error: ${e.message}")
    }

}