import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { NestedFoldersWithImageFile } from "./MainPanel.story";
import * as Jwt from "jsonwebtoken";

const feature = test.extend<{
  Given: {
    "the main panel is showing the Images section": () => Promise<void>;
    "tree view is being shown": () => Promise<void>;
    "the outer folder is open": () => Promise<void>;
    "the inner folder is selected": () => Promise<void>;
    "the outer folder is selected": () => Promise<void>;
  };
  Once: {
    "a clipboard success alert is shown": () => Promise<void>;
  };
  When: {
    "the user taps the paths's copy-to-clipboard button": () => Promise<void>;
    "the user opens the outer folder": () => Promise<void>;
    "the user opens the inner folder": () => Promise<void>;
    "the user taps the gallery section breadcrumb": () => Promise<void>;
    "the user taps the outer folder breadcrumb": () => Promise<void>;
  };
  Then: {
    "the clipboard contains a link to the file's parent folder": () => Promise<void>;
    "the clipboard contains the gallery-section link": () => Promise<void>;
    "only the gallery section is shown in the breadcrumbs": () => Promise<void>;
    "the opened folder and gallery section are shown in the breadcrumbs": () => Promise<void>;
    "the root gallery section is returned to": () => Promise<void>;
    "the outer folder is returned to": () => Promise<void>;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount, page }, use) => {
    await use({
      "the main panel is showing the Images section": async () => {
        await mount(<NestedFoldersWithImageFile />);
      },
      "tree view is being shown": async () => {
        await page.getByRole("button", { name: /views/i }).click();
        await page.getByRole("menuitem", { name: /tree view/i }).click();
      },
      "the outer folder is open": async () => {
        const treeitem = page.getByRole("treeitem", {
          name: "Outer folder",
        });
        await treeitem.locator("> div").click();
        await expect(
          page.getByRole("treeitem", { name: "Inner folder" })
        ).toBeVisible();
      },
      "the inner folder is selected": async () => {
        await page
          .getByRole("treeitem", { name: "Inner folder" })
          .locator("> div")
          .click();
      },
      "the outer folder is selected": async () => {
        await page
          .getByRole("treeitem", { name: "Outer folder" })
          .locator("> div")
          .click();
      },
    });
  },
  Once: async ({ page }, use) => {
    await use({
      "a clipboard success alert is shown": async () => {
        await expect(
          page.getByRole("alert", {
            name: "Link copied to clipboard successfully!",
          })
        ).toBeVisible();
      },
    });
  },
  When: async ({ page, context, browserName }, use) => {
    await use({
      "the user taps the paths's copy-to-clipboard button": async () => {
        // Chrome supports the clipboard API, but only after opting in
        if (browserName === "chromium") {
          await context.grantPermissions(
            ["clipboard-read", "clipboard-write"],
            {
              origin: page.url(),
            }
          );
        }
        await page.getByRole("button", { name: "Copy to clipboard" }).click();
      },
      "the user opens the outer folder": async () => {
        await page.getByRole("button", { name: "Outer folder" }).dblclick();
      },
      "the user opens the inner folder": async () => {
        await page.getByRole("button", { name: "Inner folder" }).dblclick();
      },
      "the user taps the gallery section breadcrumb": async () => {
        await page
          .getByRole("navigation", { name: "Breadcrumbs" })
          .getByRole("button", { name: "Images" })
          .click();
      },
      "the user taps the outer folder breadcrumb": async () => {
        await page
          .getByRole("navigation", { name: "Breadcrumbs" })
          .getByRole("button", { name: "Outer folder" })
          .click();
      },
    });
  },
  Then: async ({ page }, use) => {
    await use({
      "the clipboard contains a link to the file's parent folder": async () => {
        const clipboardText = await page.evaluate(() =>
          navigator.clipboard.readText()
        );
        expect(clipboardText).toMatch(/\/gallery\/1/);
      },
      "the clipboard contains the gallery-section link": async () => {
        const clipboardText = await page.evaluate(() =>
          navigator.clipboard.readText()
        );
        expect(clipboardText).toMatch(/\?mediaType=Images/);
      },
      "only the gallery section is shown in the breadcrumbs": async () => {
        const breadcrumbs = page.getByRole("navigation", {
          name: "Breadcrumbs",
        });
        const listItems = breadcrumbs.getByRole("listitem");
        await expect(listItems).toHaveCount(1);
        await expect(listItems.first()).toHaveText("Images");
      },
      "the opened folder and gallery section are shown in the breadcrumbs":
        async () => {
          const breadcrumbs = page.getByRole("navigation", {
            name: "Breadcrumbs",
          });
          const listItems = breadcrumbs.getByRole("listitem");
          await expect(listItems).toHaveCount(2);
          await expect(listItems.first()).toHaveText("Images");
          await expect(listItems.nth(1)).toHaveText("Outer folder");
        },
      "the root gallery section is returned to": async () => {
        const breadcrumbs = page.getByRole("navigation", {
          name: "Breadcrumbs",
        });
        const listItems = breadcrumbs.getByRole("listitem");
        await expect(listItems).toHaveCount(1);
        await expect(listItems.first().getByRole("button")).toHaveText(
          "Images"
        );
        await expect(
          page.getByRole("button", { name: "Outer folder" })
        ).toBeVisible();
      },
      "the outer folder is returned to": async () => {
        const breadcrumbs = page.getByRole("navigation", {
          name: "Breadcrumbs",
        });
        const listItems = breadcrumbs.getByRole("listitem");
        await expect(listItems).toHaveCount(2);
        await expect(listItems.first().getByRole("button")).toHaveText(
          "Images"
        );
        await expect(listItems.nth(1).getByRole("button")).toHaveText(
          "Outer folder"
        );
      },
    });
  },
  networkRequests: async ({}, use) => {
    await use([]);
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
  await router.route("/*/supportedExts", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({}),
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
  await router.route("/gallery/ajax/getLinkedDocuments/*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: [],
        error: null,
        success: true,
        errorMsg: null,
      }),
    });
  });
  await router.route(
    "/gallery/getUploadedFiles?mediatype=Images&currentFolderId=1&name=&pageNumber=0&sortOrder=ASC&orderBy=name&foldersOnly=false",
    (route) => {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            items: {
              totalHits: 1,
              totalPages: 1,
              results: [
                {
                  id: 2,
                  oid: { idString: "GF2" },
                  name: "Inner folder",
                  ownerName: "user1",
                  description: null,
                  creationDate: 1672531200,
                  modificationDate: 1672531200,
                  type: "Folder",
                  extension: null,
                  thumbnailId: null,
                  size: 12345,
                  version: 1,
                  originalImageOid: { idString: "GF2" },
                },
              ],
            },
            parentId: 1,
          },
          error: null,
          success: true,
          errorMsg: null,
        }),
      });
    }
  );
  await router.route("/gallery/getThumbnail/*/*", (route) => {
    return route.fulfill({
      status: 200,
      contentType: "image/png",
      body: Buffer.from(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR42mP8//8/AwAI/wH+9Q4AAAAASUVORK5CYII=",
        "base64"
      ),
    });
  });
});

feature.afterEach(({}) => {});

test.describe("MainPanel", () => {
  test.describe("breadcrumbs", () => {
    feature("The root of the gallery section", async ({ Given, Then }) => {
      await Given["the main panel is showing the Images section"]();
      await Then["only the gallery section is shown in the breadcrumbs"]();
    });
    feature("A outer folder is opened", async ({ Given, When, Then }) => {
      await Given["the main panel is showing the Images section"]();
      await When["the user opens the outer folder"]();
      await Then[
        "the opened folder and gallery section are shown in the breadcrumbs"
      ]();
    });
    feature(
      "Selecting the inner folder alters the breadcrumbs",
      async ({ Given, Then }) => {
        await Given["the main panel is showing the Images section"]();
        await Given["tree view is being shown"]();
        await Given["the outer folder is open"]();
        await Given["the inner folder is selected"]();
        await Then[
          "the opened folder and gallery section are shown in the breadcrumbs"
        ]();
        /*
         * When the user selects a file inside a folder when using tree view,
         * the breadcrumbs update to show the path to the selected file, not the
         * root folder, mimicing the behaviour of similar systems like macOS's
         * Finder.
         */
      }
    );
    feature(
      "Tapping the root gallery section breadcrumb works as a link",
      async ({ Given, When, Then }) => {
        await Given["the main panel is showing the Images section"]();
        await When["the user opens the outer folder"]();
        await When["the user taps the gallery section breadcrumb"]();
        await Then["the root gallery section is returned to"]();
      }
    );
    feature(
      "Tapping the outer folder breadcrump works as a link",
      async ({ Given, When, Then }) => {
        await Given["the main panel is showing the Images section"]();
        await When["the user opens the outer folder"]();
        await When["the user opens the inner folder"]();
        await When["the user taps the outer folder breadcrumb"]();
        await Then["the outer folder is returned to"]();
      }
    );
  });
  test.describe("Copy-to-clipboard button and tree-view", () => {
    test.skip(
      ({ browserName }) => browserName === "webkit",
      "Safari does not support clipboard API"
    );
    feature("Nothing is selected.", async ({ Given, When, Once, Then }) => {
      await Given["the main panel is showing the Images section"]();
      await Given["tree view is being shown"]();
      await Given["the outer folder is open"]();
      await When["the user taps the paths's copy-to-clipboard button"]();
      await Once["a clipboard success alert is shown"]();
      await Then["the clipboard contains the gallery-section link"]();
    });
    feature(
      "A outer folder is selected.",
      async ({ Given, When, Once, Then }) => {
        await Given["the main panel is showing the Images section"]();
        await Given["tree view is being shown"]();
        await Given["the outer folder is open"]();
        await Given["the outer folder is selected"]();
        await When["the user taps the paths's copy-to-clipboard button"]();
        await Once["a clipboard success alert is shown"]();
        await Then["the clipboard contains the gallery-section link"]();
      }
    );
    feature(
      "The inner folder is selected.",
      async ({ Given, When, Once, Then }) => {
        await Given["the main panel is showing the Images section"]();
        await Given["tree view is being shown"]();
        await Given["the outer folder is open"]();
        await Given["the inner folder is selected"]();
        await When["the user taps the paths's copy-to-clipboard button"]();
        await Once["a clipboard success alert is shown"]();
        await Then[
          "the clipboard contains a link to the file's parent folder"
        ]();
        /*
         * When the user selects a file inside a folder when using tree view,
         * and the breadcrumbs update to show the path to the selected file, the
         * adjacent button that copies the URL of the path should similarly
         * refer to the parent folder of the selected file and not the current
         * root folder.
         */
      }
    );
  });
});
