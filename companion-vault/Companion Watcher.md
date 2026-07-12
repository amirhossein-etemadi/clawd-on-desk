---
tags: [clawd, companion, watcher]
---

# Companion Watcher

See [[00 Index]] for the overview and [[Local Hook API Cheatsheet]] for the
HTTP contract this script relies on.

**Works on any theme, not just Cozy Cat.** companion-watcher.js always sends
`display_svg: "gaming"/"music"/"video"` regardless of the active theme. All
four built-in themes (`themes/clawd`, `themes/calico`, `themes/cloudling`,
and `themes/boss-cat` / Cozy Cat) now declare those three keys in their own
`displayHintMap`, reusing each theme's own existing art (e.g. Clawd's
`clawd-headphones-groove.svg` for music, `clawd-dizzy.svg` for gaming,
`clawd-idle-reading.svg` for video -- see each `theme.json` for the exact
mapping). A theme with none of those keys just shows its plain `juggling`
visual for all three activities undifferentiated -- still reacts, just not
distinctly. `themes/template/` (the scaffold for new user themes) does not
have these keys; add them there too if a new theme should support this out
of the box.

## Location & how to run

```
scripts/companion-watcher.js   -- the watcher itself
scripts/games.config.json      -- editable list of tracked game process names
scripts/media-status.ps1       -- Windows SMTC query helper (spawned as a child process)
```

**Auto-started by Clawd itself as of the auto-launch pass** (see
[[Auto-Start]]) -- `src/companion-watcher-sidecar.js` spawns this script as a
background child process on `app.whenReady()` and stops it on `before-quit`,
gated by the `companionWatcherAutoStart` pref (default on, toggle at
Settings -> General -> "Auto-start companion watcher"). Manual invocation
still works the same way if you want to run it outside Clawd (e.g. while
debugging):

```bash
npm run watch-companion
# or
node scripts/companion-watcher.js
```

It is a **standalone Node script with zero new dependencies** -- it only
uses Node built-ins (`child_process`, `fs`, `path`) plus one existing repo
module (`hooks/server-config.js`, for the HTTP POST + port discovery). It
does not import Electron, so `node scripts/companion-watcher.js` runs fine
outside the Electron process.

## Detection logic

**Games** (all platforms): every ~5s (`POLL_MS`), lists running process
names (`tasklist /fo csv /nh` on Windows, `ps -A -o comm=` on
mac/Linux) and checks for a case-insensitive substring match against
`games.config.json`'s `processNames` array. That file is re-read on every
poll, so you can add/remove games without restarting the watcher.

**Music / video** (Windows primary, best-effort elsewhere): every ~10s
(`MEDIA_EVERY_N_POLLS = 2` polls), spawns `powershell.exe
scripts/media-status.ps1`, which queries the Windows **System Media
Transport Controls** (the same system behind the volume flyout's "now
playing" card) via
`Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager`.
This sees Spotify, browser tabs playing YouTube/Netflix/etc, VLC, Windows
Media Player -- anything that registers a system media session -- and
reports whether each one is actually `Playing` (not just open/paused), plus
`PlaybackType` (Music/Video) and title/artist when the source app provides
them.

Classification (`classify()` in companion-watcher.js): prefers the
OS-reported `PlaybackType` field; if that's missing, falls back to
substring-matching the app id / title against `VIDEO_HINTS` /
`MUSIC_HINTS` keyword lists; if still ambiguous, defaults to "music" (more
common case for an unknown source that's actively playing).

- **Linux**: falls back to `playerctl -a status` (MPRIS) if installed --
  reports "music" for any playing source (no music/video distinction
  available from MPRIS generically).
- **macOS**: falls back to AppleScript queries against Spotify.app and
  Music.app only (no generic "now playing" API on macOS the way Windows has
  SMTC) -- other players aren't detected yet.

## Priority & session lifecycle

```js
const PRIORITY = ["gaming", "video", "music"];  // first match wins if several are true at once
```

Single fixed `session_id: "companion-watcher"` (see [[Local Hook API
Cheatsheet]] for why a fixed, non-"default" id matters) drives one
state machine:

- **Nothing -> something** (`startActivity`): POST `thinking` (a quick
  "notices you" beat), then ~1.5s later POST `juggling` with the
  `display_svg` hint, then start a 60s heartbeat re-sending the same
  `juggling` + hint (see stale-cleanup timers in [[Local Hook API
  Cheatsheet]] -- 60s is comfortably under the 5-minute
  working-like-state timeout).
- **Something -> a different something** (`switchActivity`, e.g. gaming
  stops but music was already playing underneath): immediately re-POST
  `juggling` with the new hint, no thinking/stop beat -- it's a smooth
  hand-off, not a new "session".
- **Something -> nothing** (`stopActivity`): stop the heartbeat, POST
  `attention` (a "gg" celebration beat, reuses the same visual as a
  completed coding task), then ~4.5s later POST `event: "SessionEnd"` to
  delete the session outright so the pet falls back to idle (or to
  whatever real coding-agent session is still active) instead of lingering.

## Extending it

To track a new game: add its process/executable name to
`games.config.json`'s `processNames` array (case-insensitive substring
match, no restart needed).

To add a new activity category (e.g. video calls): add a process/keyword
list similar to `DEFAULT_GAMES`/`VIDEO_HINTS`, a new branch in the
detection logic, add the new key to `PRIORITY`, and add a matching entry +
asset to the theme's `displayHintMap` (see [[Boss Cat Theme]]) -- no core
app changes needed, same pattern as gaming/music/video.
