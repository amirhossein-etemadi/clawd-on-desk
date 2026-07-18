package com.iliacompanion.app

/**
 * Cosmetic overlays the pet can wear. Each is a 72dp vector drawn on the
 * same 25.6 grid as the pets, centered at x=12.8 with its brim bottom at
 * y=12.8; PetThemes.hatAnchorFor supplies the per-theme offset/scale that
 * seats it on the head. "none" renders nothing.
 */
object Cosmetics {
    const val NONE = "none"

    data class Cosmetic(val id: String, val drawable: Int, val label: Int)

    val ALL = listOf(
        Cosmetic(NONE, 0, R.string.cosmetic_none),
        Cosmetic("party", R.drawable.cosmetic_party, R.string.cosmetic_party),
        Cosmetic("crown", R.drawable.cosmetic_crown, R.string.cosmetic_crown),
        Cosmetic("beanie", R.drawable.cosmetic_beanie, R.string.cosmetic_beanie),
        Cosmetic("bow", R.drawable.cosmetic_bow, R.string.cosmetic_bow),
        Cosmetic("sprout", R.drawable.cosmetic_sprout, R.string.cosmetic_sprout)
    )

    fun byId(id: String): Cosmetic = ALL.firstOrNull { it.id == id } ?: ALL[0]
}
