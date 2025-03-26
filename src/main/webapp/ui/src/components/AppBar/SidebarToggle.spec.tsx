import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import AxeBuilder from "@axe-core/playwright";
import { SimplePageWithSidebarToggle } from "./SidebarToggle.story";

test.describe("Functional requirements", () => {
  test("Clicking the button should toggle the sidebar.", async ({
    mount,
    page,
  }) => {
    const initialSidebarOpen = Math.random() < 0.5;
    let sidebarOpen = initialSidebarOpen;
    await mount(
      <SimplePageWithSidebarToggle
        sidebarOpen={initialSidebarOpen}
        setSidebarOpen={(newSidebarOpen) => {
          sidebarOpen = newSidebarOpen;
        }}
      />
    );
    await page.click("button");
    expect(sidebarOpen).toBe(!initialSidebarOpen);
  });
});

test.describe("Accessibility", () => {
  test("Should have no axe violations.", async ({ mount, page }) => {
    await mount(<SimplePageWithSidebarToggle />);
    const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test("When sidebar is open, aria-expanded should be true.", async ({
    mount,
    page,
  }) => {
    await mount(<SimplePageWithSidebarToggle sidebarOpen={true} />);
    const ariaExpanded = await page.getAttribute("button", "aria-expanded");
    expect(ariaExpanded).toBe("true");
  });

  test("When sidebar is closed, aria-expanded should be false.", async ({
    mount,
    page,
  }) => {
    await mount(<SimplePageWithSidebarToggle sidebarOpen={false} />);
    const ariaExpanded = await page.getAttribute("button", "aria-expanded");
    expect(ariaExpanded).toBe("false");
  });

  test("Applies the sidebarId as the aria-controls attribute.", async ({
    mount,
    page,
  }) => {
    await mount(<SimplePageWithSidebarToggle sidebarId="test" />);
    const ariaControls = await page.getAttribute("button", "aria-controls");
    expect(ariaControls).toBe("test");
  });
});
