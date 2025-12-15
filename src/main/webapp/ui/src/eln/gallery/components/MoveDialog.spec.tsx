import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { MoveDialogStory } from "./MoveDialog.story";
import * as Jwt from "jsonwebtoken";

const feature = test.extend<{
  Given: {
    "the move dialog is mounted": () => Promise<void>;
  };
  Then: {
    "the API should be called with foldersOnly=true": () => void;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount, page }, use) => {
    await use({
      "the move dialog is mounted": async () => {
        await mount(<MoveDialogStory />);
        await expect(page.getByRole("dialog")).toBeVisible();
      },
    });
  },
  Then: async ({ networkRequests }, use) => {
    await use({
      "the API should be called with foldersOnly=true": () => {
        expect(
          networkRequests
            .find((url) => url.searchParams.has("foldersOnly"))
            ?.searchParams.get("foldersOnly")
        ).toBe("true");
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },
});

feature.beforeEach(async ({ router, page, networkRequests }) => {
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
  await router.route("/*/supportedExts", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({}),
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
  await router.route("/gallery/getUploadedFiles*", (route) => {
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

  page.on("request", (request) => {
    networkRequests.push(new URL(request.url()));
  });
});

feature.afterEach(({ networkRequests }) => {
  networkRequests.splice(0, networkRequests.length);
});

test.describe("MoveDialog", () => {
  feature("Should request only folders", async ({ Given, Then, page }) => {
    await Given["the move dialog is mounted"]();
    Then["the API should be called with foldersOnly=true"]();
  });
});
