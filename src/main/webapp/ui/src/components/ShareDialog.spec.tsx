import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { NoPreviousShares, SharedWithAnotherUser } from "./ShareDialog.story";
import { type emptyObject } from "../util/types";
import * as Jwt from "jsonwebtoken";

const feature = test.extend<{
  Given: {
    "the dialog is displayed with a document without previous shares": () => Promise<void>;
    "the dialog is displayed with a document with a previous share with Bob": () => Promise<void>;
  };
  Once: emptyObject;
  When: emptyObject;
  Then: {
    "a dialog should be visible": () => Promise<void>;
    "a table listing Bob as a user with whom the document is shared should be visible": () => Promise<void>;
  };
}>({
  Given: async ({ mount }, use) => {
    await use({
      "the dialog is displayed with a document without previous shares":
        async () => {
          await mount(<NoPreviousShares />);
        },
      "the dialog is displayed with a document with a previous share with Bob":
        async () => {
          await mount(<SharedWithAnotherUser />);
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
      "a table listing Bob as a user with whom the document is shared should be visible":
        async () => {
          const dialog = page.getByRole("dialog", {
            name: /Share A shared document/i,
          });
          await expect(dialog).toBeVisible();
          const table = dialog.getByRole("table");
          await expect(table).toBeVisible();
          const row = table.getByRole("row").nth(1);
          await expect(row).toBeVisible();
          await expect(
            row.getByRole("cell").getByRole("button", { name: "Bob" }),
          ).toBeVisible();
          await expect(row.getByRole("combobox")).toHaveText(/READ/i);
          await expect(row.getByRole("cell").nth(3)).toHaveText("—");
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
  await router.route("/api/v1/share/document/2", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        sharedDocId: 2,
        sharedDocName: "A shared document",
        directShares: [
          {
            shareId: 1,
            sharedDocId: 2,
            sharedDocName: "A shared document",
            sharerId: 1,
            sharerName: "Alice",
            permission: "READ",
            recipientType: "USER",
            recipientId: 2,
            recipientName: "Bob",
            locationId: null,
            locationName: null,
          },
        ],
        notebookShares: [],
      }),
    });
  });
  await router.route("/api/v1/groups", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([
        {
          id: 1,
          globalId: "GP1",
          name: "Alice and Bob's Group",
          type: "LAB_GROUP",
          sharedFolderId: 1,
          members: [
            {
              id: 1,
              username: "alice",
              role: "PI",
            },
            {
              id: 2,
              username: "bob",
              role: "USER",
            },
          ],
          uniqueName: "aliceAndBobGroup",
          _links: [],
        },
      ]),
    });
  });
  await router.route("api/v1/userDetails/groupMembers", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([
        {
          id: 2,
          username: "bob",
          email: "bob@example.com",
          firstName: "Bob",
          lastName: "",
          homeFolderId: 2,
          workbenchId: 1,
          hasPiRole: false,
          hasSysAdminRole: false,
          _links: [],
        },
      ]),
    });
  });
  await router.route(
    "/api/v1/folders/1?includePathToRootFolder=true",
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: 1,
          globalId: "FL1",
          name: "alice-bob",
          created: "2025-09-09T12:05:14.109Z",
          lastModified: "2025-09-09T12:05:14.109Z",
          parentFolderId: 124,
          notebook: false,
          mediaType: null,
          pathToRootFolder: [
            {
              id: 128,
              globalId: "FL128",
              name: "IndividualShareItems",
              created: "2025-09-09T12:05:13.716Z",
              lastModified: "2025-09-09T12:05:13.716Z",
              parentFolderId: 125,
              notebook: false,
              mediaType: null,
              pathToRootFolder: null,
              _links: [],
            },
            {
              id: 125,
              globalId: "FL125",
              name: "Shared",
              created: "2025-09-09T12:05:13.691Z",
              lastModified: "2025-09-09T12:05:13.691Z",
              parentFolderId: 124,
              notebook: false,
              mediaType: null,
              pathToRootFolder: null,
              _links: [],
            },
            {
              id: 124,
              globalId: "FL124",
              name: "alice",
              created: "2025-09-09T12:05:13.223Z",
              lastModified: "2025-09-09T12:05:13.223Z",
              parentFolderId: null,
              notebook: false,
              mediaType: null,
              pathToRootFolder: null,
              _links: [],
            },
          ],
          _links: [
            {
              link: "http://localhost:8080/api/v1/folders/154",
              rel: "self",
            },
          ],
        }),
      });
    },
  );
});

test.describe("ShareDialog", () => {
  feature("Renders a dialog", async ({ Given, Then }) => {
    await Given[
      "the dialog is displayed with a document without previous shares"
    ]();
    await Then["a dialog should be visible"]();
  });

  feature(
    "When a document has been shared with another user, there's a table",
    async ({ Given, Then }) => {
      await Given[
        "the dialog is displayed with a document with a previous share with Bob"
      ]();
      await Then[
        "a table listing Bob as a user with whom the document is shared should be visible"
      ]();
    },
  );
});
