package com.sshdia.app.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.sshdia.app.data.HostProfile
import java.io.ByteArrayOutputStream
import java.util.Properties

/**
 * Minimal SSH helper used by step 1: open a session, run a single command and
 * return its combined output decoded as UTF-8 (so Korean output is preserved).
 *
 * The interactive terminal (shell channel + emulator) arrives in step 2.
 */
object SshClient {

    data class CommandResult(
        val output: String,
        val exitStatus: Int
    )

    fun runCommand(
        profile: HostProfile,
        command: String,
        connectTimeoutMs: Int = 15000
    ): CommandResult {
        val session = openSession(profile, connectTimeoutMs)
        try {
            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            val errStream = ByteArrayOutputStream()
            channel.setErrStream(errStream)
            val stdout = channel.inputStream

            val out = ByteArrayOutputStream()
            channel.connect(connectTimeoutMs)

            val buffer = ByteArray(8192)
            while (true) {
                while (stdout.available() > 0) {
                    val n = stdout.read(buffer, 0, buffer.size)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                if (channel.isClosed) {
                    if (stdout.available() > 0) continue
                    break
                }
                Thread.sleep(50)
            }

            val exit = channel.exitStatus
            channel.disconnect()

            val text = buildString {
                append(out.toString(Charsets.UTF_8.name()))
                val err = errStream.toString(Charsets.UTF_8.name())
                if (err.isNotBlank()) {
                    if (isNotEmpty() && !endsWith("\n")) append('\n')
                    append(err)
                }
            }
            return CommandResult(text, exit)
        } finally {
            session.disconnect()
        }
    }

    private fun openSession(profile: HostProfile, connectTimeoutMs: Int): Session {
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

        val session = jsch.getSession(profile.username, profile.host, profile.port)
        if (profile.privateKeyPem.isBlank()) {
            session.setPassword(profile.password)
        }

        val config = Properties()
        // TODO(step 2+): proper host-key verification with a known_hosts store.
        config["StrictHostKeyChecking"] = "no"
        config["PreferredAuthentications"] = "publickey,keyboard-interactive,password"
        session.setConfig(config)
        session.connect(connectTimeoutMs)
        return session
    }
}
