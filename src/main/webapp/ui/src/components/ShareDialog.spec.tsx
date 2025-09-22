import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import {
  DocumentThatHasBeenSharedIntoANotebook,
  MultipleDocuments,
  NoPreviousShares,
  SharedWithAGroup,
  SharedWithAnotherUser,
} from "./ShareDialog.story";
import { type emptyObject } from "../util/types";
import * as Jwt from "jsonwebtoken";

const feature = test.extend<{
  Given: {
    "the dialog is displayed with a document without previous shares": () => Promise<void>;
    "the dialog is displayed with a document with a previous share with Bob": () => Promise<void>;
    "the dialog is displayed with a document with a previous share with Alice and Bob's group": () => Promise<void>;
    "the dialog is displated with multiple documents": () => Promise<void>;
    "the dialog is displayed with a document that has been shared into a notebook": () => Promise<void>;
  };
  Once: emptyObject;
  When: emptyObject;
  Then: {
    "a dialog should be visible": () => Promise<void>;
    "a table listing Bob as a user with whom the document is shared should be visible": () => Promise<void>;
    "a table listing Alice and Bob's group as a group with whom the document is shared should be visible": () => Promise<void>;
    "no table should be visible": () => Promise<void>;
    "two tables listing the shared notebook's implicit and explicit shares should be visible": () => Promise<void>;
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
      "the dialog is displayed with a document with a previous share with Alice and Bob's group":
        async () => {
          await mount(<SharedWithAGroup />);
        },
      "the dialog is displated with multiple documents": async () => {
        await mount(<MultipleDocuments />);
      },
      "the dialog is displayed with a document that has been shared into a notebook":
        async () => {
          await mount(<DocumentThatHasBeenSharedIntoANotebook />);
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
      "a table listing Alice and Bob's group as a group with whom the document is shared should be visible":
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
            row
              .getByRole("cell")
              .getByRole("button", { name: /^Alice and Bob's Group$/ }),
          ).toBeVisible();
          await expect(row.getByRole("combobox")).toHaveText(/READ/i);
          await expect(row.getByRole("cell").nth(3)).toHaveText(
            /aliceAndBobGroup_SHARED/,
          );
        },
      "no table should be visible": async () => {
        const dialog = page.getByRole("dialog", {
          name: /Share \d+ items/i,
        });
        await expect(dialog).toBeVisible();
        const table = dialog.getByRole("table");
        await expect(table).toHaveCount(0);
        await expect(
          dialog.getByRole("heading", {
            name: /adding shares to 2 documents/i,
          }),
        ).toBeVisible();
      },
      "two tables listing the shared notebook's implicit and explicit shares should be visible":
        async () => {
          const dialog = page.getByRole("dialog", {
            name: /Share A shared notebook document/i,
          });
          await expect(dialog).toBeVisible();
          const tables = dialog.getByRole("table");
          await expect(tables).toHaveCount(2);
          const directShareTable = tables.nth(0);
          const notebookShareTable = tables.nth(1);
          await expect(directShareTable).toBeVisible();
          await expect(notebookShareTable).toBeVisible();
          const directShareRow = directShareTable.getByRole("row").nth(1);
          await expect(directShareRow).toBeVisible();
          await expect(
            directShareRow
              .getByRole("cell")
              .getByRole("button", { name: /^Alice and Bob's Group$/ }),
          ).toBeVisible();
          await expect(directShareRow.getByRole("combobox")).toHaveText(
            /READ/i,
          );
          await expect(directShareRow.getByRole("cell").nth(3)).toHaveText(
            /A notebook/,
          );

          const notebookShareRow = notebookShareTable.getByRole("row").nth(1);
          await expect(notebookShareRow).toBeVisible();
          await expect(
            notebookShareRow
              .getByRole("cell")
              .getByRole("button", { name: /^Alice and Bob's Group$/ }),
          ).toBeVisible();
          await expect(notebookShareRow.getByRole("combobox")).toHaveText(
            /EDIT/i,
          );
          await expect(notebookShareRow.getByRole("combobox")).toBeDisabled();
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
  await router.route("/api/v1/share/document/3", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        sharedDocId: 3,
        sharedDocName: "A shared document",
        directShares: [
          {
            shareId: 2,
            sharedDocId: 3,
            sharedDocName: "A shared document",
            sharerId: 1,
            sharerName: "Alice",
            permission: "READ",
            recipientType: "GROUP",
            recipientId: 1,
            recipientName: "Alice and Bob's Group",
            locationId: 1,
            locationName: "aliceAndBobGroup_SHARED",
          },
        ],
        notebookShares: [],
      }),
    });
  });
  await router.route("/api/v1/share/document/4", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        sharedDocId: 4,
        sharedDocName: "A shared notebook document",
        directShares: [
          {
            shareId: 3,
            sharedDocId: 4,
            sharedDocName: "A shared notebook document",
            sharerId: 1,
            sharerName: "Alice",
            permission: "READ",
            recipientType: "GROUP",
            recipientId: 1,
            recipientName: "Alice and Bob's Group",
            locationId: 2,
            locationName: "A notebook",
          },
        ],
        notebookShares: [
          {
            shareId: 4,
            sharerId: 2,
            sharerName: "Bob",
            recipientId: 1,
            recipientName: "Alice and Bob's Group",
            recipientType: "GROUP",
            permission: "EDIT",
            locationId: 2,
            locationName: "A notebook",
          },
        ],
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

  feature(
    "When a document has been shared with another group, there's a table",
    async ({ Given, Then }) => {
      await Given[
        "the dialog is displayed with a document with a previous share with Alice and Bob's group"
      ]();
      await Then[
        "a table listing Alice and Bob's group as a group with whom the document is shared should be visible"
      ]();
    },
  );

  /*
   * This is because the UI became too complex to show a table for each document
   */
  feature(
    "When multiple documents are selected, no table is shown",
    async ({ Given, Then }) => {
      await Given["the dialog is displated with multiple documents"]();
      await Then["no table should be visible"]();
    },
  );

  feature(
    "When a document has been shared into a notebook, the implicit shares are shown",
    async ({ Given, Then }) => {
      await Given[
        "the dialog is displayed with a document that has been shared into a notebook"
      ]();
      await Then[
        "two tables listing the shared notebook's implicit and explicit shares should be visible"
      ]();
    },
  );
});
