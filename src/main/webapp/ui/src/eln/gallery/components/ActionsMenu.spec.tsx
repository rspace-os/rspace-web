import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import * as Jwt from "jsonwebtoken";

import AxeBuilder from "@axe-core/playwright";
// Import the story components
import {
  ActionsMenuWithNonFolder,
  ActionsMenuWithFolder,
  ActionsMenuWithMultipleFiles,
  ActionsMenuWithSnippet,
  ActionsMenuWithMixedSelection,
  ActionsMenuWithMultipleSnippets,
  ActionsMenuWithSnippetMissingGlobalId,
} from "./ActionsMenu.story";

test.skip(
  ({ browserName }) => browserName === "webkit",
  "Flaky on WebKit",
);

const feature = test.extend<{
  Given: {
    "the actions menu with a non-folder is mounted": () => Promise<void>;
    "the actions menu with a folder is mounted": () => Promise<void>;
    "the actions menu with multiple files is mounted": () => Promise<void>;
    "the actions menu with a snippet is mounted": () => Promise<void>;
    "the actions menu with mixed selection is mounted": () => Promise<void>;
    "the actions menu with multiple snippets is mounted": () => Promise<void>;
    "the actions menu with a snippet missing global ID is mounted": () => Promise<void>;
  };
  When: {
    "the user clicks the actions menu button": () => Promise<void>;
    "the user selects 'Export' from the menu": () => Promise<void>;
    "the user selects 'Share' from the menu": () => Promise<void>;
    "the user selects Bob from share recipient dropdown": () => Promise<void>;
    "the user saves the share dialog": () => Promise<void>;
  };
  Then: {
    "the actions menu should be visible": () => Promise<void>;
    "the Open option should not be visible": () => Promise<void>;
    "the Open option should be visible": () => Promise<void>;
    "the Download option should be disabled": () => Promise<void>;
    "the Share option should be visible": () => Promise<void>;
    "the Share option should be disabled": () => Promise<void>;
    "the Share option should be enabled": () => Promise<void>;
    "the share dialog for the selected snippet should be visible": () => Promise<void>;
    "the share dialog for two snippets should be visible": () => Promise<void>;
    "share info should be requested for both selected snippets": () => void;
    "the Share disabled reason for missing global IDs should be visible": () => Promise<void>;
    "a share success alert should be visible": () => Promise<void>;
    "the share dialog should close": () => Promise<void>;
    "there shouldn't be any axe violations": () => Promise<void>;
  };
  networkRequests: Array<URL>;
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
      "the actions menu with a snippet is mounted": async () => {
        await mount(<ActionsMenuWithSnippet />);
      },
      "the actions menu with mixed selection is mounted": async () => {
        await mount(<ActionsMenuWithMixedSelection />);
      },
      "the actions menu with multiple snippets is mounted": async () => {
        await mount(<ActionsMenuWithMultipleSnippets />);
      },
      "the actions menu with a snippet missing global ID is mounted":
        async () => {
          await mount(<ActionsMenuWithSnippetMissingGlobalId />);
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
      "the user selects 'Share' from the menu": async () => {
        await page.getByRole("menuitem", { name: /share/i }).click();
      },
      "the user selects Bob from share recipient dropdown": async () => {
        const shareDialog = page.getByRole("dialog");
        const recipientDropdown = shareDialog.getByRole("combobox", {
          name: /Add RSpace users or groups/i,
        });
        await recipientDropdown.click();
        await page.getByRole("option", { name: /^Bob/ }).click();
      },
      "the user saves the share dialog": async () => {
        await page.getByRole("dialog").getByRole("button", { name: /Save/i }).click();
      },
    });
  },
  Then: async ({ page, networkRequests }, use) => {
    await use({
      "the actions menu should be visible": async () => {
        await expect(
          page.getByRole("button", { name: /actions/i }),
        ).toBeVisible({ timeout: 5000 });
      },
      "the Open option should not be visible": async () => {
        await expect(
          page.getByRole("menuitem", { name: /open/i }),
        ).not.toBeVisible({ timeout: 5000 });
      },
      "the Open option should be visible": async () => {
        await expect(page.getByRole("menuitem", { name: /open/i })).toBeVisible(
          { timeout: 5000 },
        );
      },
      "the Download option should be disabled": async () => {
        await expect(
          page.getByRole("menuitem", { name: /download/i }),
        ).toBeDisabled({ timeout: 5000 });
      },
      "the Share option should be visible": async () => {
        await expect(page.getByRole("menuitem", { name: /share/i })).toBeVisible(
          { timeout: 5000 },
        );
      },
      "the Share option should be disabled": async () => {
        await expect(page.getByRole("menuitem", { name: /share/i })).toBeDisabled(
          {
            timeout: 5000,
          },
        );
      },
      "the Share option should be enabled": async () => {
        await expect(page.getByRole("menuitem", { name: /share/i })).toBeEnabled(
          {
            timeout: 5000,
          },
        );
      },
      "the share dialog for the selected snippet should be visible": async () => {
        await expect(
          page.getByRole("dialog", { name: /Share My Snippet/i }),
        ).toBeVisible({ timeout: 5000 });
      },
      "the share dialog for two snippets should be visible": async () => {
        await expect(
          page.getByRole("dialog", { name: /Share 2 snippets/i }),
        ).toBeVisible({ timeout: 5000 });
      },
      "share info should be requested for both selected snippets": () => {
        const requestedPaths = networkRequests.map((url) => url.pathname);
        expect(requestedPaths).toContain("/api/v1/share/document/3");
        expect(requestedPaths).toContain("/api/v1/share/document/5");
      },
      "the Share disabled reason for missing global IDs should be visible":
        async () => {
          const shareMenuItem = page.getByRole("menuitem", { name: /share/i });
          await expect(shareMenuItem).toContainText(
            /Cannot share snippets that are missing global IDs\./i,
            { timeout: 5000 },
          );
        },
      "a share success alert should be visible": async () => {
        await expect(
          page.getByRole("alert").filter({
            hasText: /Shares updated successfully\./i,
          }),
        ).toContainText(
          /Shares updated successfully\./i,
          { timeout: 5000 },
        );
      },
      "the share dialog should close": async () => {
        await expect(page.getByRole("dialog", { name: /Share/i })).not.toBeVisible({
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
             */
            return (
              v.description !==
                "Ensure elements with an ARIA role that require child roles contain them" &&
              v.id !== "landmark-one-main" &&
              v.id !== "page-has-heading-one" &&
              v.id !== "region"
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
feature.beforeEach(async ({ router, page, networkRequests }) => {
  page.on("request", (request) => {
    networkRequests.push(new URL(request.url()));
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
  await router.route("/collaboraOnline/supportedExts", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({}),
    });

  });
  await router.route("/integration/integrationInfo?name=RAID", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        success: true,
        data: {
          name: "RAID",
          displayName: "RAiD",
          available: false,
          enabled: false,
          oauthConnected: false,
          options: {
            RAID_CONFIGURED_SERVERS: [],
          },
        },
      }),
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
        hasPiRole: false,
        hasSysAdminRole: false,
        workbenchId: 1,
      }),
    });
  });
  await router.route("/api/v1/share/document/3", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        sharedDocId: 3,
        sharedDocName: "My Snippet",
        directShares: [],
        notebookShares: [],
      }),
    });
  });
  await router.route("/api/v1/share/document/5", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        sharedDocId: 5,
        sharedDocName: "My Second Snippet",
        directShares: [],
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
  await router.route("/api/v1/userDetails/groupMembers", async (route) => {
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
  await router.route("/api/v1/folders/1*", async (route) => {
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
        pathToRootFolder: [],
        _links: [],
      }),
    });
  });
  await router.route("/api/v1/share", async (route) => {
    const request = route.request();
    if (request.method() === "POST") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          shareInfos: [],
          failedShares: [],
          _links: [],
        }),
      });
      return;
    }
    if (request.method() === "PUT") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          shareInfo: {
            id: 1,
            sharedItemId: 3,
            shareItemName: "My Snippet",
            sharedTargetType: "USER",
            permission: "READ",
            _links: [],
          },
          _links: [],
        }),
      });
      return;
    }
    await route.fulfill({
      status: 204,
      body: "",
    });
  });
  await router.route("/api/v1/share/*", async (route) => {
    await route.fulfill({
      status: 204,
      body: "",
    });
  });

});
feature.afterEach(({ networkRequests }) => {
  networkRequests.splice(0, networkRequests.length);
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
      },

    );
    feature(
      "When the selected file is a folder, open should be visible",
      async ({ Given, When, Then }) => {
        await Given["the actions menu with a folder is mounted"]();
        await When["the user clicks the actions menu button"]();
        await Then["the Open option should be visible"]();
      },

    );
    feature(
      "When the selected file is a snippet, download should be disabled",
      async ({ Given, When, Then }) => {
        await Given["the actions menu with a snippet is mounted"]();
        await When["the user clicks the actions menu button"]();
        await Then["the Download option should be disabled"]();
      },
    );
    feature(
      "Share should always be visible and enabled when only snippets are selected",
      async ({ Given, When, Then }) => {
        await Given["the actions menu with a snippet is mounted"]();
        await When["the user clicks the actions menu button"]();
        await Then["the Share option should be visible"]();
        await Then["the Share option should be enabled"]();
      },
    );
    feature(
      "Share should be disabled when snippets and non-snippets are selected together",
      async ({ Given, When, Then }) => {
        await Given["the actions menu with mixed selection is mounted"]();
        await When["the user clicks the actions menu button"]();
        await Then["the Share option should be visible"]();
        await Then["the Share option should be disabled"]();
      },
    );
    feature(
      "Share should open dialog for a single snippet",
      async ({ Given, When, Then }) => {
        await Given["the actions menu with a snippet is mounted"]();
        await When["the user clicks the actions menu button"]();
        await When["the user selects 'Share' from the menu"]();
        await Then["the share dialog for the selected snippet should be visible"]();
      },
    );
    feature(
      "Share should pass all selected snippets to the dialog",
      async ({ Given, When, Then }) => {
        await Given["the actions menu with multiple snippets is mounted"]();
        await When["the user clicks the actions menu button"]();
        await When["the user selects 'Share' from the menu"]();
        await Then["the share dialog for two snippets should be visible"]();
        await Then["share info should be requested for both selected snippets"]();
      },
    );
    feature(
      "Share should be disabled when snippet global ID is missing",
      async ({ Given, When, Then }) => {
        await Given[
          "the actions menu with a snippet missing global ID is mounted"
        ]();
        await When["the user clicks the actions menu button"]();
        await Then["the Share option should be disabled"]();
        await Then[
          "the Share disabled reason for missing global IDs should be visible"
        ]();
      },
    );
    feature(
      "Saving a gallery share should show success alert and close dialog",
      async ({ Given, When, Then }) => {
        await Given["the actions menu with a snippet is mounted"]();
        await When["the user clicks the actions menu button"]();
        await When["the user selects 'Share' from the menu"]();
        await When["the user selects Bob from share recipient dropdown"]();
        await When["the user saves the share dialog"]();
        await Then["a share success alert should be visible"]();
        await Then["the share dialog should close"]();
      },
    );
  });
});
