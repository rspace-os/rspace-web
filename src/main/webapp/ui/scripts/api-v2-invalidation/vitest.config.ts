import { defineConfig } from "vitest/config";
export default defineConfig({ test: { environment: "node", include: ["scripts/api-v2-invalidation/*.check.ts"] } });
