package com.sshdia.app.data

import java.util.UUID

/**
 * A saved SSH/SFTP connection. Credentials are encrypted at rest by [HostStore].
 */
data class HostProfile(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val password: String = "",
    val privateKeyPem: String = ""
) {
    val displayName: String
        get() = label.ifBlank { "$username@$host" }

    val target: String
        get() = "$username@$host:$port"
}
