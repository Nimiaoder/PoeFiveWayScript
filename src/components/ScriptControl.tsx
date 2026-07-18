import type { Settings, KeyCombo } from "../types";
import { KeyRecorder } from "./KeyRecorder";
import { Switch } from "./Switch";
import { Section } from "./Section";

type Props = {
  s: Settings;
  update: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  running: boolean;
  onStart: () => void;
  onStop: () => void;
  duplicateOf: (k: KeyCombo | null, exclude: string) => string | null;
  showError: (msg: string) => void;
};

// 區塊一：腳本控制
export function ScriptControl({ s, update, running, onStart, onStop, duplicateOf, showError }: Props) {
  const disabled = running;

  // 快捷鍵設定：檢查重複
  const setToggle = (v: KeyCombo | null) => {
    if (v) {
      const dup = duplicateOf(v, "toggle");
      if (dup) {
        showError(`此按鍵已綁定於「${dup}」，請選擇其它按鍵。`);
        return;
      }
    }
    update("toggleHotkey", v);
  };

  return (
    <>
      <Section
        title="腳本控制"
        extra={
          <span className={`status ${running ? "on" : ""}`}>
            <span className="dot" /> {running ? "執行中" : "已停止"}
          </span>
        }
      >
        <div className="row">
          <span className="row-label">啟動 / 關閉</span>
          <div className="row-value">
            <Switch checked={running} onChange={(v) => (v ? onStart() : onStop())} />
          </div>
        </div>

        <div className="row">
          <span className="row-label">快捷鍵</span>
          <div className="row-value">
            <KeyRecorder value={s.toggleHotkey} onChange={setToggle} disabled={disabled} />
          </div>
        </div>

        <div className="row">
          <span className="row-label">快捷鍵模式</span>
          <div className="row-value radio-group">
            <label className={`check ${disabled ? "disabled" : ""}`}>
              <input
                type="radio"
                checked={s.hotkeyMode === "listen"}
                disabled={disabled}
                onChange={() => update("hotkeyMode", "listen")}
              />
              監聽
            </label>
            <label className={`check ${disabled ? "disabled" : ""}`}>
              <input
                type="radio"
                checked={s.hotkeyMode === "intercept"}
                disabled={disabled}
                onChange={() => update("hotkeyMode", "intercept")}
              />
              攔截
            </label>
          </div>
        </div>
      </Section>

      <Section title="模式">
        <div className="row">
          <label className={`check ${disabled ? "disabled" : ""}`}>
            <input
              type="checkbox"
              checked={s.attackerMode}
              disabled={disabled}
              onChange={(e) => update("attackerMode", e.target.checked)}
            />
            打手模式
          </label>
          <label className={`check ${disabled ? "disabled" : ""}`}>
            <input
              type="checkbox"
              checked={s.refreshMode}
              disabled={disabled}
              onChange={(e) => update("refreshMode", e.target.checked)}
            />
            刷新模式
          </label>
        </div>

        <div className="row">
          <span className="row-label">刷新方向</span>
          <div className="row-value radio-group">
            <label className={`check ${disabled ? "disabled" : ""}`}>
              <input
                type="radio"
                checked={s.refreshDirection === "enter-first"}
                disabled={disabled}
                onChange={() => update("refreshDirection", "enter-first")}
              />
              先進圈
            </label>
            <label className={`check ${disabled ? "disabled" : ""}`}>
              <input
                type="radio"
                checked={s.refreshDirection === "exit-first"}
                disabled={disabled}
                onChange={() => update("refreshDirection", "exit-first")}
              />
              先出圈
            </label>
            <button
              className="btn ghost small"
              disabled={disabled}
              onClick={() => update("refreshDirection", null)}
            >
              清除
            </button>
          </div>
        </div>
      </Section>
    </>
  );
}
