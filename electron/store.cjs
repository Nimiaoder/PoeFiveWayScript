// 設定持久化：改為自製 JSON store，全部寫在 <專案或exe目錄>/data 之下
//   data/config/settings.json         目前設定
//   data/backup/settings-<ts>.json    每次寫入前備份（保留最新 10 份）
const fs = require("fs");
const path = require("path");
const { app } = require("electron");

// 預設值
const defaults = {
  windowBounds: { width: 520, height: 780 },
  opacity: 1,
  alwaysOnTop: false,
  toggleHotkey: null,           // { keys: [...], display: 'Shift+E' }
  hotkeyMode: "listen",         // 'listen' | 'intercept'
  attackerMode: false,          // 自動攻擊
  refreshMode: false,           // 自動刷新
  buffMode: false,              // 自動 Buff
  refreshDirection: null,       // 'enter-first' | 'exit-first' | null
  attackKey: null,              // { keys: [...], display }
  enterKey: null,
  exitKey: null,
  enterWaitMs: 2000,
  exitWaitMs: 1000,
  attackResumeDelay: 30,        // ms
  // 自動控制鼠標位置 (刷新用)
  mouseControl: false,
  mousePosition: "2/4",         // '1/4' | '2/4' | '3/4' 螢幕頂端幾分之幾
  buffs: [],                    // [{ id, name, key, intervalSec, castOnStart, enabled }]
};

// 決定根目錄：
//   打包後：與 exe 同層（可攜式，資料跟著程式走）
//   開發時：process.cwd()（就是專案根目錄）
function baseDir() {
  try {
    if (app && app.isPackaged) return path.dirname(app.getPath("exe"));
  } catch {}
  return process.cwd();
}

const ROOT = baseDir();
const CONFIG_DIR = path.join(ROOT, "data", "config");
const BACKUP_DIR = path.join(ROOT, "data", "backup");
const CONFIG_FILE = path.join(CONFIG_DIR, "settings.json");
const MAX_BACKUPS = 10;

function ensureDirs() {
  fs.mkdirSync(CONFIG_DIR, { recursive: true });
  fs.mkdirSync(BACKUP_DIR, { recursive: true });
}

let cache = null;

function load() {
  ensureDirs();
  if (cache) return cache;
  try {
    if (fs.existsSync(CONFIG_FILE)) {
      const raw = fs.readFileSync(CONFIG_FILE, "utf-8");
      const data = JSON.parse(raw);
      cache = { ...defaults, ...data };
    } else {
      cache = { ...defaults };
      persist(cache, /*skipBackup*/ true);
    }
  } catch (e) {
    console.error("[store] 讀取設定失敗，改用預設值：", e.message);
    cache = { ...defaults };
  }
  return cache;
}

function backupCurrent() {
  try {
    if (!fs.existsSync(CONFIG_FILE)) return;
    const ts = new Date().toISOString().replace(/[:.]/g, "-");
    const dest = path.join(BACKUP_DIR, `settings-${ts}.json`);
    fs.copyFileSync(CONFIG_FILE, dest);
    // 清理舊備份，只保留最新 MAX_BACKUPS 份
    const files = fs
      .readdirSync(BACKUP_DIR)
      .filter((f) => f.startsWith("settings-") && f.endsWith(".json"))
      .map((f) => ({ f, t: fs.statSync(path.join(BACKUP_DIR, f)).mtimeMs }))
      .sort((a, b) => b.t - a.t);
    files.slice(MAX_BACKUPS).forEach(({ f }) => {
      try { fs.unlinkSync(path.join(BACKUP_DIR, f)); } catch {}
    });
  } catch (e) {
    console.warn("[store] 備份失敗：", e.message);
  }
}

// 防抖寫入，避免 resize/move 密集事件卡 IO
let writeTimer = null;
let pendingSkipBackup = false;
function persist(data, skipBackup = false) {
  ensureDirs();
  pendingSkipBackup = pendingSkipBackup || skipBackup;
  if (writeTimer) return;
  writeTimer = setTimeout(() => {
    writeTimer = null;
    const skip = pendingSkipBackup;
    pendingSkipBackup = false;
    try {
      if (!skip) backupCurrent();
      fs.writeFileSync(CONFIG_FILE, JSON.stringify(cache, null, 2), "utf-8");
    } catch (e) {
      console.error("[store] 寫入失敗：", e.message);
    }
  }, 150);
}

module.exports = {
  get: (k) => load()[k],
  set: (k, v) => {
    load();
    cache[k] = v;
    // 視窗 bounds 高頻變動 → 不做備份，只寫檔
    persist(cache, k === "windowBounds");
  },
  getAll: () => load(),
  reset: () => {
    // 不重設視窗大小/位置
    load();
    const bounds = cache.windowBounds;
    cache = { ...defaults };
    if (bounds) cache.windowBounds = bounds;
    persist(cache, false);
    return cache;
  },
  paths: () => ({ root: ROOT, config: CONFIG_FILE, backup: BACKUP_DIR }),
};
