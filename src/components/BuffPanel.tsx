import { useEffect, useState } from "react";
import type { Settings, KeyCombo, Buff } from "../types";
import { KeyRecorder } from "./KeyRecorder";
import { Switch } from "./Switch";
import { Section } from "./Section";

type Props = {
  s: Settings;
  update: <K extends keyof Settings>(key: K, value: Settings[K]) => void;
  running: boolean;
  duplicateOf: (k: KeyCombo | null, exclude: string) => string | null;
  showError: (msg: string) => void;
  confirm: (title: string, message: string, onOk: () => void) => void;
};

type EditorState =
  | { mode: "add" }
  | { mode: "edit"; id: string }
  | null;

// Buff 區塊: 新增/編輯 modal + 檢核 + 紅色刪除 + 確認刪除
export function BuffPanel({ s, update, running, duplicateOf, showError, confirm }: Props) {
  const disabled = running;
  const [editor, setEditor] = useState<EditorState>(null);

  const openAdd = () => setEditor({ mode: "add" });
  const openEdit = (id: string) => setEditor({ mode: "edit", id });
  const close = () => setEditor(null);

  const removeBuff = (b: Buff) => {
    confirm("刪除 Buff", `確定要刪除「${b.name || "(未命名)"}」嗎？此動作無法復原。`, () => {
      update("buffs", s.buffs.filter((x) => x.id !== b.id));
    });
  };

  const toggleEnabled = (b: Buff, v: boolean) => {
    update("buffs", s.buffs.map((x) => (x.id === b.id ? { ...x, enabled: v } : x)));
  };
  const toggleCastOnStart = (b: Buff) => {
    if (disabled) return;
    update(
      "buffs",
      s.buffs.map((x) => (x.id === b.id ? { ...x, castOnStart: !x.castOnStart } : x))
    );
  };

  return (
    <Section
      title="Buff"
      extra={
        <button className="btn primary small" onClick={openAdd} disabled={disabled}>
          + 新增
        </button>
      }
    >
      {s.buffs.length === 0 && <p className="hint">尚未設定 Buff,點右上角「新增」建立一個。</p>}

      <div className="buff-list">
        {s.buffs.map((b) => (
          <div className="buff-row" key={b.id}>
            <div className="buff-row-main">
              <Switch
                checked={b.enabled}
                disabled={disabled}
                onChange={(v) => toggleEnabled(b, v)}
              />
              <div className="buff-row-info">
                <div className="buff-row-name">{b.name || "(未命名)"}</div>
                <div className="buff-row-meta">
                  <span className="tag">{b.key?.display || "未設定按鍵"}</span>
                  <span className="tag">{b.intervalSec} 秒</span>
                  <button
                    type="button"
                    className={`tag tag-toggle ${b.castOnStart ? "tag-accent" : ""}`}
                    disabled={disabled}
                    onClick={() => toggleCastOnStart(b)}
                    title="點擊切換:啟動後是否立即施放"
                  >
                    {b.castOnStart ? "✓ 啟動立即施放" : "啟動立即施放"}
                  </button>
                </div>
              </div>
            </div>
            <div className="buff-row-actions">
              <button
                className="btn small"
                disabled={disabled}
                onClick={() => openEdit(b.id)}
              >
                編輯
              </button>
              <button
                className="btn danger small"
                disabled={disabled}
                onClick={() => removeBuff(b)}
              >
                刪除
              </button>
            </div>
          </div>
        ))}
      </div>

      {editor && (
        <BuffEditor
          state={editor}
          buffs={s.buffs}
          duplicateOf={duplicateOf}
          onClose={close}
          onSave={(newBuff, id) => {
            if (id) {
              update("buffs", s.buffs.map((x) => (x.id === id ? newBuff : x)));
            } else {
              update("buffs", [...s.buffs, newBuff]);
            }
            close();
          }}
          showError={showError}
        />
      )}
    </Section>
  );
}

/** Buff 新增/編輯 Modal */
function BuffEditor({
  state,
  buffs,
  duplicateOf,
  onClose,
  onSave,
  showError,
}: {
  state: EditorState;
  buffs: Buff[];
  duplicateOf: (k: KeyCombo | null, exclude: string) => string | null;
  onClose: () => void;
  onSave: (b: Buff, id?: string) => void;
  showError: (msg: string) => void;
}) {
  const isEdit = state?.mode === "edit";
  const existing = isEdit ? buffs.find((x) => x.id === (state as any).id) : undefined;

  const [name, setName] = useState(existing?.name ?? "");
  const [key, setKey] = useState<KeyCombo | null>(existing?.key ?? null);
  const [intervalSec, setIntervalSec] = useState<number>(existing?.intervalSec ?? 10);

  useEffect(() => {
    const esc = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", esc);
    return () => document.removeEventListener("keydown", esc);
  }, [onClose]);

  const submit = () => {
    // === 檢核 ===
    const trimmed = name.trim();
    if (!trimmed) {
      showError("請輸入 Buff 名稱。");
      return;
    }
    if (trimmed.length > 40) {
      showError("Buff 名稱過長 (最多 40 字)。");
      return;
    }
    // 名稱重複
    const nameDup = buffs.some(
      (b) => b.name.trim() === trimmed && (!isEdit || b.id !== existing!.id)
    );
    if (nameDup) {
      showError(`已存在同名的 Buff「${trimmed}」,請更換名稱。`);
      return;
    }
    if (!key) {
      showError("請設定觸發按鍵。");
      return;
    }
    const dup = duplicateOf(key, isEdit ? `buff:${existing!.id}` : "buff:__new__");
    if (dup) {
      showError(`此按鍵已綁定於「${dup}」,請選擇其它按鍵。`);
      return;
    }
    const sec = Number(intervalSec);
    if (!Number.isFinite(sec) || sec < 1) {
      showError("間隔秒數必須 ≥ 1。");
      return;
    }

    // 新增時預設: 啟用=true, 啟動立即施放=false
    // 編輯時保留既有值 (在主畫面調整)
    const buff: Buff = {
      id: existing?.id ?? `buff_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
      name: trimmed,
      key,
      intervalSec: sec,
      castOnStart: existing?.castOnStart ?? false,
      enabled: existing?.enabled ?? true,
    };
    onSave(buff, existing?.id);
  };

  return (
    <div className="dialog-backdrop" onClick={onClose}>
      <div className="dialog buff-editor" onClick={(e) => e.stopPropagation()}>
        <h3>{isEdit ? "編輯 Buff" : "新增 Buff"}</h3>

        <div className="form-row">
          <label>名稱</label>
          <input
            className="text"
            autoFocus
            maxLength={40}
            value={name}
            placeholder="例如:狂怒"
            onChange={(e) => setName(e.target.value)}
          />
        </div>

        <div className="form-row">
          <label>觸發按鍵</label>
          <KeyRecorder value={key} onChange={setKey} />
        </div>

        <div className="form-row">
          <label>間隔 (秒)</label>
          <input
            type="number"
            className="num"
            min={1}
            value={intervalSec}
            onChange={(e) => setIntervalSec(Number(e.target.value) || 1)}
          />
        </div>

        <p className="hint" style={{ margin: "0 0 12px 0" }}>
          「啟用」與「啟動立即施放」請於主畫面 Buff 列表直接調整。
        </p>

        <div className="dialog-actions">
          <button className="btn ghost" onClick={onClose}>取消</button>
          <button className="btn primary" onClick={submit}>
            {isEdit ? "儲存" : "新增"}
          </button>
        </div>
      </div>
    </div>
  );
}
