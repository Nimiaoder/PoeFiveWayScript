import { useState, type ReactNode } from "react";

type Props = {
  title: ReactNode;
  /** 標題右側額外元素 (例如「+新增」按鈕、狀態徽章)。點擊不會觸發展開/收起。 */
  extra?: ReactNode;
  defaultOpen?: boolean;
  children: ReactNode;
};

/**
 * 可展開/收起的區塊卡片。
 * 現代簡潔風格:點擊整條標題列切換,箭頭以旋轉動畫指示狀態。
 */
export function Section({ title, extra, defaultOpen = true, children }: Props) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className={`card section ${open ? "open" : "collapsed"}`}>
      <button
        type="button"
        className="section-header"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <span className="section-title">
          <svg
            className={`section-chev ${open ? "open" : ""}`}
            width="12"
            height="12"
            viewBox="0 0 12 12"
            aria-hidden="true"
          >
            <path
              d="M4 2.5 L8 6 L4 9.5"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
          <span>{title}</span>
        </span>
        {extra && (
          <span className="section-extra" onClick={(e) => e.stopPropagation()}>
            {extra}
          </span>
        )}
      </button>
      <div className="section-body" hidden={!open}>
        {children}
      </div>
    </div>
  );
}
