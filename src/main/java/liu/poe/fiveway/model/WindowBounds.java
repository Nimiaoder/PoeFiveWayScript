package liu.poe.fiveway.model;

import liu.poe.fiveway.config.AppConfig;

/** 視窗大小與位置 (x / y 為 null 代表交由系統置中) */
public class WindowBounds {
    public double width = AppConfig.DEFAULT_WIDTH;
    public double height = AppConfig.DEFAULT_HEIGHT;
    public Double x;
    public Double y;
}
