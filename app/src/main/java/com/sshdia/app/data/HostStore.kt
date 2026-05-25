package com.sshdia.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HostStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
        id = o.optString("id", java.util.UUID.randomUUID().toString()),
        label = o.optString("label", ""),
        host = o.optString("host", ""),
        port = o.optInt("port", 22),
        username = o.optString("username", ""),
        password = o.optString("password", ""),
        privateKeyPem = o.optString("privateKeyPem", "")
    )

    private companion object {
        const val PREFS = "sshdia_hosts"
        const val KEY_HOSTS = "hosts"
    }
}
