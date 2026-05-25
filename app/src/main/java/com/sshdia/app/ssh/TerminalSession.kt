package com.sshdia.app.ssh

import android.os.Handler
import android.os.Looper
import com.sshdia.app.data.HostProfile
import com.sshdia.app.terminal.TerminalEmulator

/**
 * A live terminal: owns the VT emulator and the underlying SSH shell. The session
 * is independent of any UI. Output is processed on the main looper (so the emulator
 * is only ever touched from one thread) and the latest screen snapshot is exposed
 * for the UI to read. A [listener] is invoked on the main thread whenever the
 * screen or status changes.
 */
class TerminalSession(val profile: HostProfile) {

    private val handler = Handler(Looper.getMainLooper())
    private val emulator = TerminalEmulator(80, 24)
    private var shell: SshShellSession? = null

    @Volatile var cols = 80
        private set
    @Volatile var rows = 24
        private set

    var screenText: String = ""
        private set
    var cursorRow: Int = 0
        private set
    var cursorCharIndex: Int = 0
        private set
    var status: String = "연결 중..."
        private set
    @Volatile var closed = false
        private set

    var listener: (() -> Unit)? = null

    fun connect() {
        if (shell != null) return
        val shellSession = SshShellSession(
            profile = profile,
            cols = cols,
            rows = rows,
            onOutput = { data, n ->
                handler.post {
                    emulator.append(data, n)
                    refreshSnapshot()
                    listener?.invoke()
                }
            },
            onClosed = { err ->
                handler.post {
                    closed = true
                    status = err?.let { "연결 종료: $it" } ?: "연결이 종료되었습니다."
                    listener?.invoke()
                }
            }
        )
        shell = shellSession
        shellSession.connect()
    }

    private fun refreshSnapshot() {
        screenText = emulator.render()
        cursorRow = emulator.cursorRow
        cursorCharIndex = emulator.cursorCharIndex()
    }

    fun write(text: String) {
        if (text.isEmpty()) return
        shell?.writeText(text)
    }

    fun resize(newCols: Int, newRows: Int) {
        if (newCols == cols && newRows == rows) return
        cols = newCols
        rows = newRows
        handler.post {
            emulator.resize(newCols, newRows)
            refreshSnapshot()
            listener?.invoke()
        }
        shell?.resize(newCols, newRows)
    }

    fun close() {
        closed = true
        listener = null
        shell?.close()
        shell = null
    }
}
