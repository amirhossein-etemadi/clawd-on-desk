"use strict";

const { contextBridge, ipcRenderer } = require("electron");

// Minimal bridge for the minigames window: award XP (fire-and-forget, main
// process hands it off to companion-watcher.js via a queue directory -- see
// minigames-ipc.js), close the window, and read the current UI language so
// game text matches the rest of the app.
contextBridge.exposeInMainWorld("minigamesAPI", {
  awardXp: (amount, reason) => ipcRenderer.send("minigames:award-xp", amount, reason),
  close: () => ipcRenderer.send("minigames:close"),
  getLang: () => ipcRenderer.invoke("minigames:get-lang"),
});
