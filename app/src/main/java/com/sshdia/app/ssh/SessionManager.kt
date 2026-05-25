package com.sshdia.app.ssh

import android.content.Context
import com.sshdia.app.data.HostProfile
import com.sshdia.app.service.SshSessionService

/**
 * Keeps SSH terminal sessions alive independently of the UI. Sessions survive
 * back navigation and (via [SshSessionService]) app backgrounding; they are only
 * torn down when explicitly closed.
 */
object SessionManager {

    private val sessions = LinkedHashMap<String, TerminalSession>()

    @Synchronized
    fun getOrCreate(context: Context, profile: HostProfile): TerminalSession {
        val existing = sessions[profile.id]
        if (existing != null && !existing.closed) return existing
        if (existing != null) sessions.remove(profile.id)

        val session = TerminalSession(profile)
        sessions[profile.id] = session
        session.connect()
        runCatching { SshSessionService.start(context.applicationContext, sessions.size) }
        return session
    }

    @Synchronized
    fun get(id: String): TerminalSession? = sessions[id]

    @Synchronized
    fun activeIds(): Set<String> = sessions.keys.toSet()

    @Synchronized
    fun count(): Int = sessions.size

    @Synchronized
    fun close(context: Context, id: String) {
        sessions.remove(id)?.close()
        val app = context.applicationContext
        if (sessions.isEmpty()) {
            runCatching { SshSessionService.stop(app) }
        } else {
            runCatching { SshSessionService.start(app, sessions.size) }
        }
    }
}
