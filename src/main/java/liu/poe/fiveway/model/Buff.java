package liu.poe.fiveway.model;

/** 單一 Buff 設定 */
public class Buff {
    public String id;
    public String name = "";
    public KeyCombo key;
    public int intervalSec = 10;
    public boolean castOnStart;
    public boolean enabled = true;

    /** 可用條件：已啟用且已設定按鍵 */
    public boolean usable() {
        return enabled && key != null && key.hasKeys();
    }
}
