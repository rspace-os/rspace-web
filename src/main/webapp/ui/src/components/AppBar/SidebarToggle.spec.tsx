import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import AxeBuilder from "@axe-core/playwright";
import { SimplePageWithSidebarToggle } from "./SidebarToggle.story";

test("Should have no axe violations.", async ({ mount, page }) => {
  await mount(<SimplePageWithSidebarToggle />);
  const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
  expect(accessibilityScanResults.violations).toEqual([]);
});

test("When tapped, setSidebarOpen should be called.", async ({
  mount,
  page,
}) => {
  let clicked = false;
  await mount(
    <SimplePageWithSidebarToggle
      setSidebarOpen={() => {
        clicked = true;
      }}
    />
  );
  await page.click("button");
  expect(clicked).toBe(true);
});
