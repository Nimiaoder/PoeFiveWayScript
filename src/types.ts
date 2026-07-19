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

export type MousePosition = "1/4" | "2/4" | "3/4";

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
  // 自動控制鼠標位置 (刷新用)
  mouseControl: boolean;
  mousePosition: MousePosition;
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
    };
  }
}
