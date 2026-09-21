const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("api", {
  // App / 視窗
  getAppInfo: () => ipcRenderer.invoke("app:info"),
  minimize: () => ipcRenderer.invoke("window:minimize"),
  toggleMaximize: () => ipcRenderer.invoke("window:toggleMaximize"),
  close: () => ipcRenderer.invoke("window:close"),
  isMaximized: () => ipcRenderer.invoke("window:isMaximized"),
  onMaximizeChanged: (cb) => {
    const listener = (_e, v) => cb(v);
    ipcRenderer.on("window:maximized", listener);
    return () => ipcRenderer.removeListener("window:maximized", listener);
  },

  // 設定
  getAll: () => ipcRenderer.invoke("store:getAll"),
  set: (key, value) => ipcRenderer.invoke("store:set", key, value),
  reset: () => ipcRenderer.invoke("store:reset"),

  // 視窗
  setOpacity: (v) => ipcRenderer.invoke("window:setOpacity", v),
  setAlwaysOnTop: (v) => ipcRenderer.invoke("window:setAlwaysOnTop", v),

  // 快捷鍵錄製
  startCapture: () => ipcRenderer.invoke("hotkey:startCapture"),
  cancelCapture: () => ipcRenderer.invoke("hotkey:cancelCapture"),
  onCaptured: (cb) => {
    const listener = (_e, data) => cb(data);
    ipcRenderer.on("hotkey:captured", listener);
    return () => ipcRenderer.removeListener("hotkey:captured", listener);
  },

  // 綁定觸發快捷鍵
  bindToggle: (combo, mode) => ipcRenderer.invoke("hotkey:bindToggle", combo, mode),
  onToggleRequested: (cb) => {
    const listener = () => cb();
    ipcRenderer.on("script:toggleRequested", listener);
    return () => ipcRenderer.removeListener("script:toggleRequested", listener);
  },

  bindHide: (combo) => ipcRenderer.invoke("hotkey:bindHide", combo),

  // 腳本
  startScript: (config) => ipcRenderer.invoke("script:start", config),
  stopScript: () => ipcRenderer.invoke("script:stop"),

  // 更新
  checkUpdate: () => ipcRenderer.invoke("updater:check"),
  openExternal: (url) => ipcRenderer.invoke("shell:openExternal", url),

  // 擷取滑鼠座標 (雙人刷新模式綁定視窗位置)
  captureMousePosition: (delayMs) => ipcRenderer.invoke("mouse:capturePosition", delayMs),
});
