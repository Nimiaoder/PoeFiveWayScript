import { useEffect, useState } from "react";
import type { AppInfo } from "../types";

// 自訂 titlebar：顯示 package 名稱與版本 + 視窗控制按鈕
export function TitleBar() {
  const [info, setInfo] = useState<AppInfo | null>(null);
  const [maximized, setMaximized] = useState(false);

  useEffect(() => {
    window.api.getAppInfo().then(setInfo);
    window.api.isMaximized().then(setMaximized);
    return window.api.onMaximizeChanged(setMaximized);
  }, []);

  return (
    <div className="titlebar">
      <div className="titlebar-drag">
        <span className="titlebar-title">
          {info ? `${info.name}` : "載入中..."}
          {info && <span className="titlebar-version">v{info.version}</span>}
        </span>
      </div>
      <div className="titlebar-actions">
        <button className="tb-btn" onClick={() => window.api.minimize()} aria-label="最小化">
          <svg width="10" height="10" viewBox="0 0 10 10"><rect y="4.5" width="10" height="1" fill="currentColor" /></svg>
        </button>
        <button className="tb-btn" onClick={() => window.api.toggleMaximize()} aria-label="最大化">
          {maximized ? (
            <svg width="10" height="10" viewBox="0 0 10 10">
              <rect x="1" y="2.5" width="6.5" height="6.5" fill="none" stroke="currentColor" strokeWidth="1" />
              <rect x="2.5" y="1" width="6.5" height="6.5" fill="none" stroke="currentColor" strokeWidth="1" />
            </svg>
          ) : (
            <svg width="10" height="10" viewBox="0 0 10 10"><rect x="0.5" y="0.5" width="9" height="9" fill="none" stroke="currentColor" /></svg>
          )}
        </button>
        <button className="tb-btn tb-close" onClick={() => window.api.close()} aria-label="關閉">
          <svg width="10" height="10" viewBox="0 0 10 10">
            <line x1="1" y1="1" x2="9" y2="9" stroke="currentColor" />
            <line x1="9" y1="1" x2="1" y2="9" stroke="currentColor" />
          </svg>
        </button>
      </div>
    </div>
  );
}
