import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/experimental-ct-react";
import { HighContrastExample } from "./ValidatingSubmitButton.story";

/*
 * Only the WCAG AAA contrast case remains in Playwright. It relies on
 * emulateMedia({ forcedColors }) and the color-contrast / color-contrast-enhanced
 * axe rules, which require a real browser to compute rendered colours. The
 * render / click / disabled / progress / structural-axe cases have been ported
 * to the jsdom unit test in ValidatingSubmitButton.test.tsx.
 */
const createOnClickSpy = () => {
  let clicked = false;
  const handler = () => {
    clicked = true;
  };

  const hasBeenClicked = () => clicked;
  return {
    handler,
    hasBeenClicked,
  };
};
const feature = test.extend<{
  Given: {
    "the ValidatingSubmitButton with high contrast is rendered": () => Promise<{
      onClickSpy: ReturnType<typeof createOnClickSpy>;
    }>;
  };
  Then: {
    "there shouldn't be any contrast violations at AAA level": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the ValidatingSubmitButton with high contrast is rendered": async () => {
        const onClickSpy = createOnClickSpy();
        await mount(<HighContrastExample onClick={onClickSpy.handler} />);
        return { onClickSpy };
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "there shouldn't be any contrast violations at AAA level": async () => {
        /*
         * The MUI Button ripple disrupts Axe from being able to properly
         * determine the contrast of the button text. We remove it here
         * before performing the analysis.
         */
        await page.evaluate(() => {
          const button = Array.from(document.querySelectorAll("button")).find((btn) => btn.textContent === "Submit");
          if (!button) return;
          const ripple = button.querySelector(".MuiTouchRipple-root");
          if (ripple) {
            ripple.remove();
          }
        });
        const { violations } = await new AxeBuilder({
          page,
        })
          /*
           * We need to assert both because color-contrast-enhanced only
           * checks for elements that already meet AA, whereas color-contrast
           * checks all elements. We want to ensure that all elements meet AAA.
           */
          .withRules(["color-contrast", "color-contrast-enhanced"])
          .analyze();
        expect(violations).toEqual([]);
      },
    });
  },
});
test.describe("ValidatingSubmitButton", () => {
  test.describe("Accessibility", () => {
    feature(
      "When user prefers more contrast, button should meet WCAG AAA contrast requirements",
      async ({ Given, Then, page }) => {
        await page.emulateMedia({ forcedColors: "active" });
        // Stub prefers-contrast
        await page.addInitScript(() => {
          const originalMatchMedia = window.matchMedia;
          window.matchMedia = (query: string) => {
            if (query === "(prefers-contrast: more)") {
              return {
                matches: true,
                media: query,
                onchange: null,
                addListener() {}, // deprecated
                removeListener() {},
                addEventListener() {},
                removeEventListener() {},
                dispatchEvent() {
                  return false;
                },
              } as MediaQueryList;
            }
            return originalMatchMedia(query);
          };
        });
        await Given["the ValidatingSubmitButton with high contrast is rendered"]();
        await Then["there shouldn't be any contrast violations at AAA level"]();
      },
    );
  });
});
