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
    const val SPRIG = "sprig"
    const val PIPPIN = "pippin"

    val ALL = listOf(CLAWD, BOSS_CAT, CALICO, CLOUDLING, SPRIG, PIPPIN)

    fun labelFor(theme: String): Int = when (theme) {
        BOSS_CAT -> R.string.theme_boss_cat
        CALICO -> R.string.theme_calico
        CLOUDLING -> R.string.theme_cloudling
        SPRIG -> R.string.theme_sprig
        PIPPIN -> R.string.theme_pippin
        else -> R.string.theme_clawd
    }

    /**
     * Where a cosmetic sits on this theme's head: x = horizontal center as a
     * fraction of the art canvas, y = fraction where the hat brim rests,
     * scale = relative hat size. Cosmetic drawables are centered at (0.5,
     * 0.5) with the brim at y=0.5, so the overlay view is translated by
     * (x-0.5, y-0.5) of the pet size. Values were tuned visually against
     * every theme's idle art (scratchpad pet-preview).
     */
    data class HatAnchor(val x: Float, val y: Float, val scale: Float)

    fun hatAnchorFor(theme: String): HatAnchor = when (theme) {
        BOSS_CAT -> HatAnchor(0.50f, 0.20f, 1.3f)
        CALICO -> HatAnchor(0.42f, 0.35f, 1.0f)
        CLOUDLING -> HatAnchor(0.50f, 0.27f, 1.1f)
        SPRIG -> HatAnchor(0.539f, 0.492f, 1.0f)
        PIPPIN -> HatAnchor(0.539f, 0.539f, 1.0f)
        else -> HatAnchor(0.539f, 0.578f, 1.0f)
    }

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

    private val sprigSet = ArtSet(
        idle = R.drawable.pet_sprig_idle,
        typing = R.drawable.pet_sprig_typing,
        music = R.drawable.pet_sprig_music,
        video = R.drawable.pet_sprig_video,
        meeting = R.drawable.pet_sprig_meeting,
        gaming = R.drawable.pet_sprig_gaming
    )

    private val pippinSet = ArtSet(
        idle = R.drawable.pet_pippin_idle,
        typing = R.drawable.pet_pippin_typing,
        music = R.drawable.pet_pippin_music,
        video = R.drawable.pet_pippin_video,
        meeting = R.drawable.pet_pippin_meeting,
        gaming = R.drawable.pet_pippin_gaming
    )

    fun setFor(theme: String): ArtSet = when (theme) {
        BOSS_CAT -> bossCatSet
        CALICO -> calicoSet
        CLOUDLING -> cloudlingSet
        SPRIG -> sprigSet
        PIPPIN -> pippinSet
        else -> clawdSet
    }
}
