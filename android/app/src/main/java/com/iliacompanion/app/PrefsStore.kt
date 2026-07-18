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

    var petTheme: String
        get() = prefs.getString(KEY_PET_THEME, PetThemes.CLAWD) ?: PetThemes.CLAWD
        set(value) = prefs.edit().putString(KEY_PET_THEME, value).apply()

    var petCosmetic: String
        get() = prefs.getString(KEY_PET_COSMETIC, Cosmetics.NONE) ?: Cosmetics.NONE
        set(value) = prefs.edit().putString(KEY_PET_COSMETIC, value).apply()

    /** Floating pet size in dp (side of the square pet view). */
    var petSizeDp: Int
        get() = prefs.getInt(KEY_PET_SIZE_DP, DEFAULT_PET_SIZE_DP)
        set(value) = prefs.edit().putInt(KEY_PET_SIZE_DP, value.coerceIn(MIN_PET_SIZE_DP, MAX_PET_SIZE_DP)).apply()

    fun isConfigured(): Boolean = relayUrl.isNotBlank() && syncCode.isNotBlank()

    companion object {
        private const val KEY_RELAY_URL = "relay_url"
        private const val KEY_SYNC_CODE = "sync_code"
        private const val KEY_PET_THEME = "pet_theme"
        private const val KEY_PET_COSMETIC = "pet_cosmetic"
        private const val KEY_PET_SIZE_DP = "pet_size_dp"

        const val MIN_PET_SIZE_DP = 48
        const val MAX_PET_SIZE_DP = 144
        // A notch bigger than the original hardcoded 72dp overlay.
        const val DEFAULT_PET_SIZE_DP = 88
    }
}
