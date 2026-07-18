// 按鍵模擬：使用 @nut-tree-fork/nut-js 進行 KeyDown / KeyUp
// 提供 pressKey / releaseKey / tap 三個 API，供 scriptEngine 使用
let keyboard, Key;
try {
  const nut = require("@nut-tree-fork/nut-js");
  keyboard = nut.keyboard;
  Key = nut.Key;
  keyboard.config.autoDelayMs = 0;
} catch (e) {
  console.error("nut-js 載入失敗:", e.message);
}

// UI 顯示名稱 → nut-js Key enum
const NAME_TO_KEY = {
  Ctrl: "LeftControl", Shift: "LeftShift", Alt: "LeftAlt",
  Space: "Space", Tab: "Tab", Enter: "Enter", Backspace: "Backspace",
  Esc: "Escape",
  F1: "F1", F2: "F2", F3: "F3", F4: "F4", F5: "F5", F6: "F6",
  F7: "F7", F8: "F8", F9: "F9", F10: "F10", F11: "F11", F12: "F12",
  "`": "Grave", "-": "Minus", "=": "Equal",
  "[": "LeftBracket", "]": "RightBracket", "\\": "Backslash",
  ";": "Semicolon", "'": "Quote", ",": "Comma", ".": "Period", "/": "Slash",
};

// 將顯示名稱轉為 nut-js Key 值
function toNutKey(name) {
  if (!Key) return null;
  if (NAME_TO_KEY[name]) return Key[NAME_TO_KEY[name]];
  if (/^[A-Z]$/.test(name)) return Key[name];
  if (/^[0-9]$/.test(name)) return Key["Num" + name];
  return null;
}

// 將組合鍵 keys 陣列轉為 nut-js Key 陣列
function toNutKeys(keys) {
  return keys.map(toNutKey).filter(Boolean);
}

async function pressKey(keys) {
  if (!keyboard || !keys?.length) return;
  const arr = toNutKeys(keys);
  if (arr.length) await keyboard.pressKey(...arr);
}
async function releaseKey(keys) {
  if (!keyboard || !keys?.length) return;
  const arr = toNutKeys(keys);
  if (arr.length) await keyboard.releaseKey(...arr);
}

// 短按：按下 40ms 再放開
async function tap(keys, holdMs = 40) {
  await pressKey(keys);
  await new Promise((r) => setTimeout(r, holdMs));
  await releaseKey(keys);
}

module.exports = { pressKey, releaseKey, tap };
