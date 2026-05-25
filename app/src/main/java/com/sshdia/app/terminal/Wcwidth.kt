package com.sshdia.app.terminal

/**
 * Display width of a Unicode code point in terminal cells.
 *
 * Returns 0 for combining/zero-width marks, 2 for East-Asian wide characters
 * (including Hangul syllables U+AC00..U+D7A3) and 1 otherwise.
 *
 * Treating Hangul as 2 cells is the fix for the common "last Korean character
 * gets cut off" bug seen in terminals that assume every character is 1 cell.
 */
fun charWidth(cp: Int): Int {
    if (cp == 0) return 0
    if (cp < 0x20 || cp in 0x7F..0x9F) return 0
    if (isZeroWidth(cp)) return 0
    return if (isWide(cp)) 2 else 1
}

private fun isZeroWidth(cp: Int): Boolean =
    cp == 0x200B ||
        cp in 0x0300..0x036F ||
        cp in 0x0483..0x0489 ||
        cp in 0x0591..0x05BD ||
        cp in 0x0610..0x061A ||
        cp in 0x064B..0x065F ||
        cp == 0x0670 ||
        cp in 0x06D6..0x06DC ||
        cp in 0x1160..0x11FF ||
        cp in 0x1AB0..0x1AFF ||
        cp in 0x1DC0..0x1DFF ||
        cp in 0x20D0..0x20FF ||
        cp in 0xFE20..0xFE2F

private fun isWide(cp: Int): Boolean =
    cp in 0x1100..0x115F ||
        cp == 0x2329 || cp == 0x232A ||
        cp in 0x2E80..0x303E ||
        cp in 0x3041..0x33FF ||
        cp in 0x3400..0x4DBF ||
        cp in 0x4E00..0x9FFF ||
        cp in 0xA000..0xA4CF ||
        cp in 0xAC00..0xD7A3 ||
        cp in 0xF900..0xFAFF ||
        cp in 0xFE10..0xFE19 ||
        cp in 0xFE30..0xFE6F ||
        cp in 0xFF00..0xFF60 ||
        cp in 0xFFE0..0xFFE6 ||
        cp in 0x1F300..0x1FAFF ||
        cp in 0x20000..0x3FFFD
