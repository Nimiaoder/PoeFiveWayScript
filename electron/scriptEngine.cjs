// 腳本狀態機：自動攻擊 + 自動刷新 (單人 / 雙人) + 自動 Buff 排程
// 停止時：立即取消所有 Timer / Wait / 釋放所有按下的鍵
const keySender = require("./keySender.cjs");
const hotkey = require("./hotkey.cjs");
let nutMouse = null;
let nutButton = null;
try {
  const nut = require("@nut-tree-fork/nut-js");
  nutMouse = nut.mouse;
  nutButton = nut.Button;
} catch {}
// 雙人模式用：移動到「絕對螢幕座標」(支援雙螢幕 / 單螢幕雙視窗)
async function moveMouseAbsolute(point) {
  if (!nutMouse || !point) return;
  try {
    await nutMouse.setPosition({ x: Math.round(point.x), y: Math.round(point.y) });
  } catch (e) {
    console.warn("[dual] 移動鼠標失敗：", e.message);
  }
}

// 雙人模式用：點右鍵以「聚焦(focus)」該遊戲視窗
// 之所以用右鍵而非左鍵：左鍵在 POE 會讓角色移動，右鍵通常綁定技能/無位移
async function rightClickFocus() {
  if (!nutMouse || !nutButton) return;
  try {
    await nutMouse.click(nutButton.RIGHT);
  } catch (e) {
    console.warn("[dual] 右鍵點擊失敗：", e.message);
  }
}

const HOLD_MS = 40; // 所有模擬按鍵固定按壓 40ms

let running = false;
let config = null;
let refreshLoopToken = 0;             // 用於中斷 refresh loop
let buffTimers = [];                  // setTimeout ids
let cancellableWaits = [];            // { t, ms, restart } 讓 stop 立即中斷 / 時空裂隙鍵重算
let actionQueue = Promise.resolve();  // 序列化所有按鍵動作 (Buff 排隊)
let heldKeys = [];                    // 目前被按下的鍵 (用於 stop 時全部 KeyUp)
let riftListenerBound = false;        // 是否已註冊時空裂隙鍵監聽

// 可中斷 sleep
//   resettable = true 時，此等待可被「時空裂隙按鍵」重新計時 (歸零重算)
function sleep(ms, resettable = false) {
  return new Promise((resolve) => {
    const entry = { ms, resettable, t: null };
    const done = () => {
      cancellableWaits = cancellableWaits.filter((w) => w !== entry);
      resolve();
    };
    entry.restart = () => {
      clearTimeout(entry.t);
      entry.t = setTimeout(done, entry.ms);
    };
    entry.t = setTimeout(done, ms);
    cancellableWaits.push(entry);
  });
}

// 時空裂隙按鍵：使用者手動按下時，把「進圈/出圈之間的等待」歸零重算
// 用途：獵首把玩家傳送走 手動歸位後，原本的等待秒數會失準，按一下即可重新計時
function resetPendingWaits() {
  const targets = cancellableWaits.filter((w) => w.resettable);
  if (!targets.length) return;
  targets.forEach((w) => w.restart());
  console.log(`[rift] 時空裂隙鍵觸發，重算等待計時 (${targets.length} 筆)`);
}

// 註冊全域鍵盤監聽，偵測時空裂隙按鍵
// 說明：uiohook 逐鍵回報，這裡以組合鍵的「最後一個非修飾鍵」作為判斷依據即可
function bindRiftListener() {
  if (!config?.riftEnabled || !config?.riftKey?.keys?.length) return;
  const keys = config.riftKey.keys;
  const mainKey = keys[keys.length - 1];
  hotkey.setScriptKeyListener((type, name) => {
    if (!running) return;
    if (type !== "down") return;
    if (name !== mainKey) return;
    resetPendingWaits();
  });
  riftListenerBound = true;
}
function unbindRiftListener() {
  if (!riftListenerBound) return;
  hotkey.setScriptKeyListener(null);
  riftListenerBound = false;
}

// 標記按下 (追蹤)
async function markPress(keys) {
  heldKeys.push(keys);
  await keySender.pressKey(keys);
}
async function markRelease(keys) {
  heldKeys = heldKeys.filter((k) => k !== keys);
  await keySender.releaseKey(keys);
}

// 序列化執行 (Buff / Refresh 動作排隊，不並發送鍵)
function enqueue(fn) {
  const p = actionQueue.then(async () => {
    if (!running) return;
    try { await fn(); } catch (e) { if (running) console.error(e); }
  });
  actionQueue = p.catch(() => {});
  return p;
}

// 單純短按一個鍵
async function tapKey(keys) {
  if (!running || !keys?.length) return;
  await keySender.pressKey(keys);
  await sleep(HOLD_MS);
  await keySender.releaseKey(keys);
}

// 攻擊鍵：按住 / 放開 (只有啟用自動攻擊且有設定攻擊鍵時才作用)
function attackUsable() {
  return !!(config.attackerMode && config.attackKey?.keys?.length);
}
async function holdAttack() {
  if (!attackUsable()) return;
  if (heldKeys.includes(config.attackKey.keys)) return;
  await markPress(config.attackKey.keys);
}
async function pauseAttack() {
  if (!attackUsable()) return;
  await markRelease(config.attackKey.keys);
}

// 其它按鍵流程：Attack Up → delay → key tap → delay → Attack Down
async function executeWithAttackPause(keys) {
  if (!running) return;
  const delay = config.attackResumeDelay ?? 30;
  const useAttack = attackUsable();

  if (useAttack) await pauseAttack();
  await sleep(delay);
  if (!running) return;

  await keySender.pressKey(keys);
  await sleep(HOLD_MS);
  await keySender.releaseKey(keys);

  await sleep(delay);
  if (!running) return;
  if (useAttack) await holdAttack();
}

// ── 單人 Refresh 主 loop ──────────────────────────────────────────────
async function refreshLoop(token) {
  const seq = config.refreshDirection === "enter-first"
    ? [
        { keys: config.enterKey.keys, waitMs: config.enterWaitMs },
        { keys: config.exitKey.keys, waitMs: config.exitWaitMs },
      ]
    : [
        { keys: config.exitKey.keys, waitMs: config.exitWaitMs },
        { keys: config.enterKey.keys, waitMs: config.enterWaitMs },
      ];

  while (running && token === refreshLoopToken) {
    for (const step of seq) {
      if (!running || token !== refreshLoopToken) return;
      // 進圈/出圈前：如啟用自動控制鼠標位置，先移動鼠標
      if (config.mouseControl && config.mousePos) {
        await moveMouseAbsolute(config.mousePos);
      }
      // Refresh 具最高優先，透過 enqueue 保證與 Buff 不衝突
      await enqueue(() => executeWithAttackPause(step.keys));
      if (!running || token !== refreshLoopToken) return;
      // 此等待可被時空裂隙鍵重算
      await sleep(step.waitMs, true);
    }
  }
}

// ── 雙人 Refresh 主 loop ──────────────────────────────────────────────
// 流程 (以「先進圈」為例)：
//   打手視窗(移鼠標→右鍵 focus)→進圈 → 光環師視窗→進圈 → 回打手視窗→恢復攻擊
//   → 等待 n 毫秒 (可被時空裂隙鍵重算)
//   打手視窗→出圈 → 光環師視窗→出圈 → 回打手視窗→恢復攻擊 → 等待 n 毫秒 → loop
// 重點：只有鼠標停在打手視窗時才送出攻擊鍵，避免把攻擊打到光環師視窗
async function focusWindow(point) {
  if (!running) return;
  await moveMouseAbsolute(point);
  if (config.dualRightClickFocus !== false) {
    await rightClickFocus();
  }
  // 視窗切換 / focus 需要一點緩衝，否則按鍵會掉進舊視窗
  await sleep(Math.max(0, Number(config.dualFocusDelayMs) || 0));
}

async function dualPhase(keys, waitMs) {
  const attackerPos = config.dualAttackerPos;
  const auraPos = config.dualAuraPos;

  // 1) 離開打手視窗前先放開攻擊鍵
  await pauseAttack();

  // 2) 打手視窗：focus → 送出按鍵
  await focusWindow(attackerPos);
  if (!running) return;
  await tapKey(keys);

  // 3) 光環師視窗：focus → 送出同一個按鍵
  await focusWindow(auraPos);
  if (!running) return;
  await tapKey(keys);

  // 4) 回到打手視窗：focus → 盡可能持續攻擊
  await focusWindow(attackerPos);
  if (!running) return;
  await holdAttack();

  // 5) 等待 (可被時空裂隙鍵重算)
  await sleep(waitMs, true);
}

async function dualRefreshLoop(token) {
  const enterStep = { keys: config.enterKey.keys, waitMs: config.enterWaitMs };
  const exitStep = { keys: config.exitKey.keys, waitMs: config.exitWaitMs };
  const seq = config.refreshDirection === "exit-first"
    ? [exitStep, enterStep]
    : [enterStep, exitStep];

  while (running && token === refreshLoopToken) {
    for (const step of seq) {
      if (!running || token !== refreshLoopToken) return;
      // 雙人流程整段排隊，避免 Buff 插在視窗切換中間造成按鍵打錯視窗
      await enqueue(() => dualPhase(step.keys, step.waitMs));
      if (!running || token !== refreshLoopToken) return;
    }
  }
}

// 是否使用雙人刷新模式 (需同時勾選打手 + 刷新，並完成兩個座標綁定)
function isDualMode(cfg) {
  return !!(
    cfg.dualRefreshMode &&
    cfg.attackerMode &&
    cfg.refreshMode &&
    cfg.dualAttackerPos &&
    cfg.dualAuraPos
  );
}

// 啟動 Buff 排程 (僅在自動 Buff 模式啟用時呼叫)
function scheduleBuffs() {
  const buffs = (config.buffs || []).filter((b) => b.enabled && b.key?.keys?.length);
  buffs.forEach((buff) => {
    const cast = async () => {
      if (!running) return;
      // Buff 使用 enqueue 排隊，不與 Refresh / 其它 Buff 同時送鍵
      await enqueue(() => executeWithAttackPause(buff.key.keys));
    };
    // 啟動立即施放
    if (buff.castOnStart) cast();
    const tick = () => {
      if (!running) return;
      cast();
      const id = setTimeout(tick, buff.intervalSec * 1000);
      buffTimers.push(id);
    };
    const id = setTimeout(tick, buff.intervalSec * 1000);
    buffTimers.push(id);
  });
}

// 啟動腳本
async function start(cfg, win) {
  if (running) return { ok: false, error: "已在執行" };
  config = cfg;
  running = true;
  refreshLoopToken++;
  buffTimers = [];
  cancellableWaits = [];
  heldKeys = [];
  actionQueue = Promise.resolve();

  try {
    // 時空裂隙按鍵監聽 (可開關)
    bindRiftListener();

    // 自動攻擊：先按下 Attack
    if (attackUsable()) {
      await holdAttack();
    }

    // 自動刷新
    if (config.refreshMode) {
      if (isDualMode(config)) dualRefreshLoop(refreshLoopToken);
      else refreshLoop(refreshLoopToken);
    }

    // 自動 Buff
    if (config.buffMode) {
      scheduleBuffs();
    }

    return { ok: true };
  } catch (e) {
    console.error(e);
    await stop();
    return { ok: false, error: e.message };
  }
}

// 停止腳本：立即取消所有 Timer / Wait，釋放所有按下的鍵
async function stop() {
  unbindRiftListener();
  if (!running) {
    // 即使未 running，也保險釋放
    for (const k of [...heldKeys]) {
      try { await keySender.releaseKey(k); } catch {}
    }
    heldKeys = [];
    return { ok: true };
  }
  running = false;
  refreshLoopToken++;

  // 清 Buff timers
  buffTimers.forEach((id) => clearTimeout(id));
  buffTimers = [];

  // 中斷所有等待
  cancellableWaits.forEach((w) => {
    clearTimeout(w.t);
  });
  cancellableWaits = [];

  // 釋放所有按下的鍵 (含 Attack)
  const toRelease = [...heldKeys];
  heldKeys = [];
  for (const keys of toRelease) {
    try { await keySender.releaseKey(keys); } catch {}
  }
  // 保險：若 Attack 未在 heldKeys 中也再釋放一次
  if (config?.attackKey?.keys?.length) {
    try { await keySender.releaseKey(config.attackKey.keys); } catch {}
  }

  actionQueue = Promise.resolve();
  return { ok: true };
}

module.exports = { start, stop, isRunning: () => running };
