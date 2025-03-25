import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import SidebarToggle from "./SidebarToggle";
import AxeBuilder from "@axe-core/playwright";

test("Should have no axe violations.", async ({ mount, page }) => {
  await mount(
    <body>
      <header>
        <h1>A simple page</h1>
        <SidebarToggle
          sidebarOpen={true}
          setSidebarOpen={() => {}}
          sidebarId={"sidebar"}
        />
      </header>
      <main>
        <div id="sidebar"></div>
      </main>
    </body>
  );
  const accessibilityScanResults = await new AxeBuilder({ page }).analyze();
  expect(accessibilityScanResults.violations).toEqual([]);
});
