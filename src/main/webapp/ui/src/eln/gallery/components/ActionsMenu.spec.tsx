import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import * as Jwt from "jsonwebtoken";
import AxeBuilder from "@axe-core/playwright";

// Import the story components
import {
  ActionsMenuWithNonFolder,
  ActionsMenuWithFolder,
  ActionsMenuWithMultipleFiles,
} from "./ActionsMenu.story";

const feature = test.extend<{
  Given: {
    "the actions menu with a non-folder is mounted": () => Promise<void>;
    "the actions menu with a folder is mounted": () => Promise<void>;
    "the actions menu with multiple files is mounted": () => Promise<void>;
  };
  When: {
    "the user clicks the actions menu button": () => Promise<void>;
    "the user selects 'Export' from the menu": () => Promise<void>;
  };
  Then: {
    "the actions menu should be visible": () => Promise<void>;
    "the Open option should not be visible": () => Promise<void>;
    "the Open option should be visible": () => Promise<void>;
    "there shouldn't be any axe violations": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the actions menu with a non-folder is mounted": async () => {
        await mount(<ActionsMenuWithNonFolder />);
      },
      "the actions menu with a folder is mounted": async () => {
        await mount(<ActionsMenuWithFolder />);
      },
      "the actions menu with multiple files is mounted": async () => {
        await mount(<ActionsMenuWithMultipleFiles />);
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the user clicks the actions menu button": async () => {
        await page.getByRole("button", { name: /actions/i }).click();
      },
      "the user selects 'Export' from the menu": async () => {
        await page.getByRole("menuitem", { name: /export/i }).click();
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "the actions menu should be visible": async () => {
        await expect(
          page.getByRole("button", { name: /actions/i })
        ).toBeVisible({ timeout: 5000 });
      },
      "the Open option should not be visible": async () => {
        await expect(
          page.getByRole("menuitem", { name: /open/i })
        ).not.toBeVisible({ timeout: 5000 });
      },
      "the Open option should be visible": async () => {
        await expect(page.getByRole("menuitem", { name: /open/i })).toBeVisible(
          { timeout: 5000 }
        );
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
             */
            return (
              v.description !==
                "Ensure elements with an ARIA role that require child roles contain them" &&
              v.id !== "landmark-one-main" &&
              v.id !== "page-has-heading-one" &&
              v.id !== "region"
            );
          })
        ).toEqual([]);
      },
    });
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

  await router.route("/collaboraOnline/supportedExts", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({}),
    });
  });

  await router.route("/officeOnline/supportedExts", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({}),
    });
  });

  await router.route("/export/ajax/defaultPDFConfig", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: { pageSize: "A4" },
      }),
    });
  });

  await router.route("/gallery/getUploadedFiles", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: {
          parentId: 1,
          items: {
            results: [],
          },
        },
      }),
    });
  });

  await router.route("**/*.svg", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "image/svg+xml",
      body: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><rect width="24" height="24" fill="none"/></svg>`,
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
});

test.describe("ActionsMenu", () => {
  test.describe("Should have no axe violations", () => {
    feature("Should have no axe violations", async ({ Given, Then }) => {
      await Given["the actions menu with a non-folder is mounted"]();
      await Then["there shouldn't be any axe violations"]();
    });
  });

  test.describe("File menu options", () => {
    feature(
      "When the selected file isn't a folder, open should not be visible",
      async ({ Given, When, Then }) => {
        await Given["the actions menu with a non-folder is mounted"]();
        await When["the user clicks the actions menu button"]();
        await Then["the Open option should not be visible"]();
      }
    );

    feature(
      "When the selected file is a folder, open should be visible",
      async ({ Given, When, Then }) => {
        await Given["the actions menu with a folder is mounted"]();
        await When["the user clicks the actions menu button"]();
        await Then["the Open option should be visible"]();
      }
    );
  });
});
