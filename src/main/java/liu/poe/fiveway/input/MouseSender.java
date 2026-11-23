package liu.poe.fiveway.input;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import liu.poe.fiveway.model.ScreenPoint;

import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;

/** 滑鼠模擬與座標擷取 (座標皆為絕對螢幕座標，支援多螢幕) */
public final class MouseSender {
    private static final Logger log = LoggerFactory.getLogger(MouseSender.class);

    /** 右鍵按下到放開的間隔 (毫秒) */
    private static final int CLICK_HOLD_MS = 20;

    /** 移動到絕對螢幕座標 */
    public void moveAbsolute(ScreenPoint p) {
        Robot robot = RobotHolder.get();
        if (robot == null || p == null) return;
        try {
            robot.mouseMove(p.x, p.y);
        } catch (Exception e) {
            log.error("[dual] 移動鼠標失敗", e);
        }
    }

    /**
     * 點右鍵以「聚焦(focus)」遊戲視窗。
     * 之所以用右鍵而非左鍵：左鍵在 POE 會讓角色移動，右鍵通常綁定技能/無位移。
     */
    public void rightClick() {
        Robot robot = RobotHolder.get();
        if (robot == null) return;
        try {
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.delay(CLICK_HOLD_MS);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        } catch (Exception e) {
            log.error("[dual] 右鍵點擊失敗", e);
        }
    }

    /** 目前滑鼠的絕對螢幕座標 */
    public static ScreenPoint currentPosition() {
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) return null;
        Point p = info.getLocation();
        return new ScreenPoint(p.x, p.y);
    }
}
