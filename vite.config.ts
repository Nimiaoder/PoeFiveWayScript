import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Vite 設定：Electron file:// 載入需要相對路徑
export default defineConfig({
  plugins: [react()],
  base: "./",
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
  server: {
    port: 5173,
  },
});
