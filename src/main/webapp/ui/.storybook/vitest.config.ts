import path from "node:path";
import { fileURLToPath } from "node:url";
import { storybookTest } from "@storybook/addon-vitest/vitest-plugin";
import { playwright } from "@vitest/browser-playwright";
import { defineConfig } from "vitest/config";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
type PlaywrightBrowser = "chromium" | "firefox" | "webkit";
// Same VITEST_BROWSERS parsing as vitest.browser.config.ts; Chromium stays the default here.
const browsers = (process.env.VITEST_BROWSERS ?? "chromium")
  .split(",")
  .map((name) => name.trim())
  .filter(Boolean) as PlaywrightBrowser[];

export default defineConfig({
  plugins: [storybookTest({ configDir: __dirname })],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "../src"),
    },
  },
  optimizeDeps: {
    // Storybook imports React DOM from pnpm's global store outside the project tree.
    include: ["react-dom"],
  },
  test: {
    name: "storybook",
    globals: true,
    browser: {
      enabled: true,
      provider: playwright(),
      headless: true,
      screenshotFailures: false,
      instances: browsers.map((browser) => ({ browser })),
    },
  },
});
