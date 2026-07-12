---
tags: [clawd, companion, auto-start]
---

# Auto-Start

See [[00 Index]] for the overview and [[Companion Watcher]] for what actually
runs. This note covers **only** how everything gets launched without the user
running commands by hand.

## The two things that need to start

1. **Clawd itself** (the Electron app).
2. **`scripts/companion-watcher.js`** (a separate, plain Node script -- not
   part of the Electron process tree by default).

## 1. Clawd itself -- reused an existing feature, no code change

Clawd already had a working "start on login" setting: Settings -> General ->
**"Open at login"** (`openAtLogin` pref, `src/login-item.js` +
`src/main.js`'s `hydrateSystemBackedSettings()`/effect router). On Windows
and macOS this calls Electron's `app.setLoginItemSettings()`, which registers
the real OS-level login item (Windows Registry `Run` key equivalent, handled
internally by Electron) -- not a hand-rolled shortcut/task. On Linux it drops
a `.desktop` file into `~/.config/autostart/` (`linuxSetOpenAtLogin` in
`login-item.js`).

This is a **one-time manual toggle** -- flipping a setting inside a running
Electron app can't be done from outside the app, so there was no code path to
automate turning it on for the first time. Turn it on once at Settings ->
General -> "Open at login".

## 2. companion-watcher.js -- new: rides along with Clawd's own startup

Rather than registering a second, independent OS-level startup entry (a
separate Task Scheduler job, or a `shell:startup` shortcut) -- which the user
would have to manage separately from Clawd's own auto-start, and which would
drift if Clawd's own on/off state ever disagreed with it -- `src/main.js` now
spawns `companion-watcher.js` itself, tying its lifecycle directly to
Clawd's. Practically: **whatever makes Clawd start (manual launch, or the
"Open at login" system login item) also starts the companion watcher.**
Enabling that one existing toggle is enough for "both run on system
startup."

### The mechanism: `src/companion-watcher-sidecar.js`

- `createCompanionWatcherSidecar({ log })` returns `{ start, stop, isRunning
  }`. `main.js` creates one instance inside `app.whenReady()` and calls
  `.start()` if the `companionWatcherAutoStart` pref isn't explicitly
  `false`; `.stop()` runs in the `before-quit` handler.
- Spawns via `spawn(process.execPath, [scriptPath], { env: { ...env,
  ELECTRON_RUN_AS_NODE: "1" }, stdio: "ignore", windowsHide: true })`.
  `process.execPath` is Electron's own binary; `ELECTRON_RUN_AS_NODE=1` is
  the standard trick that makes an Electron binary run a plain CommonJS
  script headlessly (full Node API, no window, no Chromium) -- this works
  identically in a source checkout (`npm start`) and in a packaged build,
  where the end user has no separate system Node.js install to fall back on.
- If the child exits unexpectedly (crash, not an explicit `.stop()`), it
  restarts with a backoff, capped at 5 restarts per rolling 60s window (then
  gives up silently until the next Clawd restart, logged via the same
  `sessionLog` callback other startup services use -- never a crash loop
  that pegs a CPU core).
- If `scripts/companion-watcher.js` doesn't exist (e.g. a packaged build
  that hasn't bundled `scripts/**/*` -- see caveat below), `start()` just
  logs and no-ops. Safe by design, matches the existing "missing sound
  file" no-op pattern used elsewhere in this project.

### New pref: `companionWatcherAutoStart`

`src/prefs.js` schema, default `true`. Settings -> General ->
**"Auto-start companion watcher"**, right below "Open at login". Wired
through the same `SETTINGS_MIRROR_SETTERS` live-toggle pattern as `freeRoam`
-- flipping it off calls `.stop()` immediately (no restart needed), flipping
it back on calls `.start()` immediately.

### Known caveat: packaged builds

`package.json`'s `build.files` list (electron-builder) does **not**
currently include `scripts/**/*` -- only `src/**/*`, `assets/**/*`,
`hooks/**/*`, `themes/**/*`, etc. This means in a proper installed/packaged
build (as opposed to running from this source checkout via `npm start`),
`scripts/companion-watcher.js` wouldn't be bundled and the sidecar would
silently no-op. Not an issue for the current source-checkout workflow this
project actually uses, but worth knowing before ever shipping a built
installer -- add `"scripts/**/*"` to `build.files` first (it's a handful of
small JS/JSON/ps1 files, no real bundle-size concern).
