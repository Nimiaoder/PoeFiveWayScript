package liu.poe.fiveway.hotkey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全域鍵盤事件的 keycode → 顯示名稱。
 * JNativeHook 與原版 uiohook 使用相同的 keycode 編碼 (擴充鍵為 0xE000 系列)，對照表沿用原版。
 */
public final class KeyNames {

    public static final String CTRL = "Ctrl";
    public static final String SHIFT = "Shift";
    public static final String ALT = "Alt";
    public static final Set<String> MODIFIERS = Set.of(CTRL, SHIFT, ALT);
    /** 組合鍵顯示順序：Ctrl, Shift, Alt, 其它 */
    public static final List<String> MODIFIER_ORDER = List.of(CTRL, SHIFT, ALT);

    private static final Map<Integer, String> RAW_TO_NAME = new HashMap<>();

    static {
        // 字母
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int[] letterCodes = {30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50, 49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44};
        for (int i = 0; i < letters.length(); i++) {
            RAW_TO_NAME.put(letterCodes[i], String.valueOf(letters.charAt(i)));
        }
        // 數字 (上排)
        int[] digitCodes = {11, 2, 3, 4, 5, 6, 7, 8, 9, 10}; // 0..9
        for (int i = 0; i < digitCodes.length; i++) {
            RAW_TO_NAME.put(digitCodes[i], String.valueOf(i));
        }
        // 功能鍵
        int[] fCodes = {59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 87, 88};
        for (int i = 0; i < fCodes.length; i++) {
            RAW_TO_NAME.put(fCodes[i], "F" + (i + 1));
        }
        // 特殊
        put(57, "Space");
        put(15, "Tab");
        put(28, "Enter");
        put(14, "Backspace");
        put(1, "Esc");
        put(41, "`");
        put(12, "-");
        put(13, "=");
        put(26, "[");
        put(27, "]");
        put(43, "\\");
        put(39, ";");
        put(40, "'");
        put(51, ",");
        put(52, ".");
        put(53, "/");
        // 導覽 / 編輯
        put(3655, "Home");
        put(3663, "End");
        put(3657, "PageUp");
        put(3665, "PageDown");
        put(3666, "Insert");
        put(3667, "Delete");
        put(57416, "Up");
        put(57424, "Down");
        put(57419, "Left");
        put(57421, "Right");
        // 鎖定鍵
        put(58, "CapsLock");
        put(69, "NumLock");
        put(70, "ScrollLock");
        // 數字鍵盤
        put(71, "Num7");
        put(72, "Num8");
        put(73, "Num9");
        put(74, "Num-");
        put(75, "Num4");
        put(76, "Num5");
        put(77, "Num6");
        put(78, "Num+");
        put(79, "Num1");
        put(80, "Num2");
        put(81, "Num3");
        put(82, "Num0");
        put(83, "Num.");
        put(55, "Num*");
        put(3637, "Num/");
        put(3612, "NumEnter");
        // 其他
        put(3639, "PrintScreen");
        put(3677, "Menu");
    }

    private KeyNames() {
    }

    private static void put(int code, String name) {
        RAW_TO_NAME.put(code, name);
    }

    /** LCtrl 29, RCtrl 3613, LShift 42, RShift 54, LAlt 56, RAlt 3640 */
    public static String modifierName(int keycode) {
        return switch (keycode) {
            case 29, 3613 -> CTRL;
            case 42, 54 -> SHIFT;
            case 56, 3640 -> ALT;
            default -> null;
        };
    }

    public static String keyName(int keycode) {
        String mod = modifierName(keycode);
        if (mod != null) return mod;
        String n = RAW_TO_NAME.get(keycode);
        return n != null ? n : "Key(" + keycode + ")";
    }
}
