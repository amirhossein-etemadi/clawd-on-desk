---
tags: [clawd, companion, discord]
---

# Discord Presence Patch

See [[00 Index]] for the overview. This documents the **only two changes
made to core clawd-on-desk source files** for the whole companion feature --
everything else (theme, watcher script) is purely additive new files.

## Why a patch was needed at all

Discord Rich Presence already existed upstream
(`src/discord-presence-rpc.js`): it picks the highest-priority live session
(`pickDominantSession`, using [[Local Hook API Cheatsheet#Priority when
multiple sessions are live|the same STATE_PRIORITY table]]) and shows a
coarse label -- `toCoarseState()` collapses `working`/`juggling`/`carrying`/
`sweeping` all into one bucket labeled **"Working"**.

Without a patch, the companion-watcher session (state `juggling`) would show
up in Discord as **"Claude Code -- Working"**, which is just wrong while
you're gaming or listening to music.

## Patch 1: `src/state-session-snapshot.js`

Added one field to the object returned by `buildSessionSnapshotEntry()`
(right after `state,`):

```js
displayHint: (session && session.displayHint) || null,
```

`session.displayHint` already existed internally (set from the request's
`display_svg`, see [[Local Hook API Cheatsheet]]) but was never exposed in
the snapshot sent to dashboard/HUD/Discord/etc. This is a pure addition --
`null` for every ordinary coding-agent session, so nothing that reads the
snapshot and doesn't know about this field is affected.

## Patch 2: `src/discord-presence-rpc.js`

Added a fixed-id special case at the top of `buildPresencePayload()`:

```js
const COMPANION_SESSION_ID = "companion-watcher";
const COMPANION_LABEL = Object.freeze({
  gaming: "Playing a game",
  music: "Listening to music",
  video: "Watching a video",
});

function buildPresencePayload(session, privacy = {}) {
  if (session && session.id === COMPANION_SESSION_ID) {
    const label = COMPANION_LABEL[session.displayHint] || "Off the clock";
    return {
      details: "Boss Cat",
      state: label,
      assets: { large_image: CLAWD_ICON_URL, large_text: "Clawd on Desk" },
    };
  }
  const coarse = toCoarseState(session && session.state);
  // ...unchanged original logic below
```

Identifies the companion session by its **fixed `session_id`**
(`"companion-watcher"`, matching companion-watcher.js), not by `agentId` --
see [[Local Hook API Cheatsheet#agent_id -- why companion-watcher.js omits
it|the agent_id note]] for why that's the safer identifier here. Everything
below the `if` block is the original, completely untouched logic for every
real coding-agent session.

## Net effect

| Session | Discord shows |
|---|---|
| Real Claude Code session, coding | `Claude Code` / `Working` (unchanged) |
| companion-watcher, `display_svg: "gaming"` | `Boss Cat` / `Playing a game` |
| companion-watcher, `display_svg: "music"` | `Boss Cat` / `Listening to music` |
| companion-watcher, `display_svg: "video"` | `Boss Cat` / `Watching a video` |

## Re-applying after an upstream `git pull`

Both diffs are small and additive (one new field, one new early-return
branch) with no upstream logic deleted or reordered, so a `git pull`
conflict here should be a one-line "also keep my hunk" resolution, not a
rewrite. If either file has been refactored upstream, re-locate:
`buildSessionSnapshotEntry`'s returned object literal (patch 1), and the top
of `buildPresencePayload` (patch 2).
