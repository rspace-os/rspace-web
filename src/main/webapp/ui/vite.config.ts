import fs from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import browserslist from "browserslist";
import browserslistToEsbuild from "browserslist-to-esbuild";
import { browserslistToTargets } from "lightningcss";
import type { Alias, Plugin, PluginOption, UserConfig } from "vite";
import { nodePolyfills } from "vite-plugin-node-polyfills";
import { defineConfig } from "vitest/config";
import bundleEntries from "./bundleEntries.json";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const resolveFromRoot = (relativePath: string) => path.resolve(__dirname, relativePath);

/*
 * Serves the self-hosted TinyMCE 8 build as static files under
 * `<base>/tinymce/`, so the Inventory editor can lazy-load it at runtime via
 * @tinymce/tinymce-react's `tinymceScriptSrc` (see StyledTinyMceEditor.tsx).
 * TinyMCE then derives its base URL from that script and lazy-loads its model,
 * theme, icons, skin and plugins from the same directory.
 *
 * This avoids bundling TinyMCE's resources as side-effect imports (which did
 * not reliably register the `dom` model under the bundler, leaving TinyMCE
 * fetching `models/dom/model.js` from a wrong base URL) and avoids running
 * TinyMCE's minified skin CSS through lightningcss (which rejects its
 * `:nth-child(2of...)` selector). The files are served verbatim.
 */
const TINYMCE_URL_SEGMENT = "tinymce";
const TINYMCE_MIME: Record<string, string> = {
  ".js": "text/javascript",
  ".mjs": "text/javascript",
  ".css": "text/css",
  ".svg": "image/svg+xml",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".json": "application/json",
  ".html": "text/html",
};
// Subset of the package needed at runtime (omit TS/source files from dist).
const TINYMCE_RUNTIME_ENTRIES = ["tinymce.min.js", "models", "themes", "icons", "skins", "plugins"];

// Resolve the installed TinyMCE package and read its version. The version is
// the cache-busting token for the lazily-loaded TinyMCE assets (see the
// `define` of __TINYMCE_VERSION__ below and StyledTinyMceEditor.tsx): a new
// TinyMCE release changes the `?v=` suffix and invalidates browser/proxy
// caches, matching the `?v=<token>` convention RSpace uses elsewhere
// (com.axiope.webapp.taglib.AssetUrlTag).
const tinymceDir = path.dirname(createRequire(import.meta.url).resolve("tinymce/package.json"));
const tinymceVersion = (
  JSON.parse(fs.readFileSync(path.join(tinymceDir, "package.json"), "utf8")) as {
    version: string;
  }
).version;

function tinymceAssets(base: string): Plugin {
  // `base` always has a trailing slash in Vite, e.g. "/ui/dist/". Depending on
  // middleware ordering Vite may or may not have stripped the base from
  // req.url, so accept both the based and unbased forms.
  const prefixes = [`${base}${TINYMCE_URL_SEGMENT}/`, `/${TINYMCE_URL_SEGMENT}/`];
  return {
    name: "rspace:tinymce-assets",
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const pathname = (req.url ?? "").split("?")[0];
        const matched = prefixes.find((p) => pathname.startsWith(p));
        if (!matched) return next();
        const rel = decodeURIComponent(pathname.slice(matched.length));
        const filePath = path.normalize(path.join(tinymceDir, rel));
        if (!filePath.startsWith(tinymceDir) || !fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
          return next();
        }
        res.setHeader("Content-Type", TINYMCE_MIME[path.extname(filePath)] ?? "application/octet-stream");
        // Keep dev fresh; production cache-busting is handled by the `?v=`
        // version suffix on the asset URLs (see __TINYMCE_VERSION__).
        res.setHeader("Cache-Control", "no-cache");
        fs.createReadStream(filePath).pipe(res);
      });
    },
    closeBundle() {
      const dest = resolveFromRoot(`dist/${TINYMCE_URL_SEGMENT}`);
      for (const entry of TINYMCE_RUNTIME_ENTRIES) {
        const from = path.join(tinymceDir, entry);
        if (fs.existsSync(from)) {
          fs.cpSync(from, path.join(dest, entry), { recursive: true });
        }
      }
    },
  };
}

const esbuildTargets = browserslistToEsbuild();
const lightningCssTargets = browserslistToTargets(browserslist());

const shouldGenerateBuildStats = process.env.FRONTEND_BUILD_STATS === "true";
const devServerHost = process.env.VITE_DEV_SERVER_HOST ?? "127.0.0.1";
const devServerPort = Number(process.env.VITE_DEV_SERVER_PORT ?? "5173");
// When the dev server runs in a container, it binds 0.0.0.0 internally while the
// browser reaches HMR via a published host port. These let the browser-facing
// HMR host/port differ from the bind host/port; both default to the bind values
// so local (non-container) dev is unchanged. VITE_USE_POLLING enables polling-
// based file watching, which is needed for HMR over bind mounts (macOS/Windows
// Docker), where native filesystem events are not delivered.
const hmrHost = process.env.VITE_HMR_HOST ?? devServerHost;
const hmrClientPort = Number(process.env.VITE_HMR_CLIENT_PORT ?? devServerPort);
const useFsPolling = process.env.VITE_USE_POLLING === "true";

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
  Object.entries(bundleEntries).map(([name, relativePath]) => [name, resolveFromRoot(relativePath)]),
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

  if (!isVitest) {
    plugins.push(tinymceAssets("/ui/dist/"));
  }

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
      // Cache-busting token + base URL for the lazily-loaded, self-hosted
      // TinyMCE assets. The base differs between the app build ("/ui/dist/")
      // and the Playwright component-test build ("/", see
      // playwright-ct.config.ts), so it is injected rather than hard-coded.
      __TINYMCE_VERSION__: JSON.stringify(tinymceVersion),
      // Full directory URL the TinyMCE assets are served from (the
      // rspace:tinymce-assets plugin serves /ui/dist/tinymce/*).
      __TINYMCE_BASE__: JSON.stringify("/ui/dist/tinymce/"),
    },
    plugins,
    resolve: {
      tsconfigPaths: true,
      alias: isVitest ? [{ find: /^@\//, replacement: `${resolveFromRoot("src")}/` }, ...vitestAliases] : [],
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
        host: hmrHost,
        port: devServerPort,
        clientPort: hmrClientPort,
      },
      ...(useFsPolling
        ? { watch: { usePolling: true, interval: 200 } }
        : {}),
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
          // Required by the codeSplitting workaround below.
          strictExecutionOrder: true,
          codeSplitting: {
            groups: [
              /*
               * Workaround for the Ketcher circular-chunk crash that
               * rolldown 1.0.1 (pinned in pnpm-workspace.yaml) produces:
               * without this group, rolldown splits Ketcher into two chunks
               * that import each other circularly, each calling the other's
               * __commonJS factory (lodash / regenerator-runtime) before that
               * chunk has evaluated, crashing as "TypeError: undefined is not
               * a function" the moment Ketcher is opened in production.
               * (https://github.com/rolldown/rolldown/issues/9502 tracks the
               * root cause in rolldown 1.0.2+; 1.0.1 has the same splitting
               * bug from a different code path.)
               *
               * This group forces Ketcher and its CJS-only dependencies into
               * one chunk family, removing the circular edge. entriesAware is
               * required: without it rolldown's recursive dependency capture
               * pulls React and emotion into the group, producing a 25 MB
               * chunk eagerly loaded by every page. includeDependenciesRecursively:false
               * was tried as an alternative but only relocated the crash.
               *
               * Remove this group (and the rolldown override in
               * pnpm-workspace.yaml) once rolldown ships a release that fixes
               * the init_* missing-import bug (rolldown#9502 / vite#22499).
               */
              {
                name: "ketcher",
                test: /node_modules[\\/](ketcher-(core|react|standalone)|miew|lodash|regenerator-runtime)[\\/]/,
                entriesAware: true,
              },
            ],
          },
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
