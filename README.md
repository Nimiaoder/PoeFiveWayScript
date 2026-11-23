# POE 五軍腳本 (PoeFiveWayScript) — JavaFX 版

由原 Electron + React 版本移植到 **Java 21 + JavaFX 21**。功能與介面維持與原版一致，
配色改為 **黑灰色系**（含自訂標題列），並新增 **繁中 / 簡中** 切換、**GitHub Release 自動更新**，
以及可把 **Java 21 執行環境一起打包** 的 **免安裝版 / 安裝檔**。

> ⚠️ **移植當下未在 Windows + JavaFX 實機執行過**（開發環境無 Maven/JavaFX/網路）。
> 已驗證：非 UI 程式碼（設定、引擎、快捷鍵、更新、i18n）可編譯，引擎有跑過冒煙測試（啟動/停止/重啟/長等待中途停止、無殘留執行緒），
> FXML 的 `fx:id` / `#handler` 與控制器已逐一比對、語系 key 兩種語言完全對齊。
> UI 部分請第一次建置後實際操作一輪；若有排版或 CSS 小差異，主要集中在 `css/dark-gray.css` 調整即可。

---

## 快速開始

需求：**JDK 21**、**Maven 3.9+**（打包安裝檔另需 **WiX Toolset 3.x** 並加入 PATH）。

```bash
# 開發執行
mvn javafx:run

# 只編譯 / 產生 jar
mvn clean package

# 免安裝版 (含 Java runtime，解壓即用)
mvn clean package -Pwin-portable

# 安裝檔 (含 Java runtime，使用者不需另裝 JVM)
mvn clean package -Pwin-installer

# 兩種一起
mvn clean package -Pwin-portable,win-installer
```

> `jpackage` 會使用**執行 Maven 的那個 JDK 21** 產生精簡 runtime（jlink）並包進成品，
> 所以請確認 `java -version` / `JAVA_HOME` 是你要內含的 Java 21。打包必須在 **Windows** 上執行。

### 日誌 (Logback)

- 主控台 (IDE / `mvn javafx:run`) 與檔案同時輸出，設定在 `src/main/resources/logback.xml`
- 日誌檔：`data/<yyyy-MM-dd>/logs.log`，每天一個資料夾，預設保留 30 天
  - 開發：`./data/…`　免安裝：exe 同層 `data/…`　安裝版：`%APPDATA%\PoeFiveWayScript\data\…`
- 在程式碼中使用 `private static final Logger log = LoggerFactory.getLogger(X.class);`
- Windows 主控台若出現亂碼，可加 JVM 參數 `-Dconsole.charset=MS950`

### 應用程式圖標

- 圖標位置：`src/main/resources/icon/app/icon.ico` (jpackage 打包 exe / 安裝檔使用) 與 `icon.png` (JavaFX 視窗與工作列使用)
- 換圖直接覆蓋這兩個檔案即可；`icon.ico` 建議內含 16 / 32 / 48 / 256 px

### 產出位置

| 型態 | 路徑 |
| --- | --- |
| 免安裝資料夾 | `target/dist/portable/PoeFiveWayScript/PoeFiveWayScript.exe` |
| 免安裝 zip（放 Release 用） | `target/dist/PoeFiveWayScript-<版本>-portable.zip` |
| 安裝檔 | `target/dist/installer/PoeFiveWayScript-<版本>.exe` |

---

## 專案結構

```
src/main/java/liu/poe/fiveway/
├── Launcher.java                 程式進入點 (非模組化打包必須與 Application 分開)
├── app/
│   ├── PoeFiveWayApp.java        JavaFX Application：視窗、生命週期、重建 UI
│   ├── AppContext.java           全域狀態：設定、服務、腳本啟停、設定變更通知、按鍵重複檢查
│   ├── AppHost.java              應用層操作介面 (重建 UI / 結束 / 開啟網址)
│   ├── SingleInstanceGuard.java  單一實例鎖定 (本機通訊埠)           ← 原 main.cjs 的 requestSingleInstanceLock
│   └── UpdateFlow.java           自動更新流程 (檢查→詢問→下載→套用)
├── config/
│   ├── AppConfig.java            讀 app.properties + 常數 (HOLD_MS=40 等)
│   └── AppPaths.java             data 資料夾位置 (dev / portable / installer)
├── model/                        Settings、KeyCombo、Buff、ScreenPoint、WindowBounds
├── store/SettingsStore.java      JSON 設定 + 備份 (保留 10 份) + 150ms 防抖   ← 原 store.cjs
├── hotkey/
│   ├── HotkeyService.java        全域快捷鍵：錄製 / 監聽 / 攔截 (JNativeHook)  ← 原 hotkey.cjs
│   └── KeyNames.java             keycode → 顯示名稱
├── input/
│   ├── KeySender.java            按鍵模擬 (java.awt.Robot)                  ← 原 keySender.cjs
│   ├── MouseSender.java          滑鼠移動 / 右鍵 / 座標擷取
│   └── KeyMap.java               顯示名稱 → VK 碼
├── engine/ScriptEngine.java      腳本狀態機 (攻擊/刷新/雙人/Buff/時空裂隙)    ← 原 scriptEngine.cjs
├── update/
│   ├── UpdateService.java        GitHub Release API 檢查 + 下載             ← 原 updater.cjs
│   ├── UpdateInstaller.java      套用更新 (免安裝版覆蓋 / 安裝版執行安裝程式)
│   └── Version.java              版本比較
├── i18n/I18n.java                多語系管理
└── ui/
    ├── MainController.java …     各畫面控制器 (一個 FXML 對應一個 Controller)
    ├── WindowManager.java        無邊框視窗：拖曳 / 邊緣縮放 / 最大化 / 透明度 / 置頂 / 隱藏
    └── component/                Section、ToggleSwitch (純 Java 小元件)、KeyRecorder、PositionPicker (FXML 元件)

src/main/resources/
├── app.properties                版本、更新來源 (update.repo) 等設定
├── fxml/                         所有畫面 (.fxml)
├── css/dark-gray.css             黑灰色系主題
└── i18n/messages_zh_TW.properties / messages_zh_CN.properties
```

### 原版 → 新版 對照

| 原 (Electron / React) | 新 (JavaFX) |
| --- | --- |
| `App.tsx` | `MainController` + `main.fxml` |
| `TitleBar.tsx` | `TitleBarController` + `titlebar.fxml` + `WindowManager` |
| `ScriptControl.tsx` | `ScriptControlController` + `script_control.fxml` |
| `KeyConfig.tsx` | `KeyConfigController` + `key_config.fxml` |
| `BuffPanel.tsx` | `BuffPanelController` / `BuffRowController` / `BuffEditorController` + `buff_*.fxml` |
| `SettingsPanel.tsx` | `SettingsPanelController` + `settings_panel.fxml` |
| `Dialog.tsx` | `DialogController` + `dialog.fxml` |
| `KeyRecorder.tsx` / `PositionPicker.tsx` | `KeyRecorder` / `PositionPicker` (fx:root FXML 元件) |
| `Section.tsx` / `Switch.tsx` | `Section` / `ToggleSwitch` |
| `styles.css` (藍色 Fluent) | `dark-gray.css` (黑灰色系) |

---

## 多語系 (i18n)

* 所有文字都在 `src/main/resources/i18n/messages_zh_TW.properties`（繁中）與 `messages_zh_CN.properties`（簡中）。
* FXML 以 `%key` 取用；Java 以 `I18n.t("key")` / `I18n.t("key", 參數...)` 取用 (`{0}` `{1}` 代入)。
* 在 **設定 → 顯示 → 語言** 切換，會立即重建 UI 並記錄於設定檔 (`language`)。第一次啟動依系統語言決定。
* 新增語言：複製一份 properties（例如 `messages_en.properties`），在 `I18n.Language` 加一個列舉即可。

---

## 自動更新

* 更新來源在 `src/main/resources/app.properties`：

  ```properties
  update.repo=Nimiaoder/PoeFiveWayScript      # <owner>/<repo>；尚未確定可先填 Sample/PoeFiveWayScript
  update.checkOnStartup=true                  # 啟動後自動檢查
  ```
* 流程：啟動 2 秒後（或在「設定 → 其它 → 檢查更新」手動）呼叫 `GET /repos/{repo}/releases/latest`，
  以 `tag_name`（可帶 `v` 前綴，如 `v1.2.0`）和目前版本比較 → 有新版就詢問 → 下載 (有進度條) → 套用。
* **Release 附件命名規則**（可在 `app.properties` 以正規表示式調整）：

  | 發行型態 | 需上傳的附件 | 比對規則 |
  | --- | --- | --- |
  | 免安裝版 | `PoeFiveWayScript-<版本>-portable.zip` | 檔名含 `portable` 的 `.zip` |
  | 安裝版 | `PoeFiveWayScript-<版本>.exe` | `.exe` / `.msi` |

* **免安裝版套用方式**：解壓到暫存 → 產生批次檔 → 本程式結束後以 `robocopy` 覆蓋程式檔（**保留 `data` 資料夾，設定不會遺失**）→ 自動重新啟動。
* **安裝版套用方式**：直接執行下載的安裝程式並結束本程式。`pom.xml` 內的 `win.upgrade.uuid` 固定不變，新版才會「覆蓋升級」舊版，**請勿修改**。
* 開發模式（`mvn javafx:run`）或找不到對應附件時，退回「開啟 Release 頁面」（與原版行為相同）。
* 發版步驟：改 `pom.xml` 的 `<version>` → `mvn clean package -Pwin-portable,win-installer` → 在 GitHub 建立 tag `v<版本>` 的 Release，上傳 zip 與 exe。

---

## 設定檔位置

* 免安裝 / 開發：`<exe 或專案目錄>/data/config/settings.json`，備份在 `data/backup/`（保留最新 10 份）。
* 安裝版：`%APPDATA%\PoeFiveWayScript\data\...`（Program Files 底下沒有寫入權限，所以改放使用者資料夾）。
* 欄位名稱與原 Electron 版相同，**舊的 `settings.json` 可直接複製過來沿用**。

---

## 與原版行為不同 / 需要留意的地方

為了在 Java 上實現，以下是**非功能性的必要差異**（腳本邏輯、驗證規則、UI 配置皆照原版）：

1. **按鍵模擬 / 滑鼠**：`nut-js` → `java.awt.Robot`。原版能錄製但無法送出的鍵（方向鍵、Home/End、數字鍵盤、CapsLock 等），現在也有對應的 VK 碼可送出。
2. **全域鍵盤監聽**：`uiohook-napi` → `JNativeHook`（keycode 編碼與原版相同）。「攔截」模式以反射標記事件為已處理，僅 Windows 有效；失敗時自動退化為「監聽」。
3. **停止時的殘留按鍵**：原版在「按下→等 40ms→放開」中途被停止時，非攻擊鍵可能卡住；Java 版的短按有 `finally` 保證放開。
4. **滑鼠右鍵聚焦**：按下與放開之間加入 20ms，避免部分遊戲吃不到過快的點擊。
5. **設定 → 重設**：同時會還原視窗透明度與置頂（原版只重設儲存值、視窗當下不變）；語言設定會保留。
6. **無邊框視窗**：JavaFX 不提供原生拖曳/縮放，已自行實作標題列拖曳、雙擊最大化、邊緣縮放；最大化使用工作區，不會蓋住工作列。
7. **視覺**：JavaFX CSS 不支援 `@keyframes` / `backdrop-filter`，故「錄製中按鈕脈動」與「對話框背景模糊」省略（錄製中仍有高亮邊框）。關閉鈕 hover 與「刪除」鈕保留低飽和暗紅作為警示色，其餘皆為黑灰色系。
8. **新增設定**：`language`（介面語言）。

---

## 常見問題

* **打包後啟動閃退 / ClassNotFoundException**：在 `pom.xml` 的 `<addModules>` 補上缺少的 JDK 模組。可先用
  `jdeps --print-module-deps --ignore-missing-deps target/jpackage-input/*.jar` 檢查。
* **`mvn ... -Pwin-installer` 找不到 light.exe / candle.exe**：尚未安裝 WiX Toolset 3.x 或未加入 PATH。
* **要換圖示**：在 `jpackage-maven-plugin` 的 `<configuration>` 加上 `<icon>path/to/app.ico</icon>`。
* **遊戲以系統管理員身分執行時快捷鍵/按鍵無效**：本程式也需以系統管理員身分執行（Windows UIPI 限制）。

## 授權

MIT License（沿用原專案）。
