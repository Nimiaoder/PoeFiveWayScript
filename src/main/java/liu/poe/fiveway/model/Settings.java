package liu.poe.fiveway.model;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;

/**
 * 全部設定。欄位名稱與原 Electron 版 settings.json 相同，可直接沿用舊設定檔。
 * 新增欄位：language (介面語言)。
 */
public class Settings {
    public static final String HOTKEY_LISTEN = "listen";
    public static final String HOTKEY_INTERCEPT = "intercept";
    public static final String DIR_ENTER_FIRST = "enter-first";
    public static final String DIR_EXIT_FIRST = "exit-first";

    private static final Gson COPIER = new Gson();

    public WindowBounds windowBounds = new WindowBounds();
    public double opacity = 1;
    public boolean alwaysOnTop = false;
    public KeyCombo toggleHotkey;
    public String hotkeyMode = HOTKEY_LISTEN;   // listen | intercept
    public boolean attackerMode = false;        // 自動攻擊
    public boolean refreshMode = false;         // 自動刷新
    public boolean buffMode = false;            // 自動 Buff
    public String refreshDirection;             // enter-first | exit-first | null
    public KeyCombo attackKey;
    public KeyCombo enterKey;
    public KeyCombo exitKey;
    public int enterWaitMs = 2000;
    public int exitWaitMs = 1000;
    public int attackResumeDelay = 30;          // ms
    // 時空裂隙按鍵：手動按下時重算「進圈/出圈之間的等待」計時
    public boolean riftEnabled = false;
    public KeyCombo riftKey;
    // 雙人刷新模式
    public boolean dualRefreshMode = false;
    public ScreenPoint dualAttackerPos;         // 打手視窗的絕對螢幕座標
    public ScreenPoint dualAuraPos;             // 光環師視窗的絕對螢幕座標
    public boolean dualRightClickFocus = true;  // 以右鍵點擊聚焦視窗
    public int dualFocusDelayMs = 120;          // 切換視窗後等待多久才送出按鍵
    // 自動控制鼠標位置 (刷新用)
    public boolean mouseControl = false;
    public ScreenPoint mousePos;
    // 隱藏畫面快捷鍵 (可不設定)
    public boolean hideEnabled = false;
    public KeyCombo hideHotkey;
    public List<Buff> buffs = new ArrayList<>();
    // 介面語言：zh_TW | zh_CN (null 代表依系統語言)
    public String language;

    /** 深拷貝：啟動腳本時交給引擎的快照，避免執行中被 UI 修改 */
    public Settings copy() {
        return COPIER.fromJson(COPIER.toJson(this), Settings.class);
    }

    /** 修補 JSON 缺漏造成的 null */
    public void normalize() {
        if (windowBounds == null) windowBounds = new WindowBounds();
        if (buffs == null) buffs = new ArrayList<>();
        if (hotkeyMode == null) hotkeyMode = HOTKEY_LISTEN;
        if (opacity < 0.3 || opacity > 1) opacity = 1;
    }
}
