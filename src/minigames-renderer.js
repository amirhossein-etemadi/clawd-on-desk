"use strict";

// Minigames window renderer: a tiny in-house arcade, purely for fun. No
// frameworks -- vanilla DOM/canvas, same style as the rest of this app's
// renderer scripts. Each game is a self-contained start*(container, onExit)
// function that renders into the shared #app root and tears itself down
// (removes listeners, cancels rAF loops) before handing control back to the
// menu. XP is awarded via window.minigamesAPI.awardXp(amount, reason), which
// hands off to companion-watcher.js's leveling system (see
// scripts/companion-watcher.js's consumeMinigameXpQueue()).

(function () {
  const root = document.getElementById("app");
  let activeCleanup = null;

  function awardXp(amount, reason) {
    if (window.minigamesAPI && typeof window.minigamesAPI.awardXp === "function") {
      window.minigamesAPI.awardXp(amount, reason);
    }
  }

  function clear(el) {
    while (el.firstChild) el.removeChild(el.firstChild);
  }

  function el(tag, props = {}, children = []) {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
      if (k === "style" && typeof v === "object") Object.assign(node.style, v);
      else if (k.startsWith("on") && typeof v === "function") node.addEventListener(k.slice(2).toLowerCase(), v);
      else if (k === "text") node.textContent = v;
      else node.setAttribute(k, v);
    }
    for (const child of [].concat(children)) {
      if (child) node.appendChild(typeof child === "string" ? document.createTextNode(child) : child);
    }
    return node;
  }

  function teardown() {
    if (typeof activeCleanup === "function") {
      try { activeCleanup(); } catch (err) { console.warn("minigame cleanup threw:", err); }
    }
    activeCleanup = null;
  }

  function goToMenu() {
    teardown();
    renderMenu();
  }

  function baseLayout(title) {
    clear(root);
    const bar = el("div", {
      style: {
        display: "flex", alignItems: "center", gap: "8px", padding: "10px 12px",
        borderBottom: "1px solid var(--shadow)", flexShrink: "0",
      },
    }, [
      el("button", {
        text: "←", title: "Back to menu", onClick: goToMenu,
        style: {
          border: "none", background: "var(--panel)", color: "var(--ink)", borderRadius: "8px",
          width: "30px", height: "30px", cursor: "pointer", fontSize: "16px",
        },
      }),
      el("div", { text: title, style: { fontWeight: "700", fontSize: "15px" } }),
    ]);
    const body = el("div", {
      style: {
        flex: "1", display: "flex", flexDirection: "column", alignItems: "center",
        justifyContent: "flex-start", padding: "14px", overflow: "hidden", position: "relative",
      },
    });
    root.appendChild(bar);
    root.appendChild(body);
    return body;
  }

  function bigButton(label, onClick, extraStyle = {}) {
    return el("button", {
      text: label, onClick,
      style: Object.assign({
        border: "none", borderRadius: "12px", padding: "12px 18px", fontSize: "15px",
        fontWeight: "700", cursor: "pointer", background: "var(--accent)", color: "var(--ink)",
        boxShadow: "0 2px 6px var(--shadow)",
      }, extraStyle),
    });
  }

  // ---------- Menu ----------

  const GAMES = [
    { id: "rally", emoji: "🏸", name: "Rally", desc: "Badminton/baseball-style paddle rally -- keep the volley alive." },
    { id: "catch", emoji: "🍣", name: "Catch", desc: "Move the basket, catch treats, dodge bombs." },
    { id: "tap", emoji: "🐾", name: "Tap Frenzy", desc: "Tap your companion as fast as you can for 10 seconds." },
    { id: "memory", emoji: "🧩", name: "Memory Match", desc: "Flip cards, find every pair, beat your best time." },
  ];

  function renderMenu() {
    clear(root);
    const header = el("div", {
      style: { padding: "16px 16px 6px", textAlign: "center", flexShrink: "0" },
    }, [
      el("div", { text: "🎮", style: { fontSize: "30px" } }),
      el("div", { text: "Minigames", style: { fontWeight: "800", fontSize: "18px", marginTop: "4px" } }),
      el("div", {
        text: "Quick games with your companion. Wins earn bonus XP.",
        style: { color: "var(--sub)", fontSize: "12px", marginTop: "2px" },
      }),
    ]);
    const list = el("div", {
      style: {
        flex: "1", overflowY: "auto", padding: "10px 14px 16px", display: "flex",
        flexDirection: "column", gap: "10px",
      },
    });
    for (const g of GAMES) {
      const card = el("button", {
        onClick: () => { teardown(); openGame(g.id); },
        style: {
          display: "flex", alignItems: "center", gap: "12px", textAlign: "left",
          border: "none", borderRadius: "14px", padding: "12px 14px", cursor: "pointer",
          background: "var(--panel)", boxShadow: "0 2px 6px var(--shadow)",
        },
      }, [
        el("div", { text: g.emoji, style: { fontSize: "26px" } }),
        el("div", {}, [
          el("div", { text: g.name, style: { fontWeight: "700", fontSize: "14px" } }),
          el("div", { text: g.desc, style: { color: "var(--sub)", fontSize: "11.5px", marginTop: "2px" } }),
        ]),
      ]);
      list.appendChild(card);
    }
    root.appendChild(header);
    root.appendChild(list);
  }

  function openGame(id) {
    if (id === "rally") activeCleanup = startRally(baseLayout("Rally"));
    else if (id === "catch") activeCleanup = startCatch(baseLayout("Catch"));
    else if (id === "tap") activeCleanup = startTapFrenzy(baseLayout("Tap Frenzy"));
    else if (id === "memory") activeCleanup = startMemoryMatch(baseLayout("Memory Match"));
  }

  // ---------- Game 1: Rally (badminton/baseball-style paddle volley) ----------

  function startRally(body) {
    const W = 360, H = 460;
    const canvas = el("canvas", { width: String(W), height: String(H), style: { borderRadius: "10px", background: "var(--panel)", boxShadow: "0 2px 8px var(--shadow)" } });
    const scoreEl = el("div", { style: { marginTop: "8px", fontWeight: "700", fontSize: "13px" }, text: "Rallies: 0" });
    const hint = el("div", { style: { color: "var(--sub)", fontSize: "11px", marginTop: "2px" }, text: "Move the mouse to slide your paddle. Keep the volley going!" });
    body.appendChild(canvas);
    body.appendChild(scoreEl);
    body.appendChild(hint);
    const ctx = canvas.getContext("2d");

    const paddleW = 64, paddleH = 10;
    let paddleX = W / 2 - paddleW / 2;
    let ballX = W / 2, ballY = 60, ballVX = 2.4, ballVY = 3.2;
    let rallies = 0;
    let gameOver = false;
    let rafId = null;
    let xpGiven = false;

    function onMove(e) {
      const rect = canvas.getBoundingClientRect();
      const x = (e.clientX - rect.left) * (W / rect.width);
      paddleX = Math.max(0, Math.min(W - paddleW, x - paddleW / 2));
    }
    canvas.addEventListener("mousemove", onMove);

    function reset() {
      paddleX = W / 2 - paddleW / 2;
      ballX = W / 2; ballY = 60; ballVX = 2.4; ballVY = 3.2;
      rallies = 0; gameOver = false; xpGiven = false;
      scoreEl.textContent = "Rallies: 0";
    }

    function endGame() {
      gameOver = true;
      if (!xpGiven) {
        xpGiven = true;
        const xp = Math.min(60, rallies * 2);
        if (xp > 0) awardXp(xp, `Rally: ${rallies} hits`);
      }
    }

    function step() {
      if (!gameOver) {
        ballX += ballVX;
        ballY += ballVY;
        if (ballX <= 6 || ballX >= W - 6) ballVX *= -1;
        if (ballY <= 6) ballVY *= -1;
        const paddleY = H - 30;
        if (ballY >= paddleY - 8 && ballY <= paddleY + paddleH && ballX >= paddleX && ballX <= paddleX + paddleW && ballVY > 0) {
          rallies += 1;
          scoreEl.textContent = `Rallies: ${rallies}`;
          const hitPos = (ballX - (paddleX + paddleW / 2)) / (paddleW / 2);
          ballVY = -Math.abs(ballVY) * 1.015;
          ballVX = Math.max(-6, Math.min(6, ballVX + hitPos * 2.2));
        } else if (ballY > H + 12) {
          endGame();
        }
      }

      ctx.clearRect(0, 0, W, H);
      ctx.fillStyle = "rgba(246,191,203,0.15)";
      ctx.fillRect(0, 0, W, H);
      // paddle
      ctx.fillStyle = "#f6bfcb";
      ctx.beginPath();
      ctx.roundRect(paddleX, H - 30, paddleW, paddleH, 5);
      ctx.fill();
      // ball
      ctx.fillStyle = "#3a2a1e";
      ctx.beginPath();
      ctx.arc(ballX, ballY, 6, 0, Math.PI * 2);
      ctx.fill();

      if (gameOver) {
        ctx.fillStyle = "rgba(0,0,0,0.55)";
        ctx.fillRect(0, 0, W, H);
        ctx.fillStyle = "#fff";
        ctx.font = "bold 18px sans-serif";
        ctx.textAlign = "center";
        ctx.fillText("Rally over!", W / 2, H / 2 - 16);
        ctx.font = "14px sans-serif";
        ctx.fillText(`${rallies} hits`, W / 2, H / 2 + 8);
        ctx.font = "12px sans-serif";
        ctx.fillText("Click to play again", W / 2, H / 2 + 30);
      }

      rafId = requestAnimationFrame(step);
    }

    function onClick() {
      if (gameOver) reset();
    }
    canvas.addEventListener("click", onClick);

    rafId = requestAnimationFrame(step);

    return function cleanup() {
      if (rafId) cancelAnimationFrame(rafId);
      canvas.removeEventListener("mousemove", onMove);
      canvas.removeEventListener("click", onClick);
    };
  }

  // ---------- Game 2: Catch ----------

  function startCatch(body) {
    const W = 360, H = 460;
    const canvas = el("canvas", { width: String(W), height: String(H), style: { borderRadius: "10px", background: "var(--panel)", boxShadow: "0 2px 8px var(--shadow)" } });
    const scoreEl = el("div", { style: { marginTop: "8px", fontWeight: "700", fontSize: "13px" }, text: "Score: 0  |  Lives: 3" });
    const hint = el("div", { style: { color: "var(--sub)", fontSize: "11px", marginTop: "2px" }, text: "Catch 🥟, dodge 💣." });
    body.appendChild(canvas);
    body.appendChild(scoreEl);
    body.appendChild(hint);
    const ctx = canvas.getContext("2d");

    const basketW = 56;
    let basketX = W / 2 - basketW / 2;
    let items = [];
    let score = 0, lives = 3, gameOver = false, xpGiven = false;
    let spawnEvery = 70;
    let tick = 0;
    let rafId = null;

    function onMove(e) {
      const rect = canvas.getBoundingClientRect();
      const x = (e.clientX - rect.left) * (W / rect.width);
      basketX = Math.max(0, Math.min(W - basketW, x - basketW / 2));
    }
    canvas.addEventListener("mousemove", onMove);

    function reset() {
      items = []; score = 0; lives = 3; gameOver = false; xpGiven = false; spawnEvery = 70; tick = 0;
      scoreEl.textContent = "Score: 0  |  Lives: 3";
    }

    function endGame() {
      gameOver = true;
      if (!xpGiven) {
        xpGiven = true;
        const xp = Math.min(60, score * 3);
        if (xp > 0) awardXp(xp, `Catch: ${score} caught`);
      }
    }

    function step() {
      if (!gameOver) {
        tick += 1;
        if (tick % spawnEvery === 0) {
          const bad = Math.random() < 0.22;
          items.push({ x: 16 + Math.random() * (W - 32), y: -10, v: 2 + Math.random() * 1.6, bad, r: 10 });
          if (spawnEvery > 34) spawnEvery -= 1;
        }
        for (const it of items) it.y += it.v;
        const basketY = H - 26;
        const remaining = [];
        for (const it of items) {
          if (it.y >= basketY - 10 && it.y <= basketY + 14 && it.x >= basketX - 4 && it.x <= basketX + basketW + 4) {
            if (it.bad) { lives -= 1; } else { score += 1; }
            scoreEl.textContent = `Score: ${score}  |  Lives: ${Math.max(0, lives)}`;
            if (lives <= 0) endGame();
            continue;
          }
          if (it.y > H + 12) {
            if (!it.bad) { lives -= 1; scoreEl.textContent = `Score: ${score}  |  Lives: ${Math.max(0, lives)}`; if (lives <= 0) endGame(); }
            continue;
          }
          remaining.push(it);
        }
        items = remaining;
      }

      ctx.clearRect(0, 0, W, H);
      ctx.fillStyle = "rgba(243,217,168,0.15)";
      ctx.fillRect(0, 0, W, H);
      ctx.font = "18px sans-serif";
      ctx.textAlign = "center";
      for (const it of items) ctx.fillText(it.bad ? "💣" : "🥟", it.x, it.y);
      ctx.fillStyle = "#f3d9a8";
      ctx.beginPath();
      ctx.roundRect(basketX, H - 26, basketW, 14, 6);
      ctx.fill();

      if (gameOver) {
        ctx.fillStyle = "rgba(0,0,0,0.55)";
        ctx.fillRect(0, 0, W, H);
        ctx.fillStyle = "#fff";
        ctx.font = "bold 18px sans-serif";
        ctx.fillText("Out of lives!", W / 2, H / 2 - 16);
        ctx.font = "14px sans-serif";
        ctx.fillText(`Caught ${score}`, W / 2, H / 2 + 8);
        ctx.font = "12px sans-serif";
        ctx.fillText("Click to play again", W / 2, H / 2 + 30);
      }

      rafId = requestAnimationFrame(step);
    }

    function onClick() {
      if (gameOver) reset();
    }
    canvas.addEventListener("click", onClick);
    rafId = requestAnimationFrame(step);

    return function cleanup() {
      if (rafId) cancelAnimationFrame(rafId);
      canvas.removeEventListener("mousemove", onMove);
      canvas.removeEventListener("click", onClick);
    };
  }

  // ---------- Game 3: Tap Frenzy ----------

  function startTapFrenzy(body) {
    const DURATION_MS = 10000;
    const wrap = el("div", { style: { display: "flex", flexDirection: "column", alignItems: "center", gap: "14px", marginTop: "10px" } });
    const timerEl = el("div", { style: { fontWeight: "700", fontSize: "14px" }, text: "10.0s" });
    const scoreEl = el("div", { style: { fontWeight: "800", fontSize: "22px" }, text: "0 taps" });
    const tapBtn = el("button", {
      text: "🐾", style: {
        fontSize: "64px", width: "160px", height: "160px", borderRadius: "50%", border: "none",
        background: "var(--accent)", cursor: "pointer", boxShadow: "0 4px 10px var(--shadow)",
      },
    });
    const startBtn = bigButton("Start (10s)", startRound);
    wrap.appendChild(timerEl);
    wrap.appendChild(scoreEl);
    wrap.appendChild(tapBtn);
    wrap.appendChild(startBtn);
    body.appendChild(wrap);

    let taps = 0;
    let running = false;
    let endAt = 0;
    let rafId = null;
    let xpGiven = false;

    function onTap() {
      if (!running) return;
      taps += 1;
      scoreEl.textContent = `${taps} taps`;
    }
    tapBtn.addEventListener("click", onTap);

    function startRound() {
      taps = 0; running = true; xpGiven = false;
      endAt = Date.now() + DURATION_MS;
      scoreEl.textContent = "0 taps";
      startBtn.style.display = "none";
      tick();
    }

    function endRound() {
      running = false;
      startBtn.textContent = "Play again";
      startBtn.style.display = "inline-block";
      if (!xpGiven) {
        xpGiven = true;
        const xp = Math.min(50, Math.floor(taps / 2));
        if (xp > 0) awardXp(xp, `Tap Frenzy: ${taps} taps`);
      }
    }

    function tick() {
      const remaining = Math.max(0, endAt - Date.now());
      timerEl.textContent = `${(remaining / 1000).toFixed(1)}s`;
      if (remaining <= 0) {
        timerEl.textContent = "0.0s";
        endRound();
        return;
      }
      rafId = requestAnimationFrame(tick);
    }

    return function cleanup() {
      running = false;
      if (rafId) cancelAnimationFrame(rafId);
      tapBtn.removeEventListener("click", onTap);
    };
  }

  // ---------- Game 4: Memory Match ----------

  function startMemoryMatch(body) {
    const ICONS = ["🐾", "🎞️", "🍬", "⭐", "🎸", "🌈"];
    const cardsData = ICONS.concat(ICONS)
      .map((icon, i) => ({ icon, id: i }))
      .sort(() => Math.random() - 0.5);

    const statusEl = el("div", { style: { fontWeight: "700", fontSize: "13px", marginBottom: "10px" }, text: "Flips: 0" });
    const grid = el("div", {
      style: {
        display: "grid", gridTemplateColumns: "repeat(4, 64px)", gap: "8px", justifyContent: "center",
      },
    });
    body.appendChild(statusEl);
    body.appendChild(grid);

    let flips = 0;
    let firstCard = null;
    let lock = false;
    let matched = 0;
    let xpGiven = false;
    const timers = [];

    function cardEl(data) {
      const card = el("button", {
        style: {
          width: "64px", height: "64px", borderRadius: "10px", border: "none", cursor: "pointer",
          background: "var(--panel)", boxShadow: "0 2px 6px var(--shadow)", fontSize: "26px",
        },
        text: "",
      });
      card.dataset.matched = "0";
      card.dataset.open = "0";
      card.addEventListener("click", () => onFlip(card, data));
      return card;
    }

    const cardEls = cardsData.map(cardEl);
    for (const c of cardEls) grid.appendChild(c);

    function setOpen(card, data, open) {
      card.dataset.open = open ? "1" : "0";
      card.textContent = open ? data.icon : "";
      card.style.background = open ? "var(--accent2)" : "var(--panel)";
    }

    function onFlip(card, data) {
      if (lock || card.dataset.matched === "1" || card.dataset.open === "1") return;
      setOpen(card, data, true);

      if (!firstCard) {
        firstCard = { card, data };
        return;
      }

      flips += 1;
      statusEl.textContent = `Flips: ${flips}`;
      lock = true;
      const second = { card, data };
      if (firstCard.data.icon === second.data.icon && firstCard.card !== second.card) {
        firstCard.card.dataset.matched = "1";
        second.card.dataset.matched = "1";
        firstCard.card.style.background = "var(--good)";
        second.card.style.background = "var(--good)";
        matched += 2;
        firstCard = null;
        lock = false;
        if (matched === cardsData.length) finishGame();
      } else {
        const t = setTimeout(() => {
          setOpen(firstCard.card, firstCard.data, false);
          setOpen(second.card, second.data, false);
          firstCard = null;
          lock = false;
        }, 650);
        timers.push(t);
      }
    }

    function finishGame() {
      if (xpGiven) return;
      xpGiven = true;
      // Fewer flips = more XP; a perfect run is 6 flips (one per pair).
      const perfect = ICONS.length;
      const extra = Math.max(0, flips - perfect);
      const xp = Math.max(15, Math.min(50, 50 - extra * 3));
      awardXp(xp, `Memory Match: ${flips} flips`);
      statusEl.textContent = `Solved in ${flips} flips! +${xp} XP`;
    }

    return function cleanup() {
      for (const t of timers) clearTimeout(t);
    };
  }

  renderMenu();
})();
