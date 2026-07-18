import { useEffect, useState, useCallback } from "react";
import type { Settings } from "../types";

// 設定 hook：初次載入 + 自動儲存
export function useSettings() {
  const [settings, setSettings] = useState<Settings | null>(null);

  useEffect(() => {
    window.api.getAll().then(setSettings);
  }, []);

  // 更新單一欄位並持久化
  const update = useCallback(<K extends keyof Settings>(key: K, value: Settings[K]) => {
    setSettings((prev) => (prev ? { ...prev, [key]: value } : prev));
    window.api.set(key as string, value);
  }, []);

  // 重設 (保留視窗大小/位置)
  const reset = useCallback(async () => {
    const fresh = await window.api.reset();
    setSettings(fresh);
  }, []);

  return { settings, update, reset, setSettings };
}
