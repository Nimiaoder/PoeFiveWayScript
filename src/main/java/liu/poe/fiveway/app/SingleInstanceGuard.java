package liu.poe.fiveway.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 單一實例鎖定：防止程式被重複開啟。
 * 以本機通訊埠作為鎖；第二個程式啟動時會通知第一個程式 (觸發 onSecondInstance) 後自行結束。
 */
public final class SingleInstanceGuard {

    private ServerSocket server;

    /**
     * @return true 代表本程式是第一個實例；false 代表已有實例在執行 (已通知它)
     */
    public boolean tryAcquire(int port, Runnable onSecondInstance) {
        try {
            server = new ServerSocket(port, 5, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            notifyExisting(port);
            return false;
        }
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try (Socket s = server.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
                    in.readLine();
                    onSecondInstance.run();
                } catch (IOException ignored) {
                    // 伺服器關閉或連線錯誤
                }
            }
        }, "single-instance");
        t.setDaemon(true);
        t.start();
        return true;
    }

    private void notifyExisting(int port) {
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), port)) {
            OutputStream out = s.getOutputStream();
            out.write("SHOW\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException ignored) {
            // 無法通知就直接結束
        }
    }

    public void close() {
        try {
            if (server != null) server.close();
        } catch (IOException ignored) {
            // 略過
        }
    }
}
