package com.iliacompanion.app

/**
 * Registry mapping (theme id, activity) -> pet art drawable, mirroring the
 * desktop themes in themes/ and their displayHintMap in each theme.json.
 * Theme ids match the desktop's (clawd-prefs.json "theme" field). Activity
 * values are the ones companion-watcher.js sends over the relay: gaming,
 * music, video, meeting, typing, or null for idle.
 *
 * Themes not yet ported to native art fall back to the clawd set rather
 * than the old generic placeholder, so the pet always looks intentional.
 */
object PetThemes {
    const val CLAWD = "clawd"
    const val BOSS_CAT = "boss-cat"
    const val CALICO = "calico"
    const val CLOUDLING = "cloudling"

    val ALL = listOf(CLAWD, BOSS_CAT, CALICO, CLOUDLING)

    fun drawableFor(theme: String, activity: String?): Int {
        val set = setFor(theme)
        return when (activity) {
            "gaming" -> set.gaming
            "music" -> set.music
            "video" -> set.video
            "meeting" -> set.meeting
            "typing" -> set.typing
            else -> set.idle
        }
    }

    data class ArtSet(
        val idle: Int,
        val typing: Int,
        val music: Int,
        val video: Int,
        val meeting: Int,
        val gaming: Int
    )

    // AnimatedVectorDrawables generated from the desktop SVGs' CSS keyframes
    // (see scratchpad gen_clawd_avd.py) -- same choreography as the PC pet:
    // breathe/blink, typing bounce + arm hammering + code-line reveals,
    // music sway + floating notes, juggling balls, thinking bubble dots.
    private val clawdSet = ArtSet(
        idle = R.drawable.avd_pet_clawd_idle,
        typing = R.drawable.avd_pet_clawd_typing,
        music = R.drawable.avd_pet_clawd_music,
        video = R.drawable.avd_pet_clawd_video,
        meeting = R.drawable.avd_pet_clawd_meeting,
        gaming = R.drawable.avd_pet_clawd_gaming
    )

    /** Themes whose art animates itself (AVD); others get the view-level
     *  breathe fallback in OverlayService. */
    fun hasBuiltInAnimation(theme: String): Boolean = setFor(theme) === clawdSet

    private val bossCatSet = ArtSet(
        idle = R.drawable.pet_bosscat_idle,
        typing = R.drawable.pet_bosscat_typing,
        music = R.drawable.pet_bosscat_music,
        video = R.drawable.pet_bosscat_video,
        meeting = R.drawable.pet_bosscat_meeting,
        gaming = R.drawable.pet_bosscat_gaming
    )

    private val calicoSet = ArtSet(
        idle = R.drawable.pet_calico_idle,
        typing = R.drawable.pet_calico_typing,
        music = R.drawable.pet_calico_music,
        video = R.drawable.pet_calico_video,
        meeting = R.drawable.pet_calico_meeting,
        gaming = R.drawable.pet_calico_gaming
    )

    private val cloudlingSet = ArtSet(
        idle = R.drawable.pet_cloudling_idle,
        typing = R.drawable.pet_cloudling_typing,
        music = R.drawable.pet_cloudling_music,
        video = R.drawable.pet_cloudling_video,
        meeting = R.drawable.pet_cloudling_meeting,
        gaming = R.drawable.pet_cloudling_gaming
    )

    fun setFor(theme: String): ArtSet = when (theme) {
        BOSS_CAT -> bossCatSet
        CALICO -> calicoSet
        CLOUDLING -> cloudlingSet
        else -> clawdSet
    }
}
