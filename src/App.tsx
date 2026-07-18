import { useCallback, useEffect, useState } from "react";
import { useSettings } from "./hooks/useSettings";
import { ScriptControl } from "./components/ScriptControl";
import { KeyConfig } from "./components/KeyConfig";
import { BuffPanel } from "./components/BuffPanel";
import { SettingsPanel } from "./components/SettingsPanel";
import { Dialog } from "./components/Dialog";
import { TitleBar } from "./components/TitleBar";
import type { KeyCombo, Settings } from "./types";

type Tab = "home" | "settings";
type DialogState = {
  open: boolean;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  onConfirm?: () => void;
};

export default function App() {
  const { settings, update, reset } = useSettings();
  const [tab, setTab] = useState<Tab>("home");
  const [running, setRunning] = useState(false);
  const [dialog, setDialog] = useState<DialogState>({ open: false, title: "", message: "" });

  const showError = useCallback((msg: string) => {
    setDialog({ open: true, title: "無法設定", message: msg });
  }, []);
  const showInfo = useCallback((title: string, message: string, url?: string) => {
    setDialog({
      open: true,
      title,
      message,
      confirmText: url ? "下載最新版" : "確定",
      cancelText: url ? "關閉" : undefined,
      onConfirm: url ? () => window.api.openExternal(url) : undefined,
    });
  }, []);
  const confirm = useCallback((title: string, message: string, onOk: () => void) => {
    setDialog({
      open: true,
      title,
      message,
      confirmText: "確定",
      cancelText: "取消",
      onConfirm: onOk,
    });
  }, []);

  // 監聽 KeyRecorder 發出的錯誤事件 (例如純 modifier)
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent).detail;
      setDialog({ open: true, title: detail.title, message: detail.message });
    };
    window.addEventListener("app:dialog", handler);
    return () => window.removeEventListener("app:dialog", handler);
  }, []);

  // 檢查重複綁定
  const duplicateOf = useCallback(
    (k: KeyCombo | null, exclude: string): string | null => {
      if (!k || !settings) return null;
      const same = (a: KeyCombo | null | undefined) => !!a && a.display === k.display;
      if (exclude !== "toggle" && same(settings.toggleHotkey)) return "啟動快捷鍵";
      if (exclude !== "attackKey" && same(settings.attackKey)) return "攻擊鍵";
      if (exclude !== "enterKey" && same(settings.enterKey)) return "進圈鍵";
      if (exclude !== "exitKey" && same(settings.exitKey)) return "出圈鍵";
      for (const b of settings.buffs) {
        if (exclude === `buff:${b.id}`) continue;
        if (same(b.key)) return `Buff (${b.name})`;
      }
      return null;
    },
    [settings]
  );

  // 註冊 toggle 快捷鍵到主行程
  useEffect(() => {
    if (!settings) return;
    window.api.bindToggle(settings.toggleHotkey, settings.hotkeyMode);
  }, [settings?.toggleHotkey, settings?.hotkeyMode]);

  // 收到 toggle 觸發：切換 script
  useEffect(() => {
    return window.api.onToggleRequested(() => {
      if (running) doStop();
      else doStart();
    });
  }, [running, settings]);

  // 啟動檢查
  const validate = (s: Settings): string | null => {
    if (!s.attackerMode && !s.refreshMode) return "請至少啟用「打手模式」或「刷新模式」。";
    if (s.attackerMode && !s.attackKey) return "打手模式需要設定攻擊鍵。";
    if (s.refreshMode) {
      if (!s.enterKey) return "刷新模式需要設定進圈鍵。";
      if (!s.exitKey) return "刷新模式需要設定出圈鍵。";
      if (!s.refreshDirection) return "刷新模式需要選擇「先進圈」或「先出圈」。";
    }
    if (!s.buffs || s.buffs.length === 0) return "請至少建立一個 Buff。";
    return null;
  };

  // 啟動
  const doStart = async () => {
    if (!settings) return;
    const err = validate(settings);
    if (err) {
      setDialog({ open: true, title: "無法啟動", message: err });
      return;
    }
    const res = await window.api.startScript(settings);
    if (!res.ok) {
      setDialog({ open: true, title: "啟動失敗", message: res.error || "未知錯誤" });
      return;
    }
    setRunning(true);
  };

  // 停止
  const doStop = async () => {
    await window.api.stopScript();
    setRunning(false);
  };

  if (!settings) {
    return (
      <div className="app">
        <TitleBar />
        <div className="card">載入中...</div>
      </div>
    );
  }

  return (
    <div className="app">
      <TitleBar />

      <div className="tabs">
        <button className={`tab ${tab === "home" ? "active" : ""}`} onClick={() => setTab("home")}>
          主頁
        </button>
        <button className={`tab ${tab === "settings" ? "active" : ""}`} onClick={() => setTab("settings")}>
          設定
        </button>
      </div>

      <div className="content">
        {tab === "home" && (
          <>
            <ScriptControl
              s={settings}
              update={update}
              running={running}
              onStart={doStart}
              onStop={doStop}
              duplicateOf={duplicateOf}
              showError={showError}
            />
            <KeyConfig
              s={settings}
              update={update}
              running={running}
              duplicateOf={duplicateOf}
              showError={showError}
            />
            <BuffPanel
              s={settings}
              update={update}
              running={running}
              duplicateOf={duplicateOf}
              showError={showError}
              confirm={confirm}
            />
          </>
        )}
        {tab === "settings" && (
          <SettingsPanel
            s={settings}
            update={update}
            running={running}
            onReset={() =>
              confirm("重設所有設定", "確定要恢復所有設定值嗎？視窗大小與位置不會被重設。", () => reset())
            }
            showInfo={showInfo}
          />
        )}
      </div>

      <Dialog
        open={dialog.open}
        title={dialog.title}
        message={dialog.message}
        confirmText={dialog.confirmText}
        cancelText={dialog.cancelText}
        onConfirm={dialog.onConfirm}
        onClose={() => setDialog((d) => ({ ...d, open: false }))}
      />
    </div>
  );
}
