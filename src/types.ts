// Renderer 型別定義
export type KeyCombo = { keys: string[]; display: string };

export type Buff = {
  id: string;
  name: string;
  key: KeyCombo | null;
  intervalSec: number;
  castOnStart: boolean;
  enabled: boolean;
};

export type MousePosition = "2/4" | "custom";

// 絕對螢幕座標 (雙人刷新模式用，支援雙螢幕 / 單螢幕雙視窗)
export type ScreenPoint = { x: number; y: number };

export type Settings = {
  windowBounds: { width: number; height: number; x?: number; y?: number };
  opacity: number;
  alwaysOnTop: boolean;
  toggleHotkey: KeyCombo | null;
  hotkeyMode: "listen" | "intercept";
  attackerMode: boolean;   // 自動攻擊
  refreshMode: boolean;    // 自動刷新
  buffMode: boolean;       // 自動 Buff
  refreshDirection: "enter-first" | "exit-first" | null;
  attackKey: KeyCombo | null;
  enterKey: KeyCombo | null;
  exitKey: KeyCombo | null;
  enterWaitMs: number;
  exitWaitMs: number;
  attackResumeDelay: number;
  // 時空裂隙按鍵 (可開關)：手動按下時重算進圈/出圈等待計時
  riftEnabled: boolean;
  riftKey: KeyCombo | null;
  // 雙人刷新模式 (打手 + 刷新 同時啟用時才可勾選)
  dualRefreshMode: boolean;
  dualAttackerPos: ScreenPoint | null;
  dualAuraPos: ScreenPoint | null;
  dualRightClickFocus: boolean;
  dualFocusDelayMs: number;
  // 自動控制鼠標位置 (刷新用)
  mouseControl: boolean;
  mousePosition: MousePosition;
  mouseCustomX: number;
  mouseCustomY: number;
  // 隱藏畫面 (可不設定)
  hideEnabled: boolean;
  hideHotkey: KeyCombo | null;
  buffs: Buff[];
};

export type AppInfo = { name: string; version: string };

declare global {
  interface Window {
    api: {
      getAppInfo: () => Promise<AppInfo>;
      minimize: () => Promise<void>;
      toggleMaximize: () => Promise<void>;
      close: () => Promise<void>;
      isMaximized: () => Promise<boolean>;
      onMaximizeChanged: (cb: (v: boolean) => void) => () => void;

      getAll: () => Promise<Settings>;
      set: (key: string, value: any) => Promise<boolean>;
      reset: () => Promise<Settings>;
      setOpacity: (v: number) => Promise<void>;
      setAlwaysOnTop: (v: boolean) => Promise<void>;
      startCapture: () => Promise<void>;
      cancelCapture: () => Promise<void>;
      onCaptured: (
        cb: (data: { keys?: string[]; display?: string; cancelled?: boolean; error?: string }) => void
      ) => () => void;
      bindToggle: (combo: KeyCombo | null, mode: "listen" | "intercept") => Promise<void>;
      bindHide: (combo: KeyCombo | null) => Promise<void>;
      onToggleRequested: (cb: () => void) => () => void;
      startScript: (config: Settings) => Promise<{ ok: boolean; error?: string }>;
      stopScript: () => Promise<{ ok: boolean }>;
      checkUpdate: () => Promise<{
        ok: boolean;
        hasUpdate?: boolean;
        latest?: string;
        current?: string;
        url?: string;
        error?: string;
      }>;
      openExternal: (url: string) => Promise<void>;
      // 倒數 delayMs 後擷取目前滑鼠的絕對螢幕座標 (綁定雙人視窗位置用)
      captureMousePosition: (delayMs: number) => Promise<ScreenPoint>;
    };
  }
}
