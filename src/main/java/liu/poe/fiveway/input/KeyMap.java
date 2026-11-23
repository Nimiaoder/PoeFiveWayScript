package liu.poe.fiveway.input;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/** UI 顯示名稱 → java.awt.event.KeyEvent 的 VK 碼 */
final class KeyMap {
    private static final Map<String, Integer> NAME_TO_VK = new HashMap<>();

    static {
        put("Ctrl", KeyEvent.VK_CONTROL);
        put("Shift", KeyEvent.VK_SHIFT);
        put("Alt", KeyEvent.VK_ALT);
        put("Space", KeyEvent.VK_SPACE);
        put("Tab", KeyEvent.VK_TAB);
        put("Enter", KeyEvent.VK_ENTER);
        put("Backspace", KeyEvent.VK_BACK_SPACE);
        put("Esc", KeyEvent.VK_ESCAPE);
        for (int i = 1; i <= 12; i++) {
            put("F" + i, KeyEvent.VK_F1 + (i - 1));
        }
        put("`", KeyEvent.VK_BACK_QUOTE);
        put("-", KeyEvent.VK_MINUS);
        put("=", KeyEvent.VK_EQUALS);
        put("[", KeyEvent.VK_OPEN_BRACKET);
        put("]", KeyEvent.VK_CLOSE_BRACKET);
        put("\\", KeyEvent.VK_BACK_SLASH);
        put(";", KeyEvent.VK_SEMICOLON);
        put("'", KeyEvent.VK_QUOTE);
        put(",", KeyEvent.VK_COMMA);
        put(".", KeyEvent.VK_PERIOD);
        put("/", KeyEvent.VK_SLASH);
        // 導覽 / 編輯
        put("Home", KeyEvent.VK_HOME);
        put("End", KeyEvent.VK_END);
        put("PageUp", KeyEvent.VK_PAGE_UP);
        put("PageDown", KeyEvent.VK_PAGE_DOWN);
        put("Insert", KeyEvent.VK_INSERT);
        put("Delete", KeyEvent.VK_DELETE);
        put("Up", KeyEvent.VK_UP);
        put("Down", KeyEvent.VK_DOWN);
        put("Left", KeyEvent.VK_LEFT);
        put("Right", KeyEvent.VK_RIGHT);
        // 鎖定鍵 / 其他
        put("CapsLock", KeyEvent.VK_CAPS_LOCK);
        put("NumLock", KeyEvent.VK_NUM_LOCK);
        put("ScrollLock", KeyEvent.VK_SCROLL_LOCK);
        put("PrintScreen", KeyEvent.VK_PRINTSCREEN);
        put("Menu", KeyEvent.VK_CONTEXT_MENU);
        // 數字鍵盤
        for (int i = 0; i <= 9; i++) {
            put("Num" + i, KeyEvent.VK_NUMPAD0 + i);
        }
        put("Num-", KeyEvent.VK_SUBTRACT);
        put("Num+", KeyEvent.VK_ADD);
        put("Num*", KeyEvent.VK_MULTIPLY);
        put("Num/", KeyEvent.VK_DIVIDE);
        put("Num.", KeyEvent.VK_DECIMAL);
        put("NumEnter", KeyEvent.VK_ENTER);
    }

    private KeyMap() {
    }

    private static void put(String name, int vk) {
        NAME_TO_VK.put(name, vk);
    }

    /** 回傳 VK 碼；無法對應回傳 -1 (該鍵會被略過，與原版 toNutKey 回傳 null 相同) */
    static int toVk(String name) {
        if (name == null || name.isEmpty()) return -1;
        Integer vk = NAME_TO_VK.get(name);
        if (vk != null) return vk;
        if (name.length() == 1) {
            char c = name.charAt(0);
            if (c >= 'A' && c <= 'Z') return c;   // VK_A..VK_Z 等於大寫字母 ASCII
            if (c >= '0' && c <= '9') return c;   // VK_0..VK_9 等於數字 ASCII
        }
        return -1;
    }
}
