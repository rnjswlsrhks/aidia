package com.sshdia.app.terminal

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * A transparent, focusable view that captures keyboard input for the terminal.
 *
 * Committed text (including completed Hangul syllables) is forwarded to [onInput]
 * and sent to the shell. While the IME is composing (e.g. a half-typed Korean
 * syllable) nothing is sent yet — the in-progress text is reported via
 * [onComposing] so it can be shown at the cursor — and it is sent only once the
 * IME commits it. This is what avoids the "last Hangul character gets cut off"
 * problem seen when composing bytes are sent prematurely.
 */
class TerminalInputView(context: Context) : View(context) {

    var onInput: (String) -> Unit = {}
    var onComposing: (String) -> Unit = {}

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(0x00000000)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { showKeyboard() }
    }

    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            showKeyboard()
            performClick()
            return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN
        return TerminalInputConnection()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleSpecialKey(event)) return true
        val ch = event.unicodeChar
        if (ch != 0) {
            onInput(ch.toChar().toString())
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleSpecialKey(event: KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            val c = event.getUnicodeChar(0)
            when (c) {
                in 'a'.code..'z'.code -> { onInput(((c - 'a'.code) + 1).toChar().toString()); return true }
                in 'A'.code..'Z'.code -> { onInput(((c - 'A'.code) + 1).toChar().toString()); return true }
            }
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> { onInput("\r"); return true }
            KeyEvent.KEYCODE_DEL -> { onInput("\u007f"); return true }
            KeyEvent.KEYCODE_FORWARD_DEL -> { onInput("\u001b[3~"); return true }
            KeyEvent.KEYCODE_ESCAPE -> { onInput("\u001b"); return true }
            KeyEvent.KEYCODE_TAB -> { onInput("\t"); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { onInput("\u001b[A"); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { onInput("\u001b[B"); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { onInput("\u001b[D"); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { onInput("\u001b[C"); return true }
        }
        return false
    }

    private inner class TerminalInputConnection :
        BaseInputConnection(this@TerminalInputView, true) {

        private val composing = StringBuilder()

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            composing.setLength(0)
            onComposing("")
            val s = text.toString().replace("\n", "\r")
            if (s.isNotEmpty()) onInput(s)
            return true
        }

        override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
            composing.setLength(0)
            composing.append(text)
            onComposing(composing.toString())
            return true
        }

        override fun finishComposingText(): Boolean {
            if (composing.isNotEmpty()) {
                onInput(composing.toString())
                composing.setLength(0)
                onComposing("")
            }
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (composing.isEmpty()) {
                repeat(beforeLength.coerceAtLeast(0)) { onInput("\u007f") }
                return true
            }
            return super.deleteSurroundingText(beforeLength, afterLength)
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN && handleSpecialKey(event)) {
                return true
            }
            return super.sendKeyEvent(event)
        }
    }
}
