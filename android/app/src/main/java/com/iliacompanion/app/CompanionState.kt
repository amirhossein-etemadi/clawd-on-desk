package com.iliacompanion.app

import org.json.JSONObject

/**
 * Mirrors the payload companion-watcher.js pushes over the relay (see
 * buildCloudStatePayload() in scripts/companion-watcher.js and
 * cloud/src/worker.js's {type:"state", data:{...}} envelope).
 */
data class CompanionState(
    val activity: String?,
    val label: String?,
    val title: String?,
    val level: Int,
    val levelTitle: String?,
    val xp: Double,
    val xpToNext: Double,
    val streak: Int,
    val updatedAt: Long
) {
    companion object {
        fun fromJson(obj: JSONObject): CompanionState = CompanionState(
            activity = obj.optStringOrNull("activity"),
            label = obj.optStringOrNull("label"),
            title = obj.optStringOrNull("title"),
            level = obj.optInt("level", 1),
            levelTitle = obj.optStringOrNull("levelTitle"),
            xp = obj.optDouble("xp", 0.0),
            xpToNext = obj.optDouble("xpToNext", 140.0),
            streak = obj.optInt("streak", 0),
            updatedAt = obj.optLong("updatedAt", 0L)
        )
    }
}

data class PresenceInfo(val desktopOnline: Boolean, val phoneCount: Int)

fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
