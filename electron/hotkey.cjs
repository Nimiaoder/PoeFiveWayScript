// 全域快捷鍵：使用 uiohook-napi 監聽/攔截
// 錄製：同時間只允許一個 Session；開始新錄製時立即取消舊的
let uIOhook, UiohookKey;
try {
  const mod = require("uiohook-napi");
  uIOhook = mod.uIOhook;
  UiohookKey = mod.UiohookKey;
} catch (e) {
  console.error("uiohook-napi 載入失敗:", e.message);
}

let started = false;
let captureSession = null; // { win, pressed:Set, onDone }
let toggleBinding = null;  // { keys:Set, mode:'listen'|'intercept', cb }
let scriptKeyListener = null; // 供 scriptEngine 註冊全域監聽

// uiohook keycode -> 顯示名稱
const RAW_TO_NAME = {
  // 字母
  30: "A", 48: "B", 46: "C", 32: "D", 18: "E", 33: "F", 34: "G", 35: "H",
  23: "I", 36: "J", 37: "K", 38: "L", 50: "M", 49: "N", 24: "O", 25: "P",
  16: "Q", 19: "R", 31: "S", 20: "T", 22: "U", 47: "V", 17: "W", 45: "X",
  21: "Y", 44: "Z",
  // 數字
  2: "1", 3: "2", 4: "3", 5: "4", 6: "5", 7: "6", 8: "7", 9: "8", 10: "9", 11: "0",
  // 功能鍵
  59: "F1", 60: "F2", 61: "F3", 62: "F4", 63: "F5", 64: "F6",
  65: "F7", 66: "F8", 67: "F9", 68: "F10", 87: "F11", 88: "F12",
  // 特殊
  57: "Space", 15: "Tab", 28: "Enter", 14: "Backspace",
  1: "Esc", 41: "`", 12: "-", 13: "=", 26: "[", 27: "]", 43: "\\",
  39: ";", 40: "'", 51: ",", 52: ".", 53: "/",
  // 導覽 / 編輯 (uiohook 使用擴充碼 0xE0__ = 3584+xx)
  3655: "Home", 3663: "End", 3657: "PageUp", 3665: "PageDown",
  3666: "Insert", 3667: "Delete",
  57416: "Up", 57424: "Down", 57419: "Left", 57421: "Right",
  // 鎖定鍵
  58: "CapsLock", 69: "NumLock", 70: "ScrollLock",
  // 數字鍵盤
  71: "Num7", 72: "Num8", 73: "Num9", 74: "Num-",
  75: "Num4", 76: "Num5", 77: "Num6", 78: "Num+",
  79: "Num1", 80: "Num2", 81: "Num3",
  82: "Num0", 83: "Num.",
  55: "Num*", 3637: "Num/", 3612: "NumEnter",
  // 其他
  3639: "PrintScreen", 3677: "Menu",
};

const MODIFIERS = new Set(["Ctrl", "Shift", "Alt"]);

// 判斷是否為 modifier 的 keycode
function isModifier(keycode) {
  // LCtrl 29, RCtrl 3613, LShift 42, RShift 54, LAlt 56, RAlt 3640
  return [29, 3613, 42, 54, 56, 3640].includes(keycode);
}
function modifierName(keycode) {
  if (keycode === 29 || keycode === 3613) return "Ctrl";
  if (keycode === 42 || keycode === 54) return "Shift";
  if (keycode === 56 || keycode === 3640) return "Alt";
  return null;
}
function keyName(keycode) {
  const mod = modifierName(keycode);
  if (mod) return mod;
  return RAW_TO_NAME[keycode] || `Key(${keycode})`;
}

// 依序：Ctrl, Shift, Alt, 其它
function normalizeCombo(keys) {
  const order = ["Ctrl", "Shift", "Alt"];
  const mods = order.filter((m) => keys.includes(m));
  const others = keys.filter((k) => !MODIFIERS.has(k));
  return [...mods, ...others];
}

// 啟動 uiohook (只啟動一次)
function ensureStarted() {
  if (started || !uIOhook) return;
  uIOhook.on("keydown", onKeyDown);
  uIOhook.on("keyup", onKeyUp);
  uIOhook.start();
  started = true;
}

// 追蹤目前實體按著的 modifiers
const heldMods = new Set();

function onKeyDown(e) {
  const name = keyName(e.keycode);
  if (MODIFIERS.has(name)) heldMods.add(name);

  // 錄製 Session
  if (captureSession) {
    if (name === "Esc") {
      const cs = captureSession;
      captureSession = null;
      cs.win?.webContents.send("hotkey:captured", { cancelled: true });
      return;
    }
    captureSession.pressed.add(name);
    return;
  }

  // 觸發 toggle 快捷鍵
  if (toggleBinding) {
    const current = new Set(heldMods);
    if (!MODIFIERS.has(name)) current.add(name);
    if (setsEqual(current, toggleBinding.keys)) {
      toggleBinding.cb();
      if (toggleBinding.mode === "intercept") {
        // 攔截：阻止事件傳給遊戲
        try { e.preventDefault && e.preventDefault(); } catch {}
      }
    }
  }

  if (scriptKeyListener) scriptKeyListener("down", name);
}

function onKeyUp(e) {
  const name = keyName(e.keycode);
  if (MODIFIERS.has(name)) heldMods.delete(name);

  if (captureSession) {
    // 使用者放開所有鍵 → 完成錄製
    const cs = captureSession;
    const combo = normalizeCombo(Array.from(cs.pressed));
    // 驗證：不得單獨為 Ctrl/Shift/Alt
    const nonMods = combo.filter((k) => !MODIFIERS.has(k));
    if (combo.length === 0) return; // 尚未按任何鍵
    if (nonMods.length === 0) {
      captureSession = null;
      cs.win?.webContents.send("hotkey:captured", {
        error: "不可單獨使用 Ctrl / Shift / Alt",
      });
      return;
    }
    captureSession = null;
    cs.win?.webContents.send("hotkey:captured", {
      keys: combo,
      display: combo.join("+"),
    });
    return;
  }

  if (scriptKeyListener) scriptKeyListener("up", name);
}

function setsEqual(a, b) {
  if (a.size !== b.size) return false;
  for (const v of a) if (!b.has(v)) return false;
  return true;
}

module.exports = {
  // 開始錄製 (同一時間只有一個 session)
  startCapture(win) {
    ensureStarted();
    // 立即取消舊 session
    if (captureSession) {
      captureSession.win?.webContents.send("hotkey:captured", { cancelled: true });
    }
    captureSession = { win, pressed: new Set() };
  },
  cancelCapture() {
    if (captureSession) {
      captureSession.win?.webContents.send("hotkey:captured", { cancelled: true });
      captureSession = null;
    }
  },
  // 綁定 toggle 快捷鍵
  bindToggle(combo, mode, cb) {
    if (!combo || !combo.keys || combo.keys.length === 0) {
      toggleBinding = null;
      return;
    }
    ensureStarted();
    toggleBinding = { keys: new Set(combo.keys), mode, cb };
  },
  // 供 scriptEngine 註冊 (若需要)
  setScriptKeyListener(fn) {
    scriptKeyListener = fn;
    if (fn) ensureStarted();
  },
  shutdown() {
    if (started && uIOhook) {
      try { uIOhook.stop(); } catch {}
      started = false;
    }
  },
};
