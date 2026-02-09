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

import AxeBuilder from "@axe-core/playwright";
const feature = test.extend<{
  Given: {
    "the dialog is displayed with a document without previous shares": () => Promise<void>;
    "the dialog is displayed with a document with a previous share with Bob": () => Promise<void>;
    "the dialog is displayed with a document with a previous share with Alice and Bob's group": () => Promise<void>;
    "the dialog is displayed with multiple documents": () => Promise<void>;
    "the dialog is displayed with a document that has been shared into a notebook": () => Promise<void>;
  };
  Once: emptyObject;
  When: {
    "the user selects Bob from the recipient dropdown": () => Promise<void>;
    "the user selects Alice and Bob's Group from the recipient dropdown": () => Promise<void>;
    "the user saves the new share": () => Promise<void>;
    "Alice and Bob's Group is chosen in the recipient dropdown": () => Promise<void>;
    "the user chooses unshare from the permission menu for Bob": () => Promise<void>;
    "the user changes Bob's permission from READ to EDIT": () => Promise<void>;
    "the user changes Alice and Bob's Group permission from EDIT to read": () => Promise<void>;
    "the user clicks the Change button for Alice and Bob's Group folder": () => Promise<void>;
    "the user selects a different folder in the folder selection dialog": () => Promise<void>;
  };
  Then: {
    "a dialog should be visible": () => Promise<void>;
    "a table listing Bob as a user with whom the document is shared should be visible": () => Promise<void>;
    "a table listing Alice and Bob's group as a group with whom the document is shared should be visible": () => Promise<void>;
    "no table should be visible": () => Promise<void>;
    "two tables listing the shared notebook's implicit and explicit shares should be visible": () => Promise<void>;
    "there shouldn't be any axe violations": () => Promise<void>;
    "the Save button should have changed to Done": () => Promise<void>;
    "a POST request should have been made to create the share": () => void;
    "a PUT request should have been made to update the existing share": () => void;
    "Bob is disabled in the recipient dropdown": () => Promise<void>;
    "a DELETE request should have been made to remove the share": () => void;
    "a PUT request should have been made to update Bob's permission to EDIT": () => void;
    "a PUT request should have been made to update the group's permission to {newPermission}": ({
      newPermission,
    }: {
      newPermission: "EDIT" | "READ";
    }) => void;
    "a POST request should have been made to move the document to the new folder": () => void;
  };
  networkRequests: Array<{ url: URL; postData: string | null; method: string }>;
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
      "the dialog is displayed with multiple documents": async () => {
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
  When: async ({ page }, use) => {
    await use({
      "the user selects Bob from the recipient dropdown": async () => {
        const recipientDropdown = page.getByRole("combobox", {
          name: /Add RSpace users or groups/i,
        });
        await recipientDropdown.click();
        const bobOption = page.getByRole("option", { name: /^Bob/ });
        await bobOption.click();
      },
      "the user selects Alice and Bob's Group from the recipient dropdown":
        async () => {
          const recipientDropdown = page.getByRole("combobox", {
            name: /Add RSpace users or groups/i,
          });
          await recipientDropdown.click();
          const groupOption = page.getByRole("option", {
            name: /^Alice and Bob's Group/,
          });
          await groupOption.click();
        },
      "the user saves the new share": async () => {
        // Try both Save and Done buttons as the text might change
        // Target the ShareDialog specifically, not any other dialogs
        const shareDialog = page.getByRole("dialog", { name: /Share/ });
        const saveButton = shareDialog.getByRole("button", { name: /Save/i });

        const doneButton = shareDialog.getByRole("button", { name: /Done/i });
        if (await saveButton.isVisible()) {
          await saveButton.click();
        } else if (await doneButton.isVisible()) {
          await doneButton.click();
        } else {
          throw new Error(
            "Neither Save nor Done button is visible in share dialog",
          );
        }
      },
      "the user changes Bob's permission from READ to EDIT": async () => {
        const dialog = page.getByRole("dialog");
        const table = dialog.getByRole("table");
        const bobRow = table.getByRole("row").filter({
          has: page.getByRole("button", { name: "Bob" }),
        });
        const permissionDropdown = bobRow.getByRole("combobox");
        await permissionDropdown.click();
        const editOption = page.getByRole("option", { name: /^Edit$/i });
        await editOption.click();
      },
      "the user changes Alice and Bob's Group permission from EDIT to read":
        async () => {
          const dialog = page.getByRole("dialog");
          const table = dialog.getByRole("table").first();
          const groupRow = table.getByRole("row").filter({
            has: page.getByRole("button", { name: /Alice and Bob's Group/i }),
          });
          const permissionDropdown = groupRow.getByRole("combobox");
          await permissionDropdown.click();
          const editOption = page.getByRole("option", { name: /^Read$/i });
          await editOption.click();
        },
      "Alice and Bob's Group is chosen in the recipient dropdown": async () => {
        const recipientDropdown = page.getByRole("combobox", {
          name: /Add RSpace users or groups/i,
        });
        await recipientDropdown.click();
        const groupOption = page.getByRole("option", {
          name: /Alice and Bob's Group/i,
        });
        await groupOption.click();
      },
      "the user chooses unshare from the permission menu for Bob": async () => {
        const dialog = page.getByRole("dialog");
        const table = dialog.getByRole("table");
        const bobRow = table.getByRole("row").filter({
          has: page.getByRole("button", { name: "Bob" }),
        });
        const permissionDropdown = bobRow.getByRole("combobox");
        await permissionDropdown.click();
        const unshareOption = page.getByRole("option", { name: /unshare/i });
        await unshareOption.click();
      },
      "the user clicks the Change button for Alice and Bob's Group folder":
        async () => {
          const dialog = page.getByRole("dialog");
          const table = dialog.getByRole("table").first();
          const groupRow = table.getByRole("row").filter({
            has: page.getByRole("button", { name: /Alice and Bob's Group/i }),
          });
          const changeButton = groupRow.getByRole("button", {
            name: /Change/i,
          });
          await changeButton.click();
        },
      "the user selects a different folder in the folder selection dialog":
        async () => {
          const folderDialog = page.getByRole("dialog", {
            name: /Select Shared Folder Location/i,
          });

          await expect(folderDialog).toBeVisible();
          // Wait for the folder tree to load

          await page.waitForTimeout(1000);
          // Click on the existing tree item (alice-bob)
          const treeItem = folderDialog.getByText("alice-bob").first();
          await expect(treeItem).toBeVisible();

          await treeItem.click();
          const selectButton = folderDialog.getByRole("button", {
            name: /Select/i,
          });

          await selectButton.click();
          // Wait for the folder dialog to close
          await expect(folderDialog).not.toBeVisible();
        },
    });
  },
  Then: async ({ page, networkRequests }, use) => {
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
            name: /Share Another shared document/i,
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
          await expect(row.getByRole("combobox")).toHaveText(/EDIT/i);
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
      "there shouldn't be any axe violations": async () => {
        const accessibilityScanResults = await new AxeBuilder({
          page,
        })
          /*
           * These violations are expected in component tests as we're not rendering
           * a complete page with proper document structure:
           *
           * 1. Component tests don't have main landmarks as they're isolated components
           * 2. Component tests typically don't have h1 headings as they're not full pages
           * 3. Content not in landmarks is expected in component testing context
           */
          .disableRules(["landmark-one-main", "page-has-heading-one", "region"])
          .analyze();
        expect(accessibilityScanResults.violations).toEqual([]);
      },
      "the Save button should have changed to Done": async () => {
        const doneButton = page.getByRole("button", { name: /Done/i });
        await expect(doneButton).toBeVisible();
      },
      "a POST request should have been made to create the share": () => {
        const shareRequest = networkRequests.find(
          (request) =>
            request.url.pathname === "/api/v1/share" &&
            request.method === "POST" &&
            request.postData !== null,
        );
        expect(shareRequest).toBeDefined();
      },
      "a PUT request should have been made to update the existing share":
        () => {
          const updateRequest = networkRequests.find(
            (request) =>
              request.url.pathname === "/api/v1/share" &&
              request.method === "PUT" &&
              request.postData !== null,
          );
          expect(updateRequest).toBeDefined();
          if (updateRequest != null && updateRequest.postData != null) {
            const body = JSON.parse(updateRequest.postData) as object;
            expect(body).toHaveProperty("shareId");
            expect((body as { shareId: number }).shareId).toEqual(2);
            expect(body).toHaveProperty("permission");
            expect(
              (body as { permission: "EDIT" | "READ" }).permission,
            ).toEqual("READ");
          }
        },
      "Bob is disabled in the recipient dropdown": async () => {
        const recipientDropdown = page.getByRole("combobox", {
          name: /Add RSpace users or groups/i,
        });
        await recipientDropdown.click();
        const bobOption = page.getByRole("option", { name: /^Bob/ });
        await expect(bobOption).toBeDisabled();
      },
      "a DELETE request should have been made to remove the share": () => {
        const deleteRequest = networkRequests.find(
          (request) =>
            request.method === "DELETE" &&
            request.url.pathname.startsWith("/api/v1/share/"),
        );
        expect(deleteRequest).toBeDefined();
      },
      "a PUT request should have been made to update Bob's permission to EDIT":
        () => {
          const updateRequest = networkRequests.find(
            (request) =>
              request.url.pathname === "/api/v1/share" &&
              request.method === "PUT" &&
              request.postData !== null,
          );
          expect(updateRequest).toBeDefined();
          if (updateRequest != null && updateRequest.postData != null) {
            const body = JSON.parse(updateRequest.postData) as object;
            expect(body).toHaveProperty("shareId");
            expect((body as { shareId: number }).shareId).toEqual(1);
            expect(body).toHaveProperty("permission");
            expect(
              (body as { permission: "EDIT" | "READ" }).permission,
            ).toEqual("EDIT");
          }
        },
      "a PUT request should have been made to update the group's permission to {newPermission}":
        ({ newPermission }: { newPermission: "EDIT" | "READ" }) => {
          const updateRequest = networkRequests.find(
            (request) =>
              request.url.pathname === "/api/v1/share" &&
              request.method === "PUT" &&
              request.postData !== null,
          );
          expect(updateRequest).toBeDefined();
          if (updateRequest != null && updateRequest.postData != null) {
            const body = JSON.parse(updateRequest.postData) as object;
            expect(body).toHaveProperty("shareId");
            expect(body).toHaveProperty("permission");
            expect(
              (body as { permission: "EDIT" | "READ" }).permission,
            ).toEqual(newPermission);
          }
        },
      "a POST request should have been made to move the document to the new folder":
        () => {
          const moveRequest = networkRequests.find(
            (request) =>
              request.url.pathname === "/api/v1/documents/move" &&
              request.method === "POST" &&
              request.postData !== null,
          );
          expect(moveRequest).toBeDefined();
          if (moveRequest != null && moveRequest.postData != null) {
            const body = JSON.parse(moveRequest.postData) as object;
            expect(body).toHaveProperty("docId");
            expect((body as { docId: number }).docId).toEqual(3);
            expect(body).toHaveProperty("sourceFolderId");
            expect((body as { sourceFolderId: number }).sourceFolderId).toEqual(
              1,
            );
            expect(body).toHaveProperty("targetFolderId");
            expect((body as { targetFolderId: number }).targetFolderId).toEqual(
              129,
            );
          }
        },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
  },

});
feature.beforeEach(async ({ router, page, networkRequests }) => {
  page.on("request", (request) => {
    networkRequests.push({
      url: new URL(request.url()),
      postData: request.postData(),
      method: request.method(),
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
            parentId: null,
            path: null,
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
        sharedDocName: "Another shared document",
        directShares: [
          {
            shareId: 2,
            sharedDocId: 3,
            sharedDocName: "Another shared document",
            sharerId: 1,
            sharerName: "Alice",
            permission: "EDIT",
            recipientType: "GROUP",
            recipientId: 1,
            recipientName: "Alice and Bob's Group",
            parentId: 1,
            path: "aliceAndBobGroup_SHARED",
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
            parentId: 2,
            path: "aliceAndBobGroup_SHARED/A notebook",
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
            parentId: 2,
            path: "aliceAndBobGroup_SHARED/A notebook",
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
          id: 129,
          globalId: "FL129",
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
  await router.route(/\/api\/v1\/folders\/tree.*/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        totalHits: 1,
        pageNumber: 0,
        parentId: null,
        _links: [],
        records: [
          {
            id: 2,
            globalId: "FL2",
            name: "alice-bob",
            created: "2025-09-09T12:05:14.109Z",
            lastModified: "2025-09-09T12:05:14.109Z",
            parentFolderId: null,
            type: "folder",
            _links: [],
            owner: {
              id: 1,
              username: "alice",
              email: "alice@example.com",
              firstName: "Alice",
              lastName: "Smith",
              homeFolderId: 1,
              workbenchId: null,
              hasPiRole: true,
              hasSysAdminRole: false,
              _links: [],
            },
          },
        ],
      }),
    });
  });
  await router.route("/api/v1/documents/move", async (route) => {
    const request = route.request();
    if (request.method() === "POST") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ success: true }),
      });
      return;
    }
  });
  await router.route("/api/v1/share", async (route) => {
    const request = route.request();
    if (request.method() === "POST") {
      const body: any = (await request.postDataJSON());
      const itemId = body.itemsToShare[0] as number;

      const permission = body.users[0]?.permission as string;
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          shareInfos: [
            {
              id: 294912,
              sharedItemId: itemId,
              shareItemName: "Untitled document",
              sharedTargetType: "USER",
              permission: permission,
              _links: [],
            },
          ],
          failedShares: [],
          _links: [],
        }),
      });
      return;
    }
    if (request.method() === "PUT") {
      const body: any = (await request.postDataJSON());
      const shareId = body.shareId as number;
      const permission = body.permission as string;
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          shareInfo: {
            id: shareId,
            sharedItemId: 2,
            shareItemName: "A shared document",
            sharedTargetType: "USER",
            permission: permission,
            _links: [],
          },
          _links: [],
        }),
      });
      return;
    }
    if (request.method() !== "DELETE") {
      await route.fulfill({
        status: 204,
      });
      return;
    }
  });

});
feature.afterEach(({ networkRequests }) => {
  networkRequests.splice(0, networkRequests.length);

});
test.describe("ShareDialog", () => {
  test.describe("Renders correctly", () => {
    feature("Renders a dialog", async ({ Given, Then }) => {
      await Given[
        "the dialog is displayed with a document without previous shares"
      ]();
      await Then["a dialog should be visible"]();
      await Then["there shouldn't be any axe violations"]();

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
        await Then["there shouldn't be any axe violations"]();
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
        await Then["there shouldn't be any axe violations"]();
      },

    );
    /*
     * This is because the UI became too complex to show a table for each document
     */
    feature(
      "When multiple documents are selected, no table is shown",
      async ({ Given, Then }) => {
        await Given["the dialog is displayed with multiple documents"]();
        await Then["no table should be visible"]();
        await Then["there shouldn't be any axe violations"]();
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
        await Then["there shouldn't be any axe violations"]();
      },
    );

  });
  test.describe("Creating new shares", () => {
    feature(
      "An unshared document should be sharable with a member of the same group",
      async ({ Given, When, Then }) => {
        await Given[
          "the dialog is displayed with a document without previous shares"
        ]();
        await When["the user selects Bob from the recipient dropdown"]();
        await When["the user saves the new share"]();
        await Then["the Save button should have changed to Done"]();
        Then["a POST request should have been made to create the share"]();
      },

    );
    feature(
      "Multiple documents should be sharable with a group",
      async ({ Given, When, Then }) => {
        await Given["the dialog is displayed with multiple documents"]();
        await When[
          "the user selects Alice and Bob's Group from the recipient dropdown"
        ]();
        await When["the user saves the new share"]();
        await Then["the Save button should have changed to Done"]();
        Then["a POST request should have been made to create the share"]();
      },

    );
    feature(
      "The same document shouldn't be shareable twice with the same user",
      async ({ Given, Then }) => {
        await Given[
          "the dialog is displayed with a document with a previous share with Bob"
        ]();
        await Then["Bob is disabled in the recipient dropdown"]();
      },

    );
    feature(
      "When sharing multiple documents, choosing a recipient who already has access to one of them will share both with default read permission",
      async ({ Given, When, Then }) => {
        await Given["the dialog is displayed with multiple documents"]();
        await When[
          "Alice and Bob's Group is chosen in the recipient dropdown"
        ]();
        await When["the user saves the new share"]();
        await Then["the Save button should have changed to Done"]();
        Then["a POST request should have been made to create the share"]();
        Then[
          "a PUT request should have been made to update the existing share"
        ]();
      },
    );

  });
  test.describe("Removing shares", () => {
    feature(
      "When the user chooses unshare from the permission menu, it should make a DELETE request",
      async ({ Given, When, Then }) => {
        await Given[
          "the dialog is displayed with a document with a previous share with Bob"
        ]();
        await When[
          "the user chooses unshare from the permission menu for Bob"
        ]();
        await When["the user saves the new share"]();
        Then["a DELETE request should have been made to remove the share"]();
      },
    );

  });
  test.describe("Updating shares", () => {
    feature(
      "When the user changes a user's permission from READ to EDIT, it should make a PUT request",
      async ({ Given, When, Then }) => {
        await Given[
          "the dialog is displayed with a document with a previous share with Bob"
        ]();
        await When["the user changes Bob's permission from READ to EDIT"]();
        await When["the user saves the new share"]();
        Then[
          "a PUT request should have been made to update Bob's permission to EDIT"
        ]();
      },

    );
    feature(
      "When the user changes a group's permission from EDIT to read, it should make a PUT request",
      async ({ Given, When, Then }) => {
        await Given[
          "the dialog is displayed with a document with a previous share with Alice and Bob's group"
        ]();
        await When[
          "the user changes Alice and Bob's Group permission from EDIT to read"
        ]();
        await When["the user saves the new share"]();
        Then[
          "a PUT request should have been made to update the group's permission to {newPermission}"
        ]({ newPermission: "READ" });
      },

    );
    feature(
      "When the user changes the folder location for a group share, it should make a POST request to move the document",
      async ({ Given, When, Then }) => {
        await Given[
          "the dialog is displayed with a document with a previous share with Alice and Bob's group"
        ]();
        await When[
          "the user clicks the Change button for Alice and Bob's Group folder"
        ]();
        await When[
          "the user selects a different folder in the folder selection dialog"
        ]();
        await When["the user saves the new share"]();
        Then[
          "a POST request should have been made to move the document to the new folder"
        ]();
      },
    );
  });
});
