---
tags: [clawd, companion, changelog]
---

# Changelog

See [[00 Index]] for the overview.

## 2026-07-11

- Added **Boss Cat** custom theme (`theme.json` + 11 SVGs) -- see [[Boss Cat Theme]].
  Delivered as a standalone theme folder for the user themes directory
  (installed separately from the repo, not committed under `themes/`).
- Added **companion-watcher.js** (`scripts/companion-watcher.js`,
  `scripts/games.config.json`, `scripts/media-status.ps1`) -- see
  [[Companion Watcher]]. Reacts to real games (process detection,
  cross-platform), music, and video (Windows SMTC; best-effort
  playerctl/AppleScript fallback on Linux/macOS).
  Added npm script: `"watch-companion": "node scripts/companion-watcher.js"`.
- Patched `src/state-session-snapshot.js` (added `displayHint` field to
  session snapshot entries) and `src/discord-presence-rpc.js` (companion
  session special case in `buildPresencePayload`) so Discord Rich Presence
  shows "Playing a game" / "Listening to music" / "Watching a video"
  instead of the generic "Working" bucket -- see [[Discord Presence Patch]].
  Both are small, additive, non-breaking diffs against upstream.
- Added this vault (`companion-vault/`) as a token-efficient reference for
  future sessions working on this feature set, so the local hook API,
  theme mechanism, and patch rationale don't need to be re-derived from
  source every time.

## Verification performed at the time

- `node scripts/validate-theme.js companion-vault-adjacent-theme-folder`
  (actual path: wherever `boss-cat/` was installed) passed every schema /
  eye-tracking / fallback / asset-existence check with no warnings.
- All 11 SVG assets parsed as well-formed XML; `theme.json` parsed as valid
  JSON.
- `node --check scripts/companion-watcher.js` passed (syntax only; the
  script wasn't run live against a running Clawd instance as part of this
  change -- test it with `npm run watch-companion` and watch the console
  log lines before relying on it).
- Read (not executed) `src/server-route-state.js`, `src/state.js`,
  `src/state-stale-cleanup.js`, `src/state-visual-resolver.js`,
  `src/state-session-events.js`, `src/state-priority.js`,
  `src/server-agent-id.js`, `agents/registry.js`,
  `src/discord-presence-rpc.js`, `src/state-session-snapshot.js` to confirm
  the request contract and patch points against actual source rather than
  assumption.
