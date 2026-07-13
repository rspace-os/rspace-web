import { TextDecoder, TextEncoder } from "node:util";
import { afterAll, afterEach, expect, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { setup, toBeAccessible } from "@sa11y/vitest";
import { cleanup } from "@testing-library/react";
import createFetchMock from "vitest-fetch-mock";
import { silenceProcessOutput } from "@/__tests__/helpers/silenceConsole";
import i18n from "@/modules/common/i18n";

await i18n.loadNamespaces([
  "about",
  "admin",
  "apps",
  "common",
  "dashboard",
  "gallery",
  "groups",
  "inventory",
  "public",
  "system",
  "workspace",
]);
// Component tests assert the translation identifier, not the English copy.
i18n.options.appendNamespaceToCIMode = true;
await i18n.changeLanguage("cimode");

function createStorageMock() {
  const storage = new Map<string, string>();

  return {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => {
      storage.set(key, String(value));
    },
    removeItem: (key: string) => {
      storage.delete(key);
    },
    clear: () => {
      storage.clear();
    },
  };
}

setup();

expect.extend({ toBeAccessible });

const fetchMocker = createFetchMock(vi);

fetchMocker.enableMocks();
// @ts-expect-error Mocking
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

afterEach(() => {
  cleanup();
  globalThis.localStorage?.clear?.();
  globalThis.sessionStorage?.clear?.();
});

const restoreStderr = silenceProcessOutput(["stderr"], ["AggregateError"]);
afterAll(() => {
  restoreStderr();
});

/*
 * Polyfill for TextEncoder and TextDecoder in Vitest tests.
 *
 * These classes are available in browsers but not in Node.js by default.
 * Some dependencies (such as MUI, emotion, or others) may require TextEncoder/TextDecoder
 * for encoding/decoding text, even in test environments.
 * This ensures tests that rely on these APIs do not fail with "ReferenceError: TextEncoder is not defined".
 */
if (typeof globalThis.TextEncoder !== "function") {
  globalThis.TextEncoder = TextEncoder;
}
if (typeof globalThis.TextDecoder !== "function") {
  // @ts-expect-error Only needed for mocking
  globalThis.TextDecoder = TextDecoder;
}

if (typeof globalThis.localStorage !== "object") {
  Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    writable: true,
    value: createStorageMock(),
  });
}

if (typeof globalThis.sessionStorage !== "object") {
  Object.defineProperty(globalThis, "sessionStorage", {
    configurable: true,
    writable: true,
    value: createStorageMock(),
  });
}

/*
 * Polyfill for IntersectionObserver in jsdom. Some components (e.g.
 * ExtendedContextMenu) instantiate one on mount to detect overflowing child
 * elements. jsdom does not implement this API, so we provide a no-op stub.
 */
if (typeof globalThis.IntersectionObserver !== "function") {
  class IntersectionObserverMock {
    root: Element | Document | null = null;
    rootMargin: string = "";
    thresholds: ReadonlyArray<number> = [];
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
    takeRecords(): IntersectionObserverEntry[] {
      return [];
    }
  }
  // @ts-expect-error Mocking
  globalThis.IntersectionObserver = IntersectionObserverMock;
}

/*
 * Polyfill for DOMMatrix in Vitest tests. jsdom does not implement it, but
 * pdfjs-dist v5 (pulled in by react-pdf) references DOMMatrix at module-load
 * time, so importing any component that uses react-pdf (e.g. the gallery
 * Carousel / PDF preview) throws "DOMMatrix is not defined". A minimal,
 * chainable stub is enough for tests that import these components without
 * actually rasterising a PDF.
 */
if (typeof globalThis.DOMMatrix !== "function") {
  class DOMMatrixMock {
    a = 1;
    b = 0;
    c = 0;
    d = 1;
    e = 0;
    f = 0;
    multiplySelf(): this {
      return this;
    }
    preMultiplySelf(): this {
      return this;
    }
    translateSelf(): this {
      return this;
    }
    scaleSelf(): this {
      return this;
    }
    rotateSelf(): this {
      return this;
    }
    invertSelf(): this {
      return this;
    }
  }
  // @ts-expect-error Mocking
  globalThis.DOMMatrix = DOMMatrixMock;
}
