import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";

// The API v2 generator scripts load Orval, which loads esbuild at import time. esbuild needs Node's realm,
// so these tests run in the node environment, separately from the jsdom suite (which excludes scripts/).
export default defineConfig({
  resolve: { alias: [{ find: /^@\//, replacement: `${fileURLToPath(new URL("../src", import.meta.url))}/` }] },
  test: {
    environment: "node",
    include: ["scripts/api-v2-invalidation/*.check.ts", "scripts/api-v2-valibot/*.test.ts"],
    testTimeout: 20000,
  },
});
