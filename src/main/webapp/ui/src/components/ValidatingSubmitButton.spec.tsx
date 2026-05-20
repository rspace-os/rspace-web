import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import AxeBuilder from "@axe-core/playwright";
import { HighContrastExample } from "./ValidatingSubmitButton.story";

test("meets the contrast rules in forced high-contrast mode", async ({
  mount,
  page,
}) => {
  await page.emulateMedia({ forcedColors: "active" });
  await page.addInitScript(() => {
    const originalMatchMedia = window.matchMedia;
    window.matchMedia = (query: string) => {
      if (query === "(prefers-contrast: more)") {
        return {
          matches: true,
          media: query,
          onchange: null,
          addListener() {},
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

  await mount(<HighContrastExample onClick={() => {}} />);
  await page.evaluate(() => {
    const button = Array.from(document.querySelectorAll("button")).find(
      (btn) => btn.textContent === "Submit",
    );
    button?.querySelector(".MuiTouchRipple-root")?.remove();
  });

  const { violations } = await new AxeBuilder({ page })
    .withRules(["color-contrast", "color-contrast-enhanced"])
    .analyze();
  expect(violations).toEqual([]);
});
