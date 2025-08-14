import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { TestFolderTreeExample } from "./FolderTree.story";
import { type emptyObject } from "../util/types";
import { type RouterFixture } from "@playwright/experimental-ct-core";
import * as Jwt from "jsonwebtoken";

const feature = test.extend<{
  Given: {
    "the FolderTree component is rendered with mocked API responses": () => Promise<void>;
  };
  Once: emptyObject;
  When: {
    "the user expands a folder node": (params: {
      folderName: string;
    }) => Promise<void>;
    "the user clicks on a folder": (params: {
      folderName: string;
    }) => Promise<void>;
    "the user clicks the Load More button": () => Promise<void>;
  };
  Then: {
    "the folder tree should display the root folders": () => Promise<void>;
    "the folder should be expanded showing its subfolders": (params: {
      folderName: string;
    }) => Promise<void>;
    "the folder should be selected": (params: {
      folderName: string;
    }) => Promise<void>;
    "the selected folder details should be displayed": (params: {
      folderName: string;
      folderId: number;
    }) => Promise<void>;
    "a Load More button should be visible": () => Promise<void>;
    "additional folders should be loaded": () => Promise<void>;
  };
}>({
  Given: async ({ mount, router }, use) => {
    await use({
      "the FolderTree component is rendered with mocked API responses":
        async () => {
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
          await router.route("**/api/v1/folders/tree**", async (route) => {
            const url = new URL(route.request().url());
            const typesToInclude = url.searchParams.get("typesToInclude");

            if (typesToInclude === "folder") {
              await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify({
                  totalHits: 3,
                  pageNumber: 0,
                  _links: [],
                  parentId: null,
                  records: [
                    {
                      id: 100,
                      globalId: "FL100",
                      name: "Research Projects",
                      created: "2025-01-01T10:00:00.000Z",
                      lastModified: "2025-01-15T14:00:00.000Z",
                      parentFolderId: null,
                      type: "FOLDER",
                      _links: [],
                      owner: {
                        id: 1,
                        username: "researcher",
                        email: "researcher@example.com",
                        firstName: "Research",
                        lastName: "User",
                        homeFolderId: 124,
                        workbenchId: null,
                        hasPiRole: true,
                        hasSysAdminRole: false,
                        _links: [],
                      },
                    },
                    {
                      id: 101,
                      globalId: "FL101",
                      name: "Lab Notebooks",
                      created: "2025-01-02T10:00:00.000Z",
                      lastModified: "2025-01-16T14:00:00.000Z",
                      parentFolderId: null,
                      type: "FOLDER",
                      _links: [],
                      owner: {
                        id: 1,
                        username: "researcher",
                        email: "researcher@example.com",
                        firstName: "Research",
                        lastName: "User",
                        homeFolderId: 124,
                        workbenchId: null,
                        hasPiRole: true,
                        hasSysAdminRole: false,
                        _links: [],
                      },
                    },
                  ],
                }),
              });
            } else {
              await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify({
                  totalHits: 0,
                  pageNumber: 0,
                  records: [],
                }),
              });
            }
          });

          await router.route("**/api/v1/folders/tree/100**", async (route) => {
            const url = new URL(route.request().url());
            const pageNumber = parseInt(
              url.searchParams.get("pageNumber") || "0",
            );

            if (pageNumber === 0) {
              await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify({
                  totalHits: 25,
                  pageNumber: 0,
                  _links: [],
                  parentId: 100,
                  records: Array.from({ length: 20 }, (_, i) => ({
                    id: 200 + i,
                    globalId: `FL${200 + i}`,
                    name: `Subfolder ${i + 1}`,
                    created: "2025-01-01T10:00:00.000Z",
                    lastModified: "2025-01-15T14:00:00.000Z",
                    parentFolderId: 100,
                    type: "FOLDER",
                    _links: [],
                    owner: {
                      id: 1,
                      username: "researcher",
                      email: "researcher@example.com",
                      firstName: "Research",
                      lastName: "User",
                      homeFolderId: 124,
                      workbenchId: null,
                      hasPiRole: true,
                      hasSysAdminRole: false,
                      _links: [],
                    },
                  })),
                }),
              });
            } else {
              await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify({
                  totalHits: 25,
                  pageNumber: 1,
                  _links: [],
                  parentId: 100,
                  records: Array.from({ length: 5 }, (_, i) => ({
                    id: 220 + i,
                    globalId: `FL${220 + i}`,
                    name: `Subfolder ${20 + i + 1}`,
                    created: "2025-01-01T10:00:00.000Z",
                    lastModified: "2025-01-15T14:00:00.000Z",
                    parentFolderId: 100,
                    type: "FOLDER",
                    _links: [],
                    owner: {
                      id: 1,
                      username: "researcher",
                      email: "researcher@example.com",
                      firstName: "Research",
                      lastName: "User",
                      homeFolderId: 124,
                      workbenchId: null,
                      hasPiRole: true,
                      hasSysAdminRole: false,
                      _links: [],
                    },
                  })),
                }),
              });
            }
          });

          await mount(<TestFolderTreeExample />);
        },
    });
  },
  Once: async ({}, use) => {
    await use({});
  },
  When: async ({ page }, use) => {
    await use({
      "the user expands a folder node": async ({ folderName }) => {
        const treeItem = page.getByRole("treeitem", {
          name: new RegExp(folderName),
        });
        await treeItem.locator("svg").first().click();
      },
      "the user clicks on a folder": async ({ folderName }) => {
        const treeItem = page.getByRole("treeitem", {
          name: new RegExp(folderName),
        });
        await treeItem.click();
      },
      "the user clicks the Load More button": async () => {
        const loadMoreButton = page.getByRole("button", {
          name: "Load More...",
        });
        await loadMoreButton.click();
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "the folder tree should display the root folders": async () => {
        await expect(
          page.getByRole("treeitem", { name: /Research Projects/ }),
        ).toBeVisible();
        await expect(
          page.getByRole("treeitem", { name: /Lab Notebooks/ }),
        ).toBeVisible();
      },
      "the folder should be expanded showing its subfolders": async ({
        folderName,
      }) => {
        const treeItem = page.getByRole("treeitem", {
          name: new RegExp(folderName),
        });
        await expect(treeItem).toHaveAttribute("aria-expanded", "true");
      },
      "the folder should be selected": async ({ folderName }) => {
        const treeItem = page.getByRole("treeitem", {
          name: new RegExp(folderName),
        });
        await expect(treeItem).toHaveAttribute("aria-selected", "true");
      },
      "the selected folder details should be displayed": async ({
        folderName,
        folderId,
      }) => {
        await expect(page.getByTestId("selected-folder")).toBeVisible();
        await expect(page.getByTestId("folder-name")).toHaveText(folderName);
        await expect(page.getByTestId("folder-id")).toHaveText(
          folderId.toString(),
        );
      },
      "a Load More button should be visible": async () => {
        await expect(
          page.getByRole("button", { name: "Load More..." }),
        ).toBeVisible();
      },
      "additional folders should be loaded": async () => {
        await expect(
          page.getByRole("treeitem", { name: /Subfolder 21/ }),
        ).toBeVisible();
      },
    });
  },
});

test.describe("FolderTree", () => {
  feature("Initially displays root folder listing", async ({ Given, Then }) => {
    await Given[
      "the FolderTree component is rendered with mocked API responses"
    ]();
    await Then["the folder tree should display the root folders"]();
  });

  feature("Allows selecting folders", async ({ Given, When, Then }) => {
    await Given[
      "the FolderTree component is rendered with mocked API responses"
    ]();
    await When["the user clicks on a folder"]({
      folderName: "Research Projects",
    });
    await Then["the folder should be selected"]({
      folderName: "Research Projects",
    });
    await Then["the selected folder details should be displayed"]({
      folderName: "Research Projects",
      folderId: 100,
    });
  });

  feature(
    "Allows expanding folder nodes to show subfolders",
    async ({ Given, When, Then }) => {
      await Given[
        "the FolderTree component is rendered with mocked API responses"
      ]();
      await When["the user expands a folder node"]({
        folderName: "Research Projects",
      });
      await Then["the folder should be expanded showing its subfolders"]({
        folderName: "Research Projects",
      });
    },
  );

  feature(
    "Shows Load More button for folders with more than 20 items",
    async ({ Given, When, Then }) => {
      await Given[
        "the FolderTree component is rendered with mocked API responses"
      ]();
      await When["the user expands a folder node"]({
        folderName: "Research Projects",
      });
      await Then["a Load More button should be visible"]();
    },
  );

  feature(
    "Loads additional folders when Load More is clicked",
    async ({ Given, When, Then }) => {
      await Given[
        "the FolderTree component is rendered with mocked API responses"
      ]();
      await When["the user expands a folder node"]({
        folderName: "Research Projects",
      });
      await When["the user clicks the Load More button"]();
      await Then["additional folders should be loaded"]();
    },
  );
});
