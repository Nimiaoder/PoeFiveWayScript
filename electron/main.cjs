// 主行程：建立視窗、載入 UI、註冊 IPC
const { app, BrowserWindow, ipcMain, shell, screen } = require("electron");
const path = require("path");
const pkg = require("../package.json");
const store = require("./store.cjs");
const hotkey = require("./hotkey.cjs");
const scriptEngine = require("./scriptEngine.cjs");
const updater = require("./updater.cjs");

let mainWindow = null;

function applyAlwaysOnTop(win, value) {
  if (!win) return;
  // 修正: 遇到 fullscreen / 無邊框全螢幕遊戲時，預設 'floating' level 會與遊戲搶焦點，
  // 造成拖曳/alt+tab 卡死。改用 'screen-saver' 這個更高的 level，並在所有虛擬桌面顯示。
  if (value) {
    win.setAlwaysOnTop(true, "screen-saver", 1);
    try { win.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true }); } catch {}
  } else {
    win.setAlwaysOnTop(false);
    try { win.setVisibleOnAllWorkspaces(false); } catch {}
  }
}
// 建立主視窗
function createWindow() {
  const bounds = store.get("windowBounds") || { width: 520, height: 780 };
  const alwaysOnTop = store.get("alwaysOnTop") ?? false;
  const opacity = store.get("opacity") ?? 1;

  mainWindow = new BrowserWindow({
    width: bounds.width,
    height: bounds.height,
    x: bounds.x,
    y: bounds.y,
    minWidth: 480,
    minHeight: 640,
    frame: false,                 // 自訂 titlebar
    backgroundColor: "#1a1a1a",
    title: `${pkg.name} v${pkg.version}`,
    opacity,
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  applyAlwaysOnTop(mainWindow, alwaysOnTop);
  // 開發載入 vite dev server，否則載入打包後 index.html
  if (process.env.NODE_ENV === "development") {
    mainWindow.loadURL("http://localhost:5173");
  } else {
    mainWindow.loadFile(path.join(__dirname, "..", "dist", "index.html"));
  }

  // 視窗大小/位置自動儲存
  const saveBounds = () => {
    if (!mainWindow || mainWindow.isMinimized() || mainWindow.isMaximized()) return;
    store.set("windowBounds", mainWindow.getBounds());
  };
  mainWindow.on("resize", saveBounds);
  mainWindow.on("move", saveBounds);

  const emitMax = () => mainWindow?.webContents.send("window:maximized", mainWindow.isMaximized());
  mainWindow.on("maximize", emitMax);
  mainWindow.on("unmaximize", emitMax);

  mainWindow.on("closed", () => {
    mainWindow = null;
  });
}

// 初始化 IPC
function registerIpc() {
  ipcMain.handle("app:info", () => ({ name: pkg.name, version: pkg.version }));

  // 視窗控制 (自訂 titlebar 使用)
  ipcMain.handle("window:minimize", () => mainWindow?.minimize());
  ipcMain.handle("window:toggleMaximize", () => {
    if (!mainWindow) return;
    if (mainWindow.isMaximized()) mainWindow.unmaximize();
    else mainWindow.maximize();
  });
  ipcMain.handle("window:close", () => mainWindow?.close());
  ipcMain.handle("window:isMaximized", () => !!mainWindow?.isMaximized());

  // 讀寫設定
  ipcMain.handle("store:getAll", () => store.getAll());
  ipcMain.handle("store:set", (_e, key, value) => {
    store.set(key, value);
    return true;
  });
  ipcMain.handle("store:reset", () => store.reset());

  // 視窗控制
  ipcMain.handle("window:setOpacity", (_e, value) => {
    if (mainWindow) mainWindow.setOpacity(value);
    store.set("opacity", value);
  });
  ipcMain.handle("window:setAlwaysOnTop", (_e, value) => {
    applyAlwaysOnTop(mainWindow, value);
    store.set("alwaysOnTop", value);
  });

  // 快捷鍵錄製
  ipcMain.handle("hotkey:startCapture", () => hotkey.startCapture(mainWindow));
  ipcMain.handle("hotkey:cancelCapture", () => hotkey.cancelCapture());

  // 註冊觸發用的快捷鍵 (啟動/關閉腳本)
  ipcMain.handle("hotkey:bindToggle", (_e, combo, mode) => {
    hotkey.bindToggle(combo, mode, () => {
      if (mainWindow) mainWindow.webContents.send("script:toggleRequested");
    });
  });
  // 註冊隱藏畫面快捷鍵
  ipcMain.handle("hotkey:bindHide", (_e, combo) => {
    hotkey.bindHide(combo, () => {
      if (!mainWindow) return;
      if (mainWindow.isVisible()) mainWindow.hide();
      else {
        mainWindow.show();
        mainWindow.focus();
      }
    });
  });

  // 腳本控制
  ipcMain.handle("script:start", (_e, config) => scriptEngine.start(config, mainWindow));
  ipcMain.handle("script:stop", () => scriptEngine.stop());

  // 擷取滑鼠絕對座標：倒數 delayMs 後回報，讓使用者有時間把滑鼠移到遊戲視窗
  ipcMain.handle("mouse:capturePosition", async (_e, delayMs) => {
    const wait = Math.max(0, Number(delayMs) || 0);
    await new Promise((r) => setTimeout(r, wait));
    const p = screen.getCursorScreenPoint();
    return { x: p.x, y: p.y };
  });

  // 檢查更新
  ipcMain.handle("updater:check", () => updater.checkForUpdates(app.getVersion()));
  ipcMain.handle("shell:openExternal", (_e, url) => shell.openExternal(url));
}

app.whenReady().then(() => {
  registerIpc();
  createWindow();

  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

// 應用關閉時：停止腳本、卸載鉤子，避免按鍵殘留
app.on("before-quit", () => {
  scriptEngine.stop();
  hotkey.shutdown();
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
