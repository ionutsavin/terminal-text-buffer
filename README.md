# Terminal Text Buffer (Kotlin)

A small, dependency-free Kotlin library that models a terminal screen buffer with scrollback history. It provides a fixed-size visible screen (rows × columns), immutable scrollback history, a cursor, cell attributes (colors and styles), and basic editing operations. A simple `Main.kt` is included for manual exploration.

## 1. Project overview
A terminal buffer is an in-memory representation of what a terminal displays:
- A fixed-size grid of character cells (the visible screen)
- A scrollback history of previous lines once they leave the screen
- A cursor that determines where the next write occurs

This project implements such a buffer with:
- Per-cell attributes (foreground/background color and styles)
- Cursor movement and clamped positioning
- Editing operations (write, insert, fill line, clears)
- Scrollback management with a maximum capacity
- Resizing the visible screen with width/height adjustments

## 2. Features
- Core data model:
  - `TerminalColor` (DEFAULT + 16 ANSI colors)
  - `TextStyle` (BOLD, ITALIC, UNDERLINE)
  - `CellAttributes` (immutable, with defaults)
  - `Cell` (character + attributes, with `EMPTY`)
- Terminal buffer operations (`TerminalBuffer`):
  - Cursor: `getCursorCol`, `getCursorRow`, `setCursor`, `moveCursor`
  - Attributes: `setAttributes`
  - Editing: `write`, `insert`, `fillLine`, `insertEmptyLineAtBottom`, `clearScreen`, `clearAll`
  - Resize: `resize(newWidth, newHeight)` (bonus)
  - Content access: `getCell`, `getScrollbackCell`, `getScreenLine`, `getScrollbackLine`, `getScreenAsString`, `getAllAsString`

## 3. Project structure
- `src/main/kotlin/com/terminal/TerminalColor.kt` — enum of supported terminal colors
- `src/main/kotlin/com/terminal/TextStyle.kt` — enum of text styles
- `src/main/kotlin/com/terminal/CellAttributes.kt` — immutable cell attributes with defaults
- `src/main/kotlin/com/terminal/Cell.kt` — a single character cell with attributes
- `src/main/kotlin/com/terminal/TerminalBuffer.kt` — terminal buffer implementation (screen + scrollback)
- `src/main/kotlin/Main.kt` — minimal demo for manual testing
- `src/test/kotlin/com/terminal/TerminalBufferTest.kt` — comprehensive JUnit 5 test suite
- `DECISIONS.md` — architecture decisions, trade-offs, and known limitations
- `build.gradle.kts`, `settings.gradle.kts` — Gradle build configuration

Note: Exact classpath values depend on your environment/installation. Using an IDE is typically the simplest way to run the demo.

## 6. Architecture details
See `DECISIONS.md` for rationale behind the design (screen vs. scrollback separation, immutability choices, insert semantics, data structure trade-offs, and known limitations).
