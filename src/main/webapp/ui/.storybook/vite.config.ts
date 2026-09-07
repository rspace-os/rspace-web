import path from "node:path";
import { fileURLToPath } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig, type Plugin } from "vite";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const bookingPageOptimizeDeps = [
  "@base-ui/react/tabs",
  "@fortawesome/free-brands-svg-icons/faApple",
  "@fortawesome/free-brands-svg-icons/faGoogle",
  "@fortawesome/react-fontawesome",
];

/**
 * `@storybook/tanstack-react` forces `@tanstack/react-store` and
 * `use-sync-external-store/shim/with-selector` into `optimizeDeps.include` so that the
 * CJS-only shim gets interop-converted before `react-store` does a named import from it.
 * It lists them as bare specifiers, which assumes a hoisted `node_modules`. Both are
 * transitive-only here, so under pnpm's strict layout Vite cannot resolve them from the
 * project root and skips them with a "Failed to resolve dependency" warning.
 *
 * Rewriting them to the nested `parent > child` form resolves them through the package
 * that actually depends on them, so they stay pre-bundled without us having to declare
 * (and then version-track) somebody else's transitive dependencies.
 *
 * Upstream: storybookjs/storybook#35873, vitejs/vite#16293.
 *
 * Exported because `vitest.config.ts` builds its own Vite config for Browser Mode and pulls in
 * the same preset, so it needs the same rewrite.
 */
export const nestTransitiveOptimizeDeps = (): Plugin => ({
  name: "rspace:nest-transitive-optimize-deps",
  configResolved(config) {
    const nested = (config.optimizeDeps.include ?? [])
      // The preset already includes `@tanstack/react-router > @tanstack/react-store`.
      .filter((dep) => dep !== "@tanstack/react-store")
      .map((dep) =>
        dep === "use-sync-external-store/shim/with-selector"
          ? `@tanstack/react-router > @tanstack/react-store > ${dep}`
          : dep,
      );
    config.optimizeDeps.include = [...new Set([...nested, ...bookingPageOptimizeDeps])];
  },
});

export default defineConfig({
  plugins: [tailwindcss(), nestTransitiveOptimizeDeps()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "../src"),
    },
  },
});
