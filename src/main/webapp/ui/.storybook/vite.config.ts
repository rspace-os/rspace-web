import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default defineConfig(({ command }) => ({
  // Keep built asset URLs rooted at the RSpace development route; standalone
  // Storybook uses its normal root-relative URLs while running locally.
  base: command === "build" ? "/public/storybook/" : "/",
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "../src"),
    },
  },
}));
