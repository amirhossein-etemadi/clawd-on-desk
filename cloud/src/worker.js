// Cloudflare Worker: relays live companion state between the desktop app
// and the Android companion app in real time over WebSocket.
//
// One CompanionRoom Durable Object exists per "sync code" (see
// generateSyncCode() in src/main.js on the desktop side, surfaced in
// Settings > Companion > Phone Sync). The desktop connects as
// role=desktop and pushes state updates; any connected phones
// (role=phone) receive them instantly. The room also remembers the last
// state (in its own persistent SQLite-backed storage) so a phone that
// connects later, or reconnects after a network blip, gets caught up
// immediately without waiting for the next desktop update.
//
// This relay never sees your source code, files, or anything from your
// coding sessions -- only the same small "companion state" summary
// (activity label, display hint, level, XP, streak) that already drives
// the desktop pet and Discord presence.
//
// Deploy: from this directory, `npx wrangler login` once, then
// `npx wrangler deploy`. See README.md for the full walkthrough.

const SYNC_CODE_RE = /^[A-Z2-9]{8,12}$/; // Crockford-ish base32, no 0/1/I/O/L confusion
const MAX_STATE_BYTES = 8192; // generous cap -- real payloads are ~1-2KB
const MAX_REACTION_BYTES = 512;

export class CompanionRoom {
  constructor(state, env) {
    this.state = state;
    this.env = env;
    this.sockets = new Set(); // { ws, role }
    this.lastState = null; // last state payload pushed by the desktop, cached in memory
  }

  async fetch(request) {
    const url = new URL(request.url);
    const role = url.searchParams.get("role");
    if (role !== "desktop" && role !== "phone") {
      return new Response("role must be 'desktop' or 'phone'", { status: 400 });
    }
    if (request.headers.get("Upgrade") !== "websocket") {
      return new Response("expected websocket upgrade", { status: 426 });
    }

    if (this.lastState === null) {
      this.lastState = (await this.state.storage.get("lastState")) || null;
    }

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.accept();

    const conn = { ws: server, role };
    this.sockets.add(conn);

    // Catch the new client up immediately instead of making it wait for
    // the next desktop state change (which might be minutes away if
    // you're just idling).
    if (role === "phone" && this.lastState) {
      this.safeSend(server, { type: "state", data: this.lastState });
    }
    this.broadcastPresence();

    server.addEventListener("message", (evt) => {
      this.handleMessage(conn, evt).catch(() => {
        /* malformed/oversized frame -- ignore, connection stays open */
      });
    });
    server.addEventListener("close", () => {
      this.sockets.delete(conn);
      this.broadcastPresence();
    });
    server.addEventListener("error", () => {
      this.sockets.delete(conn);
      this.broadcastPresence();
    });

    return new Response(null, { status: 101, webSocket: client });
  }

  async handleMessage(conn, evt) {
    const raw = typeof evt.data === "string" ? evt.data : "";
    if (!raw) return;

    let msg;
    try {
      msg = JSON.parse(raw);
    } catch {
      return; // ignore malformed frames
    }
    if (!msg || typeof msg.type !== "string") return;

    if (conn.role === "desktop" && msg.type === "state") {
      if (raw.length > MAX_STATE_BYTES) return;
      this.lastState = msg.data;
      // Persist so a phone connecting after the desktop briefly drops
      // still sees the last known state, and so state survives Durable
      // Object eviction/restart between updates.
      await this.state.storage.put("lastState", this.lastState);
      for (const other of this.sockets) {
        if (other.role === "phone") this.safeSend(other.ws, { type: "state", data: this.lastState });
      }
    } else if (conn.role === "phone" && msg.type === "reaction") {
      // Phone taps/pets the mirror -- forward to the desktop as a
      // cosmetic-only reaction hint. Never mutates real progression
      // state; the desktop decides what (if anything) to do with it.
      if (raw.length > MAX_REACTION_BYTES) return;
      for (const other of this.sockets) {
        if (other.role === "desktop") this.safeSend(other.ws, { type: "reaction", data: msg.data });
      }
    } else if (msg.type === "ping") {
      this.safeSend(conn.ws, { type: "pong" });
    }
  }

  broadcastPresence() {
    const desktopOnline = [...this.sockets].some((c) => c.role === "desktop");
    const phoneCount = [...this.sockets].filter((c) => c.role === "phone").length;
    for (const conn of this.sockets) {
      this.safeSend(conn.ws, { type: "presence", data: { desktopOnline, phoneCount } });
    }
  }

  safeSend(ws, obj) {
    try {
      ws.send(JSON.stringify(obj));
    } catch {
      /* socket likely already closing; the close/error listener will clean it up */
    }
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return new Response("ok", { status: 200 });
    }

    if (url.pathname === "/ws") {
      const code = (url.searchParams.get("code") || "").toUpperCase();
      if (!SYNC_CODE_RE.test(code)) {
        return new Response("invalid or missing 'code' query param", { status: 400 });
      }
      const id = env.COMPANION_ROOM.idFromName(code);
      const room = env.COMPANION_ROOM.get(id);
      return room.fetch(request);
    }

    return new Response(
      "Ilia Companion sync relay. See /health and /ws?code=SYNCCODE&role=desktop|phone",
      { status: 200 }
    );
  },
};
