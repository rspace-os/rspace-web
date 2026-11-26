import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { expect, type FrameLocator, type Locator, type Page } from "@playwright/test";
import type { AxeResults } from "axe-core";

export type ColorMode = "light" | "dark" | "system";

/** Shared with ui/src/colorScheme.ts and scripts/darkMode.js. */
export const MODE_STORAGE_KEY = "mui-mode";
/** Mirrors the stored mode so the server can render the dark stylesheets. */
export const MODE_COOKIE = "rs-color-mode";
/** The stylesheets scripts/darkMode.js keeps after every other dark stylesheet, in order. */
export const LAST_DARK_SHEETS = ["/styles/dark/generated/templates.css", "/styles/dark/legacy-dark.css"];

const DARK_SHEETS = "link[data-dark-mode-sheet]";

const axeSource = readFileSync(createRequire(import.meta.url).resolve("axe-core/axe.min.js"), "utf8");

type Scope = Page | FrameLocator;

/**
 * Renders a CSS colour (any syntax the browser understands) over the colours of its
 * ancestors, so the result is what the user sees. Runs in the browser.
 */
function effectiveColours(element: Element): { text: number[]; background: number[] } {
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = 1;
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context) throw new Error("No 2D canvas context.");
  const rgba = (colour: string): number[] => {
    context.clearRect(0, 0, 1, 1);
    context.fillStyle = colour;
    context.fillRect(0, 0, 1, 1);
    const [r, g, b, a] = context.getImageData(0, 0, 1, 1).data;
    return [r, g, b, a / 255];
  };
  const over = (top: number[], bottom: number[]): number[] => [
    ...[0, 1, 2].map((i) => top[i] * top[3] + bottom[i] * (1 - top[3])),
    1,
  ];
  const parent = (node: Element): Element | null =>
    node.parentElement ?? ((node.getRootNode() as ShadowRoot).host as Element | undefined) ?? null;

  let opacity = 1;
  const layers: { colour: number[]; opacity: number }[] = [];
  for (let node: Element | null = element; node; node = parent(node)) {
    const style = getComputedStyle(node);
    opacity *= Number(style.opacity);
    const colour = rgba(style.backgroundColor);
    if (colour[3] > 0) layers.push({ colour, opacity });
    if (colour[3] === 1) break;
  }
  let background = [255, 255, 255, 1];
  for (const layer of layers.reverse()) {
    background = over([...layer.colour.slice(0, 3), layer.colour[3] * layer.opacity], background);
  }
  const text = rgba(getComputedStyle(element).color);
  return { text: over([...text.slice(0, 3), text[3] * opacity], background), background };
}

function luminance([r, g, b]: number[]): number {
  const channel = (value: number) => {
    const c = value / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

/** WCAG 2 contrast ratio between an element's text and what is behind it. */
export async function textContrast(element: Locator): Promise<number> {
  const { text, background } = await element.evaluate(effectiveColours);
  const [lighter, darker] = [luminance(text), luminance(background)].sort((a, b) => b - a);
  return (lighter + 0.05) / (darker + 0.05);
}

/** Relative luminance (0 black, 1 white) of the background behind `element`. */
export async function backgroundLuminance(element: Locator): Promise<number> {
  return luminance((await element.evaluate(effectiveColours)).background);
}

/** Paths, without the cache-busting query, of the page's dark stylesheets in document order. */
export async function darkSheetPaths(page: Page): Promise<string[]> {
  return page
    .locator(DARK_SHEETS)
    .evaluateAll((links) => links.map((link) => new URL((link as HTMLLinkElement).href).pathname));
}

/** axe colour-contrast violations, one line per failing element. */
export async function colourContrastViolations(page: Page): Promise<string[]> {
  if (!(await page.evaluate(() => "axe" in window))) await page.evaluate(axeSource);
  const results = await page.evaluate(() =>
    (window as unknown as { axe: { run(context: Document, options: object): Promise<AxeResults> } }).axe.run(document, {
      runOnly: ["color-contrast"],
      resultTypes: ["violations"],
    }),
  );
  return results.violations.flatMap((violation) =>
    violation.nodes.map((node) => `${node.target.join(" ")}: ${node.failureSummary ?? violation.help}`),
  );
}

export async function expectDarkMode(scope: Scope): Promise<void> {
  await expect(scope.locator("html")).toHaveClass(/(^|\s)dark(\s|$)/);
  await expect(scope.locator("html")).not.toHaveClass(/(^|\s)light(\s|$)/);
}

export async function expectLightMode(scope: Scope): Promise<void> {
  await expect(scope.locator("html")).toHaveClass(/(^|\s)light(\s|$)/);
  await expect(scope.locator("html")).not.toHaveClass(/(^|\s)dark(\s|$)/);
}

/**
 * A page shown dark: the class is set, the background is dark, every dark stylesheet loaded,
 * and on pages React does not render the hand-written rules come last.
 */
export async function expectDarkPage(page: Page): Promise<void> {
  await expectDarkMode(page);
  expect(await backgroundLuminance(page.locator("body")), "body background luminance").toBeLessThan(0.05);
  const failed = await page
    .locator(DARK_SHEETS)
    .evaluateAll((links) => links.filter((link) => !(link as HTMLLinkElement).sheet).map((link) => link.outerHTML));
  expect(failed, "dark stylesheets that did not load").toEqual([]);
  if ((await page.locator("html[data-dark-mode]").count()) > 0) {
    expect((await darkSheetPaths(page)).slice(-LAST_DARK_SHEETS.length)).toEqual(LAST_DARK_SHEETS);
  }
}

/** A page shown light with no dark stylesheets, as when the mode cookie says light. */
export async function expectLightPageWithoutDarkSheets(page: Page): Promise<void> {
  await expectLightMode(page);
  expect(await backgroundLuminance(page.locator("body")), "body background luminance").toBeGreaterThan(0.8);
  expect(await darkSheetPaths(page)).toEqual([]);
}

export async function storedMode(page: Page): Promise<string | null> {
  return page.evaluate((key) => window.localStorage.getItem(key), MODE_STORAGE_KEY);
}

export async function modeCookie(page: Page): Promise<string | undefined> {
  return (await page.context().cookies()).find((cookie) => cookie.name === MODE_COOKIE)?.value;
}

export type DarkSwitch = { pendingSheets: string[]; hidden: boolean };

/**
 * From the next navigation on, records each moment <html> turns dark: which dark stylesheets had
 * not loaded yet, and whether the page was hidden meanwhile. Read the records with darkSwitches().
 */
export async function recordDarkSwitches(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const records: DarkSwitch[] = [];
    (window as unknown as { e2eDarkSwitches: DarkSwitch[] }).e2eDarkSwitches = records;
    let wasDark = false;
    new MutationObserver(() => {
      const root = document.documentElement;
      const dark = root?.classList.contains("dark") ?? false;
      if (dark && !wasDark) {
        records.push({
          pendingSheets: [...document.querySelectorAll<HTMLLinkElement>("link[data-dark-mode-sheet]")]
            .filter((link) => !link.sheet)
            .map((link) => new URL(link.href).pathname),
          hidden: root.style.visibility === "hidden",
        });
      }
      wasDark = dark;
    }).observe(document, { attributes: true, attributeFilter: ["class"], subtree: true, childList: true });
  });
}

export async function darkSwitches(page: Page): Promise<DarkSwitch[]> {
  return page.evaluate(() => (window as unknown as { e2eDarkSwitches: DarkSwitch[] }).e2eDarkSwitches);
}
