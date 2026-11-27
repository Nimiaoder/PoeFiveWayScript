package liu.poe.fiveway.input;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Robot;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按鍵模擬：KeyDown / KeyUp (java.awt.Robot)。
 * 提供 pressKey / releaseKey 兩個 API，供 ScriptEngine 組合出短按與長按。
 */
public final class KeySender {
    private static final Logger log = LoggerFactory.getLogger(KeySender.class);

    /** 同一個問題只警告一次，避免刷新迴圈每輪都重複寫 log */
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private void warnOnce(String key, String reason) {
        if (warned.add(key + "|" + reason)) log.warn("[key] 無法送出按鍵 {}：{}", key, reason);
    }

    public void pressKey(List<String> keys) {
        Robot robot = RobotHolder.get();
        if (robot == null) {
            warnOnce("*", "Robot 無法使用，按鍵未送出");
            return;
        }
        if (keys == null) return;
        for (String name : keys) {
            int vk = KeyMap.toVk(name);
            if (vk < 0) {
                warnOnce(name, "找不到對應的虛擬鍵碼");
                continue;
            }
            try {
                robot.keyPress(vk);
            } catch (IllegalArgumentException ignored) {
                // 不支援的鍵，略過
                warnOnce(name, "系統不支援此按鍵");
            }
        }
    }

    public void releaseKey(List<String> keys) {
        Robot robot = RobotHolder.get();
        if (robot == null) {
            warnOnce("*", "Robot 無法使用，按鍵未送出");
            return;
        }
        if (keys == null) return;
        for (String name : keys) {
            int vk = KeyMap.toVk(name);
            if (vk < 0) {
                warnOnce(name, "找不到對應的虛擬鍵碼");
                continue;
            }
            try {
                robot.keyRelease(vk);
            } catch (IllegalArgumentException ignored) {
                // 不支援的鍵，略過
                warnOnce(name, "系統不支援此按鍵");
            }
        }
    }
}
