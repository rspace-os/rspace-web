const DIST_PATH = "/ui/dist/";
const MANIFEST_PATH = "/ui/dist/.vite/manifest.json";
const ENTRIES_PATH = "/ui/bundleEntries.json";
const VITE_CLIENT_SELECTOR = 'script[type="module"][src*="/ui/dist/@vite/client"]';

const state =
  window.__rsTinyMcePluginBundleLoaderState ??=
  {
    loadedAssets: new Set(),
    manifestPromise: null,
    entriesPromise: null,
  };

const isViteHmrActive = () => Boolean(document.querySelector(VITE_CLIENT_SELECTOR));

const toBundleList = (bundleNames) => {
  if (Array.isArray(bundleNames)) {
    return bundleNames.filter(Boolean);
  }

  if (typeof bundleNames !== "string") {
    return [];
  }

  return bundleNames
    .split(",")
    .map((bundleName) => bundleName.trim())
    .filter(Boolean);
};

const normaliseAssetUrl = (assetPath) => {
  if (!assetPath) {
    return assetPath;
  }

  if (/^(?:[a-z]+:)?\/\//i.test(assetPath) || assetPath.startsWith("/")) {
    return assetPath;
  }

  return DIST_PATH + assetPath.replace(/^\/+/, "");
};

const fetchJson = async (url, label) => {
  const response = await fetch(url, { credentials: "same-origin" });
  if (!response.ok) {
    throw new Error(`Failed to load ${label} from ${url} (status ${response.status})`);
  }

  return response.json();
};

const getManifest = () =>
  (state.manifestPromise ??= fetchJson(MANIFEST_PATH, "Vite chunks manifest"));

const getBundleEntries = () =>
  (state.entriesPromise ??= fetchJson(ENTRIES_PATH, "Vite bundle entries"));

const appendLink = (rel, href) => {
  const key = `link:${rel}:${href}`;
  if (state.loadedAssets.has(key)) {
    return;
  }

  const link = document.createElement("link");
  link.rel = rel;
  link.href = href;
  document.head.appendChild(link);
  state.loadedAssets.add(key);
};

const appendModuleScript = (src) => {
  const key = `script:module:${src}`;
  if (state.loadedAssets.has(key)) {
    return;
  }

  const script = document.createElement("script");
  script.type = "module";
  script.src = src;
  document.head.appendChild(script);
  state.loadedAssets.add(key);
};

const getBundleEntry = (manifest, bundleName) => {
  if (manifest[bundleName]?.isEntry) {
    return manifest[bundleName];
  }

  for (const manifestEntry of Object.values(manifest)) {
    if (manifestEntry?.isEntry && manifestEntry.name === bundleName) {
      return manifestEntry;
    }
  }

  return null;
};

const pushUnique = (target, seen, value) => {
  if (!value || seen.has(value)) {
    return;
  }

  seen.add(value);
  target.push(value);
};

const collectImportedAssets = (
  manifest,
  bundleName,
  manifestEntry,
  styles,
  preloads,
  seenStyles,
  seenPreloads,
  visitedImports,
) => {
  for (const importKey of manifestEntry.imports ?? []) {
    if (visitedImports.has(importKey)) {
      continue;
    }
    visitedImports.add(importKey);

    const importedEntry = manifest[importKey];
    if (!importedEntry) {
      console.warn(
        `TinyMCE Vite bundle ${bundleName} references missing manifest import: ${importKey}`,
      );
      continue;
    }

    pushUnique(preloads, seenPreloads, normaliseAssetUrl(importedEntry.file));
    for (const href of importedEntry.css ?? []) {
      pushUnique(styles, seenStyles, normaliseAssetUrl(href));
    }

    collectImportedAssets(
      manifest,
      bundleName,
      importedEntry,
      styles,
      preloads,
      seenStyles,
      seenPreloads,
      visitedImports,
    );
  }
};

const getBundleAssets = (manifest, bundleName) => {
  const manifestEntry = getBundleEntry(manifest, bundleName);
  if (!manifestEntry) {
    throw new Error(`No Vite bundle manifest entry found for TinyMCE bundle: ${bundleName}`);
  }

  const styles = [];
  const preloads = [];
  const seenStyles = new Set();
  const seenPreloads = new Set();

  for (const href of manifestEntry.css ?? []) {
    pushUnique(styles, seenStyles, normaliseAssetUrl(href));
  }

  collectImportedAssets(
    manifest,
    bundleName,
    manifestEntry,
    styles,
    preloads,
    seenStyles,
    seenPreloads,
    new Set(),
  );

  return {
    styles,
    preloads,
    scripts: [normaliseAssetUrl(manifestEntry.file)],
  };
};

const loadBundlesFromManifest = async (bundles) => {
  const manifest = await getManifest();

  for (const bundleName of bundles) {
    const assets = getBundleAssets(manifest, bundleName);
    for (const href of assets.styles) {
      appendLink("stylesheet", href);
    }
    for (const href of assets.preloads) {
      appendLink("modulepreload", href);
    }
    for (const src of assets.scripts) {
      appendModuleScript(src);
    }
  }
};

const loadBundlesFromDevServer = async (bundles) => {
  const entries = await getBundleEntries();

  for (const bundleName of bundles) {
    const entryPath = entries[bundleName];
    if (!entryPath) {
      console.warn(
        `Vite dev mode: no entry path for TinyMCE bundle ${bundleName} in ${ENTRIES_PATH}`,
      );
      continue;
    }

    appendModuleScript(normaliseAssetUrl(entryPath));
  }
};

export async function loadTinyMCEPluginBundles(bundleNames) {
  const bundles = toBundleList(bundleNames);
  if (!bundles.length) {
    return;
  }

  if (isViteHmrActive()) {
    await loadBundlesFromDevServer(bundles);
    return;
  }

  await loadBundlesFromManifest(bundles);
}
