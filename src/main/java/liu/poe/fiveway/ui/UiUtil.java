package liu.poe.fiveway.ui;

import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** UI 小工具 */
public final class UiUtil {

    private UiUtil() {
    }

    /** 顯示 / 隱藏節點 (隱藏時不佔版面) */
    public static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** 空字串或非數字視為 fallback */
    public static int parseInt(String s, int fallback) {
        if (s == null || s.isEmpty() || "-".equals(s)) return fallback;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 數字輸入框：只允許輸入數字，輸入即時寫回設定；失焦時以實際值重新顯示。
     * 空白或 0 視為 emptyValue (等同原版的 Number(x) || emptyValue：一般欄位為 0，Buff 間隔為 1)。
     */
    public static void bindInt(TextField tf, IntSupplier getter, IntConsumer setter,
                               boolean allowNegative, int emptyValue) {
        String regex = allowNegative ? "-?\\d{0,9}" : "\\d{0,9}";
        tf.setTextFormatter(new TextFormatter<String>(c -> c.getControlNewText().matches(regex) ? c : null));
        tf.textProperty().addListener((o, ov, nv) -> {
            int v = parseInt(nv, emptyValue);
            if (v == 0) v = emptyValue;
            if (v != getter.getAsInt()) setter.accept(v);
        });
        tf.focusedProperty().addListener((o, was, now) -> {
            if (!now) tf.setText(String.valueOf(getter.getAsInt()));
        });
    }

    /** 由設定值同步到輸入框 (使用者正在輸入且數值相同時，不覆蓋其輸入中的文字) */
    public static void syncInt(TextField tf, int value, int emptyValue) {
        if (tf.isFocused()) {
            int cur = parseInt(tf.getText(), emptyValue);
            if (cur == 0) cur = emptyValue;
            if (cur == value) return;
        }
        String s = String.valueOf(value);
        if (!s.equals(tf.getText())) tf.setText(s);
    }
}
