# POE 五軍腳本 (Five Army Script)

現代化 Windows 11 Fluent Design 風格的 Electron Desktop 應用，用於 Path of Exile 五軍任務自動化。

## 快速開始

```bash
npm install
npm run package:win
```

打包後的安裝檔位於 `release/` 資料夾。

若要開發模式執行：

```bash
npm run dev
```

## 專案架構

```
poe-five-army-script/
├── electron/                    # Electron 主行程 (Node.js 環境)
│   ├── main.cjs                 # 主行程入口：視窗建立、IPC 註冊
│   ├── preload.cjs              # 預載腳本：contextBridge 暴露安全 API
│   ├── store.cjs                # electron-store 設定持久化
│   ├── hotkey.cjs               # 全域快捷鍵錄製、監聽、攔截 (uiohook-napi)
│   ├── keySender.cjs            # 按鍵模擬 KeyDown/KeyUp (nut-js)
│   ├── scriptEngine.cjs         # 腳本狀態機：Attack / Refresh / Buff 排程
│   └── updater.cjs              # GitHub Release 版本檢查
│
├── src/                         # 前端 UI (React + TypeScript)
│   ├── main.tsx                 # React 入口
│   ├── App.tsx                  # 主畫面 (四大區塊 Tab)
│   ├── styles.css               # Fluent Design 深色主題
│   ├── ipc.ts                   # 封裝 window.api 呼叫
│   ├── types.ts                 # 型別定義
│   ├── hooks/
│   │   └── useSettings.ts       # 設定 hook (auto-save)
│   └── components/
│       ├── ScriptControl.tsx    # 區塊一：腳本控制、模式、快捷鍵
│       ├── KeyConfig.tsx        # 區塊二：Attack / Refresh 按鍵
│       ├── BuffPanel.tsx        # 區塊三：Buff 清單
│       ├── SettingsPanel.tsx    # 設定：透明度、置頂、Delay、更新
│       ├── KeyRecorder.tsx      # 按鍵錄製組件
│       ├── Dialog.tsx           # 通用對話框
│       ├── Switch.tsx           # 開關
│       ├── Slider.tsx           # 滑桿
│       └── Toggle.tsx           # Checkbox / Radio
│
├── index.html                   # Vite 入口
├── vite.config.ts               # Vite 設定 (base: './')
├── tsconfig.json
└── package.json
```

## 執行流程

1. **主行程 (main.cjs)** 建立 BrowserWindow，載入 Vite 打包後的 `dist/index.html`。
2. **preload.cjs** 透過 `contextBridge` 將受控 API 暴露給 renderer (`window.api`)。
3. **UI (React)** 讀寫設定 → 透過 IPC 呼叫主行程。
4. **scriptEngine** 收到啟動指令 → 使用 `uiohook-napi` 監聽全域鍵、`nut-js` 送出模擬鍵。
5. **停止** 時立即取消所有 Timer 並釋放所有按下的鍵，避免殘留。

## 主要相依套件

| 套件 | 用途 |
| --- | --- |
| `electron` | 桌面應用框架 |
| `electron-store` | 設定自動儲存 (視窗大小/位置、所有偏好) |
| `uiohook-napi` | 全域鍵盤事件監聽 (快捷鍵錄製 & 觸發) |
| `@nut-tree-fork/nut-js` | 模擬按鍵 KeyDown/KeyUp |
| `semver` | 版本比較 (檢查更新) |
| `react` + `vite` | UI |

## 注意事項

- 所有模擬按鍵固定按壓 **40ms**。
- Attack Resume Delay 於「設定」中調整，所有 Attack 恢復皆使用此值。
- 停止腳本會立即取消所有 Timer / Wait / Thread 並 KeyUp 所有按鍵。
- 按鍵不可重複綁定 (啟動快捷鍵 / Attack / 進圈 / 出圈 / 所有 Buff)。
- 腳本啟動期間，除「透明度」與「永遠置頂」外，所有控制均被停用。
- 更新來源：GitHub Release (於 `electron/updater.cjs` 修改 `REPO` 常數)。
