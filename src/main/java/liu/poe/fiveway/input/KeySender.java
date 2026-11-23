package liu.poe.fiveway.input;

import java.awt.Robot;
import java.util.List;

/**
 * 按鍵模擬：KeyDown / KeyUp (java.awt.Robot)。
 * 提供 pressKey / releaseKey 兩個 API，供 ScriptEngine 組合出短按與長按。
 */
public final class KeySender {

    public void pressKey(List<String> keys) {
        Robot robot = RobotHolder.get();
        if (robot == null || keys == null) return;
        for (String name : keys) {
            int vk = KeyMap.toVk(name);
            if (vk < 0) continue;
            try {
                robot.keyPress(vk);
            } catch (IllegalArgumentException ignored) {
                // 不支援的鍵，略過
            }
        }
    }

    public void releaseKey(List<String> keys) {
        Robot robot = RobotHolder.get();
        if (robot == null || keys == null) return;
        for (String name : keys) {
            int vk = KeyMap.toVk(name);
            if (vk < 0) continue;
            try {
                robot.keyRelease(vk);
            } catch (IllegalArgumentException ignored) {
                // 不支援的鍵，略過
            }
        }
    }
}
