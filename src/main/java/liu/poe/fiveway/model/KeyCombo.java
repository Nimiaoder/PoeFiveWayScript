package liu.poe.fiveway.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 按鍵組合，例如 keys=[Shift, E]、display="Shift+E" */
public class KeyCombo {
    public List<String> keys = new ArrayList<>();
    public String display = "";

    public KeyCombo() {
    }

    public KeyCombo(List<String> keys, String display) {
        this.keys = new ArrayList<>(keys);
        this.display = display;
    }

    public boolean hasKeys() {
        return keys != null && !keys.isEmpty();
    }

    /** log 用：顯示按鍵組合，未設定顯示 (未設定) */
    @Override
    public String toString() {
        return hasKeys() ? (display == null || display.isEmpty() ? String.join("+", keys) : display) : "(未設定)";
    }

    /** 與原版相同：以 display 字串判斷是否為同一組按鍵 */
    public boolean sameAs(KeyCombo other) {
        return other != null && Objects.equals(display, other.display);
    }
}
