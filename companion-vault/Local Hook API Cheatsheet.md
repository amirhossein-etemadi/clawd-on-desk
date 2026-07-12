---
tags: [clawd, companion, api]
---

# Local Hook API Cheatsheet

See [[00 Index]] for the overview. Source of truth: `hooks/server-config.js`
and `src/server-route-state.js` in the repo -- this note is a distilled
summary, re-check those files if behavior seems to have changed upstream.

## The endpoint

Clawd runs a plain HTTP server on `127.0.0.1`, trying ports `23333`-`23337`
in order (`hooks/server-config.js: SERVER_PORTS`). Any hook script or
external tool POSTs JSON to:

```
POST http://127.0.0.1:23333/state
Content-Type: application/json
```

A successful response has header `x-clawd-server: clawd-on-desk` -- that's
how callers confirm they actually reached Clawd and not some other local
service. `hooks/server-config.js` exports `postStateToRunningServer(body,
options, callback)` which already does port discovery + fallback + this
verification; **reuse that function** (`require("../hooks/server-config")`)
instead of reimplementing the HTTP call. This is exactly what
`companion-watcher.js` does.

## Minimum valid body

```json
{ "state": "juggling", "session_id": "my-session-id", "event": "AnythingYouWant" }
```

- `state` **must** be one of the states the *currently active theme*
  defines (`idle`, `thinking`, `working`, `error`, `attention`,
  `notification`, `sweeping`, `carrying`, `juggling`, `sleeping`, plus any
  `mini-*` states). Server checks this against `ctx.STATE_SVGS[state]` and
  returns `400 unknown state` if it's not defined -- so a custom theme that
  doesn't declare a state (even via `fallbackTo`) will reject it.
- `session_id` is your session's own namespace. Use a fixed, unique id (e.g.
  `"companion-watcher"`) for anything that isn't a real per-invocation coding
  session, so it never collides with a real agent's session id.
  `session_id` defaults to `"default"` if omitted -- **don't omit it**, or
  you'll fight with whatever else uses the default bucket.
  - **Do not name it `"default"`** either, for the same reason.
- `event` is mostly free-form / for logging (recent-events ring, dashboard).
  A few specific event strings have special server-side behavior --
  `"SessionEnd"` is the important one (see below). Avoid accidentally using
  the exact strings `PostToolUse`, `PostToolUseFailure`, `Stop`, `SessionEnd`,
  `UserPromptSubmit`, `PreToolUse` unless you want their special-case
  behavior (mostly around permission-bubble bookkeeping, harmless to trigger
  but pointless for a non-agent session).

## `display_svg` -- picking a specific asset within a state

Only for `state` values `"working"`, `"thinking"`, `"juggling"`. If the
active theme's `theme.json` declares a `displayHintMap` (a plain object
mapping short keys to filenames, e.g. `{"gaming": "juggling-gaming.svg"}`),
you can send:

```json
{ "state": "juggling", "session_id": "companion-watcher", "event": "X", "display_svg": "gaming" }
```

`display_svg` must be a **key that exists in the theme's `displayHintMap`**
(server validates via `state-session-events.js: pickDisplayHint`) -- passing
an arbitrary filename does nothing (falls back to the state's default file
or whatever was previously set). This is how [[Boss Cat Theme]] shows three
different pieces of art (gaming / music / video) from the single `juggling`
state slot without touching any core app code.

## Ending a session cleanly

Send `event: "SessionEnd"` (any `state` value; conventionally `"sleeping"`)
to delete the session outright:

```json
{ "state": "sleeping", "session_id": "companion-watcher", "event": "SessionEnd" }
```

`src/state.js` deletes the session on `event === "SessionEnd"` regardless of
`state`. If you never send this, the session lingers until the stale-cleanup
timers below kick in -- fine as a safety net, but send `SessionEnd`
explicitly when you know an activity has ended so the pet returns to idle
immediately instead of waiting on a timer.

## Staying alive (heartbeat) vs going stale

`src/state-stale-cleanup.js` has two timers that matter if you don't control
`agentPid`/`source_pid` (which a script like companion-watcher.js never
sets, since there's no real agent process to point at):

- `WORKING_STALE_MS = 300000` (5 min): if a session's state is
  `working`/`juggling`/`thinking` and hasn't been updated in 5 minutes, it
  flips to `idle` automatically.
- `SESSION_STALE_MS = 600000` (10 min): after 10 minutes with no update and
  no reachable PID, the session is deleted outright.

**Send a heartbeat well under 5 minutes** (companion-watcher.js uses 60s) by
re-POSTing the same `state`/`display_svg` periodically for as long as the
activity is ongoing, or the pet will silently drop back to idle mid-session.

## agent_id -- why companion-watcher.js omits it

`src/server-agent-id.js: resolveHookAgentId()` only accepts an `agent_id`
value that's already registered in `agents/registry.js` (the real coding
CLI integrations: `claude-code`, `codex`, `cursor-agent`, etc.). Anything
else either gets silently relabeled as a Claude Code *subagent* (confusing:
it'd show up looking like a Claude Code Task subagent in the dashboard) or,
if omitted entirely, just defaults to `agentId: "claude-code"` with
`defaulted: true`. companion-watcher.js **omits `agent_id`** on purpose --
cleanest available option without registering a whole new agent in the
registry (which would touch settings UI, doctor-detectors, and process
detection across ~10 files for a mostly cosmetic dashboard label; not worth
the blast radius). The one place this labeling gap is patched is
[[Discord Presence Patch]], which identifies the companion session by its
fixed `session_id` instead of by `agentId`.

## Priority when multiple sessions are live

`src/state-priority.js: STATE_PRIORITY` -- higher wins when deciding what
the pet displays and what Discord Rich Presence shows:

```
error 8 > notification 7 > sweeping 6 > attention 5 > carrying/juggling 4
  > working 3 > thinking 2 > idle 1 > sleeping 0
```

So a real coding-agent session in `working`/`thinking` briefly loses the
visual to a `juggling` companion session (priority 4 > 3/2) -- acceptable,
since "off the clock" is meant to be the dominant read when nothing more
important (an error, a permission prompt) is happening.
