package liu.poe.fiveway.input;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.Robot;

/** 共用的 java.awt.Robot (延遲建立，建立失敗時回傳 null 並僅印出錯誤) */
final class RobotHolder {
    private static final Logger log = LoggerFactory.getLogger(RobotHolder.class);

    private static Robot robot;
    private static boolean failed;

    private RobotHolder() {
    }

    static synchronized Robot get() {
        if (robot == null && !failed) {
            try {
                robot = new Robot();
                robot.setAutoDelay(0);
                robot.setAutoWaitForIdle(false);
            } catch (Exception e) {
                failed = true;
                log.error("Robot 建立失敗", e);
            }
        }
        return robot;
    }
}
