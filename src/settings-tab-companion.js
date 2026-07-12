"use strict";

// Settings > Companion tab: everything about the Boss Cat companion add-on
// (scripts/companion-watcher.js) that isn't already covered elsewhere --
// General has the on/off + auto-start toggles, Remote Approval has the
// Telegram alert toggle. This tab is: is it running right now, a restart
// button, the break-reminder threshold, quick access to the editable
// game/meeting config files, and a read-only look at your streak/stats
// (the same numbers `npm run companion-report` prints).

(function initSettingsTabCompanion(root) {
  let state = null;
  let helpers = null;
  let ops = null;

  const view = {
    status: null, // { running, state: { streak, stats, achievements } } once loaded
    statusLoading: false,
    statusSeq: 0,
    restartPending: false,
  };

  function t(key) {
    return helpers.t(key);
  }

  function fetchStatus() {
    if (!window.settingsAPI || typeof window.settingsAPI.companionGetStatus !== "function") return;
    view.statusLoading = true;
    const seq = ++view.statusSeq;
    window.settingsAPI.companionGetStatus().then((result) => {
      if (seq !== view.statusSeq) return;
      view.status = result;
      view.statusLoading = false;
      ops.requestRender({ content: true });
    }).catch(() => {
      if (seq !== view.statusSeq) return;
      view.statusLoading = false;
      ops.requestRender({ content: true });
    });
  }

  function fmtMinutes(seconds) {
    const mins = Math.round((seconds || 0) / 60);
    if (mins < 60) return `${mins}${t("unitMinutesShort")}`;
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    return m ? `${h}${t("unitHoursShort")} ${m}${t("unitMinutesShort")}` : `${h}${t("unitHoursShort")}`;
  }

  function todayKey() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }

  function weekTotals(stats) {
    const totals = { gaming: 0, meeting: 0, video: 0, music: 0, typing: 0 };
    const d = new Date();
    for (let i = 0; i < 7; i++) {
      const day = new Date(d);
      day.setDate(day.getDate() - i);
      const key = `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, "0")}-${String(day.getDate()).padStart(2, "0")}`;
      const bucket = stats && stats[key];
      if (!bucket) continue;
      totals.gaming += bucket.gaming || 0;
      totals.meeting += bucket.meeting || 0;
      totals.video += bucket.video || 0;
      totals.music += bucket.music || 0;
      totals.typing += bucket.typing || 0;
    }
    return totals;
  }

  function render(parent) {
    const h1 = document.createElement("h1");
    h1.textContent = t("companionTitle");
    parent.appendChild(h1);

    const subtitle = document.createElement("p");
    subtitle.className = "subtitle";
    subtitle.textContent = t("companionSubtitle");
    parent.appendChild(subtitle);

    if (!view.status && !view.statusLoading) fetchStatus();

    parent.appendChild(helpers.buildSection(t("companionSectionStatus"), [
      buildStatusRow(),
      buildRestartRow(),
      buildConfigFolderRow(),
    ]));

    parent.appendChild(helpers.buildSection(t("companionSectionSettings"), [
      helpers.buildNumberInputRow({
        key: "companionBreakReminderMinutes",
        labelKey: "rowCompanionBreakMinutes",
        descKey: "rowCompanionBreakMinutesDesc",
        unitKey: "unitMinutes",
        toDisplay: (min) => min,
        fromDisplay: (min) => min,
        min: 5,
        max: 480,
      }).row,
    ]));

    parent.appendChild(helpers.buildSection(t("companionSectionPhoneSync"), buildPhoneSyncRows()));

    parent.appendChild(helpers.buildSection(t("companionSectionLevel"), buildLevelRows()));

    parent.appendChild(helpers.buildSection(t("companionSectionStats"), buildStatsRows()));
  }

  function buildStatusRow() {
    const row = document.createElement("div");
    row.className = "row";
    const text = document.createElement("div");
    text.className = "row-text";
    const label = document.createElement("span");
    label.className = "row-label";
    label.textContent = t("rowCompanionStatus");
    const desc = document.createElement("span");
    desc.className = "row-desc";
    if (view.statusLoading && !view.status) {
      desc.textContent = t("companionStatusLoading");
    } else if (view.status && view.status.running) {
      desc.textContent = t("companionStatusRunning");
    } else {
      desc.textContent = t("companionStatusStopped");
    }
    text.appendChild(label);
    text.appendChild(desc);
    row.appendChild(text);
    return row;
  }

  function buildRestartRow() {
    const row = document.createElement("div");
    row.className = "row";
    row.innerHTML =
      `<div class="row-text">` +
        `<span class="row-label"></span>` +
        `<span class="row-desc"></span>` +
      `</div>` +
      `<div class="row-control">` +
        `<button type="button" class="soft-btn"></button>` +
      `</div>`;
    row.querySelector(".row-label").textContent = t("rowCompanionRestart");
    row.querySelector(".row-desc").textContent = t("rowCompanionRestartDesc");
    const btn = row.querySelector("button");
    btn.textContent = view.restartPending ? t("companionRestarting") : t("actionCompanionRestart");
    btn.disabled = view.restartPending;
    btn.addEventListener("click", () => {
      if (!window.settingsAPI || typeof window.settingsAPI.companionRestart !== "function") return;
      view.restartPending = true;
      ops.requestRender({ content: true });
      window.settingsAPI.companionRestart().then((result) => {
        view.restartPending = false;
        if (!result || result.status !== "ok") {
          ops.showToast((result && result.message) || t("toastSaveFailed"), { error: true });
        } else {
          ops.showToast(t("companionRestarted"));
        }
        fetchStatus();
      }).catch((err) => {
        view.restartPending = false;
        ops.showToast(t("toastSaveFailed") + (err && err.message), { error: true });
        ops.requestRender({ content: true });
      });
    });
    return row;
  }

  function buildConfigFolderRow() {
    const row = document.createElement("div");
    row.className = "row";
    row.innerHTML =
      `<div class="row-text">` +
        `<span class="row-label"></span>` +
        `<span class="row-desc"></span>` +
      `</div>` +
      `<div class="row-control">` +
        `<button type="button" class="soft-btn"></button>` +
      `</div>`;
    row.querySelector(".row-label").textContent = t("rowCompanionConfigFolder");
    row.querySelector(".row-desc").textContent = t("rowCompanionConfigFolderDesc");
    const btn = row.querySelector("button");
    btn.textContent = t("actionCompanionOpenFolder");
    btn.addEventListener("click", () => {
      if (!window.settingsAPI || typeof window.settingsAPI.companionOpenConfigFolder !== "function") return;
      window.settingsAPI.companionOpenConfigFolder().catch(() => {});
    });
    return row;
  }

  // ---- Phone Sync (Android companion app, see cloud/README.md) ----
  //
  // No 0/1/I/O in the alphabet -- avoids ambiguity when a code is read off
  // one screen and typed into another. Matches the SYNC_CODE_RE the relay
  // enforces server-side (cloud/src/worker.js): /^[A-Z2-9]{8,12}$/.
  const SYNC_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  const SYNC_CODE_LENGTH = 10;

  function generateSyncCode() {
    const bytes = new Uint8Array(SYNC_CODE_LENGTH);
    if (window.crypto && typeof window.crypto.getRandomValues === "function") {
      window.crypto.getRandomValues(bytes);
    } else {
      for (let i = 0; i < SYNC_CODE_LENGTH; i++) bytes[i] = Math.floor(Math.random() * 256);
    }
    let code = "";
    for (let i = 0; i < SYNC_CODE_LENGTH; i++) code += SYNC_CODE_ALPHABET[bytes[i] % SYNC_CODE_ALPHABET.length];
    return code;
  }

  function updateSetting(key, value) {
    if (!window.settingsAPI || typeof window.settingsAPI.update !== "function") return Promise.resolve();
    return window.settingsAPI.update(key, value).then(() => {
      ops.requestRender({ content: true });
    }).catch((err) => {
      ops.showToast(t("toastSaveFailed") + (err && err.message), { error: true });
    });
  }

  function buildCloudRelayUrlRow() {
    const row = document.createElement("div");
    row.className = "row";
    row.innerHTML =
      `<div class="row-text">` +
        `<span class="row-label"></span>` +
        `<span class="row-desc"></span>` +
      `</div>` +
      `<div class="row-control" style="display:flex;gap:6px;align-items:center">` +
        `<input type="text" class="text-input" style="min-width:260px" placeholder="https://ilia-companion-sync.your-subdomain.workers.dev" />` +
        `<button type="button" class="soft-btn"></button>` +
      `</div>`;
    row.querySelector(".row-label").textContent = t("rowCloudRelayUrl");
    row.querySelector(".row-desc").textContent = t("rowCloudRelayUrlDesc");
    const input = row.querySelector("input");
    input.value = (state.snapshot && state.snapshot.cloudRelayUrl) || "";
    const saveBtn = row.querySelector("button");
    saveBtn.textContent = t("actionSave");
    saveBtn.addEventListener("click", () => updateSetting("cloudRelayUrl", input.value.trim()));
    input.addEventListener("keydown", (e) => {
      if (e.key === "Enter") updateSetting("cloudRelayUrl", input.value.trim());
    });
    return row;
  }

  function buildCloudSyncCodeRow() {
    const row = document.createElement("div");
    row.className = "row";
    row.innerHTML =
      `<div class="row-text">` +
        `<span class="row-label"></span>` +
        `<span class="row-desc"></span>` +
      `</div>` +
      `<div class="row-control" style="display:flex;gap:6px;align-items:center;flex-wrap:wrap">` +
        `<code class="companion-sync-code"></code>` +
        `<button type="button" class="soft-btn" data-role="copy"></button>` +
        `<button type="button" class="soft-btn" data-role="generate"></button>` +
      `</div>`;
    row.querySelector(".row-label").textContent = t("rowCloudSyncCode");
    row.querySelector(".row-desc").textContent = t("rowCloudSyncCodeDesc");

    const code = (state.snapshot && state.snapshot.cloudSyncCode) || "";
    const codeEl = row.querySelector(".companion-sync-code");
    codeEl.textContent = code || t("cloudSyncCodeNone");

    const copyBtn = row.querySelector('[data-role="copy"]');
    copyBtn.textContent = t("actionCopy");
    copyBtn.disabled = !code;
    copyBtn.addEventListener("click", () => {
      if (!code || !navigator.clipboard) return;
      navigator.clipboard.writeText(code).then(() => {
        copyBtn.textContent = t("actionCopied");
        setTimeout(() => { copyBtn.textContent = t("actionCopy"); }, 1500);
      });
    });

    const genBtn = row.querySelector('[data-role="generate"]');
    genBtn.textContent = code ? t("actionRegenerateSyncCode") : t("actionGenerateSyncCode");
    genBtn.addEventListener("click", () => {
      updateSetting("cloudSyncCode", generateSyncCode());
    });

    return row;
  }

  function buildPhoneSyncRows() {
    const desc = document.createElement("p");
    desc.className = "subtitle";
    desc.textContent = t("companionPhoneSyncDesc");

    return [
      desc,
      helpers.buildSwitchRow({
        key: "cloudSyncEnabled",
        labelKey: "rowCloudSyncEnabled",
        descKey: "rowCloudSyncEnabledDesc",
      }),
      buildCloudRelayUrlRow(),
      buildCloudSyncCodeRow(),
    ];
  }

  function buildLevelRows() {
    const data = (view.status && view.status.state) || {};
    const p = data.progression || { level: 1, title: "Fresh Paws", xp: 0, xpToNext: 140 };
    const pct = p.xpToNext > 0 ? Math.max(0, Math.min(100, Math.round((p.xp / p.xpToNext) * 100))) : 0;

    const levelRow = document.createElement("div");
    levelRow.className = "row";
    levelRow.innerHTML =
      `<div class="row-text">` +
        `<span class="row-label"></span>` +
        `<span class="row-desc"></span>` +
      `</div>`;
    levelRow.querySelector(".row-label").textContent = `${t("companionStatLevel")} ${p.level} — ${p.title}`;
    levelRow.querySelector(".row-desc").textContent = `${Math.round(p.xp)} / ${Math.round(p.xpToNext)} XP`;

    const barRow = document.createElement("div");
    barRow.className = "row";
    barRow.innerHTML =
      `<div class="row-text" style="width:100%">` +
        `<div style="width:100%;height:8px;border-radius:4px;background:rgba(127,127,127,0.25);overflow:hidden;">` +
          `<div style="height:100%;width:${pct}%;border-radius:4px;background:linear-gradient(90deg,#f6bfcb,#f3d9a8);transition:width 0.3s ease;"></div>` +
        `</div>` +
      `</div>`;

    return [levelRow, barRow, buildAccessoryRow(p.level)];
  }

  // Level thresholds mirror ACCESSORY_UNLOCKS in scripts/companion-watcher.js
  // (duplicated here since this is a separate renderer process with no
  // access to that file) -- keep the two in sync if accessory art is added.
  const ACCESSORY_UNLOCK_LEVELS = { partyHat: 3, sunglasses: 7 };
  const ACCESSORY_LABEL_KEYS = { partyHat: "companionAccessoryPartyHat", sunglasses: "companionAccessorySunglasses" };

  function buildAccessoryRow(currentLevel) {
    const row = document.createElement("div");
    row.className = "row";
    row.innerHTML =
      `<div class="row-text">` +
        `<span class="row-label"></span>` +
        `<span class="row-desc"></span>` +
      `</div>` +
      `<div class="row-control" style="display:flex;gap:6px;flex-wrap:wrap"></div>`;
    row.querySelector(".row-label").textContent = t("rowCompanionAccessory");
    row.querySelector(".row-desc").textContent = t("rowCompanionAccessoryDesc");

    const control = row.querySelector(".row-control");
    const current = (state.snapshot && state.snapshot.companionEquippedAccessory) || "none";

    const options = [{ key: "none", label: t("companionAccessoryNone"), unlocked: true }].concat(
      Object.keys(ACCESSORY_UNLOCK_LEVELS).map((key) => ({
        key,
        label: t(ACCESSORY_LABEL_KEYS[key]),
        unlocked: currentLevel >= ACCESSORY_UNLOCK_LEVELS[key],
      }))
    );

    for (const opt of options) {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "soft-btn" + (opt.key === current ? " accent" : "");
      btn.textContent = opt.unlocked ? opt.label : `${opt.label} 🔒 Lv.${ACCESSORY_UNLOCK_LEVELS[opt.key]}`;
      if (!opt.unlocked) {
        btn.disabled = true;
      } else {
        btn.addEventListener("click", () => {
          if (!window.settingsAPI || typeof window.settingsAPI.update !== "function") return;
          window.settingsAPI.update("companionEquippedAccessory", opt.key).then(() => {
            ops.requestRender({ content: true });
          }).catch((err) => {
            ops.showToast(t("toastSaveFailed") + (err && err.message), { error: true });
          });
        });
      }
      control.appendChild(btn);
    }

    return row;
  }

  function buildStatsRows() {
    const data = (view.status && view.status.state) || { streak: { count: 0, lastActiveDate: null }, stats: {}, achievements: {} };
    const today = data.stats[todayKey()] || { gaming: 0, meeting: 0, video: 0, music: 0, typing: 0 };
    const week = weekTotals(data.stats);
    const achievements = data.achievements || {};

    const rows = [];

    const streakRow = document.createElement("div");
    streakRow.className = "row";
    streakRow.innerHTML =
      `<div class="row-text">` +
        `<span class="row-label"></span>` +
        `<span class="row-desc"></span>` +
      `</div>`;
    streakRow.querySelector(".row-label").textContent = t("companionStatStreak");
    streakRow.querySelector(".row-desc").textContent =
      `${data.streak.count || 0} ${t("companionStatDays")}` + (data.streak.lastActiveDate ? ` (${t("companionStatLastActive")}: ${data.streak.lastActiveDate})` : "");
    rows.push(streakRow);

    const todayRow = document.createElement("div");
    todayRow.className = "row";
    todayRow.innerHTML =
      `<div class="row-text">` +
        `<span class="row-label"></span>` +
        `<span class="row-desc"></span>` +
      `</div>`;
    todayRow.querySelector(".row-label").textContent = t("companionStatToday");
    todayRow.querySelector(".row-desc").textContent =
      `${t("companionActivityGaming")} ${fmtMinutes(today.gaming)} · ${t("companionActivityMeeting")} ${fmtMinutes(today.meeting)} · ${t("companionActivityVideo")} ${fmtMinutes(today.video)} · ${t("companionActivityMusic")} ${fmtMinutes(today.music)} · ${t("companionActivityTyping")} ${fmtMinutes(today.typing)}`;
    rows.push(todayRow);

    const weekRow = document.createElement("div");
    weekRow.className = "row";
    weekRow.innerHTML =
      `<div class="row-text">` +
        `<span class="row-label"></span>` +
        `<span class="row-desc"></span>` +
      `</div>`;
    weekRow.querySelector(".row-label").textContent = t("companionStatWeek");
    weekRow.querySelector(".row-desc").textContent =
      `${t("companionActivityGaming")} ${fmtMinutes(week.gaming)} · ${t("companionActivityMeeting")} ${fmtMinutes(week.meeting)} · ${t("companionActivityVideo")} ${fmtMinutes(week.video)} · ${t("companionActivityMusic")} ${fmtMinutes(week.music)} · ${t("companionActivityTyping")} ${fmtMinutes(week.typing)}`;
    rows.push(weekRow);

    const earned = [];
    if (achievements.nightOwl) earned.push(t("companionAchievementNightOwl"));
    if (achievements.earlyBird) earned.push(t("companionAchievementEarlyBird"));
    if (achievements.marathon) earned.push(t("companionAchievementMarathon"));
    const achievementsRow = document.createElement("div");
    achievementsRow.className = "row";
    achievementsRow.innerHTML =
      `<div class="row-text">` +
        `<span class="row-label"></span>` +
        `<span class="row-desc"></span>` +
      `</div>`;
    achievementsRow.querySelector(".row-label").textContent = t("companionStatAchievements");
    achievementsRow.querySelector(".row-desc").textContent = earned.length
      ? earned.join(" · ") + (achievements.longestSessionSeconds ? ` · ${t("companionStatLongestSession")}: ${fmtMinutes(achievements.longestSessionSeconds)}` : "")
      : t("companionStatNoAchievementsYet");
    rows.push(achievementsRow);

    return rows;
  }

  function init(core) {
    state = core.state;
    helpers = core.helpers;
    ops = core.ops;
    core.tabs["companion"] = { render };
  }

  root.ClawdSettingsTabCompanion = { init };
})(globalThis);
