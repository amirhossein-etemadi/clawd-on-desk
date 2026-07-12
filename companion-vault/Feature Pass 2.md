---
tags: [clawd, companion, changelog]
---

# Feature Pass 2 -- meetings, streaks, stats, sounds

See [[00 Index]] for the overview. This covers everything added in the
second round, on top of the original games/music/video reactions.

## What's new

- **Meeting detection** -- same mechanism as games, new
  `scripts/meeting-apps.config.json` (Zoom, Teams, Webex, Skype, etc. --
  apps normally only running during an actual call). New `"meeting"`
  `displayHintMap` key on all four themes.
- **Specific game/track names** -- `games.config.json` now has a `labels`
  map for friendly display names ("Elden Ring", not "eldenring.exe").
  companion-watcher.js sends this (and, for music/video, the
  title/artist reported by Windows SMTC) as the session's `session_title` --
  an *existing* field the app already threads through to the dashboard and
  Discord presence, so no new core patch was needed for this part. See
  [[Local Hook API Cheatsheet]] for the field.
- **Streaks** -- `scripts/companion-state.json` tracks consecutive days
  companion-watcher.js observed any activity. Hits a small celebration
  (`attention` + `companion_streak` sound + a "N-day streak!" title) at
  3/7/14/30/60/100/200/365 days. This is a proxy for "you used this PC that
  day," not real coding-agent activity (companion-watcher.js has no
  visibility into actual coding sessions) -- documented as a known
  approximation.
- **Break reminders** -- a gentle `notification` + `companion_break` sound
  if the same activity (gaming/meeting/video/music) runs uninterrupted for
  90+ minutes.
- **Daily/weekly stats** -- `scripts/companion-state.json` also accumulates
  per-day seconds per activity (and per-game). `npm run companion-report`
  prints a recap and refreshes `companion-vault/Companion Stats.md` so it's
  browsable in Obsidian too.
- **Richer idle pool (Cozy Cat only)** -- added `idleAnimations`:
  `idle-stretch.svg`, `idle-look.svg`. Built-in themes already had their own
  idle pools upstream (`clawd-idle-look.svg`, `clawd-idle-reading.svg`,
  etc.) -- untouched.
- **Sound stings** -- six short synthesized WAV tones
  (`companion_gaming/music/video/meeting/streak/break`) in
  `assets/sounds/` (shared, for built-in themes) and Cozy Cat's own
  `sounds/` folder. Generated with a small Python `wave`-module script (pure
  sine tones + linear fade, no external audio library or licensing
  concerns) -- not music, just short friendly chimes.

## The one real core patch this round: `src/state.js`

Sound *playback* for state changes was already hardcoded in `applyState()`:
`attention`/`mini-happy` always played `"complete"`, `notification`/
`mini-alert` always played `"confirm"` -- and nothing at all played for
`juggling`. Patched additively:

- `juggling` now also plays `companion_<hint>` (gaming/music/video/meeting)
  when the dominant session is `companion-watcher`, reading its
  `displayHint`. Silent no-op (via `playSound`'s existing missing-file
  guard) for any theme that doesn't define that sound name, and for every
  real subagent `juggling` trigger (no session is ever named
  `"companion-watcher"` for a real agent).
- `attention` plays `companion_streak` instead of `complete` **only** when
  the companion session's last posted `event` was `"CompanionStreak"`.
- `notification` plays `companion_break` instead of `confirm` **only**
  when the last event was `"CompanionBreak"`.

**Important implementation detail:** this reads the *event name* (from
`session.recentEvents`, the tail of which is `{at, event, state}`), not
`displayHint`. `state-session-events.js: pickDisplayHint()` only lets
`displayHint` stay set while `state` is `working`/`thinking`/`juggling` --
it's nulled the instant a session moves to `attention` or `notification`.
Using `displayHint` for the streak/break special-cases would silently never
fire. If this file is ever revisited, keep that constraint in mind before
"simplifying" it back to a `displayHint` check.

## Sound file resolution, if `companion_*` ever goes silent

`getSoundUrl()` (`src/theme-context.js`) resolves built-in themes from the
shared `assets/sounds/` folder and user themes from `<theme>/sounds/`
(falling back to the shared folder if not found locally). All four themes'
`theme.json` now declare the six `companion_*` keys -- if a new theme is
added later, it needs those same keys (or nothing plays, silently, by
design -- `playSound` no-ops on an unresolvable name).

## Explicitly not built this round (per user's own scoping call)

- Weather-based idle variation (needs an API/location; skipped).
- Voice-channel "is talking" detection (needs a real Discord bot with voice
  permissions -- a different architecture than the local presence RPC used
  here).
- Phone push-notification mirror (needs a separate mobile-facing service).
- Time-of-day / seasonal idle reskins were considered, but the current
  render pipeline only supports `displayHint` overrides on
  `working`/`thinking`/`juggling` (see [[Local Hook API Cheatsheet]]) --
  `idle` isn't hintable today. Doable later via the same additive-patch
  pattern used for sounds/Discord (add `idle` to
  `state-visual-resolver.js: getSvgOverride`), but wasn't in this pass.
