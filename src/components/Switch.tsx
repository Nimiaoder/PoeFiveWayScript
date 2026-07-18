type Props = { checked: boolean; onChange: (v: boolean) => void; disabled?: boolean };

// 開關
export function Switch({ checked, onChange, disabled }: Props) {
  return (
    <div
      className={`switch ${checked ? "on" : ""} ${disabled ? "disabled" : ""}`}
      onClick={() => !disabled && onChange(!checked)}
      role="switch"
      aria-checked={checked}
    />
  );
}
