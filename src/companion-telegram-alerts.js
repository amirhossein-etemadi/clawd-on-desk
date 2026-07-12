"use strict";

// ── Companion Telegram alerts ──
//
// Reuses clawd-on-desk's EXISTING Telegram bot (already set up for remote
// approval requests -- see settings-tab-telegram-approval.js) to DM the user
// when companion-watcher.js posts a streak milestone or break reminder, so
// those land on the phone too, not just as an on-screen pet reaction.
//
// Deliberately does NOT touch the Telegram direct-send / migration-controller
// machinery (native runner states, dogfood fallback modes, clipboard
// delivery adapters, etc.) -- that whole subsystem exists to route a Telegram
// REPLY back into a coding session, which has nothing to do with a one-way
// "hey, 7-day streak!" ping. This module only needs the already-configured
// bot token + chat id and a plain `sendMessage` call, injected from main.js
// (see createCompanionTelegramAlerts usage there) so it stays decoupled and
// unit-testable without a live Electron app.
//
// Rides the same onSnapshot fan-out as discord-presence-rpc.js and
// telegram-companion.js (see main.js's broadcastSessionSnapshot): every
// session snapshot broadcast, look at the companion-watcher session's
// lastEvent (state-session-snapshot.js already exposes { rawEvent, at }) and
// fire once per NEW event occurrence (deduped by `at` timestamp, so a
// snapshot re-broadcast with the same trailing event never double-sends).

const COMPANION_SESSION_ID = "companion-watcher";

const ALERT_EMOJI = Object.freeze({
  CompanionStreak: "🔥",
  CompanionBreak: "🧘",
  CompanionAchievement: "🏆",
});

const ALERT_FALLBACK_TEXT = Object.freeze({
  CompanionStreak: "Streak milestone!",
  CompanionBreak: "Time for a break?",
  CompanionAchievement: "Achievement unlocked!",
});

function createCompanionTelegramAlerts({ sendMessage, isEnabled, log } = {}) {
  const logFn = typeof log === "function" ? log : () => {};
  let lastSeenAt = 0;

  function onSnapshot(snapshot) {
    try {
      if (typeof isEnabled === "function" && !isEnabled()) return;
      if (typeof sendMessage !== "function") return;
      const sessions = snapshot && Array.isArray(snapshot.sessions) ? snapshot.sessions : [];
      const companion = sessions.find((s) => s && s.id === COMPANION_SESSION_ID);
      const lastEvent = companion && companion.lastEvent;
      if (!lastEvent) return;
      const rawEvent = lastEvent.rawEvent;
      if (!(rawEvent in ALERT_EMOJI)) return;
      const at = Number(lastEvent.at);
      if (!Number.isFinite(at) || at <= lastSeenAt) return;
      lastSeenAt = at;
      const emoji = ALERT_EMOJI[rawEvent];
      const text = `${emoji} ${(companion.sessionTitle && String(companion.sessionTitle).trim()) || ALERT_FALLBACK_TEXT[rawEvent]}`;
      Promise.resolve(sendMessage(text)).catch((err) => {
        logFn("warn", "companion telegram alert send failed", { error: err && err.message });
      });
    } catch (err) {
      // Never let a malformed snapshot throw into the broadcast fan-out.
      logFn("warn", "companion telegram alert onSnapshot failed", { error: err && err.message });
    }
  }

  return { onSnapshot };
}

module.exports = { createCompanionTelegramAlerts, COMPANION_SESSION_ID, ALERT_EMOJI, ALERT_FALLBACK_TEXT };
