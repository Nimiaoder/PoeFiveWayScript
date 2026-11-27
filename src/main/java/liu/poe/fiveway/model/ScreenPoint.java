package liu.poe.fiveway.model;

/** 絕對螢幕座標 (雙人刷新模式 / 自動控制鼠標位置 用) */
public class ScreenPoint {
    public int x;
    public int y;

    public ScreenPoint() {
    }

    public ScreenPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /** log 用：(x, y) */
    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
