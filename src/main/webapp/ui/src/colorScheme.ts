/**
 * Shared light/dark mode for every React root on a page.
 *
 * MUI's ThemeProvider keeps the mode in localStorage under `mui-mode` and
 * listens for `storage` events, but browsers only fire those events in other
 * tabs. Legacy pages mount many separate React roots, so `setColorMode`
 * dispatches the event itself to keep every root in step.
 *
 * The resolved mode is also mirrored to the booking stack's contract (the
 * `rspace-ds-theme` key and the `dark` class on <html>) so Tailwind-styled
 * newApp pages and the MUI pages agree.
 */

import { flushSync } from "react-dom";

export type ColorMode = "light" | "dark" | "system";

/**
 * Pages rendered entirely by React, which have no JSP decorator. Decorated
 * JSP pages opt in through `common/legacyDarkMode.jsp`, which marks <html>
 * with `data-dark-mode`. Anything else, such as bare JSP pages without the
 * decorator, stays light, and so do the MUI elements embedded in it.
 */
const DARK_MODE_PATHS = [
  /^\/gallery(\/|$)/,
  /^\/inventory(\/|$)/,
  /^\/listOfMaterials\//,
  /^\/public\/inventory\//,
  /^\/apps$/,
  /^\/about(\/|$)/,
];

export function isDarkModePath(pathname: string): boolean {
  return DARK_MODE_PATHS.some((path) => path.test(pathname));
}

export const darkModeAvailable: boolean =
  typeof window !== "undefined" &&
  (isDarkModePath(window.location.pathname) || document.documentElement.hasAttribute("data-dark-mode"));

/**
 * A JSP page in a browser without CSS `@scope`, which the legacy dark styles
 * need; legacyDarkMode.jsp keeps such pages light.
 */
export const darkModeUnsupported: boolean =
  typeof window !== "undefined" &&
  !darkModeAvailable &&
  document.documentElement.hasAttribute("data-dark-mode-unsupported");

export const MUI_MODE_STORAGE_KEY = "mui-mode";
const DS_THEME_STORAGE_KEY = "rspace-ds-theme";

function systemPrefersDark(): boolean {
  return typeof window.matchMedia === "function" && window.matchMedia("(prefers-color-scheme: dark)").matches;
}

function parseMode(value: string | null): ColorMode {
  return value === "light" || value === "dark" ? value : "system";
}

/**
 * The page's current mode. Kept in memory as well as storage so a failed
 * storage write cannot undo a change made on this page.
 */
let currentMode: ColorMode | null = null;

function readMode(): ColorMode {
  if (currentMode === null) {
    try {
      currentMode = parseMode(window.localStorage.getItem(MUI_MODE_STORAGE_KEY));
    } catch {
      // storage unavailable; follow the system preference
      currentMode = "system";
    }
  }
  return currentMode;
}

export function resolveColorMode(mode: ColorMode = readMode()): "light" | "dark" {
  if (mode === "system") return systemPrefersDark() ? "dark" : "light";
  return mode;
}

function applyToDocument(resolved: "light" | "dark") {
  const root = document.documentElement;
  root.classList.toggle("dark", resolved === "dark");
  root.classList.toggle("light", resolved === "light");
  // Native scrollbars, form controls, and the canvas follow `color-scheme`.
  root.style.colorScheme = resolved;
  try {
    window.localStorage.setItem(DS_THEME_STORAGE_KEY, resolved);
  } catch {
    // storage unavailable; the class is still applied
  }
}

export function setColorMode(mode: ColorMode): void {
  currentMode = mode;
  try {
    window.localStorage.setItem(MUI_MODE_STORAGE_KEY, mode);
  } catch {
    // storage unavailable; roots still update through the event below
  }
  applyToDocument(resolveColorMode(mode));
  window.dispatchEvent(new StorageEvent("storage", { key: MUI_MODE_STORAGE_KEY, newValue: mode }));
}

/**
 * Printouts are always light. Before printing, every root switches to light
 * synchronously, so the print snapshot sees it, and switches back afterwards.
 * MUI saves a mode change as it applies it, so storage writes of the mode are
 * dropped meanwhile; the stored choice and other tabs are left alone.
 */
let printing = false;

/** Colour transitions would otherwise still be fading when the page is printed. */
let noTransitions: HTMLStyleElement | null = null;

function showModeWithoutSaving(mode: ColorMode) {
  const setItem = Storage.prototype.setItem;
  Storage.prototype.setItem = function (this: Storage, key: string, value: string) {
    if (key !== MUI_MODE_STORAGE_KEY && key !== DS_THEME_STORAGE_KEY) setItem.call(this, key, value);
  };
  try {
    flushSync(() => {
      window.dispatchEvent(new StorageEvent("storage", { key: MUI_MODE_STORAGE_KEY, newValue: mode }));
    });
    applyToDocument(resolveColorMode(mode));
  } finally {
    Storage.prototype.setItem = setItem;
  }
}

let initialised = false;

/**
 * Applies the stored mode to <html> once per page and follows system changes
 * while the mode is "system". Safe to call from every entrypoint.
 */
export function initColorMode(): void {
  if (initialised || !darkModeAvailable) return;
  initialised = true;
  applyToDocument(resolveColorMode());
  if (typeof window.matchMedia === "function") {
    window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
      if (readMode() === "system") applyToDocument(resolveColorMode("system"));
    });
  }
  window.addEventListener("storage", (event) => {
    if (event.key !== MUI_MODE_STORAGE_KEY || printing) return;
    currentMode = parseMode(event.newValue);
    applyToDocument(resolveColorMode(currentMode));
  });
  window.addEventListener("beforeprint", () => {
    if (printing || resolveColorMode() === "light") return;
    printing = true;
    noTransitions = document.createElement("style");
    noTransitions.textContent = "*, *::before, *::after { transition: none !important; }";
    document.head.appendChild(noTransitions);
    showModeWithoutSaving("light");
  });
  window.addEventListener("afterprint", () => {
    if (!printing) return;
    printing = false;
    showModeWithoutSaving(readMode());
    noTransitions?.remove();
    noTransitions = null;
  });
}
