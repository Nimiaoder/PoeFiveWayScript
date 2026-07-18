import type { Settings, KeyCombo } from "../types";
import { KeyRecorder } from "./KeyRecorder";
import { Section } from "./Section";

type Props = {
  s: Settings;
  update: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  running: boolean;
  duplicateOf: (k: KeyCombo | null, exclude: string) => string | null;
  showError: (msg: string) => void;
};

// 區塊二：按鍵設定 (Attack + Refresh)
export function KeyConfig({ s, update, running, duplicateOf, showError }: Props) {
  const disabled = running;

  // 統一：設定按鍵前檢查重複
  const setKey = (field: "attackKey" | "enterKey" | "exitKey", v: KeyCombo | null) => {
    if (v) {
      const dup = duplicateOf(v, field);
      if (dup) {
        showError(`此按鍵已綁定於「${dup}」，請選擇其它按鍵。`);
        return;
      }
    }
    update(field, v);
  };

  return (
    <>
      <Section title="攻擊 (Attack)">
        <div className="row">
          <span className="row-label">攻擊鍵</span>
          <div className="row-value">
            <KeyRecorder
              value={s.attackKey}
              onChange={(v) => setKey("attackKey", v)}
              disabled={disabled}
            />
          </div>
        </div>
      </Section>

      <Section title="刷新 (Refresh)">
        <div className="row">
          <span className="row-label">進圈鍵</span>
          <div className="row-value">
            <KeyRecorder
              value={s.enterKey}
              onChange={(v) => setKey("enterKey", v)}
              disabled={disabled}
            />
          </div>
        </div>
        <div className="row">
          <span className="row-label">進圈後等待 (ms)</span>
          <div className="row-value">
            <input
              type="number"
              className="num"
              value={s.enterWaitMs}
              disabled={disabled}
              min={0}
              onChange={(e) => update("enterWaitMs", Number(e.target.value) || 0)}
            />
          </div>
        </div>
        <div className="row">
          <span className="row-label">出圈鍵</span>
          <div className="row-value">
            <KeyRecorder
              value={s.exitKey}
              onChange={(v) => setKey("exitKey", v)}
              disabled={disabled}
            />
          </div>
        </div>
        <div className="row">
          <span className="row-label">出圈後等待 (ms)</span>
          <div className="row-value">
            <input
              type="number"
              className="num"
              value={s.exitWaitMs}
              disabled={disabled}
              min={0}
              onChange={(e) => update("exitWaitMs", Number(e.target.value) || 0)}
            />
          </div>
        </div>
      </Section>
    </>
  );
}
