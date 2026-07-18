// 腳本狀態機：Attack + Refresh + Buff 排程
// 停止時：立即取消所有 Timer / Wait / 釋放所有按下的鍵
const keySender = require("./keySender.cjs");

const HOLD_MS = 40; // 所有模擬按鍵固定按壓 40ms

let running = false;
let config = null;
let refreshLoopToken = 0;             // 用於中斷 refresh loop
let buffTimers = [];                  // setTimeout ids
let cancellableWaits = [];            // { reject } 讓 stop 立即中斷
let actionQueue = Promise.resolve();  // 序列化所有按鍵動作 (Buff 排隊)
let heldKeys = [];                    // 目前被按下的鍵 (用於 stop 時全部 KeyUp)

// 可中斷 sleep
function sleep(ms) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => {
      cancellableWaits = cancellableWaits.filter((w) => w.t !== t);
      resolve();
    }, ms);
    cancellableWaits.push({ t, reject });
  });
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

// 其它按鍵流程：Attack Up → delay → key tap → delay → Attack Down
async function executeWithAttackPause(keys) {
  if (!running) return;
  const attack = config.attackKey?.keys;
  const delay = config.attackResumeDelay ?? 30;
  const useAttack = config.attackerMode && attack?.length;

  if (useAttack) await markRelease(attack);
  await sleep(delay);
  if (!running) return;

  await keySender.pressKey(keys);
  await sleep(HOLD_MS);
  await keySender.releaseKey(keys);

  await sleep(delay);
  if (!running) return;
  if (useAttack) await markPress(attack);
}

// Refresh 主 loop
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
      // Refresh 具最高優先，透過 enqueue 保證與 Buff 不衝突
      await enqueue(() => executeWithAttackPause(step.keys));
      if (!running || token !== refreshLoopToken) return;
      try { await sleep(step.waitMs); } catch { return; }
    }
  }
}

// 啟動 Buff 排程
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
    // 打手模式：先按下 Attack
    if (config.attackerMode && config.attackKey?.keys?.length) {
      await markPress(config.attackKey.keys);
    }

    // 刷新模式
    if (config.refreshMode) {
      refreshLoop(refreshLoopToken);
    }

    // Buff
    scheduleBuffs();

    return { ok: true };
  } catch (e) {
    console.error(e);
    await stop();
    return { ok: false, error: e.message };
  }
}

// 停止腳本：立即取消所有 Timer / Wait，釋放所有按下的鍵
async function stop() {
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
