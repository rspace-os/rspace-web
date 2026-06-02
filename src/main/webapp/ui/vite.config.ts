import path from "node:path";
import { fileURLToPath } from "node:url";
import bundleEntries from "./bundleEntries.json";
import { defineConfig } from "vitest/config";
import type { Alias, PluginOption, UserConfig } from "vite";
import react from "@vitejs/plugin-react";
import { nodePolyfills } from "vite-plugin-node-polyfills";
import browserslist from "browserslist";
import browserslistToEsbuild from "browserslist-to-esbuild";
import { browserslistToTargets } from "lightningcss";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const resolveFromRoot = (relativePath: string) =>
  path.resolve(__dirname, relativePath);

const esbuildTargets = browserslistToEsbuild();
const lightningCssTargets = browserslistToTargets(browserslist());

const shouldGenerateBuildStats = process.env.FRONTEND_BUILD_STATS === "true";
const devServerHost = process.env.VITE_DEV_SERVER_HOST ?? "127.0.0.1";
const devServerPort = Number(process.env.VITE_DEV_SERVER_PORT ?? "5173");

const vitestAliases: Alias[] = [
  {
    find: /^@mui\/x-data-grid$/,
    replacement: resolveFromRoot("src/test-stubs/MuiDataGridStub.tsx"),
  },
  {
    find: /^.+\.css$/,
    replacement: resolveFromRoot("src/test-stubs/CSSStub.js"),
  },
  {
    find: /^.+\.(jpg|png)$/,
    replacement: resolveFromRoot("src/test-stubs/ImageStub.js"),
  },
  {
    find: /^.+\.svg$/,
    replacement: resolveFromRoot("src/test-stubs/SVGStub.js"),
  },
  {
    find: /^react-photoswipe-gallery$/,
    replacement: resolveFromRoot("src/test-stubs/PhotoswipeStub.js"),
  },
];

const resolvedBundleEntries = Object.fromEntries(
  Object.entries(bundleEntries).map(([name, relativePath]) => [
    name,
    resolveFromRoot(relativePath),
  ]),
) satisfies Record<string, string>;

export default defineConfig(async ({ mode }) => {
  const isVitest = mode === "test" || process.env.VITEST === "true";

  const plugins: PluginOption[] = [
    react(),
    nodePolyfills({
      globals: { process: true, Buffer: true, global: true },
      protocolImports: false,
    }),
  ];

  if (shouldGenerateBuildStats) {
    const { visualizer } = await import("rollup-plugin-visualizer");
    const { analyzer } = await import("vite-bundle-analyzer");

    plugins.push(
      visualizer({
        filename: "stats.html",
        gzipSize: true,
        brotliSize: true,
        sourcemap: true,
      }),
      // Emits stats.json consumed by wojtekmaj/vite-compare-bundle-size in CI.
      analyzer({
        analyzerMode: "json",
        fileName: "stats",
      }),
    );
  }

  const config: UserConfig = {
    base: "/ui/dist/",
    define: {
      global: "globalThis",
    },
    plugins,
    resolve: {
      tsconfigPaths: true,
      alias: isVitest
        ? [
            { find: /^@\//, replacement: `${resolveFromRoot("src")}/` },
            ...vitestAliases,
          ]
        : [],
      ...(isVitest ? { externalConditions: ["require"] } : {}),
    },
    // HTTP requests for /ui/dist/* are reverse-proxied by Jetty (see
    // ViteDevServerProxyServlet), so the browser only sees same-origin URLs
    // and CORS does not apply. The HMR WebSocket is not proxied — clientPort
    // routes the browser directly to this dev server's port.
    server: {
      host: devServerHost,
      port: devServerPort,
      strictPort: true,
      hmr: {
        host: devServerHost,
        port: devServerPort,
        clientPort: devServerPort,
      },
    },
    build: {
      outDir: "dist",
      manifest: true,
      sourcemap: true,
      rolldownOptions: {
        input: resolvedBundleEntries,
        output: {
          entryFileNames: "[name]-[hash].js",
          chunkFileNames: "chunks/[name]-[hash].js",
          assetFileNames: "assets/[name]-[hash][extname]",
        },
      },
      target: esbuildTargets as NonNullable<UserConfig["build"]>["target"],
      cssTarget: esbuildTargets as NonNullable<UserConfig["build"]>["cssTarget"],
    },
    css: {
      transformer: "lightningcss",
      lightningcss: {
        targets: lightningCssTargets,
      },
    },
    test: {
      environment: "jsdom",
      setupFiles: ["./src/__tests__/setup.ts"],
      include: ["**/?*.test.(js|cjs|mjs|jsx|ts|tsx)"],
      testTimeout: 20000,
      reporters: ["default", "junit"],
      outputFile: {
        junit: "./junit.xml",
      },
    },
  };
  return config;
});
