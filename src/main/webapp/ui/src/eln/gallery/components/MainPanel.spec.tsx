import { test, expect } from "@playwright/experimental-ct-react";
import React from "react";
import { NestedFoldersWithImageFile, BunchOfImages } from "./MainPanel.story";
import * as Jwt from "jsonwebtoken";
import AxeBuilder from "@axe-core/playwright";

const feature = test.extend<{
  Given: {
    "the main panel is showing a nested folder structure": () => Promise<void>;
    "the main panel is showing a bunch of image files": () => Promise<void>;
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
    "the user taps the outer folder": () => Promise<void>;
    "the user taps the image file": (
      filename: string,
      options?: { modifiers: Array<"Shift" | "ControlOrMeta"> }
    ) => Promise<void>;
    "the user taps the arrow key": (
      key: "ArrowRight" | "Shift+ArrowRight" | "ArrowLeft" | "Shift+ArrowLeft"
    ) => Promise<void>;
  };
  Then: {
    "the clipboard contains a link to the file's parent folder": () => Promise<void>;
    "the clipboard contains the gallery-section link": () => Promise<void>;
    "only the gallery section is shown in the breadcrumbs": () => Promise<void>;
    "the opened folder and gallery section are shown in the breadcrumbs": () => Promise<void>;
    "the root gallery section is returned to": () => Promise<void>;
    "the outer folder is returned to": () => Promise<void>;
    "there shouldn't be any axe violations": () => Promise<void>;
    "the outer folder is selected": () => Promise<void>;
    "the selection is": (expectedSelection: Array<string>) => Promise<void>;
  };
  networkRequests: Array<URL>;
}>({
  Given: async ({ mount, page }, use) => {
    await use({
      "the main panel is showing a nested folder structure": async () => {
        await mount(<NestedFoldersWithImageFile />);
      },
      "the main panel is showing a bunch of image files": async () => {
        await mount(<BunchOfImages />);
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
        await page.getByRole("gridcell", { name: "Outer folder" }).dblclick();
      },
      "the user opens the inner folder": async () => {
        await page.getByRole("gridcell", { name: "Inner folder" }).dblclick();
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
      "the user taps the outer folder": async () => {
        await page.getByRole("gridcell", { name: "Outer folder" }).click();
      },
      "the user taps the image file": async (
        filename: string,
        options: { modifiers: Array<"Shift" | "ControlOrMeta"> } = {
          modifiers: [],
        }
      ) => {
        await page.getByRole("gridcell", { name: filename }).click(options);
      },
      "the user taps the arrow key": async (
        key: "ArrowRight" | "Shift+ArrowRight" | "ArrowLeft" | "Shift+ArrowLeft"
      ) => {
        await page.keyboard.press(key, {
          delay: 100, // Add a delay to ensure the key press is registered
        });
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
          page.getByRole("gridcell", { name: "Outer folder" })
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
          })
        ).toEqual([]);
      },
      "the outer folder is selected": async () => {
        await expect(
          page.getByRole("region", { name: "files listing controls" })
        ).toHaveText(/1 folder selected/);
        await expect(
          page.getByRole("gridcell", { name: "Outer folder" })
        ).toHaveAttribute("aria-selected", "true");
      },
      "the selection is": async (expectedSelection) => {
        for (const item of expectedSelection) {
          await expect(
            page.getByRole("gridcell", { name: item })
          ).toHaveAttribute("aria-selected", "true");
        }
        const selectionStatusText =
          (await page
            .getByRole("region", { name: "files listing controls" })
            .getByRole("status")
            .textContent()) ?? "";
        const matches = selectionStatusText.match(
          /((\d+) files?)?(, )?((\d+) folders?)?/
        );
        if (!matches)
          throw new Error(
            "Selection status text does not match expected format"
          );
        const [, , files = "0", , , folders = "0"] = matches;
        expect(expectedSelection.length).toEqual(
          parseInt(files, 10) + parseInt(folders, 10)
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
  feature("Should have no axe violations", async ({ Given, Then }) => {
    await Given["the main panel is showing a nested folder structure"]();
    await Then["there shouldn't be any axe violations"]();
  });

  test.describe("breadcrumbs", () => {
    feature("The root of the gallery section", async ({ Given, Then }) => {
      await Given["the main panel is showing a nested folder structure"]();
      await Then["only the gallery section is shown in the breadcrumbs"]();
    });
    feature("A outer folder is opened", async ({ Given, When, Then }) => {
      await Given["the main panel is showing a nested folder structure"]();
      await When["the user opens the outer folder"]();
      await Then[
        "the opened folder and gallery section are shown in the breadcrumbs"
      ]();
    });
    feature(
      "Selecting the inner folder alters the breadcrumbs",
      async ({ Given, Then }) => {
        await Given["the main panel is showing a nested folder structure"]();
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
        await Given["the main panel is showing a nested folder structure"]();
        await When["the user opens the outer folder"]();
        await When["the user taps the gallery section breadcrumb"]();
        await Then["the root gallery section is returned to"]();
      }
    );
    feature(
      "Tapping the outer folder breadcrump works as a link",
      async ({ Given, When, Then }) => {
        await Given["the main panel is showing a nested folder structure"]();
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
      await Given["the main panel is showing a nested folder structure"]();
      await Given["tree view is being shown"]();
      await Given["the outer folder is open"]();
      await When["the user taps the paths's copy-to-clipboard button"]();
      await Once["a clipboard success alert is shown"]();
      await Then["the clipboard contains the gallery-section link"]();
    });
    feature(
      "A outer folder is selected.",
      async ({ Given, When, Once, Then }) => {
        await Given["the main panel is showing a nested folder structure"]();
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
        await Given["the main panel is showing a nested folder structure"]();
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
  feature.describe("Grid view", () => {
    feature.describe("Selection", () => {
      feature(
        "When a file is tapped, it should become selected",
        async ({ Given, When, Then }) => {
          await Given["the main panel is showing a bunch of image files"]();
          await When["the user taps the image file"]("Image0.jpg");
          await Then["the selection is"](["Image0.jpg"]);
        }
      );
      feature(
        "Ctrl-clicking on a second file, adds it to the selection",
        async ({ Given, When, Then }) => {
          await Given["the main panel is showing a bunch of image files"]();
          await When["the user taps the image file"]("Image0.jpg");
          await When["the user taps the image file"]("Image1.jpg", {
            modifiers: ["ControlOrMeta"],
          });
          await Then["the selection is"](["Image0.jpg", "Image1.jpg"]);
        }
      );
      feature(
        "Shift-clicking on a second file select the region",
        async ({ Given, When, Then }) => {
          await Given["the main panel is showing a bunch of image files"]();
          await When["the user taps the image file"]("Image0.jpg");
          await When["the user taps the image file"]("Image5.jpg", {
            modifiers: ["Shift"],
          });
          await Then["the selection is"]([
            "Image0.jpg",
            "Image1.jpg",
            "Image4.jpg",
            "Image5.jpg",
          ]);
        }
      );
      feature(
        "Shift-clicking a second time modifies the selection based on the first click",
        async ({ Given, When, Then }) => {
          await Given["the main panel is showing a bunch of image files"]();
          await When["the user taps the image file"]("Image1.jpg");
          await When["the user taps the image file"]("Image6.jpg", {
            modifiers: ["Shift"],
          });
          await When["the user taps the image file"]("Image7.jpg", {
            modifiers: ["Shift"],
          });
          await Then["the selection is"]([
            "Image1.jpg",
            "Image2.jpg",
            "Image3.jpg",
            "Image5.jpg",
            "Image6.jpg",
            "Image7.jpg",
          ]);
          await When["the user taps the image file"]("Image4.jpg", {
            modifiers: ["Shift"],
          });
          await Then["the selection is"]([
            "Image0.jpg",
            "Image1.jpg",
            "Image4.jpg",
            "Image5.jpg",
          ]);
        }
      );
      feature(
        "Ctrl-clicking an unselected file after shift-clicking several files should expand the selection",
        async ({ Given, When, Then }) => {
          await Given["the main panel is showing a bunch of image files"]();
          await When["the user taps the image file"]("Image0.jpg");
          await When["the user taps the image file"]("Image5.jpg", {
            modifiers: ["Shift"],
          });
          await When["the user taps the image file"]("Image6.jpg", {
            modifiers: ["ControlOrMeta"],
          });
          await Then["the selection is"]([
            "Image0.jpg",
            "Image1.jpg",
            "Image4.jpg",
            "Image5.jpg",
            "Image6.jpg",
          ]);
        }
      );
      feature(
        "Ctrl-clicking a selected file after shift-clicking several files should reduce the selection",
        async ({ Given, When, Then }) => {
          await Given["the main panel is showing a bunch of image files"]();
          await When["the user taps the image file"]("Image0.jpg");
          await When["the user taps the image file"]("Image5.jpg", {
            modifiers: ["Shift"],
          });
          await When["the user taps the image file"]("Image1.jpg", {
            modifiers: ["ControlOrMeta"],
          });
          await Then["the selection is"]([
            "Image0.jpg",
            "Image4.jpg",
            "Image5.jpg",
          ]);
        }
      );
      feature(
        "Pressing an arrow key moves the selection",
        async ({ Given, When, Then }) => {
          await Given["the main panel is showing a bunch of image files"]();
          await When["the user taps the image file"]("Image0.jpg");
          await When["the user taps the arrow key"]("ArrowRight");
          await Then["the selection is"](["Image1.jpg"]);
        }
      );
      feature(
        "Pressing an arrow key after selecting multiple files with ctrl moves the selection relative to the second selection",
        async ({ Given, When, Then }) => {
          await Given["the main panel is showing a bunch of image files"]();
          await When["the user taps the image file"]("Image0.jpg");
          await When["the user taps the image file"]("Image1.jpg", {
            modifiers: ["ControlOrMeta"],
          });
          await When["the user taps the arrow key"]("ArrowRight");
          await Then["the selection is"](["Image2.jpg"]);
        }
      );
      feature(
        "Pressing an arrow key after selecting multiple files with shift moves the selection relative to the second selection",
        async ({ Given, When, Then }) => {
          await Given["the main panel is showing a bunch of image files"]();
          await When["the user taps the image file"]("Image0.jpg");
          await When["the user taps the image file"]("Image1.jpg", {
            modifiers: ["Shift"],
          });
          await When["the user taps the arrow key"]("ArrowRight");
          await Then["the selection is"](["Image2.jpg"]);
        }
      );
      feature(
        "Pressing shift-arrow key after selecting multiple files with ctrl expands the selection",
        async ({ Given, When, Then }) => {
          await Given["the main panel is showing a bunch of image files"]();
          await When["the user taps the image file"]("Image0.jpg");
          await When["the user taps the image file"]("Image1.jpg", {
            modifiers: ["ControlOrMeta"],
          });
          await Then["the selection is"](["Image0.jpg", "Image1.jpg"]);
          await When["the user taps the arrow key"]("Shift+ArrowRight");
          await Then["the selection is"]([
            "Image0.jpg",
            "Image1.jpg",
            "Image2.jpg",
          ]);
        }
      );
      feature(
        "Pressing shift-arrow key after selecting multiple files with shift expands the selection",
        async ({ Given, When, Then }) => {
          await Given["the main panel is showing a bunch of image files"]();
          await When["the user taps the image file"]("Image0.jpg");
          await When["the user taps the image file"]("Image1.jpg", {
            modifiers: ["Shift"],
          });
          await Then["the selection is"](["Image0.jpg", "Image1.jpg"]);
          await When["the user taps the arrow key"]("Shift+ArrowRight");
          await Then["the selection is"]([
            "Image0.jpg",
            "Image1.jpg",
            "Image2.jpg",
          ]);
        }
      );
      feature(
        "Shift-arrowing a second time modifies the selection based on the first click",
        async ({ Given, When, Then }) => {
          await Given["the main panel is showing a bunch of image files"]();
          await When["the user taps the image file"]("Image1.jpg");
          await When["the user taps the arrow key"]("Shift+ArrowRight");
          await Then["the selection is"](["Image1.jpg", "Image2.jpg"]);
          await When["the user taps the arrow key"]("Shift+ArrowLeft");
          await When["the user taps the arrow key"]("Shift+ArrowLeft");
          await Then["the selection is"](["Image0.jpg", "Image1.jpg"]);
        }
      );
    });
  });
});
