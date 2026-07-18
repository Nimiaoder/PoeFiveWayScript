import { useState } from "react";
import type { Settings } from "../types";
import { Switch } from "./Switch";
import { Section } from "./Section";

type Props = {
  s: Settings;
  update: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  running: boolean;
  onReset: () => void;
  showInfo: (title: string, msg: string, url?: string) => void;
};

// 設定頁：透明度、置頂、Attack Resume Delay、重設、更新
export function SettingsPanel({ s, update, running, onReset, showInfo }: Props) {
  const [checking, setChecking] = useState(false);

  // 檢查更新
  const check = async () => {
    setChecking(true);
    const res = await window.api.checkUpdate();
    setChecking(false);
    if (!res.ok) {
      showInfo("檢查失敗", res.error || "未知錯誤");
      return;
    }
    if (res.hasUpdate) {
      showInfo("有新版本可用", `目前版本 ${res.current}\n最新版本 ${res.latest}`, res.url);
    } else {
      showInfo("已是最新版本", `目前版本 ${res.current}`);
    }
  };

  return (
    <>
      <Section title="顯示">
        <div className="row">
          <span className="row-label">視窗透明度</span>
          <div className="row-value" style={{ flex: 1, maxWidth: 220 }}>
            <input
              type="range"
              className="slider"
              min={0.3}
              max={1}
              step={0.01}
              value={s.opacity}
              style={{
                background: `linear-gradient(to right, var(--accent) 0%, var(--accent) ${
                  ((s.opacity - 0.3) / 0.7) * 100
                }%, #444 ${((s.opacity - 0.3) / 0.7) * 100}%, #444 100%)`,
              }}
              onChange={(e) => {
                const v = Number(e.target.value);
                update("opacity", v);
                window.api.setOpacity(v);
              }}
            />
            <span style={{ minWidth: 40, textAlign: "right", fontSize: 12 }}>
              {Math.round(s.opacity * 100)}%
            </span>
          </div>
        </div>
        <div className="row">
          <span className="row-label">永遠置頂</span>
          <div className="row-value">
            <Switch
              checked={s.alwaysOnTop}
              onChange={(v) => {
                update("alwaysOnTop", v);
                window.api.setAlwaysOnTop(v);
              }}
            />
          </div>
        </div>
      </Section>

      <Section title="執行參數">
        <div className="row">
          <span className="row-label">攻擊恢復延遲 (毫秒)</span>
          <div className="row-value">
            <input
              type="number"
              className="num"
              min={0}
              value={s.attackResumeDelay}
              disabled={running}
              onChange={(e) => update("attackResumeDelay", Number(e.target.value) || 0)}
            />
          </div>
        </div>
      </Section>

      <Section title="其它">
        <div className="row">
          <span className="row-label">重設所有設定</span>
          <button className="btn danger small" disabled={running} onClick={onReset}>
            重設
          </button>
        </div>
        <div className="row">
          <span className="row-label">檢查更新</span>
          <button className="btn small" disabled={checking} onClick={check}>
            {checking ? "檢查中..." : "檢查"}
          </button>
        </div>
      </Section>
    </>
  );
}
