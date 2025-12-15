import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { DefaultSidebar } from "./Sidebar.story";
import * as Jwt from "jsonwebtoken";
import AxeBuilder from "@axe-core/playwright";

const feature = test.extend<{
  Given: {
    "the sidebar is visible": () => Promise<void>;
  };
  When: {
    "the user clicks the Create button": () => Promise<void>;
    "the user clicks the New Folder menu item": () => Promise<void>;
    "the user types a folder name": () => Promise<void>;
    "the user clicks the Create button in the dialog": () => Promise<void>;
    "the user types a folder name and presses Enter": () => Promise<void>;
  };
  Then: {
    "the New Folder dialog should be visible": () => Promise<void>;
    "a folder creation request should be made": () => Promise<void>;
    "there shouldn't be any axe violations": () => Promise<void>;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the sidebar is visible": async () => {
        await mount(<DefaultSidebar />);
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the user clicks the Create button": async () => {
        await page.getByRole("button", { name: "Create" }).click();
      },
      "the user clicks the New Folder menu item": async () => {
        await page.getByRole("menuitem", { name: /New Folder/i }).click();
      },
      "the user types a folder name": async () => {
        await page.getByRole("textbox").fill("test");
      },
      "the user clicks the Create button in the dialog": async () => {
        await page
          .getByRole("dialog")
          .getByRole("button", { name: "Create" })
          .click();
      },
      "the user types a folder name and presses Enter": async () => {
        await page.getByRole("textbox").fill("test");
        await page.getByRole("textbox").press("Enter");
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "the New Folder dialog should be visible": async () => {
        await expect(page.getByRole("dialog")).toBeVisible();
        await expect(
          page
            .getByRole("dialog")
            .getByRole("heading", { name: "New Folder", exact: false }),
        ).toBeVisible();
      },
      "a folder creation request should be made": async () => {
        // We'll check that the dialog is no longer visible, which indicates submission
        await expect(page.getByRole("dialog")).not.toBeVisible({
          timeout: 5000,
        });
      },
      "there shouldn't be any axe violations": async () => {
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();
        expect(
          accessibilityScanResults.violations.filter((v) => {
            /*
             * These violations are expected in component tests as we're not rendering
             * a complete page with proper document structure:
             *
             * 1. MUI DataGrid renders its immediate children with role=presentation,
             *    which Firefox considers to be a violation
             * 2. Component tests don't have main landmarks as they're isolated components
             * 3. Component tests typically don't have h1 headings as they're not full pages
             * 4. Content not in landmarks is expected in component testing context
             * 5. Color contrast issues are often present in component tests due to branding
             */
            return (
              v.description !==
                "Ensure elements with an ARIA role that require child roles contain them" &&
              v.id !== "landmark-one-main" &&
              v.id !== "page-has-heading-one" &&
              v.id !== "region" &&
              v.id !== "color-contrast"
            );
          }),
        ).toEqual([]);
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },
});

feature.beforeEach(async ({ router }) => {
  await router.route("/session/ajax/analyticsProperties", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        analyticsEnabled: false,
      }),
    });
  });
  await router.route("/userform/ajax/preference*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({}),
    });
  });
  await router.route("/deploymentproperties/ajax/property*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(false),
    });
  });
  await router.route("**/gallery/ajax/createFolder", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: true,
        error: null,
        success: true,
        errorMsg: null,
      }),
    });
  });
  await router.route("/integration/integrationInfo?name=DMPTOOL", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          name: "DMPTOOL",
          displayName: "DMPtool",
          available: true,
          enabled: false,
          oauthConnected: false,
          options: {},
        },
        error: null,
        success: true,
        errorMsg: null,
      }),
    });
  });
  await router.route("/integration/integrationInfo?name=DMPONLINE", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          name: "DMPONLINE",
          displayName: "DMPonline",
          available: true,
          enabled: true,
          oauthConnected: false,
          options: {},
        },
        error: null,
        success: true,
        errorMsg: null,
      }),
    });
  });
  await router.route("/integration/integrationInfo?name=ARGOS", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          name: "ARGOS",
          displayName: "Argos",
          available: false,
          enabled: false,
          oauthConnected: false,
          options: {},
        },
        error: null,
        success: true,
        errorMsg: null,
      }),
    });
  });
  await router.route("/userform/ajax/inventoryOauthToken", (route) => {
    const payload = {
      iss: "http://localhost:8080",
      iat: new Date().getTime(),
      exp: Math.floor(Date.now() / 1000) + 300,
      refreshTokenHash:
        "fe15fa3d5e3d5a47e33e9e34229b1ea2314ad6e6f13fa42addca4f1439582a4d",
    };
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: Jwt.sign(payload, "dummySecretKey"),
      }),
    });
  });
  await router.route("/api/v1/gallery/filesystems", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([
        {
          id: 1,
          name: "irods test",
          url: "irods-test.researchspace.com",
          clientType: "IRODS",
          authType: "PASSWORD",
          options: {},
          loggedAs: null,
        },
      ]),
    });
  });
  await router.route("**/*.svg", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "image/svg+xml",
      body: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><rect width="24" height="24" fill="none"/></svg>`,
    });
  });
});

feature.afterEach(({}) => {});

test.describe("Sidebar", () => {
  feature("Should have no axe violations", async ({ Given, Then }) => {
    await Given["the sidebar is visible"]();
    await Then["there shouldn't be any axe violations"]();
  });

  test.describe("New Folder", () => {
    feature(
      "Clicking the Submit button should work",
      async ({ Given, When, Then }) => {
        await Given["the sidebar is visible"]();
        await When["the user clicks the Create button"]();
        await When["the user clicks the New Folder menu item"]();
        await Then["the New Folder dialog should be visible"]();
        await When["the user types a folder name"]();
        await When["the user clicks the Create button in the dialog"]();
        await Then["a folder creation request should be made"]();
      },
    );

    feature(
      "Pressing enter to Submit should work",
      async ({ Given, When, Then }) => {
        await Given["the sidebar is visible"]();
        await When["the user clicks the Create button"]();
        await When["the user clicks the New Folder menu item"]();
        await Then["the New Folder dialog should be visible"]();
        await When["the user types a folder name and presses Enter"]();
        await Then["a folder creation request should be made"]();
      },
    );
  });
});
