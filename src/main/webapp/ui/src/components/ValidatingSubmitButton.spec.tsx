import { cleanup, render } from "@testing-library/react";
import axe from "axe-core";
import { afterEach, describe, expect, test } from "vitest";
import { emulateForcedColors } from "@/__tests__/pageObjects/accessibility";
import { HighContrastExample } from "./ValidatingSubmitButton.story";

let originalMatchMedia: typeof window.matchMedia;

afterEach(() => {
  if (originalMatchMedia) {
    window.matchMedia = originalMatchMedia;
  }
  cleanup();
});

describe("ValidatingSubmitButton", () => {
  describe("Accessibility", () => {
    test("When user prefers more contrast, button should meet WCAG AAA contrast requirements", async () => {
      /*
       * Patch window.matchMedia before render so the component sees
       * `(prefers-contrast: more)` as matching.
       */
      originalMatchMedia = window.matchMedia;
      window.matchMedia = (query: string) => {
        if (query === "(prefers-contrast: more)") {
          return {
            matches: true,
            media: query,
            onchange: null,
            addListener() {}, // deprecated
            removeListener() {}, // deprecated
            addEventListener() {},
            removeEventListener() {},
            dispatchEvent() {
              return false;
            },
          } as MediaQueryList;
        }
        return originalMatchMedia(query);
      };

      // Emulate forced-colors + prefers-contrast via CDP (chromium-only; no-op elsewhere)
      await emulateForcedColors();

      render(<HighContrastExample onClick={() => {}} />);

      /*
       * The MUI Button ripple disrupts Axe from being able to properly
       * determine the contrast of the button text; remove it before the scan.
       */
      const button = Array.from(document.querySelectorAll("button")).find((btn) => btn.textContent === "Submit");
      if (button) {
        const ripple = button.querySelector(".MuiTouchRipple-root");
        if (ripple) {
          ripple.remove();
        }
      }

      /*
       * We need to assert both rules because color-contrast-enhanced only
       * checks elements that already meet AA, whereas color-contrast checks all
       * elements. Together they ensure all elements meet WCAG AAA.
       */
      const results = await axe.run(document, {
        runOnly: { type: "rule", values: ["color-contrast", "color-contrast-enhanced"] },
      });

      expect(results.violations).toEqual([]);
    });
  });
});
