package com.sshdia.app.terminal

/**
 * A small VT100/xterm-subset terminal emulator.
 *
 * It keeps a character grid where wide (East-Asian) glyphs occupy two cells, so
 * Korean text aligns and is never truncated. Rendering is monochrome for now;
 * colors (SGR) are parsed and ignored. All access is expected from a single
 * thread (the UI thread in this app).
 */
class TerminalEmulator(cols: Int, rows: Int) {

    var columns = cols.coerceAtLeast(1)
        private set
    var screenRows = rows.coerceAtLeast(1)
        private set

    var cursorRow = 0
        private set
    var cursorCol = 0
        private set

    private var grid = Array(screenRows) { IntArray(columns) { BLANK } }
    private var savedRow = 0
    private var savedCol = 0
    private var wrapPending = false

    private var utf8Remaining = 0
    private var utf8Acc = 0

    private enum class State { GROUND, ESC, CSI, OSC, EAT_ONE }

    private var state = State.GROUND
    private val params = StringBuilder()

    fun resize(cols: Int, rows: Int) {
        val c = cols.coerceAtLeast(1)
        val r = rows.coerceAtLeast(1)
        if (c == columns && r == screenRows) return
        val newGrid = Array(r) { IntArray(c) { BLANK } }
        val copyRows = minOf(r, screenRows)
        val copyCols = minOf(c, columns)
        for (y in 0 until copyRows) {
            for (x in 0 until copyCols) newGrid[y][x] = grid[y][x]
        }
        grid = newGrid
        columns = c
        screenRows = r
        cursorRow = cursorRow.coerceIn(0, r - 1)
        cursorCol = cursorCol.coerceIn(0, c - 1)
        wrapPending = false
    }

    fun append(data: ByteArray, len: Int) {
        var i = 0
        while (i < len) {
            val b = data[i].toInt() and 0xFF
            i++
            if (utf8Remaining > 0) {
                if (b and 0xC0 == 0x80) {
                    utf8Acc = (utf8Acc shl 6) or (b and 0x3F)
                    utf8Remaining--
                    if (utf8Remaining == 0) putChar(utf8Acc)
                } else {
                    utf8Remaining = 0
                    processByte(b)
                }
                continue
            }
            when {
                b < 0x80 -> processByte(b)
                b in 0xC0..0xDF -> { utf8Acc = b and 0x1F; utf8Remaining = 1 }
                b in 0xE0..0xEF -> { utf8Acc = b and 0x0F; utf8Remaining = 2 }
                b in 0xF0..0xF7 -> { utf8Acc = b and 0x07; utf8Remaining = 3 }
                else -> { /* invalid lead byte */ }
            }
        }
    }

    private fun processByte(b: Int) {
        when (state) {
            State.GROUND -> groundByte(b)
            State.ESC -> escByte(b)
            State.CSI -> csiByte(b)
            State.OSC -> oscByte(b)
            State.EAT_ONE -> state = State.GROUND
        }
    }

    private fun groundByte(b: Int) {
        when (b) {
            0x1B -> state = State.ESC
            0x07 -> {}
            0x08 -> { wrapPending = false; if (cursorCol > 0) cursorCol-- }
            0x09 -> { wrapPending = false; cursorCol = minOf(((cursorCol / 8) + 1) * 8, columns - 1) }
            0x0A, 0x0B, 0x0C -> { wrapPending = false; lineFeed() }
            0x0D -> { wrapPending = false; cursorCol = 0 }
            else -> if (b >= 0x20) putChar(b)
        }
    }

    private fun escByte(b: Int) {
        when (b.toChar()) {
            '[' -> { state = State.CSI; params.setLength(0) }
            ']' -> state = State.OSC
            '(', ')', '*', '+' -> state = State.EAT_ONE
            'M' -> { reverseLineFeed(); state = State.GROUND }
            '7' -> { savedRow = cursorRow; savedCol = cursorCol; state = State.GROUND }
            '8' -> { cursorRow = savedRow; cursorCol = savedCol; state = State.GROUND }
            else -> state = State.GROUND
        }
    }

    private fun csiByte(b: Int) {
        val c = b.toChar()
        when {
            c in '0'..'9' || c == ';' || c == ':' || c == '?' || c == '<' ||
                c == '=' || c == '>' -> if (c in '0'..'9' || c == ';') params.append(c)
            b in 0x20..0x2F -> {}
            b in 0x40..0x7E -> { dispatchCsi(c); state = State.GROUND }
            else -> state = State.GROUND
        }
    }

    private fun oscByte(b: Int) {
        when (b) {
            0x07 -> state = State.GROUND
            0x1B -> state = State.EAT_ONE
        }
    }

    private fun dispatchCsi(final: Char) {
        wrapPending = false
        val parts = params.toString().split(';')
        fun p(i: Int, def: Int): Int {
            val v = parts.getOrNull(i)?.toIntOrNull() ?: return def
            return v
        }
        when (final) {
            'A' -> cursorRow = (cursorRow - maxOf(1, p(0, 1))).coerceAtLeast(0)
            'B' -> cursorRow = (cursorRow + maxOf(1, p(0, 1))).coerceAtMost(screenRows - 1)
            'C' -> cursorCol = (cursorCol + maxOf(1, p(0, 1))).coerceAtMost(columns - 1)
            'D' -> cursorCol = (cursorCol - maxOf(1, p(0, 1))).coerceAtLeast(0)
            'E' -> { cursorRow = (cursorRow + maxOf(1, p(0, 1))).coerceAtMost(screenRows - 1); cursorCol = 0 }
            'F' -> { cursorRow = (cursorRow - maxOf(1, p(0, 1))).coerceAtLeast(0); cursorCol = 0 }
            'G', '`' -> cursorCol = (p(0, 1) - 1).coerceIn(0, columns - 1)
            'd' -> cursorRow = (p(0, 1) - 1).coerceIn(0, screenRows - 1)
            'H', 'f' -> {
                cursorRow = (p(0, 1) - 1).coerceIn(0, screenRows - 1)
                cursorCol = (p(1, 1) - 1).coerceIn(0, columns - 1)
            }
            'J' -> eraseDisplay(p(0, 0))
            'K' -> eraseLine(p(0, 0))
            'P' -> deleteChars(maxOf(1, p(0, 1)))
            '@' -> insertBlanks(maxOf(1, p(0, 1)))
            'X' -> eraseChars(maxOf(1, p(0, 1)))
            's' -> { savedRow = cursorRow; savedCol = cursorCol }
            'u' -> { cursorRow = savedRow; cursorCol = savedCol }
            else -> {} // m (SGR), h/l (modes), r (scroll region), etc. ignored
        }
    }

    private fun putChar(cp: Int) {
        val w = charWidth(cp)
        if (w <= 0) return
        if (wrapPending) {
            cursorCol = 0
            lineFeed()
            wrapPending = false
        }
        if (cursorCol + w > columns) {
            cursorCol = 0
            lineFeed()
        }
        grid[cursorRow][cursorCol] = cp
        if (w == 2 && cursorCol + 1 < columns) grid[cursorRow][cursorCol + 1] = WIDE_CONT
        cursorCol += w
        if (cursorCol >= columns) {
            cursorCol = columns - 1
            wrapPending = true
        }
    }

    private fun lineFeed() {
        if (cursorRow >= screenRows - 1) scrollUp() else cursorRow++
    }

    private fun reverseLineFeed() {
        if (cursorRow == 0) scrollDown() else cursorRow--
    }

    private fun scrollUp() {
        for (y in 0 until screenRows - 1) grid[y] = grid[y + 1]
        grid[screenRows - 1] = IntArray(columns) { BLANK }
    }

    private fun scrollDown() {
        for (y in screenRows - 1 downTo 1) grid[y] = grid[y - 1]
        grid[0] = IntArray(columns) { BLANK }
    }

    private fun eraseLine(mode: Int) {
        val row = grid[cursorRow]
        val col = cursorCol.coerceIn(0, columns - 1)
        when (mode) {
            0 -> for (x in col until columns) row[x] = BLANK
            1 -> for (x in 0..col) row[x] = BLANK
            2 -> for (x in 0 until columns) row[x] = BLANK
        }
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(0)
                for (y in cursorRow + 1 until screenRows) blankRow(y)
            }
            1 -> {
                for (y in 0 until cursorRow) blankRow(y)
                eraseLine(1)
            }
            2, 3 -> for (y in 0 until screenRows) blankRow(y)
        }
    }

    private fun blankRow(y: Int) {
        val row = grid[y]
        for (x in 0 until columns) row[x] = BLANK
    }

    private fun deleteChars(n: Int) {
        val row = grid[cursorRow]
        val col = cursorCol.coerceIn(0, columns - 1)
        for (x in col until columns) {
            val src = x + n
            row[x] = if (src < columns) row[src] else BLANK
        }
    }

    private fun insertBlanks(n: Int) {
        val row = grid[cursorRow]
        val col = cursorCol.coerceIn(0, columns - 1)
        var x = columns - 1
        while (x >= col) {
            val src = x - n
            row[x] = if (src >= col) row[src] else BLANK
            x--
        }
    }

    private fun eraseChars(n: Int) {
        val row = grid[cursorRow]
        val col = cursorCol.coerceIn(0, columns - 1)
        for (x in col until minOf(columns, col + n)) row[x] = BLANK
    }

    fun render(): String {
        val sb = StringBuilder(screenRows * (columns + 1))
        for (y in 0 until screenRows) {
            val row = grid[y]
            var last = columns - 1
            while (last >= 0 && (row[last] == BLANK || row[last] == WIDE_CONT)) last--
            var x = 0
            while (x <= last) {
                val cell = row[x]
                if (cell != WIDE_CONT) {
                    sb.appendCodePoint(if (cell == BLANK) ' '.code else cell)
                }
                x++
            }
            if (y < screenRows - 1) sb.append('\n')
        }
        return sb.toString()
    }

    private companion object {
        const val BLANK = ' '.code
        const val WIDE_CONT = -1
    }
}
