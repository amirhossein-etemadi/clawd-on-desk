---
tags: [clawd, companion, changelog]
---

# Feature Pass 4 -- flavor text, idle easter egg, level/XP progression, cosmetic accessories

See [[00 Index]] for the overview. Fourth round, on top of [[Feature Pass 3]]
(Discord redesign, cross-platform, AFK, achievements, Telegram, Settings tab).
Explicit brief for this round: "personality & flavor" + "cosmetic rewards,"
built with fun as the guiding principle rather than utility.

## Rotating flavor quips

`scripts/companion-quips.json` holds pools of one-line comments per activity
(gaming/meeting/video/music/generic), plus a `games` object for per-game
overrides (e.g. Elden Ring, Minecraft). `companion-watcher.js` picks one at
random ~6s after an activity starts and then re-rolls every ~10 minutes at
35% odds (`QUIP_CHANCE`), posting it as a silent `notification`/`CompanionQuip`
event -- silent because a chime every 10 minutes would turn a fun aside into
a nagging notification sound (see the `state.js` sound-trigger branch, which
explicitly excludes `CompanionQuip`). Fully user-editable without touching
code.

## Rare idle easter egg

`idle-pounce-egg.svg`: the cat spots an invisible bug, does the classic
pre-pounce wiggle, lunges, then sits back looking confused (a "?" bubble).
Added to Cozy Cat's `idleAnimations` with a new optional `weight` field
(default 1 if omitted, fully backward compatible) at `weight: 0.12` -- about
1 in 9 idle triggers vs. the two existing animations. `tick.js` gained
`pickWeightedIdleAnim()` to do the weighted random pick instead of a flat
`Math.random() * length`.

## Level / XP progression

One unified XP currency feeds a level counter, persisted in
`companion-state.json`'s new `progression` key (`{ level, title, xp,
xpToNext, xpTimeGrantedMinutes, seenGames }`):

- **Time-based XP**: 1 XP/minute of tracked activity, credited once per poll
  tick while not AFK, capped at 240 minutes/day (`DAILY_XP_TIME_CAP_MINUTES`)
  so showing up regularly beats one huge grind session.
- **Streak-day XP**: 15 XP every day the streak continues/starts, +75 bonus
  on a milestone day (3/7/14/30/60/100/200/365).
- **Achievement XP**: 120 XP for each one-time achievement (Night Owl, Early
  Bird, Marathon).
- **New-game discovery XP**: 40 XP the first time a given game label is ever
  seen (tracked in `progression.seenGames`).

`xpNeededForLevel(level)` = `round(80 * level^1.4) + 60` -- front-loaded so
early levels (and the first accessory unlock) come quickly, then tapers into
a longer grind. `LEVEL_TITLES` gives a playful title per level band (Fresh
Paws -> Curious Cat -> ... -> Mythic Whisker at 25+).

A **celebration queue** (`queueCelebration` / `scheduleCelebrationPump`)
replaced the old ad-hoc `setTimeout`-staggered posts for streak/achievement
celebrations: same idea (don't let two "attention" beats stomp on each
other), but centralized so level-ups queue into the same line instead of
needing their own bespoke stagger logic.

Settings > Companion tab now shows level + title + an XP progress bar
(`buildLevelRows()` in `settings-tab-companion.js`), reading straight from
`companion-state.json` via the existing `companion:get-status` IPC call (no
new IPC needed).

## Cosmetic accessories (level-gated)

Two new idle-pose variants for Cozy Cat: `idle-accessory-partyhat.svg`
(unlocks at level 3) and `idle-accessory-sunglasses.svg` (level 7),
`ACCESSORY_UNLOCKS` in `companion-watcher.js`. Sunglasses are drawn *inside*
the `eyes-js` group so they move together with mouse-tracked eyes; the party
hat lives inside the body's `breathe` group with its own subtle wobble
animation.

Equipping works by **mutating the live theme object**: a new
`companionEquippedAccessory` pref ("none" | "partyHat" | "sunglasses"),
applied via `applyCompanionAccessory()` in `main.js`, which points
`theme.states.idle[0]` at the accessory file and calls both `state.js`'s and
`tick.js`'s `refreshTheme()` so it takes effect immediately -- same pattern
already used for runtime sound overrides
(`rememberRuntimeSoundOverrideFile` in `settings-ipc.js`). Two safety
checks make this robust rather than just cosmetic-fast-and-loose:

1. If the active theme doesn't ship the requested accessory file (any theme
   other than Cozy Cat), equipping is a silent no-op.
2. The unlock level is re-checked server-side in `applyCompanionAccessory`
   itself (not just gated in the Settings UI), so hand-editing
   `settings.json` can't equip something early.

Settings > Companion tab shows a row of buttons (None / Party Hat /
Sunglasses), locked ones showing a 🔒 + required level and disabled until
reached.

## Files touched this round

```
scripts/companion-watcher.js       <- quips, idle-XP/leveling, celebration queue, new-game discovery
scripts/companion-quips.json       <- NEW: editable flavor-text pools
src/state.js                       <- PATCHED: CompanionQuip silent, CompanionLevelUp sound branch
src/tick.js                        <- PATCHED: pickWeightedIdleAnim()
src/main.js                        <- PATCHED: applyCompanionAccessory(), SETTINGS_MIRROR_SETTERS entry, startup apply
src/prefs.js                       <- PATCHED: companionEquippedAccessory pref
src/settings-actions.js            <- PATCHED: companionEquippedAccessory validator (requireEnum)
src/settings-tab-companion.js      <- PATCHED: level/XP bar + accessory equip picker
src/settings-i18n.js               <- PATCHED: new strings, all 5 languages
themes/*/theme.json (all 4)        <- PATCHED: companion_levelup sound key
assets/sounds/companion_levelup.wav              <- NEW
Clawd pet/boss-cat/
  assets/idle-pounce-egg.svg                     <- NEW: rare idle easter egg
  assets/idle-accessory-partyhat.svg             <- NEW: level-3 accessory
  assets/idle-accessory-sunglasses.svg           <- NEW: level-7 accessory
  sounds/companion_levelup.wav                   <- NEW
  theme.json                                     <- PATCHED: idleAnimations weight, companion_levelup sound
```

## Not done this round

Confetti on celebrations was already present in Cozy Cat's `attention.svg`
(small falling colored squares, staggered via CSS `animation-delay`) from an
earlier pass -- verified it fires for every `attention` trigger (streak,
achievement, level-up, task completion), nothing new needed.

Seasonal palette swap (the other item under "cosmetic rewards") was **not**
built this round -- flagging it explicitly rather than silently dropping it,
since "every feature in these two sections" was the original ask. Pick this
up whenever there's a green light: swapping a few fill colors in
`theme.json`/SVGs based on month, gated by nothing (or a Settings toggle).
