"use strict";

const path = require("path");

const WINDOW_WIDTH = 420;
const WINDOW_HEIGHT = 640;

// Lightweight standalone window for the minigames feature -- modeled on
// settings-window.js's lazy-singleton pattern (create once, reuse, clear the
// var on close) but drastically simplified: fixed size, no multi-display
// bounds math, no text-scale plumbing. This window doesn't need any of that.
function createMinigamesWindowRuntime(options = {}) {
  const { app, BrowserWindow, nativeTheme, getIconPath } = options;
  const pathApi = options.path || path;
  let win = null;

  function isLive(w) {
    return !!w && !w.isDestroyed();
  }

  function open() {
    if (isLive(win)) {
      if (win.isMinimized()) win.restore();
      win.show();
      win.focus();
      return win;
    }

    const opts = {
      width: WINDOW_WIDTH,
      height: WINDOW_HEIGHT,
      show: false,
      frame: true,
      resizable: false,
      minimizable: true,
      maximizable: false,
      skipTaskbar: false,
      alwaysOnTop: false,
      title: "Minigames",
      backgroundColor: nativeTheme && nativeTheme.shouldUseDarkColors ? "#1c1c1f" : "#f5f5f7",
      webPreferences: {
        preload: pathApi.join(__dirname, "preload-minigames.js"),
        nodeIntegration: false,
        contextIsolation: true,
      },
    };
    const iconPath = typeof getIconPath === "function" ? getIconPath() : null;
    if (iconPath) opts.icon = iconPath;

    win = new BrowserWindow(opts);
    if (typeof win.setMenuBarVisibility === "function") win.setMenuBarVisibility(false);
    win.loadFile(pathApi.join(__dirname, "minigames.html"));
    win.once("ready-to-show", () => { if (isLive(win)) win.show(); });
    win.on("closed", () => { win = null; });
    return win;
  }

  function openWhenReady() {
    if (!app || app.isReady()) { open(); return; }
    app.once("ready", open);
  }

  function getWindow() {
    return win;
  }

  return { open, openWhenReady, getWindow };
}

module.exports = { createMinigamesWindowRuntime };
