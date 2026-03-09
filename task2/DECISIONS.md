# Architecture and Design Decisions

This document captures the key architectural choices, trade‑offs, and known limitations of the terminal buffer implemented in this project.

## 1. Architecture decisions

### 1.1 Separate data structures for screen and scrollback
- The visible screen is a fixed-size grid that must support in-place edits (writes, inserts, fills) with frequent random access.
- Scrollback represents historical lines that should not be mutated after they have left the screen.
- Keeping them separate allows:
  - Simple and efficient operations on the screen without risking accidental edits to history.
  - Clear, predictable semantics: once a line leaves the screen, it becomes immutable history.
  - Independent sizing/capacity handling for scrollback without coupling it to the screen buffer’s internal layout.

### 1.2 Immutability of `CellAttributes` (data class)
- Attributes are applied at write time and then considered a snapshot of style for that cell.
- Making `CellAttributes` immutable ensures:
  - Previously written cells are not affected by future attribute changes.
  - Safer sharing and reuse of attribute instances without defensive copying.
  - Simpler reasoning about state and easier testing.

### 1.3 `insert()` drops overflow instead of wrapping
- `insert()` is designed as an in-row operation only. It shifts existing characters right within the current row and discards any overflow beyond the last column.
- Reasons:
  - Predictable, local effect limited to a single visible line.
  - Matches common terminal/editing behaviors where insert within a line does not implicitly reflow subsequent lines.
  - Avoids complex wrap/reflow logic and interactions with scrolling that belong to higher-level text-editing models.

### 1.4 Scrollback rows as `List<Cell>` (immutable) vs screen rows as `MutableList<Cell>`
- Screen rows need to be edited in place for performance and simplicity, hence `MutableList<Cell>`.
- Scrollback rows should be immutable snapshots of past content to:
  - Guarantee historical integrity (read-only access only).
  - Prevent accidental mutation via shared references.
  - Allow safe sharing without copying in read paths.

## 2. Trade-offs made

### 2.1 `MutableList` grid for the screen vs a 2D array
- Chosen: `MutableList<MutableList<Cell>>`.
- Pros:
  - Idiomatic Kotlin collections, easy row-level operations (replace, resize, snapshot).
  - Flexible resizing behavior without manual array management.
- Cons:
  - Slight overhead vs primitive arrays; indirect addressing compared to a flat array.
- Rationale:
  - Clarity and maintainability were prioritized over micro-optimizations. The data size is modest for typical terminal dimensions, and operations are dominated by per-character logic rather than collection overhead.

### 2.2 Scrollback trimming strategy: simple list vs deque
- Chosen: simple `MutableList<List<Cell>>` with remove-from-front when over capacity.
- Pros:
  - Minimal code and easy to understand.
  - Sufficient for expected workloads in tests and typical use.
- Cons:
  - Removing from the front of an `ArrayList`-backed list is O(n).
- Rationale:
  - Simplicity now; can be replaced by a deque/ring buffer if profiling shows it as a bottleneck.

## 3. Known limitations and future improvements

### 3.1 Wide character support (CJK, emoji)
- Currently, each `Cell` represents a single `Char` and assumes 1-column width.
- Wide characters that occupy 2 columns are not handled (no placeholder/continuation cell semantics).
- Future work: introduce a width-aware glyph model (e.g., leading/trailing marker cells) or a grapheme cluster representation.

### 3.2 ANSI escape code parsing layer
- The buffer accepts already-decoded text and attributes, with no built-in ANSI/VT sequence parser.
- Future work: add a parsing layer that translates ANSI escape sequences into buffer operations (attribute changes, cursor movement, clears, etc.).

### 3.3 Reflow on resize
- Current resize truncates or pads lines to the new width; it does not reflow wrapped text across lines.
- Future work: implement soft-wrap tracking and line reflow on width changes, preserving logical lines across resize events.

### 3.4 Constructor parameter validation
- Negative dimensions are coerced or degrade into mostly no-op behavior; no explicit validation errors are thrown.
- Future work: validate constructor and resize parameters and throw `IllegalArgumentException` for invalid sizes; add tests covering these cases.

---

This document reflects the design intent to keep the core buffer predictable, testable, and easy to evolve. More advanced terminal features (full ANSI parsing, grapheme handling, and efficient data structures for large histories) can be layered on as needed.