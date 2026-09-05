import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default defineConfig(({ command }) => ({
  // Keep built asset URLs rooted at the RSpace development route; standalone
  // Storybook uses its normal root-relative URLs while running locally.
  base: command === "build" ? "/public/storybook/" : "/",
  // sockjs-client (pulled in by the app bar's notification hook) expects Node's `global`.
  define: { global: "globalThis" },
  optimizeDeps: {
    // TableContainer is only imported by the prototype result-table story;
    // pre-bundle it so Storybook does not reload while interaction tests run.
    include: ["@mui/material/TableContainer"],
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "../src"),
    },
  },
}));
