import { useEffect, useRef, useState } from "react";
import type { ScreenPoint } from "../types";

type Props = {
  value: ScreenPoint | null;
  onChange: (v: ScreenPoint | null) => void;
  disabled?: boolean;
  /** 倒數秒數，預設 3 秒，讓使用者有時間把滑鼠移到目標視窗 */
  countdownSec?: number;
};

/**
 * 螢幕座標擷取元件 (雙人刷新模式綁定視窗位置用)
 * 流程：按下「擷取」→ 倒數 n 秒 → 記錄目前滑鼠的絕對螢幕座標
 * 使用絕對座標的原因：雙螢幕各一個視窗、或單螢幕兩個視窗都能正確對應
 */
export function PositionPicker({ value, onChange, disabled, countdownSec = 3 }: Props) {
  const [left, setLeft] = useState(0);
  const timerRef = useRef<number | null>(null);
  const capturing = left > 0;

  // 卸載時清除倒數
  useEffect(() => () => { if (timerRef.current) window.clearInterval(timerRef.current); }, []);

  const capture = async () => {
    if (disabled || capturing) return;
    setLeft(countdownSec);
    timerRef.current = window.setInterval(() => {
      setLeft((v) => {
        if (v <= 1 && timerRef.current) window.clearInterval(timerRef.current);
        return v - 1;
      });
    }, 1000);
    try {
      const p = await window.api.captureMousePosition(countdownSec * 1000);
      onChange(p);
    } catch {
      /* 擷取失敗時維持原值 */
    } finally {
      if (timerRef.current) window.clearInterval(timerRef.current);
      setLeft(0);
    }
  };

  return (
    <>
      <span className="pos-value">
        {value ? `X ${value.x} , Y ${value.y}` : "尚未綁定"}
      </span>
      <button className="btn small" disabled={disabled || capturing} onClick={capture}>
        {capturing ? `${left} 秒後擷取…` : "擷取座標"}
      </button>
      {value && (
        <button
          className="btn ghost small"
          disabled={disabled || capturing}
          onClick={() => onChange(null)}
        >
          清除
        </button>
      )}
    </>
  );
}
