import type { Settings, KeyCombo, ScreenPoint } from "../types";
import { KeyRecorder } from "./KeyRecorder";
import { Switch } from "./Switch";
import { Section } from "./Section";
import { PositionPicker } from "./PositionPicker";

type Props = {
  s: Settings;
  update: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  running: boolean;
  duplicateOf: (k: KeyCombo | null, exclude: string) => string | null;
  showError: (msg: string) => void;
};

// 區塊二：按鍵設定 (Attack + Refresh)
// - 未勾選「自動攻擊」時隱藏 Attack 區
// - 未勾選「自動刷新」時隱藏 Refresh 區
// - 同時勾選「自動攻擊 + 自動刷新」時，才會出現「雙人刷新模式」
export function KeyConfig({ s, update, running, duplicateOf, showError }: Props) {
  const disabled = running;
  // 是否可使用雙人刷新模式：打手(自動攻擊) + 刷新 同時開啟
  const dualAvailable = s.attackerMode && s.refreshMode;
  const dualOn = dualAvailable && s.dualRefreshMode;

  // 統一：設定按鍵前檢查重複
  const setKey = (
    field: "attackKey" | "enterKey" | "exitKey" | "riftKey",
    v: KeyCombo | null
  ) => {
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
      )}

      {s.refreshMode && (
        <>
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
          </Section>

          {/* 時空裂隙按鍵：手動按下時，把目前等待計時歸零重算
              僅在「自動攻擊(打手) + 自動刷新」同時勾選時才顯示 */}
          {dualAvailable && (
          <Section title="時空裂隙(重製秒數)">
            <div className="row">
              <span className="row-label">啟用</span>
              <div className="row-value">
                <Switch
                  checked={s.riftEnabled}
                  disabled={disabled}
                  onChange={(v) => update("riftEnabled", v)}
                />
              </div>
            </div>
            {s.riftEnabled && (
              <div className="row">
                <span className="row-label">時空裂隙按鍵</span>
                <div className="row-value">
                  <KeyRecorder
                    value={s.riftKey}
                    onChange={(v) => setKey("riftKey", v)}
                    disabled={disabled}
                  />
                </div>
              </div>
            )}
            <div className="hint">
              按下時空裂隙會把目前「進圈 / 出圈之間的等待」歸零重新計時。
            </div>
          </Section>
          )}

          {/* 雙人刷新模式：僅在「自動攻擊 + 自動刷新」同時開啟時出現 */}
          {dualAvailable && (
            <Section title="雙人刷新模式">
              <div className="row">
                <span className="row-label">啟用雙人刷新</span>
                <div className="row-value">
                  <Switch
                    checked={s.dualRefreshMode}
                    disabled={disabled}
                    onChange={(v) => update("dualRefreshMode", v)}
                  />
                </div>
              </div>

              {dualOn && (
                <>
                  <div className="row">
                    <span className="row-label">打手視窗位置</span>
                    <div className="row-value">
                      <PositionPicker
                        value={s.dualAttackerPos}
                        disabled={disabled}
                        onChange={(v) => update("dualAttackerPos", v as ScreenPoint | null)}
                      />
                    </div>
                  </div>
                  <div className="row">
                    <span className="row-label">光環師視窗位置</span>
                    <div className="row-value">
                      <PositionPicker
                        value={s.dualAuraPos}
                        disabled={disabled}
                        onChange={(v) => update("dualAuraPos", v as ScreenPoint | null)}
                      />
                    </div>
                  </div>
                  <div className="row">
                    <span className="row-label">右鍵聚焦視窗</span>
                    <div className="row-value">
                      <Switch
                        checked={s.dualRightClickFocus}
                        disabled={disabled}
                        onChange={(v) => update("dualRightClickFocus", v)}
                      />
                    </div>
                  </div>
                  <div className="row">
                    <span className="row-label">切換視窗緩衝 (ms)</span>
                    <div className="row-value">
                      <input
                        type="number"
                        className="num"
                        value={s.dualFocusDelayMs}
                        disabled={disabled}
                        min={0}
                        onChange={(e) => update("dualFocusDelayMs", Number(e.target.value) || 0)}
                      />
                    </div>
                  </div>
                  <div className="hint">
                    座標為絕對螢幕座標，雙螢幕各開一個視窗或單螢幕兩個視窗都適用；
                    請把擷取點選在遊戲畫面中空白、不會誤觸物品或 NPC 的地方。
                    <br />
                    雙人模式啟用時會忽略下方「自動控制鼠標位置」。
                  </div>
                </>
              )}
            </Section>
          )}

          {/* 自動控制鼠標位置 (單人刷新用；雙人模式下不適用) */}
          {!dualOn && (
            <Section title="自動控制鼠標位置">
              <div className="row">
                <span className="row-label">啟用</span>
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
                    <div className="row-value">
                      <PositionPicker
                        value={s.mousePos}
                        disabled={disabled}
                        onChange={(v) => update("mousePos", v as ScreenPoint | null)}
                      />
                    </div>
                  </div>
                  <div className="hint">
                    按「擷取座標」後倒數 3 秒，記錄當下滑鼠的絕對螢幕座標；
                    之後可直接在 X / Y 欄位手動微調。
                  </div>
                </>
              )}
            </Section>
          )}
        </>
      )}
    </>
  );
}
