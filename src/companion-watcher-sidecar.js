"use strict";

// ── companion-watcher.js auto-start sidecar ──
//
// Spawns scripts/companion-watcher.js as a background child process tied to
// Clawd's own lifecycle, so enabling Clawd's existing "Open at login" system
// setting transparently starts the companion watcher too -- the user no
// longer has to open a terminal and run `npm run watch-companion` by hand.
//
// Spawned via `process.execPath` with ELECTRON_RUN_AS_NODE=1 rather than a
// system `node` binary: Electron's own binary embeds Node and honors that
// env var to run a plain CommonJS script headlessly (no window, no
// Chromium), which works identically in a source checkout (`npm start`) and
// in a packaged build where the end user has no separate Node.js install.
//
// companion-watcher.js has no IPC handshake (unlike the Telegram approval
// sidecar) -- it just posts HTTP to Clawd's own local hook server on its own
// poll loop -- so this wrapper only needs spawn/track/restart/stop, no
// readiness protocol.

const fs = require("fs");
const path = require("path");
const childProcess = require("child_process");

const DEFAULT_RESTART_WINDOW_MS = 60000;
const DEFAULT_RESTART_LIMIT = 5;
const DEFAULT_RESTART_BACKOFF_MS = 3000;

function defaultScriptPath() {
  return path.join(__dirname, "..", "scripts", "companion-watcher.js");
}

function createCompanionWatcherSidecar(options = {}) {
  const scriptPath = options.scriptPath || defaultScriptPath();
  const fsModule = options.fs || fs;
  const spawnFn = options.spawn || childProcess.spawn;
  const execPath = options.execPath || process.execPath;
  const baseEnv = options.baseEnv || process.env;
  const log = typeof options.log === "function" ? options.log : () => {};
  const restartWindowMs = options.restartWindowMs == null ? DEFAULT_RESTART_WINDOW_MS : Number(options.restartWindowMs);
  const restartLimit = options.restartLimit == null ? DEFAULT_RESTART_LIMIT : Number(options.restartLimit);
  const restartBackoffMs = options.restartBackoffMs == null ? DEFAULT_RESTART_BACKOFF_MS : Number(options.restartBackoffMs);
  const setTimer = options.setTimeout || setTimeout;
  const clearTimer = options.clearTimeout || clearTimeout;
  const now = options.now || (() => Date.now());

  let child = null;
  let stopped = true;
  let restartTimer = null;
  let restartAttempts = [];

  function scriptExists() {
    try {
      return fsModule.existsSync(scriptPath);
    } catch {
      return false;
    }
  }

  function clearRestartTimer() {
    if (restartTimer) clearTimer(restartTimer);
    restartTimer = null;
  }

  function scheduleRestart() {
    if (stopped) return;
    const nowTs = now();
    restartAttempts = restartAttempts.filter((ts) => nowTs - ts < restartWindowMs);
    if (restartAttempts.length >= restartLimit) {
      log("warn", `companion-watcher: crashed ${restartLimit}+ times in the last minute, giving up until next app restart`);
      return;
    }
    restartAttempts.push(nowTs);
    clearRestartTimer();
    restartTimer = setTimer(() => {
      restartTimer = null;
      start();
    }, Math.max(1, restartBackoffMs));
  }

  function start() {
    if (child) return true;
    if (!scriptExists()) {
      log("info", `companion-watcher: script not found at ${scriptPath}, skipping auto-start`);
      return false;
    }
    stopped = false;
    let proc;
    try {
      proc = spawnFn(execPath, [scriptPath], {
        env: { ...baseEnv, ELECTRON_RUN_AS_NODE: "1" },
        stdio: "ignore",
        windowsHide: true,
      });
    } catch (err) {
      log("warn", `companion-watcher: spawn failed (${err && err.message ? err.message : err})`);
      child = null;
      scheduleRestart();
      return false;
    }
    child = proc;
    proc.on("exit", (code, signal) => {
      if (child === proc) child = null;
      if (!stopped) {
        log("info", `companion-watcher: exited (code ${code == null ? "?" : code}, signal ${signal || "none"}), restarting`);
        scheduleRestart();
      }
    });
    proc.on("error", (err) => {
      log("warn", `companion-watcher: error (${err && err.message ? err.message : err})`);
    });
    // Detach from Clawd's event loop keep-alive bookkeeping; explicit stop()
    // (on before-quit) still reaches it via child.kill().
    if (typeof proc.unref === "function") proc.unref();
    return true;
  }

  function stop() {
    stopped = true;
    clearRestartTimer();
    restartAttempts = [];
    const proc = child;
    child = null;
    if (proc) {
      try {
        proc.kill();
      } catch {
        // Ignore process teardown races.
      }
    }
  }

  function isRunning() {
    return !!child;
  }

  return { start, stop, isRunning, scriptPath };
}

module.exports = { createCompanionWatcherSidecar, defaultScriptPath };
