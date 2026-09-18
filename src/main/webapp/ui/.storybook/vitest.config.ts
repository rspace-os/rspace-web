import path from "node:path";
import { fileURLToPath } from "node:url";
import { storybookTest } from "@storybook/addon-vitest/vitest-plugin";
import { playwright } from "@vitest/browser-playwright";
import { defineConfig } from "vitest/config";
import { browserDefines, sourceAlias } from "../vite.shared.ts";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default defineConfig({
  plugins: [storybookTest({ configDir: __dirname })],
  define: browserDefines("test"),
  resolve: {
    tsconfigPaths: true,
    alias: [sourceAlias],
  },
  test: {
    name: "storybook",
    globals: true,
    browser: {
      enabled: true,
      provider: playwright(),
      headless: true,
      instances: [{ browser: "chromium" }],
    },
  },
});
