import type { BrowserContext, Page } from "@playwright/test";
import { type ColorMode, MODE_COOKIE, MODE_STORAGE_KEY } from "@/__tests__/e2e/assertions/colorMode";
import { TinyMcePluginDialog } from "@/__tests__/e2e/components/document/TinyMcePluginDialog";
import { ColorModeSelect } from "@/__tests__/e2e/components/navigation/ColorModeSelect";
import { ColorModeMenu } from "@/__tests__/e2e/components/shared/ColorModeMenu";
import { env } from "@/__tests__/e2e/env";
import { test as flowsTest } from "@/__tests__/e2e/fixtures/flows";

type ColorModeOptions = {
  /** The mode stored before the first page loads; unset leaves the browser's default (system). */
  colorMode: ColorMode | undefined;
};

type ColorModeFixtures = {
  /** Uncaught errors from every page of the test's context. */
  pageErrors: string[];
  componentColorModeMenu: ColorModeMenu;
  componentColorModeSelect: ColorModeSelect;
  componentTinyMcePluginDialog: TinyMcePluginDialog;
  /** Another tab of the same browser context, blank until the test navigates it. */
  secondTab: { page: Page; colorModeMenu: ColorModeMenu };
};

const SEEDED_KEY = "e2e-color-mode-seeded";

/**
 * Stores `mode` as the user's choice and in the cookie the server reads. It is stored once per
 * context, so a mode the test later switches to survives reloads and reaches new tabs.
 */
async function seedColorMode(context: BrowserContext, mode: ColorMode): Promise<void> {
  await context.addCookies([{ name: MODE_COOKIE, value: mode, url: env.baseURL }]);
  await context.addInitScript(
    ({ key, seededKey, mode }) => {
      try {
        if (window.localStorage.getItem(seededKey) !== null) return;
        window.localStorage.setItem(key, mode);
        window.localStorage.setItem(seededKey, "true");
      } catch {
        // documents without storage, such as about:blank
      }
    },
    { key: MODE_STORAGE_KEY, seededKey: SEEDED_KEY, mode },
  );
}

export const test = flowsTest.extend<ColorModeOptions & ColorModeFixtures>({
  colorMode: [undefined, { option: true }],

  context: async ({ context, colorMode }, use) => {
    if (colorMode) await seedColorMode(context, colorMode);
    await use(context);
  },

  pageErrors: async ({ context }, use) => {
    const errors: string[] = [];
    const listen = (page: Page) => page.on("pageerror", (error) => errors.push(`${page.url()}: ${error.message}`));
    context.pages().forEach(listen);
    context.on("page", listen);
    await use(errors);
  },

  componentColorModeMenu: async ({ page }, use) => {
    await use(new ColorModeMenu(page));
  },
  componentColorModeSelect: async ({ page }, use) => {
    await use(new ColorModeSelect(page));
  },
  componentTinyMcePluginDialog: async ({ page }, use) => {
    await use(new TinyMcePluginDialog(page));
  },
  secondTab: async ({ context }, use) => {
    const page = await context.newPage();
    await use({ page, colorModeMenu: new ColorModeMenu(page) });
  },
});
