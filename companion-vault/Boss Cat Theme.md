---
tags: [clawd, companion, theme]
---

# Cozy Cat Theme (folder id: `boss-cat`)

See [[00 Index]] for the overview and [[Local Hook API Cheatsheet]] for how
`display_svg` / `displayHintMap` work under the hood.

## v2.0.0 rewrite -- what changed and why

v1 was a "business cat in a suit with a briefcase" and had a real bug: it
didn't declare a `layout` block, so the renderer's default assumptions about
where content sits inside the `viewBox` didn't match this theme's actual
drawing, which cropped the head/ears in-app. Combined with the suit, the
cropped result read as "a person," not a cat.

v2 fixes this by copying the **exact same `viewBox` (`-15 -25 45 45`),
`layout`, and `objectScale` values the built-in `themes/clawd/theme.json`
uses** (proven to render correctly), and draws the new character inside
that same coordinate frame: `centerX: 7.5` (not 0 -- the viewBox's own
center), baseline (feet) at `y: 17`, head/ears kept inside
`layout.marginBox` (`y: -14` to `17`). If this theme is ever redrawn again,
**keep reusing these exact `viewBox`/`layout`/`objectScale` numbers** unless
there's a specific reason to recompute them -- that's what actually fixes
the cropping bug, not any specific artwork choice.

Character is now a simple round cream cat (no suit, no briefcase) -- big
head, round ears, big eyes, small round body, tail. Idle motion was also
toned down (slower breathing, less frequent blinking, no constant tail/ear
motion) after feedback that the pet felt too busy/twitchy at rest.

## States (16 SVGs)

Same state list as before (idle / thinking / working / error / attention /
notification / sleeping / juggling + 3 `displayHintMap` variants -- see
[[Local Hook API Cheatsheet]] for how those work), plus two things v1 was
missing:

- **`roam`** -- a new top-level state. This is Clawd's built-in free-roam
  wander feature (`src/roam.js`): when `Settings -> General -> Free Roam` is
  on, Clawd occasionally walks the pet to a random point on screen while
  idle, switching to the `roam` state for the walk and back to `idle` on
  arrival. Themes without a `roam` state just fall back to their idle SVG
  (per `state-visual-resolver.js`); this theme now has a dedicated walk
  cycle. There's only one walk **speed** (`ROAM_SPEED_PX_PER_MS` in
  `roam.js`, not per-theme) -- "running" isn't a separate engine state, so
  the walk animation is just drawn energetically enough to read as brisk
  movement either way.
- **`reactions`** -- previously omitted entirely (which is why grabbing/
  clicking did nothing). Now defines `drag` (held/dangling pose),
  `clickLeft`/`clickRight` (shared poke-squish reaction), `annoyed`
  (grumpy, 50% chance on double-click), and `double` (flail, 4 rapid
  clicks). See `docs`/`guide-theme-creation.md`'s Reactions section for the
  full field reference if extending this further.

## `displayHintMap` (unchanged mechanism from v1)

```jsonc
"displayHintMap": {
  "gaming": "juggling-gaming.svg",
  "music": "juggling-music.svg",
  "video": "juggling-video.svg"
}
```

Still the same trick: one logical `juggling` state, three different pieces
of art selected via the session's `display_svg` hint. See [[Companion
Watcher]] for who sends these hints and [[Local Hook API Cheatsheet]] for
the request-side contract.

## Ideas for later (not built yet)

- Mini mode (8 extra states) -- still `miniMode.supported: false`.
- `idleAnimations` random pool (occasional stretch/look-around).
- A distinct "running" *visual* isn't possible without a `src/roam.js`
  change (there's no faster-roam trigger to hook a different asset to
  today) -- would need a small core change (e.g. a second roam speed tier)
  if that's ever wanted, not just a theme-side asset.
