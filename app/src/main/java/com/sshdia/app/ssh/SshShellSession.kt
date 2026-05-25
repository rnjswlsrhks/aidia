package com.sshdia.app.ssh

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.sshdia.app.data.HostProfile
import java.io.OutputStream
import java.util.Properties

/**
 * An interactive SSH shell over a pseudo-terminal. Output bytes are delivered to
 * [onOutput] from a background reader thread; the caller is responsible for
 * marshalling them onto the UI thread.
 */
class SshShellSession(
    private val profile: HostProfile,
    private var cols: Int,
    private var rows: Int,
    private val onOutput: (ByteArray, Int) -> Unit,
    private val onClosed: (String?) -> Unit
) {
    @Volatile private var session: Session? = null
    @Volatile private var channel: ChannelShell? = null
    @Volatile private var out: OutputStream? = null
    @Volatile private var closed = false

    fun connect() {
        Thread {
            try {
                val jsch = JSch()
                if (profile.privateKeyPem.isNotBlank()) {
                    val passphrase =
                        if (profile.password.isNotBlank()) profile.password.toByteArray() else null
                    jsch.addIdentity(
                        profile.displayName,
                        profile.privateKeyPem.toByteArray(),
                        null,
                        passphrase
                    )
                }
                val s = jsch.getSession(profile.username, profile.host, profile.port)
                if (profile.privateKeyPem.isBlank()) s.setPassword(profile.password)
                val cfg = Properties()
                cfg["StrictHostKeyChecking"] = "no"
                cfg["PreferredAuthentications"] = "publickey,keyboard-interactive,password"
                s.setConfig(cfg)
                s.connect(20000)
                session = s

                val ch = s.openChannel("shell") as ChannelShell
                ch.setPtyType("xterm-256color", cols, rows, 0, 0)
                runCatching {
                    ch.setEnv("LANG", "ko_KR.UTF-8")
                    ch.setEnv("LC_ALL", "ko_KR.UTF-8")
                }
                val input = ch.inputStream
                out = ch.outputStream
                ch.connect(20000)
                channel = ch

                val buf = ByteArray(8192)
                while (!closed) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) onOutput(buf.copyOf(n), n)
                }
                if (!closed) onClosed(null)
            } catch (e: Exception) {
                if (!closed) onClosed(e.message ?: e.toString())
            } finally {
                cleanup()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun writeText(text: String) {
        val o = out ?: return
        try {
            o.write(text.toByteArray(Charsets.UTF_8))
            o.flush()
        } catch (_: Exception) {
        }
    }

    fun resize(newCols: Int, newRows: Int) {
        cols = newCols
        rows = newRows
        val ch = channel ?: return
        runCatching { ch.setPtySize(newCols, newRows, 0, 0) }
    }

    fun close() {
        closed = true
        cleanup()
    }

    private fun cleanup() {
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
    }
}
