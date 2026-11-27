import createCache, { type EmotionCache } from "@emotion/cache";
import { CacheProvider } from "@emotion/react";
import { config as fontAwesomeConfig } from "@fortawesome/fontawesome-svg-core";
import "@fortawesome/fontawesome-svg-core/styles.css";
import type { PropsWithChildren, ReactNode } from "react";
import { getCspNonce } from "@/util/csp";

fontAwesomeConfig.autoAddCss = false;

export type MuiCssLayerContainer = Document | ShadowRoot;

type MuiCssLayerProviderProps = PropsWithChildren<{
  container?: MuiCssLayerContainer;
}>;

const caches = new WeakMap<MuiCssLayerContainer, EmotionCache>();

function isDocument(node: Node): node is Document {
  return node.nodeType === 9;
}

function ownerDocumentFor(container?: Node): Document | undefined {
  if (!container) {
    return typeof document === "undefined" ? undefined : document;
  }

  return isDocument(container) ? container : (container.ownerDocument ?? undefined);
}

function emotionContainerFor(container: MuiCssLayerContainer): Node {
  return isDocument(container) ? container.head : container;
}

export function createMuiCssLayerCache(options: Parameters<typeof createCache>[0]): EmotionCache {
  const ownerDocument = ownerDocumentFor(options.container);
  const cache = createCache({
    ...options,
    nonce: options.nonce ?? (ownerDocument ? getCspNonce(ownerDocument) : undefined),
    prepend: options.prepend ?? true,
  });
  const originalInsert = cache.insert;

  cache.insert = (...args) => {
    if (!args[1].styles.match(/^@layer\s+[^{}]*$/)) {
      args[1].styles = `@layer mui {${args[1].styles}}`;
    }

    return originalInsert(...args);
  };

  return cache;
}

export function getMuiCssLayerCache(container?: MuiCssLayerContainer): EmotionCache {
  const target = container ?? (typeof document === "undefined" ? undefined : document);
  if (!target) {
    throw new Error("MuiCssLayerProvider requires a browser document.");
  }

  const existingCache = caches.get(target);
  if (existingCache) {
    return existingCache;
  }

  const cache = createMuiCssLayerCache({
    key: "css",
    prepend: true,
    container: emotionContainerFor(target),
  });
  caches.set(target, cache);
  return cache;
}

export function MuiCssLayerProvider({ children, container }: MuiCssLayerProviderProps): ReactNode {
  return <CacheProvider value={getMuiCssLayerCache(container)}>{children}</CacheProvider>;
}
