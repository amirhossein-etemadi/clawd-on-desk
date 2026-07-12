package com.iliacompanion.app

import android.content.Context

/** Tiny SharedPreferences wrapper -- just the relay URL + pairing code. */
class PrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ilia_companion_prefs", Context.MODE_PRIVATE)

    var relayUrl: String
        get() = prefs.getString(KEY_RELAY_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RELAY_URL, value).apply()

    var syncCode: String
        get() = prefs.getString(KEY_SYNC_CODE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SYNC_CODE, value).apply()

    fun isConfigured(): Boolean = relayUrl.isNotBlank() && syncCode.isNotBlank()

    companion object {
        private const val KEY_RELAY_URL = "relay_url"
        private const val KEY_SYNC_CODE = "sync_code"
    }
}
