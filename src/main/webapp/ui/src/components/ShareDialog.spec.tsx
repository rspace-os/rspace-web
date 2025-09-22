import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { NoPreviousShares } from "./ShareDialog.story";
import { type emptyObject } from "../util/types";
import * as Jwt from "jsonwebtoken";

const feature = test.extend<{
  Given: {
    "the dialog is displayed with a document without previous shares": () => Promise<void>;
  };
  Once: emptyObject;
  When: emptyObject;
  Then: {
    "a dialog should be visible": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the dialog is displayed with a document without previous shares":
        async () => {
          await mount(<NoPreviousShares />);
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
      "a dialog should be visible": async () => {
        const dialog = page.getByRole("dialog", {
          name: /Share Sample Document 1/i,
        });
        await expect(dialog).toBeVisible();
        await expect(dialog).toHaveText(
          /This document is not directly shared with anyone./i,
        );
      },
    });
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
  await router.route("/api/v1/userDetails/whoami", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        id: 1,
        username: "testuser",
        email: "test@example.com",
        firstName: "Test",
        lastName: "User",
      }),
    });
  });
  await router.route("/api/v1/share/document/1", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        sharedDocId: 1,
        sharedDocName: "Sample Document 1",
        directShares: [],
        notebookShares: [],
      }),
    });
  });
  await router.route("/api/v1/groups", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([]),
    });
  });
  await router.route("api/v1/userDetails/groupMembers", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([]),
    });
  });
});

test.describe("ShareDialog", () => {
  feature("Renders a dialog", async ({ Given, Then }) => {
    await Given[
      "the dialog is displayed with a document without previous shares"
    ]();
    await Then["a dialog should be visible"]();
  });
});
