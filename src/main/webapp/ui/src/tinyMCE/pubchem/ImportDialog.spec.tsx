import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { ImportDialogStory } from "./ImportDialog.story";
import AxeBuilder from "@axe-core/playwright";
import * as Jwt from "jsonwebtoken";

const feature = test.extend<{
  Given: {
    "that the ImportDialog is mounted": () => Promise<void>;
  };
  Once: Record<string, never>;
  When: Record<string, never>;
  Then: {
    "there should be a dialog visible": () => Promise<void>;
    "there should be no axe violations": () => Promise<void>;
    "there should be a dialog header banner": () => Promise<void>;
    "there should be a title: 'Import from PubChem'": () => Promise<void>;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount }, use) => {
    await use({
      "that the ImportDialog is mounted": async () => {
        await mount(<ImportDialogStory />);
      },
    });
  },
  Once: async ({}, use) => {
    await use({});
  },
  When: async ({}, use) => {
    await use({});
  },
  Then: async ({ page }, use) => {
    await use({
      "there should be a dialog visible": async () => {
        const dialog = page.getByRole("dialog");
        await expect(dialog).toBeVisible();
      },
      "there should be no axe violations": async () => {
        const accessibilityScanResults = await new AxeBuilder({
          page,
        }).analyze();
        expect(accessibilityScanResults.violations).toEqual([]);
      },
      "there should be a dialog header banner": async () => {
        const dialogHeader = page.getByRole("banner", {
          name: "dialog header",
        });
        await expect(dialogHeader).toBeVisible();
        await expect(dialogHeader).toHaveText("PubChem");
      },
      "there should be a title: 'Import from PubChem'": async () => {
        const dialog = page.getByRole("dialog");
        await expect(dialog).toHaveAccessibleName("Import from PubChem");
        const title = dialog.getByRole("heading", { level: 3 });
        await expect(title).toBeVisible();
        await expect(title).toHaveText("Import from PubChem");
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },
});

feature.beforeEach(async ({ router }) => {
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
  await router.route("/session/ajax/livechatProperties", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        liveChatEnabled: false,
      }),
    });
  });
  await router.route("/api/v1/userDetails/uiNavigationData", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        bannerImgSrc: "/public/banner",
        visibleTabs: {
          inventory: true,
          myLabGroups: true,
          published: false,
          system: false,
        },
        userDetails: {
          username: "user1a",
          fullName: "user user",
          email: "user@user.com",
          orcidId: null,
          orcidAvailable: false,
          profileImgSrc: null,
          lastSession: "2025-03-25T15:45:57.000Z",
        },
        operatedAs: false,
        nextMaintenance: null,
      }),
    });
  });
});

feature.afterEach(({}) => {});

test.describe("ImportDialog", () => {
  feature("Renders correctly", async ({ Given, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await Then["there should be a dialog visible"]();
  });
  feature("Should have no axe violations.", async ({ Given, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await Then["there should be no axe violations"]();
  });
  feature("Should be a dialog header banner", async ({ Given, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await Then["there should be a dialog header banner"]();
  });
  feature("Should have a title", async ({ Given, Then }) => {
    await Given["that the ImportDialog is mounted"]();
    await Then["there should be a title: 'Import from PubChem'"]();
  });
});
