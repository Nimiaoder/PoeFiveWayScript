package liu.poe.fiveway.input;

import java.awt.Robot;

/** 共用的 java.awt.Robot (延遲建立，建立失敗時回傳 null 並僅印出錯誤) */
final class RobotHolder {
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
                System.err.println("Robot 建立失敗: " + e.getMessage());
            }
        }
        return robot;
    }
}
