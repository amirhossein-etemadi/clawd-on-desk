---
tags: [clawd, companion, changelog]
---

# Feature Pass 3 -- Discord redesign, cross-platform, AFK, achievements, Telegram, Settings tab

See [[00 Index]] for the overview. Third round, on top of Feature Pass 2
(meetings/streaks/stats/sounds) and the auto-start pass ([[Auto-Start]]).

## Discord presence flip

`discord-presence-rpc.js`'s companion branch used to show `details: "Boss
Cat"` (bold title line) and the activity as `state` (second line). Flipped
per explicit request: `details` is now the activity itself (with a per-hint
emoji: 🎮 gaming / 🎵 music / 📺 video / 📞 meeting), and `state` is a fixed
hint string ("🐾 Auto-tracked by Clawd Companion") so it's obvious this is an
automated feed, not a hand-typed Discord custom status. `large_text` tooltip
reinforces the same thing. Also fixed a latent gap: `COMPANION_LABEL` had no
`meeting` fallback entry.

## Cross-platform hardening

- **macOS** media detection: AppleScript now also covers VLC (video) and
  returns real title/artist for Spotify/Music.app (previously returned no
  metadata at all).
- **Linux** media detection: `playerctl -l` + per-player `status`/`metadata`
  instead of `-a status` (which only said "something is playing", not what
  or whether it's video vs music). Classifies per-player name against the
  same `VIDEO_HINTS` list Windows uses.
- `companion-watcher-sidecar.js`'s spawn (`process.execPath` +
  `ELECTRON_RUN_AS_NODE=1`) and `login-item.js`'s auto-start (Electron API on
  win/mac, `.desktop` file on Linux) were already fully cross-platform --
  verified, not changed.

## AFK / idle detection

New `getSystemIdleSeconds()` in companion-watcher.js: Windows (PowerShell
`GetLastInputInfo` P/Invoke), macOS (`ioreg -c IOHIDSystem` /
`HIDIdleTime`), Linux (`xprintidle`, fails OPEN if not installed -- a
missing tool never makes tracking worse than before this existed). When
idle >= 3 minutes, stats/streak/break accounting pauses (but the visual pet
state does NOT change, so it doesn't flicker every time someone glances
away).

## Combined-activity stats + game/song priority

Stats now credit **every** concurrently-detected activity each poll (e.g.
music running under a game counts toward both totals), not just whichever
one currently owns the visible state. The *visible* state/session
title/Discord presence still follows strict priority **gaming > meeting >
video > music** -- so playing a game while music plays in the background
correctly shows the game, per explicit request, while the weekly music
total still accrues normally in the background.

## Achievements (lifetime, one-time)

`companionState.achievements`: `nightOwl` (active 12am-4am), `earlyBird`
(active 5-7am), `marathon` (3+ continuous hours in one activity session),
plus a running `longestSessionSeconds`. Each fires once ever via the same
`attention` + sound + title pattern as streak milestones, using a new event
name `CompanionAchievement` and a new synthesized sound
(`companion_achievement.wav`, an ascending 4-note arpeggio -- brighter than
the streak chime). Wired into `src/state.js`'s existing companion
sound-trigger block (extends the `lastCompanionEvent()` check added in
Feature Pass 2) and added to all 4 built-in themes' `sounds` blocks plus
`themes/template/theme.json` (so new custom themes get it by copying the
template).

## Configurable break-reminder threshold

New pref `companionBreakReminderMinutes` (default 90, range 5-480). Since
companion-watcher.js is a separate process from Electron, the value reaches
it via a small shared file, `scripts/companion-settings.json`, written by
main.js (`writeCompanionWatcherSettingsFile`) whenever the pref changes (or
once at startup to catch a pre-existing value), and re-read by the watcher
every poll (~5s) -- no restart needed either direction.

## Telegram phone alerts (reuses the existing bot)

New `src/companion-telegram-alerts.js`: rides the same `onSnapshot` fan-out
as Discord presence and the existing `telegram-companion.js` completion
notifications (see `broadcastSessionSnapshot` in main.js). When the
companion session's last event is `CompanionStreak` / `CompanionBreak` /
`CompanionAchievement`, sends a plain DM via the bot token + chat id already
configured for Telegram approvals -- deliberately bypasses the whole
native-runner/migration-controller/direct-send machinery (that subsystem
exists to route a Telegram *reply* into a coding session; this is a
one-way ping with nothing to reply to). New pref
`companionTelegramAlertsEnabled` (default **off** -- opt-in on top of an
already-working Telegram approval setup), toggle lives in the Remote
Approval tab next to the other Telegram switches.

## Settings > Companion tab

New `settings-tab-companion.js` (+ `settings-tab-companion.js` registered in
`settings.html`/`settings-renderer.js`, new paw-print icon in
`settings-icons.js`): watcher running/stopped status, a Restart button, the
break-reminder-minutes control, an "Open config folder" button (opens
`scripts/` so `games.config.json`/`meeting-apps.config.json` can be
hand-edited), and read-only streak/today/week/achievements stats pulled from
`companion-state.json`. New IPC channels `companion:get-status` /
`companion:restart` / `companion:open-config-folder` added to
`settings-ipc.js` (plain `handle()`s, not routed through
`settingsController.applyCommand` -- these aren't persisted-field commands).

## Packaged-build fix (found while doing the above, not requested but real)

`package.json`'s electron-builder `files` list didn't include
`scripts/**/*` at all -- a real installer build would have shipped without
`companion-watcher.js`. Worse: `companion-state.json` /
`companion-settings.json` live in that same folder and are *written to* at
runtime, but files inside `app.asar` are read-only, so even bundling it
without `asarUnpack` would have broken `saveState()`. Fixed both: added
`"scripts/**/*"` to `files` AND to `asarUnpack`. Not an issue for the
current source-checkout (`npm start`) workflow, but would have silently
broken the very first real installer build.

## Explicitly still not built (per user's own scoping call, this round)

- Discord voice-channel "is talking" detection (needs a real bot + voice
  permissions -- user chose the lighter "skip" option this round too).
- Weather-based idle variation (still skipped).
