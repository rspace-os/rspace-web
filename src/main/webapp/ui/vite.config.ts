import path from "node:path";
import { fileURLToPath } from "node:url";
import bundleEntries from "./bundleEntries.json";
import { defineConfig, type PluginOption } from "vite";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const resolveFromRoot = (relativePath: string) =>
  path.resolve(__dirname, relativePath);

const shouldGenerateBuildStats = process.env.FRONTEND_BUILD_STATS === "true";
const devServerHost = process.env.VITE_DEV_SERVER_HOST ?? "127.0.0.1";
const devServerPort = Number(process.env.VITE_DEV_SERVER_PORT ?? "5173");

const resolvedBundleEntries = Object.fromEntries(
  Object.entries(bundleEntries).map(([name, relativePath]) => [
    name,
    resolveFromRoot(relativePath),
  ]),
) satisfies Record<string, string>;

export default defineConfig(async ({ mode }) => {
  const { default: react } = await import("@vitejs/plugin-react-swc");
  const useStableFilenames = mode === "development";

  const plugins: PluginOption[] = [
    react(),
  ];

  if (shouldGenerateBuildStats) {
    const { visualizer } = await import("rollup-plugin-visualizer");

    plugins.push(
      visualizer({
        filename: "stats.html",
        gzipSize: true,
        brotliSize: true,
        sourcemap: true,
      }),
    );
  }

  return {
    base: "/ui/dist/",
    define: {
      global: "globalThis",
    },
    plugins,
    resolve: {
      tsconfigPaths: true,
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
          entryFileNames: useStableFilenames ? "[name].js" : "[name]-[hash].js",
          chunkFileNames: useStableFilenames
            ? "chunks/[name].js"
            : "chunks/[name]-[hash].js",
          assetFileNames: useStableFilenames
            ? "assets/[name][extname]"
            : "assets/[name]-[hash][extname]",
        },
      },
    },
  };
});












