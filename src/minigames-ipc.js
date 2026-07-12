"use strict";

const fs = require("fs");
const path = require("path");

function requiredDependency(value, name) {
  if (!value) throw new Error(`registerMinigamesIpc requires ${name}`);
  return value;
}

// Cross-process XP hand-off to companion-watcher.js: a "drop folder" queue
// instead of a shared read-modify-write file, so two separate Node
// processes (this Electron main process, and the standalone
// companion-watcher.js script) never race on the same file. This process
// only ever CREATES new files here (a pure atomic write, no read needed);
// companion-watcher.js only ever READS+DELETES files it finds in its poll
// loop, each deletion independent of the others -- no shared mutable state,
// so there's nothing for the two processes to race on. See poll()'s
// consumeMinigameXpQueue() in scripts/companion-watcher.js for the other
// side of this.
const QUEUE_DIR = path.join(__dirname, "..", "scripts", "companion-minigame-queue");
const MAX_XP_PER_AWARD = 200; // sanity cap -- a buggy/compromised renderer can't mint unbounded XP

function ensureQueueDir() {
  try { fs.mkdirSync(QUEUE_DIR, { recursive: true }); } catch {}
}

function registerMinigamesIpc(options = {}) {
  const ipcMain = requiredDependency(options.ipcMain, "ipcMain");
  const getWindow = options.getWindow || (() => null);
  const getLang = options.getLang || (() => "en");

  const disposers = [];
  function on(channel, listener) {
    ipcMain.on(channel, listener);
    disposers.push(() => ipcMain.removeListener(channel, listener));
  }
  function handle(channel, listener) {
    ipcMain.handle(channel, listener);
    disposers.push(() => ipcMain.removeHandler(channel));
  }

  on("minigames:award-xp", (_event, amount, reason) => {
    const n = Number(amount);
    if (!Number.isFinite(n) || n <= 0) return;
    ensureQueueDir();
    const safeAmount = Math.min(MAX_XP_PER_AWARD, Math.round(n));
    const file = path.join(QUEUE_DIR, `${Date.now()}-${Math.random().toString(36).slice(2, 8)}.json`);
    try {
      fs.writeFileSync(file, JSON.stringify({
        amount: safeAmount,
        reason: typeof reason === "string" && reason ? reason.slice(0, 80) : "Minigame",
      }));
    } catch {}
  });

  on("minigames:close", () => {
    const win = getWindow();
    if (win && !win.isDestroyed()) win.close();
  });

  handle("minigames:get-lang", () => getLang());

  return {
    dispose() {
      for (const off of disposers.splice(0)) off();
    },
  };
}

module.exports = { registerMinigamesIpc, QUEUE_DIR };
