import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { TreeViewWithFiles } from "./TreeView.story";

import * as Jwt from "jsonwebtoken";

/*
 * This spec is the reduced remainder after the bulk of TreeView's cases were
 * converted to a Vitest jsdom test (see TreeView.test.tsx).
 *
 * Only the Shift+click case remains here. It asserts that shift-clicking a tree
 * item surfaces the "Shift selection is not supported" warning alert. That
 * alert only appears when MUI's SimpleTreeView performs a shift range selection
 * and invokes `onItemSelectionToggle` with `selected` true. MUI's shift-range
 * selection relies on the browser focus/selection model and item-ordering
 * computation, which does not run under jsdom, so this case must stay as a real
 * browser component test.
 */

const feature = test.extend<{
  Given: {
    "the tree view with files is mounted": () => Promise<void>;
  };
  When: {
    "the user single-clicks on a {fileType}": ({
      fileType,
    }: {
      fileType: string;
    }) => Promise<void>;
    "the user holds Shift and clicks on {fileName}": ({
      fileName,
    }: {
      fileName: string;
    }) => Promise<void>;
  };
  Then: {
    "there is an error alert": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the tree view with files is mounted": async () => {
        await mount(<TreeViewWithFiles />);
      },
    });
  },
  When: async ({ page }, use) => {
    await use({
      "the user single-clicks on a {fileType}": async ({ fileType }) => {
        const fileName = getFileNameByType(fileType);
        await page
          .getByRole("treeitem", { name: new RegExp(fileName) })
          .click();
      },
      "the user holds Shift and clicks on {fileName}": async ({ fileName }) => {
        await page
          .getByRole("treeitem", { name: new RegExp(fileName) })
          .click({ modifiers: ["Shift"] });
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "there is an error alert": async () => {
        await expect(page.getByRole("alert", { name: /Error/i })).toBeVisible();
      },
    });
  },
});

function getFileNameByType(fileType: string): string {
  switch (fileType.toLowerCase()) {
    case "folder":
      return "Test Folder";
    case "image":
      return "test-image.jpg";
    case "pdf":
    case "document":
      return "test-document.pdf";
    default:
      throw new Error(`Unknown file type: ${fileType}`);
  }
}

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
  await router.route("**/*.svg", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "image/svg+xml",
      body: `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><rect width="24" height="24" fill="none"/></svg>`,
    });
  });
});

test.describe("TreeView", () => {
  test.describe("Multi-Selection", () => {
    feature(
      "Should handle Shift+click without crashing",
      async ({ Given, When, Then }) => {
        await Given["the tree view with files is mounted"]();
        await When["the user single-clicks on a {fileType}"]({
          fileType: "folder",
        });
        await When["the user holds Shift and clicks on {fileName}"]({
          fileName: "test-document.pdf",
        });
        await Then["there is an error alert"]();
      },
    );
  });
});
