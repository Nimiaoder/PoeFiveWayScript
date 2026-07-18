import { useEffect, useRef, useState } from "react";
import type { KeyCombo } from "../types";

type Props = {
  value: KeyCombo | null;
  onChange: (v: KeyCombo | null) => void;
  disabled?: boolean;
  placeholder?: string;
  /** 是否顯示內嵌的清除 (X) 按鈕，預設 true */
  allowClear?: boolean;
};

// 目前正在錄製的元件 id (全域只允許一個 session)
let activeRecorderId = 0;
const listeners: Array<(id: number) => void> = [];
function setActive(id: number) {
  activeRecorderId = id;
  listeners.forEach((l) => l(id));
}

/**
 * 按鍵錄製元件
 *
 * 修復 (問題 1):
 *   - 點擊已在錄製中的按鈕會導致「重啟 session → 收到 cancelled 事件 → 卡住」。
 *     現在若正在錄製再點一次視為無效點擊，直接忽略；並在 onCaptured 中忽略
 *     過期的 cancelled 事件 (由 replaceToken 標示)。
 *   - 全部包 try/catch，任何錯誤都彈提示並取消錄製，不影響程式。
 */
export function KeyRecorder({
  value,
  onChange,
  disabled,
  placeholder = "點擊設定",
  allowClear = true,
}: Props) {
  const [recording, setRecording] = useState(false);
  const idRef = useRef(Math.random());
  const btnRef = useRef<HTMLButtonElement>(null);
  const recordingRef = useRef(false);

  const emitDialogError = (message: string) => {
    try {
      window.dispatchEvent(
        new CustomEvent("app:dialog", { detail: { title: "無法設定", message } })
      );
    } catch {
      /* noop */
    }
  };

  const stopRecording = () => {
    recordingRef.current = false;
    setRecording(false);
    setActive(0);
    btnRef.current?.blur();
  };

  useEffect(() => {
    const unsub = window.api.onCaptured((data) => {
      if (activeRecorderId !== idRef.current) return;
      // 只在本元件確實還在錄製時才處理
      if (!recordingRef.current) return;
      try {
        stopRecording();
        if (data.cancelled) return;
        if (data.error) {
          emitDialogError(data.error);
          return;
        }
        if (data.keys && data.display) {
          onChange({ keys: data.keys, display: data.display });
        }
      } catch (err: any) {
        stopRecording();
        emitDialogError(err?.message || "設定按鍵時發生未預期錯誤，已取消。");
      }
    });
    return unsub;
  }, [onChange]);

  // 若其它元件開始錄製 → 本元件立即結束
  useEffect(() => {
    const listener = (id: number) => {
      if (id !== idRef.current && recordingRef.current) stopRecording();
    };
    listeners.push(listener);
    return () => {
      const i = listeners.indexOf(listener);
      if (i >= 0) listeners.splice(i, 1);
    };
  }, []);

  // 點擊外部取消
  useEffect(() => {
    if (!recording) return;
    const onDown = (e: MouseEvent) => {
      const wrap = btnRef.current?.parentElement;
      if (wrap && !wrap.contains(e.target as Node)) {
        try {
          window.api.cancelCapture();
        } catch {
          /* noop */
        }
        stopRecording();
      }
    };
    document.addEventListener("mousedown", onDown);
    return () => document.removeEventListener("mousedown", onDown);
  }, [recording]);

  const start = () => {
    if (disabled) return;
    // 已在錄製中：忽略再次點擊 (修正問題 1: 卡住)
    if (recordingRef.current) return;
    try {
      // 修正:若使用者剛在輸入框輸入完 (如 Buff 名稱) 直接來點按鍵設定,
      // 要先讓輸入框失焦,否則按下的按鍵會同時寫入輸入框。
      const active = document.activeElement as HTMLElement | null;
      if (active && typeof active.blur === "function") active.blur();
      btnRef.current?.blur();
      setActive(idRef.current);
      recordingRef.current = true;
      setRecording(true);
      window.api.startCapture();
    } catch (err: any) {
      stopRecording();
      emitDialogError(err?.message || "啟動按鍵錄製失敗，已取消。");
    }
  };

  const clear = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (disabled) return;
    if (recordingRef.current) {
      try {
        window.api.cancelCapture();
      } catch {
        /* noop */
      }
      stopRecording();
    }
    onChange(null);
  };

  const label = recording ? "請按下按鍵..." : value?.display || placeholder;
  const cls = `key-btn ${recording ? "recording" : ""} ${!value && !recording ? "empty" : ""}`;

  return (
    <div className="key-recorder-wrap">
      <button
        ref={btnRef}
        type="button"
        className={cls}
        disabled={disabled}
        onMouseDown={(e) => {
          e.preventDefault(); // 避免焦點跑到按鈕
          start();
        }}
        onKeyDown={(e) => {
          // 阻止 Space / Enter 觸發 click
          if (e.key === " " || e.key === "Enter" || e.key === "Spacebar") {
            e.preventDefault();
          }
        }}
      >
        <span className="key-btn-label">{label}</span>
        {allowClear && value && !recording && !disabled && (
          <span
            className="key-btn-clear"
            role="button"
            aria-label="清除按鍵"
            title="清除"
            onMouseDown={(e) => {
              e.preventDefault();
              e.stopPropagation();
            }}
            onClick={clear}
          >
            <svg width="10" height="10" viewBox="0 0 10 10">
              <line x1="1.5" y1="1.5" x2="8.5" y2="8.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
              <line x1="8.5" y1="1.5" x2="1.5" y2="8.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
            </svg>
          </span>
        )}
      </button>
    </div>
  );
}
