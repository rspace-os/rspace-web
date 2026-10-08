import path from "node:path";
import { fileURLToPath } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig, type Plugin } from "vite";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

/**
 * `@storybook/tanstack-react` forces `@tanstack/react-store` and
 * `use-sync-external-store/shim/with-selector` into `optimizeDeps.include` so that the
 * CJS-only shim gets interop-converted before `react-store` does a named import from it.
 * It lists them as bare specifiers, which assumes a hoisted `node_modules`. Both are
 * transitive-only here, so under pnpm's strict layout Vite cannot resolve them from the
 * project root and skips them with a "Failed to resolve dependency" warning.
 *
 * Rewriting them to the nested `parent > child` form resolves them through the package
 * that actually depends on them, so they stay pre-bundled without declaring transitive
 * dependencies directly.
 */
export const nestTransitiveOptimizeDeps = (): Plugin => ({
  name: "rspace:nest-transitive-optimize-deps",
  configResolved(config) {
    const nested = (config.optimizeDeps.include ?? [])
      .filter((dep) => dep !== "@tanstack/react-store")
      .map((dep) =>
        dep === "use-sync-external-store/shim/with-selector"
          ? `@tanstack/react-router > @tanstack/react-store > ${dep}`
          : dep,
      );
    config.optimizeDeps.include = nested;
  },
});

export default defineConfig(({ command }) => ({
  // Keep built asset URLs rooted at the RSpace development route; local Storybook uses /.
  base: command === "build" ? "/public/storybook/" : "/",
  plugins: [tailwindcss(), nestTransitiveOptimizeDeps()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "../src"),
    },
  },
}));
