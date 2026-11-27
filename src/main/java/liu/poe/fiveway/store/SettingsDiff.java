package liu.poe.fiveway.store;

import liu.poe.fiveway.model.Buff;
import liu.poe.fiveway.model.Settings;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 比對兩份 {@link Settings}，產生「使用者改了什麼」的人類可讀描述，供 log 使用。
 * <p>
 * 以反射掃描 Settings 的 public 欄位，之後在 Settings 新增欄位時不需要改這裡就會自動被記錄。
 * 視窗位置/大小 (windowBounds) 變動太頻繁，不記錄。
 */
public final class SettingsDiff {

    /** @param noisy true 代表高頻變動 (例如拖曳透明度滑桿)，呼叫端應只記 DEBUG */
    public record Change(String field, String text, boolean noisy) {
    }

    private static final Set<String> SKIP = Set.of("windowBounds");
    private static final Set<String> NOISY = Set.of("opacity");

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("opacity", "視窗透明度"),
            Map.entry("alwaysOnTop", "永遠置頂"),
            Map.entry("toggleHotkey", "啟動/關閉快捷鍵"),
            Map.entry("hotkeyMode", "快捷鍵模式"),
            Map.entry("attackerMode", "自動攻擊"),
            Map.entry("refreshMode", "自動刷新"),
            Map.entry("buffMode", "自動 Buff"),
            Map.entry("refreshDirection", "刷新順序"),
            Map.entry("attackKey", "攻擊鍵"),
            Map.entry("enterKey", "進圈鍵"),
            Map.entry("exitKey", "出圈鍵"),
            Map.entry("enterWaitMs", "進圈後等待(ms)"),
            Map.entry("exitWaitMs", "出圈後等待(ms)"),
            Map.entry("attackResumeDelay", "攻擊恢復延遲(ms)"),
            Map.entry("riftEnabled", "時空裂隙重算"),
            Map.entry("riftKey", "時空裂隙鍵"),
            Map.entry("dualRefreshMode", "雙人刷新模式"),
            Map.entry("dualAttackerPos", "打手視窗座標"),
            Map.entry("dualAuraPos", "光環師視窗座標"),
            Map.entry("dualRightClickFocus", "右鍵聚焦視窗"),
            Map.entry("dualFocusDelayMs", "切窗等待(ms)"),
            Map.entry("mouseControl", "自動控制鼠標位置"),
            Map.entry("mousePos", "鼠標位置座標"),
            Map.entry("hideEnabled", "隱藏畫面快捷鍵啟用"),
            Map.entry("hideHotkey", "隱藏畫面快捷鍵"),
            Map.entry("language", "介面語言"));

    private SettingsDiff() {
    }

    public static List<Change> diff(Settings before, Settings after) {
        List<Change> out = new ArrayList<>();
        for (Field f : Settings.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            String name = f.getName();
            if (SKIP.contains(name)) continue;
            try {
                Object a = f.get(before);
                Object b = f.get(after);
                if ("buffs".equals(name)) {
                    diffBuffs(before.buffs, after.buffs, out);
                    continue;
                }
                String sa = show(a);
                String sb = show(b);
                if (!sa.equals(sb)) {
                    out.add(new Change(name, label(name) + ": " + sa + " → " + sb, NOISY.contains(name)));
                }
            } catch (IllegalAccessException ignored) {
                // 略過無法讀取的欄位
            }
        }
        return out;
    }

    private static void diffBuffs(List<Buff> before, List<Buff> after, List<Change> out) {
        Map<String, Buff> oldMap = byId(before);
        Map<String, Buff> newMap = byId(after);

        for (Map.Entry<String, Buff> e : oldMap.entrySet()) {
            if (!newMap.containsKey(e.getKey())) {
                out.add(new Change("buffs", "Buff 移除：" + describe(e.getValue()), false));
            }
        }
        for (Map.Entry<String, Buff> e : newMap.entrySet()) {
            Buff nb = e.getValue();
            Buff ob = oldMap.get(e.getKey());
            if (ob == null) {
                out.add(new Change("buffs", "Buff 新增：" + describe(nb), false));
                continue;
            }
            List<String> parts = new ArrayList<>();
            if (!Objects.equals(ob.name, nb.name)) parts.add("名稱 " + show(ob.name) + " → " + show(nb.name));
            if (!show(ob.key).equals(show(nb.key))) parts.add("按鍵 " + show(ob.key) + " → " + show(nb.key));
            if (ob.intervalSec != nb.intervalSec) parts.add("間隔 " + ob.intervalSec + " → " + nb.intervalSec + " 秒");
            if (ob.castOnStart != nb.castOnStart) parts.add("啟動立即施放 " + ob.castOnStart + " → " + nb.castOnStart);
            if (ob.enabled != nb.enabled) parts.add("啟用 " + ob.enabled + " → " + nb.enabled);
            if (!parts.isEmpty()) {
                out.add(new Change("buffs", "Buff 修改「" + nb.name + "」：" + String.join("，", parts), false));
            }
        }
    }

    private static Map<String, Buff> byId(List<Buff> list) {
        Map<String, Buff> m = new LinkedHashMap<>();
        if (list != null) {
            for (Buff b : list) m.put(String.valueOf(b.id), b);
        }
        return m;
    }

    private static String describe(Buff b) {
        return "「" + b.name + "」按鍵=" + show(b.key) + "，每 " + b.intervalSec + " 秒"
                + (b.castOnStart ? "，啟動立即施放" : "") + (b.enabled ? "" : "，已停用");
    }

    private static String label(String field) {
        String l = LABELS.get(field);
        return l != null ? l + "(" + field + ")" : field;
    }

    /** null 顯示為 (未設定)；KeyCombo / ScreenPoint 已覆寫 toString */
    private static String show(Object o) {
        return o == null ? "(未設定)" : String.valueOf(o);
    }
}
