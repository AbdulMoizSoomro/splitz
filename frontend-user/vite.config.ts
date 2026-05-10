import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: true,
    port: 5173,
    proxy: {
      "/api/user": {
        target: "http://user-service:8080",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/user/, ""),
      },
      "/api/expense": {
        target: "http://expense-service:8081",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/expense/, ""),
      },
    },
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    exclude: ["**/node_modules/**", "**/dist/**", "**/e2e/**"],
  },
});
