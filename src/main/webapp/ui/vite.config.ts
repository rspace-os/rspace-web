import fs from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import browserslist from "browserslist";
import browserslistToEsbuild from "browserslist-to-esbuild";
import { browserslistToTargets } from "lightningcss";
import type { Alias, Plugin, PluginOption, UserConfig } from "vite";
import { normalizePath } from "vite";
import { defineConfig } from "vitest/config";
import bundleEntries from "./bundleEntries.json";
import { flattenMessages } from "./src/modules/common/i18n/flattenMessages";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Module ids always use forward slashes, so paths compared against one have to be normalised or
// nothing matches on Windows, where `path.resolve` returns backslashes.
const resolveFromRoot = (relativePath: string) => normalizePath(path.resolve(__dirname, relativePath));

const legacyI18nEntryPath = resolveFromRoot("src/modules/common/i18n/legacyI18n.ts");
const listFormatPath = resolveFromRoot("src/modules/common/i18n/listFormat.ts");
const localesPath = resolveFromRoot("src/modules/common/i18n/locales");
const intlMessageFormatBundlePath = path.join(
  path.dirname(createRequire(import.meta.url).resolve("intl-messageformat")),
  "intl-messageformat.iife.js",
);

const legacyCatalogueFileName = (locale: string) => `legacyMessages.${locale}.js`;

/*
 * Emits the legacy message catalogue as one static file per locale, assigning `window.RS.i18n`.
 * Kept out of the `legacyI18n` chunk so the messages, which change on any copy edit, cache
 * separately from the formatting code, which does not. They land under `/ui/dist/`, already served
 * anonymously with a JavaScript MIME type, so nothing serves them at runtime.
 */
function legacyMessages(): Plugin {
  const catalogues = fs
    .readdirSync(localesPath, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && fs.existsSync(path.join(localesPath, entry.name, "server.legacyJs.json")))
    .map((entry) => {
      const messages = flattenMessages(
        JSON.parse(fs.readFileSync(path.join(localesPath, entry.name, "server.legacyJs.json"), "utf8")),
      );
      return {
        locale: entry.name,
        fileName: legacyCatalogueFileName(entry.name),
        source: `window.RS = window.RS || {};\nwindow.RS.i18n = ${JSON.stringify(messages)};\n`,
      };
    });

  if (catalogues.length === 0) {
    throw new Error(`No server.legacyJs.json found under ${localesPath}; RS.msg would resolve nothing.`);
  }

  return {
    name: "rspace:legacy-messages",
    generateBundle() {
      for (const { fileName, source } of catalogues) {
        this.emitFile({ type: "asset", fileName, source });
      }
    },
    // `generateBundle` never runs under `vite dev`, so serve the same bytes from memory there.
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const requested = req.url?.split("?")[0] ?? "";
        const catalogue = catalogues.find(({ fileName }) => requested.endsWith(`/${fileName}`));
        if (!catalogue) {
          next();
          return;
        }
        res.setHeader("Content-Type", "text/javascript;charset=utf-8");
        res.end(catalogue.source);
      });
    },
  };
}

function legacyI18n(): Plugin {
  const formatter = fs.readFileSync(intlMessageFormatBundlePath, "utf8");
  // Rendered as a blocking classic <script src>, so any top-level import/export left in the
  // chunk is a SyntaxError that leaves `RS.msg` undefined on every page. `listFormat` keeps its
  // own exports: thirteen other modules import it.
  const inlinedModules = [
    {
      importLine: /^import \{ formatList as formatLocalizedList \} from "\.\/listFormat";$/m,
      path: listFormatPath,
      epilogue: "const formatLocalizedList = formatList;",
    },
  ];
  const topLevelEsm = /^\s*(?:import|export)\b/m;
  return {
    name: "rspace:legacy-i18n",
    // The stripping below has to run after `vite:esbuild`, which erases the type-only imports
    // and then appends its own `export {}` to keep the file a module.
    enforce: "post",
    load(id) {
      if (id.split("?")[0] === legacyI18nEntryPath) {
        let entry = fs.readFileSync(legacyI18nEntryPath, "utf8");
        for (const { importLine, path: modulePath, epilogue } of inlinedModules) {
          if (!importLine.test(entry)) {
            this.error(
              `legacyI18n.ts no longer matches the expected import of ${path.basename(modulePath)}. Inline its ` +
                "replacement or the entry will emit an ESM import and break as a classic script.",
            );
          }
          entry = entry.replace(importLine, `${fs.readFileSync(modulePath, "utf8")}\n${epilogue}`);
        }
        return `${formatter}\nglobalThis.RSpaceIntlMessageFormat = IntlMessageFormat;\n${entry}`;
      }
    },
    transform(code, id) {
      if (id.split("?")[0] !== legacyI18nEntryPath) {
        return null;
      }
      // Drops the inlined modules' `export` keywords, the trailing re-export block FormatJS's
      // "iife" bundle ends with despite its name, and esbuild's `export {}` marker.
      const script = code
        .replace(/^export \{[^}]*\};?$/gm, "")
        .replace(/^export (?=(?:const|let|var|function|class|async)\b)/gm, "");
      // `renderChunk` and `generateBundle` never run under `vite dev`, so this is the HMR
      // path's only guard.
      if (topLevelEsm.test(script)) {
        this.error(
          "The legacyI18n entry still has a top-level import/export, so it would throw a " +
            `SyntaxError as a classic script: ${topLevelEsm.exec(script)?.[0].trim()}`,
        );
      }
      return { code: script, map: null };
    },
    renderChunk(code, chunk) {
      if (chunk.name !== "legacyI18n") {
        return null;
      }
      // Minification is free to name a top-level binding `$` (it did, for `formatterCache`),
      // which shadows jQuery and breaks every later `$(...)` on the page. Scoping the chunk
      // avoids that; it communicates through `window.RS` and `globalThis` explicitly.
      return { code: `(function () {\n${code}\n})();\n`, map: null };
    },
    generateBundle(_options, bundle) {
      // Fail the build rather than silently shipping an unloadable classic script.
      for (const chunk of Object.values(bundle)) {
        if (chunk.type !== "chunk" || chunk.name !== "legacyI18n") {
          continue;
        }
        if (chunk.imports.length > 0 || chunk.exports.length > 0) {
          this.error(
            `The legacyI18n entry must be import-free to load as a classic script, but emitted ` +
              `imports [${chunk.imports.join(", ")}] and exports [${chunk.exports.join(", ")}].`,
          );
        }
        // Minification rewrites the wrapper's shape, so match any IIFE opening rather
        // than an exact prefix.
        if (!/^\s*[!(;]*\s*(?:function\s*\(|\(\s*\)\s*=>)/.test(chunk.code)) {
          this.error(
            `The legacyI18n entry must stay wrapped in an IIFE so it leaks no globals onto ` +
              `window, but starts with: ${chunk.code.slice(0, 60)}`,
          );
        }
      }
    },
  };
}

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

  const plugins: PluginOption[] = [react(), legacyI18n(), legacyMessages(), tailwindcss()];

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

  // Some chemistry deps (openchemlib, pulled in lazily by the Ketcher editor)
  // bundle Node's `util` polyfill, which reads bare `process.*`
  // (process.stderr.isTTY, process.nextTick, …) at module-eval time. The
  // browser has no `process`, so the chunk throws "process is not defined" the
  // moment Ketcher loads. Provide a minimal global shim. esbuild/rolldown only
  // substitute *unbound* `process` references, so deps that declare their own
  // local `process` are untouched. The shim carries NODE_ENV so code that reads
  // process.env.NODE_ENV (e.g. React) still sees the right mode.
  const processShim = `{env:{NODE_ENV:${JSON.stringify(
    mode === "production" ? "production" : "development",
  )}},platform:"browser",browser:true,version:"",versions:{},argv:[],nextTick:(cb)=>Promise.resolve().then(cb),cwd:()=>"/",emitWarning:()=>{}}`;

  const config: UserConfig = {
    base: "/ui/dist/",
    define: {
      global: "globalThis",
      process: processShim,
      // Cache-busting token + base URL for the lazily-loaded, self-hosted
      // TinyMCE assets. The base is injected at build time rather than
      // hard-coded so different build targets can use different paths.
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
      ...(useFsPolling ? { watch: { usePolling: true, interval: 200 } } : {}),
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
      coverage: {
        // ponytail: istanbul over the default v8 provider — v8 coverage
        // segfaults during `--merge-reports` on this vitest/vite combo
        // (vitest-dev/vitest#10032, still open); istanbul is confirmed to
        // avoid it. Revisit once that's fixed if istanbul's slower
        // instrumentation becomes a bottleneck.
        provider: "istanbul",
        reporter: ["text", "html", "lcov"],
      },
    },
  };
  return config;
});
