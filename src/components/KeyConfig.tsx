import type { Settings, KeyCombo, MousePosition } from "../types";
import { KeyRecorder } from "./KeyRecorder";
import { Switch } from "./Switch";

type Props = {
  s: Settings;
  update: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  running: boolean;
  duplicateOf: (k: KeyCombo | null, exclude: string) => string | null;
  showError: (msg: string) => void;
};

// 區塊二：按鍵設定 (Attack + Refresh)
// - 未勾選「自動攻擊」時隱藏 Attack 欄
// - 未勾選「自動刷新」時隱藏 Refresh 欄
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
      {s.attackerMode && (
        <div className="card">
          <h3>攻擊 (Attack)</h3>
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
        </div>
      )}

      {s.refreshMode && (
        <div className="card">
          <h3>刷新 (Refresh)</h3>
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

          {/* 刷新方向 */}
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

          {/* 自動控制鼠標位置 */}
          <div className="row">
            <span className="row-label">自動控制鼠標位置</span>
            <div className="row-value">
              <Switch
                checked={s.mouseControl}
                disabled={disabled}
                onChange={(v) => update("mouseControl", v)}
              />
            </div>
          </div>
          {s.mouseControl && (
            <>
              <div className="row">
                <span className="row-label">鼠標移動位置</span>
                <div className="row-value radio-group">
                  {(["2/4", "custom"] as MousePosition[]).map((p) => (
                    <label key={p} className={`check ${disabled ? "disabled" : ""}`}>
                      <input
                        type="radio"
                        checked={s.mousePosition === p}
                        disabled={disabled}
                        onChange={() => update("mousePosition", p)}
                      />
                      {p === "custom" ? "自訂" : "2/4 處"}
                    </label>
                  ))}
                </div>
              </div>
              {s.mousePosition === "custom" && (
                <div className="row">
                  <span className="row-label">自訂座標 (X -100~100 / Y -50~50)</span>
                  <div className="row-value">
                    <input
                      type="number"
                      className="num"
                      value={s.mouseCustomX}
                      disabled={disabled}
                      min={-100}
                      max={100}
                      placeholder="X"
                      onChange={(e) => update("mouseCustomX", Number(e.target.value))}
                    />
                    <input
                      type="number"
                      className="num"
                      value={s.mouseCustomY}
                      disabled={disabled}
                      min={-50}
                      max={50}
                      placeholder="Y"
                      onChange={(e) => update("mouseCustomY", Number(e.target.value))}
                    />
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </>
  );
}
