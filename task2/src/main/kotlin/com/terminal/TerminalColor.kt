package com.terminal

/**
 * Represents terminal colors supported by the model.
 * Includes DEFAULT and the 16 standard ANSI colors with their bright variants.
 */
enum class TerminalColor {
    DEFAULT,
    // Standard 8 colors
    BLACK,
    RED,
    GREEN,
    YELLOW,
    BLUE,
    MAGENTA,
    CYAN,
    WHITE,
    // Bright variants
    BRIGHT_BLACK,
    BRIGHT_RED,
    BRIGHT_GREEN,
    BRIGHT_YELLOW,
    BRIGHT_BLUE,
    BRIGHT_MAGENTA,
    BRIGHT_CYAN,
    BRIGHT_WHITE
}
