# Ilia Companion — cloud sync relay

A tiny Cloudflare Worker that relays your companion's live state (current
activity, display hint, level, XP, streak) between the desktop app and the
Android companion app in real time over WebSocket, so the phone app can
mirror your desktop pet.

It never sees your code, files, or session content — only the same small
"companion state" summary that already drives the desktop pet's animation
and Discord presence.

Runs entirely on Cloudflare's free plan (Workers Free: 100K requests/day,
Durable Objects: 100K requests/day + 5GB storage — this app uses a tiny
fraction of that for personal use).

## One-time setup

1. Create a free Cloudflare account at https://dash.cloudflare.com/sign-up
   if you don't have one already.
2. From this `cloud/` directory:
   ```
   npm install
   npx wrangler login
   ```
   This opens a browser tab to authorize the CLI against your account.
3. Deploy:
   ```
   npx wrangler deploy
   ```
   Wrangler prints your Worker's URL, something like
   `https://ilia-companion-sync.<your-subdomain>.workers.dev`.

4. Copy that URL into the desktop app: Settings > Companion > Phone Sync >
   Relay URL. Then click "Generate Pairing Code" — enter the same code in
   the Android app's pairing screen (or scan the QR code shown in
   Settings).

That's it — no database setup, no environment variables required. The
Durable Object's own storage holds the latest state per pairing code.

## Local development

```
npm run dev
```

Runs the Worker locally (`http://localhost:8787`) via Miniflare, useful for
testing the desktop/phone WebSocket clients against a local relay before
deploying.

## How it works

- Each device pairing gets its own `CompanionRoom` Durable Object,
  identified by the pairing code (`idFromName(code)`), so different
  people's data is fully isolated — no shared database, no accounts, no
  server-side auth beyond knowing the code.
- The desktop connects as `role=desktop` and pushes `{"type":"state",...}`
  messages whenever the companion state changes.
- The phone connects as `role=phone`, immediately gets caught up with the
  last known state (even if the desktop is offline), and receives live
  pushes after that.
- The phone can send `{"type":"reaction",...}` messages back (e.g. "petted
  the mirror"), forwarded to the desktop as a cosmetic-only hint — it never
  mutates your real XP/level/progression, which stays desktop-authoritative.
- The desktop also pushes `{"type":"notify",...}` messages for discrete
  events worth a phone notification (level-ups, streak milestones,
  achievements, break reminders). These fan out to every connected phone —
  a sync code supports any number of paired devices — but are deliberately
  not persisted: a stale reminder shouldn't pop up when a phone reconnects
  an hour later.
- If the desktop is offline, the phone just shows the last synced state; if
  the relay itself is unreachable, both apps fail open (desktop pet and
  Android overlay keep working locally, sync just pauses).

## Cost

Free for personal use. If Cloudflare ever bills you here, it would only be
from far exceeding the free tier (100K Worker + 100K Durable Object
requests per day) — not realistic for a single-person companion sync.
