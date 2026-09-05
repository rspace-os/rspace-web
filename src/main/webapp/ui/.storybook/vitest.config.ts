import path from "node:path";
import { fileURLToPath } from "node:url";
import { storybookTest } from "@storybook/addon-vitest/vitest-plugin";
import { playwright } from "@vitest/browser-playwright";
import { defineConfig } from "vitest/config";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default defineConfig({
  plugins: [storybookTest({ configDir: __dirname })],
  define: { global: "globalThis" },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "../src"),
    },
  },
  test: {
    name: "storybook",
    globals: true,
    browser: {
      enabled: true,
      provider: playwright(),
      headless: true,
      instances: [{ browser: "chromium" }],
      // Vitest defaults to a phone-sized viewport; the Inventory chrome stories
      // need the desktop two-column layout and persistent sidebar.
      viewport: { width: 1280, height: 800 },
    },
  },
});
