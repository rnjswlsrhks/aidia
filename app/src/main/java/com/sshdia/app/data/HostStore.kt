package com.sshdia.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persists host profiles. Credentials are encrypted at rest with a key held in
 * the Android Keystore (via [EncryptedSharedPreferences]). If the secure store
 * cannot be created on a given device, it falls back to app-private storage so
 * the app keeps working. Any data from the older plaintext store is migrated on
 * first run.
 */
class HostStore(context: Context) {

    private val app = context.applicationContext
    private val prefs: SharedPreferences = buildSecurePrefs(app)

    init {
        migrateLegacy()
    }

    fun load(): List<HostProfile> {
        val raw = prefs.getString(KEY_HOSTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(hosts: List<HostProfile>) {
        val arr = JSONArray()
        hosts.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY_HOSTS, arr.toString()).apply()
    }

    fun upsert(profile: HostProfile): List<HostProfile> {
        val current = load().toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) current[idx] = profile else current.add(profile)
        save(current)
        return current
    }

    fun delete(id: String): List<HostProfile> {
        val current = load().filterNot { it.id == id }
        save(current)
        return current
    }

    /** Duplicate a profile (new id, suffixed label) and insert after the original. */
    fun duplicate(id: String): List<HostProfile> {
        val current = load().toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return current
        val src = current[idx]
        val baseName = src.label.ifBlank { src.host }
        val copy = src.copy(
            id = UUID.randomUUID().toString(),
            label = "$baseName 복사"
        )
        current.add(idx + 1, copy)
        save(current)
        return current
    }

    private fun buildSecurePrefs(ctx: Context): SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx,
            SECURE_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        ctx.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    }

    private fun migrateLegacy() {
        val legacy = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (legacy === prefs) return
        val legacyData = legacy.getString(KEY_HOSTS, null) ?: return
        if (prefs.getString(KEY_HOSTS, null) == null) {
            prefs.edit().putString(KEY_HOSTS, legacyData).apply()
        }
        legacy.edit().remove(KEY_HOSTS).apply()
    }

    private fun toJson(p: HostProfile): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("label", p.label)
        put("host", p.host)
        put("port", p.port)
        put("username", p.username)
        put("password", p.password)
        put("privateKeyPem", p.privateKeyPem)
    }

    private fun fromJson(o: JSONObject): HostProfile = HostProfile(
        id = o.optString("id", UUID.randomUUID().toString()),
        label = o.optString("label", ""),
        host = o.optString("host", ""),
        port = o.optInt("port", 22),
        username = o.optString("username", ""),
        password = o.optString("password", ""),
        privateKeyPem = o.optString("privateKeyPem", "")
    )

    private companion object {
        const val SECURE_PREFS = "sshdia_secure"
        const val LEGACY_PREFS = "sshdia_hosts"
        const val KEY_HOSTS = "hosts"
    }
}
